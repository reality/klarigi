package klarigi

import org.semanticweb.owlapi.model.IRI 

public class Classifier {
  static def classify(allExplanations, data, ontoHelper) {
    def tp = 0
    def fp = 0 

    def subclassCache = [:]

    data.groupings.each { cid, entities ->
      entities.each { entity ->
        def codes = data.associations[entity]
        def scores = [:]
        
        allExplanations.each { exps ->
          scores[exps.cluster] = 1

          def rs = exps.results[0].collect { e ->
            // Get subclasses + equivalent of this explanatory class
            if(!subclassCache.containsKey(e.iri)) {
              def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(e.iri))
              subclassCache[e.iri] = ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
            }
            def subeq = subclassCache[e.iri] + e.iri

            def score = 0
            if(subeq.any { codes.contains(it) }) {
              score = e.nPower
            }
            
            return score;
          }

          rs.each {
            scores[exps.cluster] = scores[exps.cluster] * (1+it)
          }
        }


        def best
        scores.each { k, v ->
          if(!best || (best && best[1] < v)) {
            best = [k, v]
          }
        }

        if(best[0] == cid) {
          tp++
        } else {
          fp++
        }
      }
    }

    def acc = tp / (tp + fp)
    println "Reclassify accuracy: $acc"
  }
}
