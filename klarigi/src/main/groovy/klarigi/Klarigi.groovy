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
  def data = [
    groupings: [:],
    associations: [:],
    ic: [:]
  ]
  def ontoHelper = [
    reasoner: null, 
    dataFactory: null
  ]
  def coefficients
  def verbose

  Klarigi(o) {
    loadData(o['data'])
    loadOntology(o['ontology'])
    loadIc(o['ic'], o['ontology'], o['save-ic'])
    coefficients = Coefficients.Generate(o)
    verbose = o['verbose']
  }

  def loadData(dataFile) {
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

  def loadIc(icFile, ontologyFile, saveIc) {
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
        def icFactory = new InformationContent(ontologyFile)
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

    // this is so abs9olutely ridiculous
    Logger.getLogger(ElkReasoner.class).setLevel(Level.OFF);
    List<Logger> loggers = Collections.<Logger>list(LogManager.getCurrentLoggers());
    loggers.add(LogManager.getRootLogger());
    for ( Logger logger : loggers ) {
      logger.setLevel(Level.OFF);
    }

    def manager = OWLManager.createOWLOntologyManager()
    def ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyFile))
    //def progressMonitor = new ConsoleProgressMonitor()
    def config = new SimpleConfiguration()
    def elkFactory = new ElkReasonerFactory() // cute
    
    // Set class props
    ontoHelper.dataFactory = manager.getOWLDataFactory()
    ontoHelper.reasoner = elkFactory.createReasoner(ontology, config)
  }

  def explainCluster(cid, outputScores) {
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


    def res = StepDown.Run(coefficients, cid, candidates, data)
    StepDown.Print(cid, res)
  }

  def explainAllClusters(outputScores) {
    data.groupings.each { g, v ->
      explainCluster(g, outputScores)
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
