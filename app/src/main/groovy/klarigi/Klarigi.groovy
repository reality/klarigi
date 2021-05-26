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

  Klarigi(o) {
    loadData(o['data'])
    loadIc(o['ic'])
    loadOntology(o['ontology'])
  }

  def loadData(dataFile) {
    new File(dataFile).splitEachLine('\t') {
      def (entity, terms, group) = it

      terms = terms.tokenize(';')
      if(terms.size() > 0 && terms[0] =~ /:/) {
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
  }

  def loadIc(icFile) {
    new File(icFile).splitEachLine('\t') {
      data.ic[it[0]] = Float.parseFloat(it[1])
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
    ontoHelper.dataFactory = manager.getOWLDataFactory()
    ontoHelper.reasoner = elkFactory.createReasoner(ontology, config)
  }

  def explainClusters(o) {
    def scorer = new Scorer(ontoHelper, data)

    /*
    def allExplanations = scorer.scoreClasses()
    def coefficients = Coefficients.Generate(o)
    def finalExplanations = StepDown.Run(coefficients, allExplanations, groupings, associations)
    */

    // Now we print
  }
}
