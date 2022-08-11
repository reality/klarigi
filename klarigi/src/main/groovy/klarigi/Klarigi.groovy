package klarigi

import org.semanticweb.owlapi.model.parameters.*
import org.semanticweb.elk.owlapi.*
import org.semanticweb.elk.reasoner.config.*
import org.semanticweb.owlapi.apibinding.*
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.*
import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.io.*
import org.semanticweb.owlapi.owllink.*
import org.semanticweb.owlapi.util.*
import org.semanticweb.owlapi.search.*
import org.semanticweb.owlapi.manchestersyntax.renderer.*
import org.semanticweb.owlapi.reasoner.structural.*

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;

import java.math.MathContext

import java.util.concurrent.*
import groovyx.gpars.*
import groovyx.gpars.GParsPool


public class Klarigi {
  def data

  // A unique list containing every class directly annotated to any entity in the 
  //  corpus. Generated after data corpus is loaded at the end of loadData. It's needed
  //  for permutation, primarily, but there are probably other reasons that it's useful
  //  to keep around too...
  //  TODO: consider merging with above data, or in the class that eventually replaces that.
  def ontoHelper = [
    reasoner: null, 
    dataFactory: null,
    labels: null
  ]
  def coefficients
  def verbose
  def icFactory
  def scorer

  Klarigi(o, excludeClasses, threads) {
    verbose = o['verbose']

    loadOntology(o['ontology'])
    loadData(o['data'], o['pp'], o['group'], o['egl'], threads)
    loadIc(o['ic'], o['ontology'], o['data'], o['resnik-ic'], o['save-ic'], o['turtle'], o['pp'], o['show-warnings'])

    coefficients = Coefficients.Generate(o)
    if(o['egl']) { coefficients['min-exclusion'] = 0 }

    this.scorer = new Scorer(ontoHelper, coefficients, data, excludeClasses, o['egl'], threads)

    if(o['output']) { // blank the output file, since we will subsequently append to it. all the output stuff could probs be better abstracted.
      new File(o['output']).text = ''
    }
  }

  def loadData(dataFile, pp, interestGroups, egl, threads) {
    data = [
      groupings: new ConcurrentHashMap(),
      associations: new ConcurrentHashMap(),
      egroups: new ConcurrentHashMap(),
      ic: new ConcurrentHashMap()
    ]
    try {
      def inputFile = new File(dataFile)
      if(!inputFile.exists()) { RaiseError("Data file not found. Check --data argument.") }

      def input
      if(pp) { // Phenopackets mode
        if(verbose) {
          println "Phenopackets input mode engaged"
        }

        def toProcess = []
        if(inputFile.isDirectory()) {
          inputFile.eachFile { f ->
            if(f.getName() =~ /json$/) {
              toProcess << f
            }
          } 
        } else {
          toProcess << inputFile
        } 

        // Convert each of the phenopackets to input triples
        input = toProcess.collect { PacketConverter.Convert(PacketConverter.Load(it)) }

        if(verbose) {
          def outName = "pp_conv.tsv"
          println "Phenopackets loaded. Also saving a converted copy to $outName"
          PacketConverter.Save(input, outName) 
        }
      } else {
        input = new File(dataFile).collect { 
          it = it.tokenize('\t') 
          if(it.size() == 2) { it << '' }
          it
        }
      }

      GParsPool.withPool(threads) { p ->
      input.eachParallel {
        def (entity, terms, group) = it

        if(group) {
          def gs = group.tokenize(';')
          if(egl) {
            gs = gs.findAll { g -> interestGroups.contains(g) }
          }

          if(interestGroups) {
            def anyFalse = false 
            gs = gs.collect { interestGroups.contains(it) ? it : 'false' }
            anyFalse = gs.contains('false')
            gs = gs.findAll { it != 'false' }
            if(anyFalse) { gs << 'false' } // hey, it's cheaper than uniq
          }

          // egroups is a map of each entity to the groups it's associates with
          data.egroups[entity] = gs

          gs.each { g ->
            if(!data.groupings.containsKey(g)) {
              data.groupings[g] = new ConcurrentHashMap()
            }
            data.groupings[g][entity] = true
          }
        }

        if(terms) {
          terms = terms.tokenize(';')
          if(terms.size() > 0 && terms[0] =~ /:/ && terms[0].indexOf('http') == -1) { // stupid
            terms = terms.collect { 
              'http://purl.obolibrary.org/obo/' + it.replace(':', '_')
            }
          }
          if(!data.associations.containsKey(entity)) {
            data.associations[entity] = [:]
          }
          terms.each {
            data.associations[entity][it] = true
          }
        }
      }
      }
    } catch(e) {
      HandleError(e, verbose, "Error loading data file ($dataFile)")
    }

    // replace it with the original lists, i'll fix this once i can be bothered to rewrite the client code
    def newGroups = [:]
    data.groupings.each { k, v ->
      newGroups[k] = v.keySet().toList()
    }
    data.groupings = newGroups

    // kind of stupid but ok 
    def qa = [:]
    data.associations.each { entity, terms ->
      terms.keySet().toList().each { qa[it] = true }
    }
    data.allAssociations = qa.keySet().toList()

    if(verbose) {
      println "Done loading dataset"
    }
  }

