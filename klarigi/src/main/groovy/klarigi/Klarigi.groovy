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

public class Klarigi {
  def data
  def allAssociations // lazy
  def ontoHelper = [
    reasoner: null, 
    dataFactory: null,
    labels: null
  ]
  def coefficients
  def verbose
  def icFactory

  Klarigi(o) {
    verbose = o['verbose']

    loadData(o['data'])
    loadOntology(o['ontology'])
    loadIc(o['ic'], o['ontology'], o['data'], o['resnik-ic'], o['save-ic'], o['turtle'])
    coefficients = Coefficients.Generate(o)

    if(o['output']) { // blank the output file, since we will subsequently append to it. all the output stuff could probs be better abstracted.
      new File(o['output']).text = ''
    }
  }

  def loadData(dataFile) {
    data = [
      groupings: [:],
      associations: [:],
      ic: [:]
    ]
    try {
      new File(dataFile).splitEachLine('\t') {
        def (entity, terms, group) = it

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

        group.tokenize(';').each { g ->
          if(!data.groupings.containsKey(g)) {
            data.groupings[g] = []
          }
          data.groupings[g] << entity
        }
      }
    } catch(e) {
      HandleError(e, verbose, "Error loading data file ($dataFile)")
    }

    allAssociations = data.associations.collect { entity, terms ->
      terms.keySet().toList()
    }.flatten().unique(false)
    println allAssociations

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
        newSample[id][allAssociations[r.nextInt(allAssociations.size())]] = true
      }
    }
    return newSample
  }

  def loadIc(icFile, ontologyFile, annotFile, resnikIc, saveIc, turtle) {
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
        icFactory = new InformationContent(ontologyFile, annotFile, resnikIc, turtle)
        def allClasses = ontoHelper.reasoner.getSubClasses(ontoHelper.dataFactory.getOWLThing(), false).collect { it.getRepresentativeElement().getIRI().toString() }.unique(false)
        allClasses = allClasses.findAll { it != 'http://www.w3.org/2002/07/owl#Nothing' } // heh
        data.ic = icFactory.getInformationContent(allClasses)
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

    if(verbose) {
      println "Done loading the ontology"
    }
  }

  def permutationTest(allExplanations, threads) {
    def i = 0

    def ae = [:] 
    allExplanations.each { a ->
      ae[a.cluster] = [:]
      a.results[0].each { z ->
        ae[a.cluster][z.iri] = z 
        ae[a.cluster][z.iri].incVals = [ z.nInclusion ]
        ae[a.cluster][z.iri].excVals = [ z.nExclusion ]
        ae[a.cluster][z.iri].powVals = [ z.nPower ]
      }
    }

    (0..1000).each {
      def subData = [
        associations: sampleData(),
        groupings: data.groupings,
        ic: data.ic
      ]
      def scorer = new Scorer(ontoHelper, subData)

      i++
      if((i % 100) == 0) {
        println i
      }
     
      ae.each { g, gp ->
        def candidates = scorer.scoreClasses(g, threads, ae[g].keySet().toList())

        candidates.each { v ->
          def k = v.iri
          ae[g][k].incVals << v.nInclusion
          ae[g][k].excVals << v.nExclusion
          ae[g][k].powVals << v.nPower
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

  def explainCluster(cid, powerMode, outputScores, threads, debug) {
    def scorer = new Scorer(ontoHelper, data)
    def candidates = scorer.scoreAllClasses(cid, threads)

    println "$cid: Scoring completed. Candidates: ${candidates.size()}"

    if(outputScores) {
      try {
        Scorer.Write(candidates, 'scores-'+cid+'.lst')
      } catch(e) {
        HandleError(e, verbose, "Error saving information content values ($saveIc)")
      }
    }

    if(powerMode) {
      StepDown.RunNewAlgorithm(coefficients, cid, candidates, data, debug)
    } else {
      StepDown.Run(coefficients, cid, candidates, data, debug)
    }
  }

  def explainClusters(groups, outputScores, powerMode, threads, debug) {
    data.groupings.findAll { g, v -> groups.contains(g) }.collect { g, v ->
      [ cluster: g, results: explainCluster(g, powerMode, outputScores, threads, debug) ]
    }
  }

  def explainAllClusters(outputScores, powerMode, threads, debug) {
    data.groupings.collect { g, v ->
      [ cluster: g, results: explainCluster(g, powerMode, outputScores, threads, debug) ]
    }
  }

  def reclassify(allExplanations, outClassScores) {
    def m = Classifier.classify(allExplanations, data, ontoHelper)

    println 'Reclassification:'
    Classifier.Print(m)
    println ''

    if(outClassScores) {
      Classifier.WriteScores(m, "reclassify")
    }
  }

  def classify(path, allExplanations, outClassScores) {
    loadData(path) // TODO I know, i know, this is awful state management and design. i'll fix it later

    println 'Classification:'
    def m = Classifier.classify(allExplanations, data, ontoHelper)
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

  def output(cid, results, pVals, outType, printMembers, toFile) {
    def cSize = data.groupings[cid].size()
    if(outType) {
      if(outType == 'latex') {
        StepDown.PrintLaTeX(cid, results, ontoHelper.labels, cSize, toFile)
      } else if(outType == 'tsv') {
        StepDown.PrintTSV(cid, results, ontoHelper.labels, cSize, toFile)
      }
    } else {
      StepDown.Print(cid, results, pVals, ontoHelper.labels, cSize, toFile, data.groupings[cid], printMembers)
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

  static def HandleError(e, verbose, msg) {
    println msg + ': ' + e.getMessage()
    if(verbose) {
      e.printStackTrace()
    }
    System.exit(1)
  }
}
