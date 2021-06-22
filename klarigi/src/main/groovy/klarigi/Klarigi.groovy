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

public class Klarigi {
  def data
  def ontoHelper = [
    reasoner: null, 
    dataFactory: null,
    labels: null
  ]
  def coefficients
  def verbose
  def icFactory

  Klarigi(o) {
    loadData(o['data'])
    loadOntology(o['ontology'])
    loadIc(o['ic'], o['ontology'], o['data'], o['resnik-ic'], o['save-ic'], o['turtle'])
    coefficients = Coefficients.Generate(o)
    verbose = o['verbose']

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
        data.associations[entity] = terms

        if(!data.groupings.containsKey(group)) {
          data.groupings[group] = []
        }
        data.groupings[group] << entity
      }
    } catch(e) {
      HandleError(e, verbose, "Error loading data file ($dataFile)")
    }
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
  }

  def explainCluster(cid, powerMode, outputScores) {
    def scorer = new Scorer(ontoHelper, data)
    def candidates = scorer.scoreClasses(cid)

    println "$cid: Scoring completed. Candidates: ${candidates.size()}"

    if(outputScores) {
      try {
        Scorer.Write(candidates, 'scores-'+cid+'.lst')
      } catch(e) {
        HandleError(e, verbose, "Error saving information content values ($saveIc)")
      }
    }

    if(powerMode) {
      StepDown.RunNewAlgorithm(coefficients, cid, candidates, data)
    } else {
      StepDown.Run(coefficients, cid, candidates, data)
    }
  }

  def explainAllClusters(outputScores, powerMode) {
    data.groupings.collect { g, v ->
      [ cluster: g, results: explainCluster(g, powerMode, outputScores) ]
    }
  }

  def reclassify(allExplanations) {
    def acc = Classifier.classify(allExplanations, data, ontoHelper)
    println "Reclassify accuracy: $acc"
  }

  def classify(path, allExplanations) {
    loadData(path) // TODO I know, i know, this is awful state management and design. i'll fix it later
    def acc = Classifier.classify(allExplanations, data, ontoHelper)
    println "Test accuracy: $acc"
  }

  def genSim(toFile) {
    if(!icFactory) {
      println "Error: IC class not loaded (--similarity and --ic are not compatible)"
      System.exit(1)
    }
    def results = icFactory.compareEntities(data.associations)
    InformationContent.WriteSimilarity(results, toFile)
  }

  def output(cid, results, outType, printMembers, toFile) {
    def cSize = data.groupings[cid].size()
    if(outType) {
      if(outType == 'latex') {
        StepDown.PrintLaTeX(cid, results, ontoHelper.labels, cSize, toFile)
      } else if(outType == 'tsv') {
        StepDown.PrintTSV(cid, results, ontoHelper.labels, cSize, toFile)
      }
    } else {
      StepDown.Print(cid, results, ontoHelper.labels, cSize, toFile, data.groupings[cid], printMembers)
    }
  }

  static def HandleError(e, verbose, msg) {
    println msg + ': ' + e.getMessage()
    if(verbose) {
      e.printStackTrace()
    }
    System.exit(1)
  }
}
