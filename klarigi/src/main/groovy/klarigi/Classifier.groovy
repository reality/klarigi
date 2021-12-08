package klarigi

import org.semanticweb.owlapi.model.IRI 
import be.cylab.java.roc.*

public class Classifier {
  static def classify(allExplanations, data, ontoHelper) {
    def subclassCache = [:]

    def metrics = [:]
    data.groupings.each { cid, entities ->
      metrics[cid] = [
        tp: 0,
        fp: 0, 
        fn: 0,
        scores: []
      ]
    }

    // Iterate each entity
    // So for some reason 
    data.groupings.each { cid, entities ->
      entities.each { entity ->
        def codes = data.associations[entity].keySet()
        def scores = [:]
        
        // Iterate each cluster
        allExplanations.each { exps ->
          scores[exps.cluster] = 1

          // Iterate all scored candidates (results[3])
          def rs = exps.results[2].collect { e ->
            // Get subclasses + equivalent of this explanatory class
            if(!subclassCache.containsKey(e.iri)) {
              def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(e.iri))
              subclassCache[e.iri] = ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
            }
            def subeq = subclassCache[e.iri] + e.iri

            def score = 0
            if(subeq.any { codes.contains(it) }) {
              score = e.nPower * e.nIc
              //e.nInclusion + e.nExclusion
              //e.nPower * e.nIc
            }
            
            return score
          }

          rs.each {
            scores[exps.cluster] = scores[exps.cluster] * (1+it)
          }
        }

        // So we have just built 'scores', which

        def best
        scores.each { k, v ->
          if(!best || (best && best[1] < v)) {
            best = [k, v]
          }
        }

        if(best[0] == cid) {
          metrics[cid].tp++
        } else {
          metrics[cid].fn++
          metrics[best[0]].fp++
        }

        metrics[cid].scores << scores
      }
    }

    /*data.groupings.each { cid, entities ->
      metrics[cid].precision = metrics[cid].tp / (metrics[cid].tp + metrics[cid].fp)
      metrics[cid].recall = metrics[cid].tp / (metrics[cid].tp + metrics[cid].fn)
    }*/

    return metrics
  }

  static def Print(metrics) {
    metrics.each { cid, m1 ->
      def out = []
      def c = 0
      
      def scores = []
      def truths = []

      // trues
      m1.scores.each { s ->
        scores << s[cid].toDouble()
        truths << 1
      }

      metrics.findAll { c2, m2 -> c2 != cid }.each { c2, m2 ->
        m2.scores.each { s ->
          scores << s[cid].toDouble()
          truths << 0
        }
      }

      def maxScore = scores.max()
      def minScore = scores.min()
      scores = scores.collect { (it-minScore)/(maxScore/minScore) }
      
      // JAVA
      double[] scar = scores.toArray()
      double[] trar = truths.toArray()
      def roc = new Roc(scar, trar)

      //def roc = new Roc(scores, truths)
      println "$cid AUC: ${roc.computeAUC()}"
    }
  }

  static def WriteScores(metrics, filePrefix) {
    metrics.each { cid, m1 ->
      def out = []
      def c = 0
      
      // trues
      m1.scores.each { s ->
        out << "$c\t${s[cid]}\ttrue"
        c++
      }

      metrics.findAll { c2, m2 -> c2 != cid }.each { c2, m2 ->
        m2.scores.each { s ->
          out << "$c\t${s[cid]}\tfalse"
          c++
        }
      }

      out = out.join('\n')
      new File("$filePrefix-$cid-scores.lst").text = out
    }
  }
}
