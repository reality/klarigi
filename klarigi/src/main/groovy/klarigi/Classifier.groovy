package klarigi

import org.semanticweb.owlapi.model.IRI 
import be.cylab.java.roc.*

public class Classifier {
  static def classify(allExplanations, data, ontoHelper, ecm) {
    def subclassCache = [:]

    def metrics = [:]
    data.groupings.each { cid, entities ->
      metrics[cid] = [
        truths: [],
        scores: []
      ]
    }

    //iterate each entity
    data.associations.each { entity, codes ->
      // Iterate each group
      def scores = [:]
      
      allExplanations.each { exps ->
        scores[exps.cluster] = 1
        def sterms = exps.results[2]
        if(ecm) {
          sterms = exps.results[0]
        }

        def rs = sterms.collect { e ->
          // Get subclasses + equivalent of this explanatory class
          if(!subclassCache.containsKey(e.iri)) {
            def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(e.iri))
            subclassCache[e.iri] = ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
          }
          def subeq = subclassCache[e.iri] + e.iri

          def score = 0
          if(subeq.any { codes.containsKey(it) }) {
            //score = e.nPower * e.nIc
            score = e.nExclusion * e.nIc
          }
            
          return score
        }

        rs.each {
          scores[exps.cluster] = scores[exps.cluster] * (1+it)
        }
      }

      scores.each { c, v ->
        def t = 0
        if(data.groupings[c].contains(entity)) {
          t = 1
        }
        metrics[c].scores << v
        metrics[c].truths << t
      }
    }

    return metrics
  }

  static def Print(metrics) {
    metrics.each { cid, m1 ->
      def max = m1.scores.max()
      def min = m1.scores.min()

      m1.scores = m1.scores.collect { v ->
        if((max - min) == 0) {
          0
        } else {
          (v - min) / (max - min)
        }
      }
  
      // JAVA
      double[] scar = m1.scores.toArray()
      double[] trar = m1.truths.toArray()
      def roc = new Roc(scar, trar)
      def auc = roc.computeAUC()

      if(!auc.isNaN()) {
        println "$cid AUC: ${auc}"
      }
    }
  }

  static def WriteScores(metrics, filePrefix) {
    metrics.each { cid, m1 ->
      def out = []
      def c = 0
      
      // trues
      m1.scores.each { s ->
        out << "$c\t${s}\t${m1.truths[c]}"
        c++
      }

      out = out.join('\n')
      new File("$filePrefix-$cid-scores.lst").text = out
    }
  }
}
