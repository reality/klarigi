package klarigi

import org.semanticweb.owlapi.model.IRI 
import be.cylab.java.roc.*

public class Classifier {
  static def classify(c, allExplanations, data, ontoHelper, threads, ucm) {
    def subclassCache = [:]

    def metrics = [:]
    data.groupings.each { cid, entities ->
      metrics[cid] = [
        truths: [],
        scores: []
      ]
    }

    def sterms = [:]
    allExplanations.each { exps ->
      // 0 is the mv exp set, 2 is all candidates (uv)
      sterms[exps.cluster] = exps.results[0]
      if(ucm) { sterms[exps.cluster] = exps.results[2] }

      def totalCoverage = StepDown.CalculateOI(c, exps.cluster, data, sterms[exps.cluster], threads, true) //.collect { it.iri })
      println "Classifying '${exps.cluster}' using candidate set with OI: $totalCoverage"
    }

    //iterate each entity
    data.associations.each { entity, codes ->
      // Iterate each group
      def scores = [:]
      
      allExplanations.each { exps ->
        scores[exps.cluster] = 1

        def rs = sterms[exps.cluster].collect { e ->
          // Get subclasses + equivalent of this explanatory class
          if(!subclassCache.containsKey(e.iri)) {
            def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(e.iri))
            subclassCache[e.iri] = ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
          }
          def subeq = subclassCache[e.iri] + e.iri

          def score = 0
          if(subeq.any { codes.containsKey(it) }) {
            score = e.nExclusion
          }
            
          return score
        }

        rs.each {
          scores[exps.cluster] = scores[exps.cluster] * (1+it)
        }
      }

      scores.each { d, v ->
        def t = 0
        if(data.groupings[d].contains(entity)) {
          t = 1
        }
        metrics[d].scores << v
        metrics[d].truths << t
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
