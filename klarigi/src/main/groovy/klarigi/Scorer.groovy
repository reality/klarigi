package klarigi

import org.semanticweb.owlapi.model.IRI 
import java.util.concurrent.*
import groovyx.gpars.*
import groovyx.gpars.GParsPool

public class Scorer {
  private def ontoHelper
  private def data

  Scorer(ontoHelper, data) {
    this.ontoHelper = ontoHelper
    this.data = data
  }

  // so what if we could build a list of the classes of interest and their subclasses, and then go through it once 
  private def processClass(explainers, cid, c) {
		if(explainers.containsKey(c)) { return; }
		def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(c))
    // could we cache this?
		def subclasses = ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
    subclasses << c
		explainers[c] = [
			ic: data.ic[c],
			internalIncluded: data.groupings[cid].findAll { pid -> subclasses.any { sc -> data.associations[pid].containsKey(sc) } },
      externalIncluded: data.groupings.collect { lcid, p ->
        if(lcid == cid) { return []; }
        p.findAll { pid ->
          subclasses.any { sc -> data.associations[pid].containsKey(sc) }
        }
      }.flatten()
		]
    // How many of this term in this group
    explainers[c].inclusion = explainers[c].internalIncluded.size()
    // How many of this term in other groups
    explainers[c].exclusion = explainers[c].externalIncluded.size()
	}

  private def normalise(explainers, cid) {
    explainers.findAll { k, v -> v.ic }
      .collect { k, v ->
        v.nIc = v.ic // TODO this depends on an already normalised IC value...
        v.nInclusion = v.inclusion / data.groupings[cid].size()

        v.nExclusion = 1
        if(data.groupings.size() > 1) {
          // 1 - the proportion of non-group members mention the term, the more there are in other groups. So, higher number of nExclusion mean, the fewer of these terms there are in other groups
          // how about we make it the proportion of total mentions that are in this group vs the other group?

          //v.nExclusion = 1 - (v.exclusion / data.groupings.findAll { kk, vv -> kk != cid }.collect { kk, vv -> vv.size() }.sum())
          v.nExclusion = 0
          if((v.inclusion + v.exclusion) > 0) {
            v.nExclusion = v.inclusion / (v.inclusion + v.exclusion)
          }
        }

        v.nPower = v.nInclusion - (1-v.nExclusion)

        v.iri = k 
        v
      }
  }

  private def findRelevantClasses(relevant, c) {
		if(relevant.contains(c)) { return; }
    relevant << c

		def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(c))
		ontoHelper.reasoner.getSuperClasses(ce, false).each { n ->
			n.getEntities().each { sc ->
				def strc = sc.getIRI().toString()
				findRelevantClasses(relevant, strc)
			}
		}

    return relevant
  }

  def scoreClasses(cid, threads, classes) {
    def explainers = new ConcurrentHashMap()
    GParsPool.withPool(threads) { p ->
      classes.eachParallel {
        processClass(explainers, cid, it)
      }
    }
    explainers = normalise(explainers, cid) // Note, this turns it into a list rather than a hashmap
    explainers
  }

  def scoreAllClasses(cid, threads) {
    // quick, though it should probably be built elsewhere
    def classMap = [:]
    data.associations.each { k, v -> v.collect { kk, vv -> classMap[kk] = true }}
    def classList = classMap.collect { k, v -> k }

    def relevant = []
    classList.each {
      findRelevantClasses(relevant, it)   
    }

    def explainers = new ConcurrentHashMap()
    GParsPool.withPool(threads) { p ->
      relevant.eachParallel {
        processClass(explainers, cid, it)
      }
    }
    explainers = normalise(explainers, cid) // Note, this turns it into a list rather than a hashmap
    explainers
  }

  static def Write(c, fName) {
    new File(fName).text = "iri\tinclusion\texclusion\tinclusivity\texclusivity\tpower\tspecificity\n" + c.collect {
      "${it.iri}\t${it.inclusion}\t${it.exclusion}\t${it.nInclusion}\t${it.nExclusion}\t${it.nPower}\t${it.nIc}"
    }.join('\n')
  }
} 
