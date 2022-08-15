package klarigi

import org.semanticweb.owlapi.model.IRI 
import be.cylab.java.roc.*
import java.util.concurrent.*
import groovyx.gpars.*
import groovyx.gpars.GParsPool
import java.util.concurrent.atomic.*

public class Classifier {
  static def classify(c, allExplanations, data, ontoHelper, threads, debug, ucm) {
    
    if(debug) { println "[Classifier] Inititalising metrics" }
    def metrics = new ConcurrentHashMap()
    data.groupings.each { cid, entities ->
      metrics[cid] = [
        truths: new ConcurrentHashMap(),
        scores: new ConcurrentHashMap()
      ]
    }
    if(debug) { println "[Classifier] Done inititalising metrics" }

    if(debug) { println "[Classifier] Inititalising sterms and calculating OI" }
    def sterms = [:]
    allExplanations.each { exps ->
      // 0 is the mv exp set, 2 is all candidates (uv)
      sterms[exps.cluster] = exps.results[0]
      if(ucm) { sterms[exps.cluster] = exps.results[2] }

      def totalCoverage = StepDown.CalculateOI(c, exps.cluster, data, sterms[exps.cluster], threads, true) //.collect { it.iri })
      println "Building classifier for '${exps.cluster}. ${data.associations.size()} total records (${data.groupings[exps.cluster].size()} labelled ${exps.cluster}. Using ${sterms[exps.cluster].size()} terms with OI: $totalCoverage"
    }
    if(debug) { println "[Classifier] Done inititalising sterms and calculating OI" }

    if(debug) { println "[Classifier] Calculating scores" }
    def no = new AtomicInteger(0)
    //iterate each entity
    GParsPool.withPool(threads) { p ->
    data.associations.eachParallel { entity, codes ->
      if(debug) {
        no.getAndIncrement()
        if((no % 1000) == 0) {
          println "[Classify] Processing record $no" 
        }
      }

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
        if(data.egroups[entity].contains(d)) {
          t = 1
        }
        metrics[d].scores[entity] = v
        metrics[d].truths[entity] = t
      }
    }
    }

    if(debug) { println "[Classifier] Done calculating scores" }
    if(debug) { println "[Classifier] Converting hashmap" }

    metrics.each { d, v ->
      v.scores = v.scores.collect { it.getValue() }
      v.truths = v.truths.collect { it.getValue() }
    }

    return metrics
  }

  static def Print(metrics, debug) {
    metrics.each { cid, m1 ->
      if(debug) { println "[Classifier] Normalising scores for $cid" }
      def max = m1.scores.max()
      def min = m1.scores.min()

      m1.scores = m1.scores.collect { v ->
        if((max - min) == 0) {
          0
        } else {
          (v - min) / (max - min)
        }
      }
      if(debug) { println "[Classifier] Done normalising scores for $cid" }
  
      if(debug) { println "[Classifier] Calculating AUC for $cid" }
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