  def sampleData() {
    def r = new Random()
    def newSample = [:] 
    data.associations.each { id, terms ->
      newSample[id] = [:]
      terms.each { // create randomly sampled profile of the same size
        newSample[id][data.allAssociations[r.nextInt(data.allAssociations.size())]] = true
      }
    }
    return newSample
  }

  def loadIc(icFile, ontologyFile, annotFile, resnikIc, saveIc, turtle, pp, showWarnings) {
    if(icFile) {
      try {
      new File(icFile).splitEachLine('\t') {
        data.ic[it[0]] = Float.parseFloat(it[1])
      }
      } catch(e) {
        HandleError(e, verbose, "Error loading information content file ($icFile)")
      }
    } else {
      try {
        icFactory = new InformationContent(ontologyFile, annotFile, resnikIc, turtle, pp)
        def allClasses = ontoHelper.reasoner.getSubClasses(ontoHelper.dataFactory.getOWLThing(), false).collect { it.getRepresentativeElement().getIRI().toString() }.unique(false)
        allClasses = allClasses.findAll { it != 'http://www.w3.org/2002/07/owl#Nothing' } // heh
        data.ic = icFactory.getInformationContent(allClasses, showWarnings)
      } catch(e) {
        HandleError(e, verbose, "Error calculating information content values")
      }
    }

    if(verbose) {
      println "Done loading IC values"
    }

    if(saveIc) {
      try {
        InformationContent.Write(data.ic, saveIc)
      } catch(e) {
        HandleError(e, verbose, "Error saving information content values ($saveIc)")
      }
    }
  }

  // Load and classify the ontology with Elk
  def loadOntology(ontologyFile) { 
    // this is so abs9olutely ridiculous (stolen from stackoverflow somewhere)
    Logger.getLogger(ElkReasoner.class).setLevel(Level.OFF);
    List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
    loggers.add(LogManager.getRootLogger());
    for(Logger logger : loggers) {
      logger.setLevel(Level.OFF);
    }

    try {
      // load ontology
      def manager = OWLManager.createOWLOntologyManager()
      def ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyFile))
      //def progressMonitor = new ConsoleProgressMonitor()
      def config = new SimpleConfiguration()
      def elkFactory = new ElkReasonerFactory() // cute

      // load labels
      def labels = [:]
      ontology.getClassesInSignature(true).each { cl ->
        def iri = cl.getIRI().toString()
        EntitySearcher.getAnnotations(cl, ontology).each { anno ->
          def property = anno.getProperty()
          OWLAnnotationValue val = anno.getValue()
          if(val instanceof OWLLiteral) {
            def literal = val.getLiteral()
            if((property.isLabel() || property.toString() == "<http://www.w3.org/2004/02/skos/core#prefLabel>") && !labels.containsKey(iri)) {
              labels[iri] = literal
            }
          }
        }
      }
   
