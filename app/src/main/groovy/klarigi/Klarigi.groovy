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

public class Klarigi {
  // This is a map of entities and their cluster associations
  def groupings = [:]

  // This is a map of entities and their ontology class associations
  def associations = [:]

  // Ontology query utility classes
  def oReasoner
  def oDataFactory

  Klarigi(o) {
    loadData(o['data-file'])
    loadOntology(o['ontology-file'])
    explainerClusters(o)
  }

  def loadData(dataFile) {
    dataFile.splitEachLine('\t') {
      def (entity, terms, group) = it

      associations[entity] = terms.tokenize(';')

      if(!groupings.containsKey(group)) {
        groupings[group] = []
      }
      groupings[group] << entity
    }
  }

  // Load and classify the ontology with Elk
  def loadOntology(ontologyFile) { 
    def manager = OWLManager.createOWLOntologyManager()
    def ontology = manager.loadOntologyFromOntologyDocument(new File(ontologyFile))
    def progressMonitor = new ConsoleProgressMonitor()
    def config = new SimpleConfiguration(progressMonitor)
    def elkFactory = new ElkReasonerFactory() // cute
    
    // Set class props
    oDataFactory = manager.getOWLDataFactory()
    oReasoner = elkFactory.createReasoner(ontology, config)
  }

  def explainClusters(o) {
    def scorer = new Scorer(oDataFactory, oReasoner, groupings, associations)

    /*
    def allExplanations = scorer.scoreClasses()
    def coefficients = Coefficients.Generate(o)
    def finalExplanations = StepDown.Run(coefficients, allExplanations, groupings, associations)
    */

    // Now we print
  }
}
