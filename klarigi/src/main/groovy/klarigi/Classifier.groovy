package klarigi

import org.semanticweb.owlapi.model.IRI 
import be.cylab.java.roc.*
import java.util.concurrent.*
import groovyx.gpars.*
import groovyx.gpars.GParsPool

public class Classifier {
  static def classify(c, allExplanations, data, ontoHelper, threads, ucm) {
    def metrics = new ConcurrentHashMap()
    data.groupings.each { cid, entities ->
      metrics[cid] = [
        truths: new ConcurrentHashMap(),
        scores: new ConcurrentHashMap()
      ]
    }

    def sterms = [:]
    allExplanations.each { exps ->
      // 0 is the mv exp set, 2 is all candidates (uv)
      sterms[exps.cluster] = exps.results[0]
      if(ucm) { sterms[exps.cluster] = exps.results[2] }

      def totalCoverage = StepDown.CalculateOI(c, exps.cluster, data, sterms[exps.cluster], threads, true) //.collect { it.iri })
      println "Building classifier for '${exps.cluster}. ${data.associations.size()} total records (${data.groupings[exps.cluster].size()} labelled ${exps.cluster}. Using ${sterms[exps.cluster].size()} terms with OI: $totalCoverage"
    }

    //iterate each entity
    GParsPool.withPool(threads) { p ->
    data.associations.eachParallel { entity, codes ->
      def scores = [:]
      
      // Iterate each group
      allExplanations.each { exps ->
        // Start from 1
        scores[exps.cluster] = new Float(1.0)

        sterms[exps.cluster].collect { e -> 
          e.incEnts.containsKey(entity) ? e.nExclusion : 0 
        }.each {
          scores[exps.cluster] = scores[exps.cluster] * (1+it)
        }
      }

      scores.each { d, v ->
        def t = 0
        if(data.groupings[d].contains(entity)) {
          t = 1
        }
        metrics[d].scores[entity] = v
        metrics[d].truths[entity] = t
      }
    }
    }

    metrics.each { d, v ->
      v.scores = v.scores.collect { it.getValue() }
      v.truths = v.truths.collect { it.getValue() }
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