      // Set class props (reasoning also performed here)
      ontoHelper.dataFactory = manager.getOWLDataFactory()
      ontoHelper.reasoner = elkFactory.createReasoner(ontology, config)
      ontoHelper.labels = labels
    } catch(e) {
      HandleError(e, verbose, "Error loading of processing the ontology file ($ontologyFile)")
    }

    if(verbose) {
      println "Done loading the ontology"
    }
  }

  def permutationTest(allExplanations, excludeClasses, threads, perms) {
    println "Doing permutation tests... This may take a while..."
    println "Tip: Don't use p-values produced by this process to inform hyperparameter optimisation. Beware multiple testing!"

    def i = 0
    def ae = [:] 
    def allCandidates = []
    allExplanations.each { a ->
      ae[a.cluster] = [:]
      a.results[0].each { z -> 
        ae[a.cluster][z.iri] = z 
        ae[a.cluster][z.iri].incVals = [ z.nInclusion ]
        ae[a.cluster][z.iri].excVals = [ z.nExclusion ]
        ae[a.cluster][z.iri].powVals = [ z.nPower ]
        allCandidates << z.iri
      }
    }
    allCandidates = allCandidates.unique(false)

    (0..perms).each {
      def subData = [
        associations: sampleData(),
        groupings: data.groupings,
        egroups: data.egroups, 
        ic: data.ic
      ]
      subData.allAssociations = subData.associations.collect { entity, terms ->
        terms.keySet().toList()
      }.flatten().unique(false)

      def reScorer = new Scorer(ontoHelper, coefficients, subData, excludeClasses, false, threads, allCandidates)
      i++
      if((i % 100) == 0) {
        println i
      }
     
      ae.each { g, gp ->
        def cSet = ae[g].keySet().toList()
        def candidates = reScorer.scoreClasses(g, threads, cSet, true)

        candidates.each { v ->
          def k = v.iri
          ae[g][k].incVals << v.nInclusion
          ae[g][k].excVals << v.nExclusion
          ae[g][k].powVals << v.nPower

          cSet -= k
        }

        // here we add zeroes for all items we didn't receive values for (ie they were ignored because they were not represented in the sample at all)
        cSet.each { k ->
          ae[g][k].incVals << 0
          ae[g][k].excVals << 0
          ae[g][k].powVals << 0
        }
      }
    }

    def ps = [:]
    ae.each { c, terms ->
      ps[c] = [:]
      terms.each { iri, cv ->
        ps[c][iri] = [
          incP: new BigDecimal(cv.incVals.findAll { it >= cv.nInclusion }.size() / cv.incVals.size()).round(new MathContext(3)),
          excP: new BigDecimal(cv.excVals.findAll { it >= cv.nExclusion }.size() / cv.excVals.size()).round(new MathContext(3)),
          powP: new BigDecimal(cv.powVals.findAll { it >= cv.nPower }.size() / cv.powVals.size()).round(new MathContext(3))
        ] 

        def out = []
        cv.incVals.eachWithIndex { e, idx ->
          out << [ cv.incVals[idx], cv.excVals[idx], cv.powVals[idx] ].join('\t')
        }
        new File(iri.tokenize('/').last()+'.txt').text = out.join('\n')
      }
    }

    ps
  }

  def explainCluster(cid, scoreOnly, outputScores, outputType, threads, debug, includeAll) {
    def candidates = scorer.scoreAllClasses(cid, threads, includeAll)

    println "$cid: Scoring completed. Candidates: ${candidates.size()}"

    if(outputScores) {
      try {
        def fName = false 
        if(outputType == 'latex') {
          Scorer.WriteLaTeX(cid, candidates, ontoHelper.labels, fName)
        } else {
          fName = 'scores-'+cid+'.lst'
          Scorer.Write(cid, candidates, ontoHelper.labels, fName)
          println "Output scores to $fName"
        }
      } catch(e) {
        HandleError(e, verbose, "Error saving information content values.")
      }
    }

    def res
    if(!scoreOnly) {
      res = StepDown.RunNewAlgorithm(coefficients, cid, candidates, data, threads, debug)
    } 

    return res
  }

  def explainClusters(groups, scoreOnly, outputScores, outputType, threads, debug, includeAll) {
    def missing = groups.findAll { g -> !data.groupings.containsKey(g) }
    if(missing.size() != 0) { RaiseError("Groups not found in dataset: " + missing.join(', ')) }

    def results = []

    groups.each { g ->
      results << [ cluster: g, results: explainCluster(g, scoreOnly, outputScores, outputType, threads, debug, includeAll) ]
    }

    return results
  }

  def explainAllClusters(outputScores, scoreOnly, outputType, threads, debug, includeAll) {
    explainClusters(data.groupings.keySet().toList(), scoreOnly, outputScores, outputType, threads, debug, includeAll)
  }

  def reclassify(allExplanations, excludeClasses, outClassScores, ucm, cwf, threads) {
    if(cwf) { 
      ucm = false

      def assoc = [:]
      new File(cwf).splitEachLine('\t') {
        if(!assoc.containsKey(it[1])) { assoc[it[1]] = [] }
        assoc[it[1]] << 'http://purl.obolibrary.org/obo/' + it[0].replace(':','_')
      }

      def reScorer = new Scorer(ontoHelper, coefficients, data, excludeClasses, false, threads)

       allExplanations.each { exps ->
         exps.results[0] = reScorer.scoreClasses(exps.cluster, threads, assoc[exps.cluster], true)
      }
    }

    def m = Classifier.classify(coefficients, allExplanations, data, ontoHelper, threads, ucm)
    if(!m) {
      RaiseError("Failed to build reclassifier. There may have been too few examples.")
    }

    println 'Reclassification:'
    Classifier.Print(m)
    println ''

    if(outClassScores) {
      Classifier.WriteScores(m, "reclassify")
    }
  }

  def classify(path, allExplanations, outClassScores, ucm, cwf, excludeClasses, threads) {
    loadData(path) // TODO I know, i know, this is awful state management and design. i'll fix it later

    def m = Classifier.classify(allExplanations, data, ontoHelper, threads, ucm)
    if(!m) {
      RaiseError("Failed to build classifier. There may have been too few examples.")
    }

    println 'Classification:'
    Classifier.Print(m)
    println ''

    if(outClassScores) {
      Classifier.WriteScores(m, "classify")
    }
  }

  def genSim(toFile, group) {
    if(!icFactory) {
      println "Error: IC class not loaded (--similarity and --ic are not compatible)"
      System.exit(1)
    }
    def results = icFactory.compareEntities(data.associations, data.groupings, group)
    InformationContent.WriteSimilarity(results, toFile)
  }

  def output(cid, results, pVals, egl, outType, printMembers, toFile) {
    def cSize = data.groupings[cid].size()
    if(outType) {
      if(outType == 'latex') {
        StepDown.PrintLaTeX(cid, results, pVals, egl, ontoHelper.labels, cSize, toFile)
      } else if(outType == 'tsv') {
        StepDown.PrintTSV(cid, results, ontoHelper.labels, cSize, toFile)
      }
    } else {
      StepDown.Print(cid, results, pVals, egl, ontoHelper.labels, cSize, toFile, data.groupings[cid], printMembers)
    }
  }

  def writeDataframe(prefix, allExplanations) {
    def out = []
    def subclassCache = [:]
    def expVars = allExplanations.collect { 
        allExplanations.collect { exps ->
          exps.results[0].collect { it.iri }
        }
    }.flatten().unique(false)
    out << "id\t" + expVars.collect { ontoHelper.labels[it] }.join('\t') + '\tgroup'

    data.groupings.each { group, members ->
      members.each { id ->
        out << "$id\t" + expVars.collect { iri ->
          if(!subclassCache.containsKey(iri)) {
            def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(iri))
            subclassCache[iri] = ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
          }
          def subeq = subclassCache[iri] + iri

          def label = false
          if(subeq.any { data.associations[id].containsKey(it) }) {
            label = true
          }

          label
        }.join('\t') + "\t$group" 
      }
    }

    new File("${prefix}_dataframe.tsv").text = out.join('\n')
  }

  // Exit from Exception
  static def HandleError(e, verbose, msg) {
    println msg + ': ' + e.getMessage()
    if(verbose) {
      e.printStackTrace()
    }
    System.exit(1)
  }

  // Exit from our code
  static def RaiseError(msg) {
    println "Error: " + msg
    System.exit(1)
  }
}
