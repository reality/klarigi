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
  def icFactory
  def scorer
  def o

  Klarigi(o) {
    this.o = o
    
    if(o['debug']) { println "Starting with debug mode" }

    loadOntology()
    loadData(o['data'])
    loadIc()

    coefficients = Coefficients.Generate(o)
    if(o['egl']) { coefficients['min-exclusion'] = 0 }

    this.scorer = new Scorer(ontoHelper, coefficients, data, o)

    if(o['output']) { // blank the output file, since we will subsequently append to it. all the output stuff could probs be better abstracted.
      new File(o['output']).text = ''
    }
  }

  def loadData(dataFile) {
    if(o['verbose']) { println "[...] Loading dataset" }

    def groupings = new ConcurrentHashMap();
    def associations = new ConcurrentHashMap();
    def egroups = new ConcurrentHashMap();
    def ic = new ConcurrentHashMap()
    try {
      def inputFile = new File(dataFile)
      if(!inputFile.exists()) { RaiseError("Data file not found. Check --data argument.") }

      def input
      if(o['pp']) { // Phenopackets mode. TODO: probably just try to figure this out automatically
        if(o['verbose']) {
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

        if(o['verbose']) {
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

      GParsPool.withPool(o['threads']) { p ->
      input.eachParallel {
        def (entity, terms, group) = it

        egroups[entity] = []
        if(group) {
          def gs = group.tokenize(';')
          
          if(o['group'].size() > 0) {
            if(o['egl']) {
              gs = gs.findAll { g -> o['group'].contains(g) }
            } else {
              def anyFalse = false 
              gs = gs.collect { o['group'].contains(it) ? it : 'false' }
              anyFalse = gs.contains('false')
              gs = gs.findAll { it != 'false' }
              if(anyFalse) { gs << 'false' } // hey, it's cheaper than uniq
            }
          }

          // egroups is a map of each entity to the groups it's associates with
          egroups[entity] = gs
        }

        if(terms) {
          terms = terms.tokenize(';')
          if(terms.size() > 0 && terms[0] =~ /:/ && terms[0].indexOf('http') == -1) { // stupid
            terms = terms.collect { 
              'http://purl.obolibrary.org/obo/' + it.replace(':', '_')
            }
          }
          if(!associations.containsKey(entity)) {
            associations[entity] = new ConcurrentHashMap()
          }
          terms.each {
            associations[entity][it] = true
          }
        }
      }
      }
    } catch(e) {
      HandleError(e, o['verbose'], o['debug'], "Error loading data file ($dataFile)")
    }

    // We do creation of the maps and population with two separate loops, because otherwise we get some strange behaviour with race conditions. I'm not sure why.
    def tGroup = new ConcurrentHashMap()
    GParsPool.withPool(o['threads']) { p ->
    egroups.eachParallel { e, gs ->
      gs.each { g ->
        if(!tGroup.containsKey(g)) {
          tGroup[g] = new ConcurrentHashMap()
        }
      }
    }
    }

    GParsPool.withPool(o['threads']) { p ->
    egroups.eachParallel { e, gs ->
      gs.each { g ->
        tGroup[g][e] = true
      }
    }
    }

    tGroup.each { k, v ->
      groupings[k] = v.keySet().toList()
    }

    println "Groupings loaded:"
    groupings.each { k, v ->
      println "  $k: ${v.size()} members" 
    }

    // kind of stupid but ok ; also TODO check for race condition
    def qa = new ConcurrentHashMap()
    GParsPool.withPool(o['threads']) { p ->
    associations.eachParallel { entity, terms ->
      terms.keySet().toList().each { qa[it] = true }
    }
    }
    def allAssociations = qa.keySet().toList()

    if(o['verbose']) {
      println "Loaded ${allAssociations.size()} entity-term associations."
    }

    data = [
      groupings: groupings,
      associations: associations,
      egroups: egroups,
      ic: ic,
      allAssociations: allAssociations
    ]

    if(o['verbose']) { println "[...] Done loading dataset" }
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

  def loadIc() {
    if(o['verbose']) { println "[...] Loading information content" }

    if(o['ic']) {
      try {
      new File(o['ic']).splitEachLine('\t') {
        data.ic[it[0]] = Float.parseFloat(it[1])
      }
      } catch(e) {
        HandleError(e, o['verbose'], o['debug'], "Error loading information content file ($icFile)")
      }
    } else {
      try {
        icFactory = new InformationContent(o)
        def allClasses = ontoHelper.reasoner.getSubClasses(ontoHelper.dataFactory.getOWLThing(), false).collect { it.getRepresentativeElement().getIRI().toString() }.unique(false)
        allClasses = allClasses.findAll { it != 'http://www.w3.org/2002/07/owl#Nothing' } // heh
        data.ic = icFactory.getInformationContent(allClasses)
      } catch(e) {
        HandleError(e, o['verbose'], o['debug'], "Error calculating information content values")
      }
    }

    if(o['verbose']) { println "[...] Done loading information content" }

    if(o['save-ic']) {
      try {
        InformationContent.Write(data.ic, o['save-ic'])
      } catch(e) {
        HandleError(e, o['verbose'], o['debug'], "Error saving information content values (${o['save-ic']})")
      }
    }
  }

  // Load and classify the ontology with Elk
  def loadOntology() { 
    if(o['verbose']) { println "[...] Loading ontology" }

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
      def ontology = manager.loadOntologyFromOntologyDocument(new File(o['ontology']))
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
      HandleError(e, o['verbose'], o['debug'], "Error loading of processing the ontology file (${o['ontology']})")
    }

    if(o['verbose']) { println "[...] Done loading ontology" }
  }

  def permutationTest(allExplanations, excludeClasses, perms) {
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

      def reScorer = new Scorer(ontoHelper, coefficients, subData, o, allCandidates)
      i++
      if((i % 100) == 0) {
        println i
      }
     
      ae.each { g, gp ->
        def cSet = ae[g].keySet().toList()
        def candidates = reScorer.scoreClasses(g, cSet, true)

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

  def explainCluster(cid) {
    def candidates = scorer.scoreAllClasses(cid, o['include-all'])

    println "$cid: Scoring completed. Candidates: ${candidates.size()}"

    if(o['output-scores']) {
      try {
        def fName = false // TODO why isn't this just o['output']? 
        if(o['output-type'] == 'latex') {
          Scorer.WriteLaTeX(cid, candidates, ontoHelper.labels, fName)
        } else {
          fName = 'scores-'+cid+'.lst'
          Scorer.Write(cid, candidates, ontoHelper.labels, fName)
          println "Output scores to $fName"
        }
      } catch(e) {
        HandleError(e, o['verbose'], o['debug'], "Error saving class scores.")
      }
    }

    // TODO we should probably separate the interface between univariate and multivariate modes. it's pretty confusing rn
    def res
    if(!o['scores-only']) {
      res = StepDown.RunNewAlgorithm(coefficients, cid, candidates, data, o['threads'], o['debug'])
    } 

    return res
  }

  def explainClusters(groups) {
    def missing = groups.findAll { g -> !data.groupings.containsKey(g) }
    if(missing.size() != 0) { RaiseError("Groups not found in dataset: " + missing.join(', ')) }

    def results = []

    groups.each { g ->
      results << [ cluster: g, results: explainCluster(g) ]
    }

    return results
  }

  def explainAllClusters() {
    explainClusters(data.groupings.keySet().toList())
  }

  def reclassify(allExplanations) {
    if(o['classify-with-variables']) { 
      def assoc = [:]
      def allAssoc = []
      new File(cwf).splitEachLine('\t') {
        if(!assoc.containsKey(it[1])) { assoc[it[1]] = [] }
        def t = 'http://purl.obolibrary.org/obo/' + it[0].replace(':','_')
        assoc[it[1]] << t
        allAssoc << t 
      }

      // We rescore to ensure we have the scores for all of our given classes, and to get the new incEnts if we've reloaded data (per classify)
      // TODO this needs to be done even if no CWF on --classify
      def reScorer = new Scorer(ontoHelper, coefficients, data, o)
      def newScores = []
      allExplanations.each { exps ->
         exps.results[0] = reScorer.scoreClasses(exps.cluster, assoc[exps.cluster], true)
         exps.results[0].each { t ->
          t.nExclusion = exps.results[2].find { it.iri == t.iri }.nExclusion
         }
      }
    }

    def m = Classifier.classify(coefficients, allExplanations, data, ontoHelper, o['threads'], o['debug'], o['univariate-classify-mode'])
    if(!m) {
      RaiseError("Failed to build reclassifier. There may have been too few examples.")
    }

    println 'Classification performance:'
    Classifier.Print(m, o['debug'])
    println ''

    if(o['output-classification-scores']) {
      Classifier.WriteScores(m, "reclassify")
    }
  }

  def classify(allExplanations) {
    if(o['verbose']) {
      println "Loading new dataset at ${o['classify']} in order to classify ..."
    }

    def saveIc = data.ic
    loadData(o['classify'])
    data.ic = saveIc 
    // holding onto ic saves us a bit of time, but this should be looked at again if decide to involve IC in classify scoring.

    reclassify(allExplanations)
  }

  def genSim(toFile, group) {
    if(!icFactory) {
      println "Error: IC class not loaded (--similarity and --ic are not compatible)"
      System.exit(1)
    }
    def results = icFactory.compareEntities(data.associations, data.groupings, group)
    InformationContent.WriteSimilarity(results, toFile)
  }

  def output(cid, results, pVals) {
    def cSize = data.groupings[cid].size()
    if(o['output-types']) {
      if(outType == 'latex') {
        StepDown.PrintLaTeX(cid, results, pVals, o['egl'], ontoHelper.labels, cSize, o['output'])
      } else if(outType == 'tsv') {
        StepDown.PrintTSV(cid, results, ontoHelper.labels, cSize, o['output'])
      }
    } else {
      StepDown.Print(cid, results, pVals, o['egl'], ontoHelper.labels, cSize, o['output'], data.groupings[cid], o['print-members'])
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
  static def HandleError(e, verbose, debug, msg) {
    println msg + ': ' + e.toString()
    if(debug) {
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
