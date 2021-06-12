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

  private def processClass(explainers, cid, c) {
		if(explainers.containsKey(c)) { return; }
		def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(c))
		def subclasses = ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
    subclasses << c
		explainers[c] = [
			ic: data.ic[c],
			internalIncluded: data.groupings[cid].findAll { pid -> data.associations[pid].any { pc -> subclasses.contains(pc) } },
      externalIncluded: data.groupings.collect { lcid, p ->
        if(lcid == cid) { return []; }
        p.findAll { pid ->
          data.associations[pid].any { pc -> subclasses.contains(pc) }
        }
      }.flatten()
		]
    explainers[c].inclusion = explainers[c].internalIncluded.size()
    explainers[c].exclusion = explainers[c].externalIncluded.size()

		ontoHelper.reasoner.getSuperClasses(ce, true).each { n ->
			n.getEntities().each { sc ->
				def strc = sc.getIRI().toString()
				processClass(explainers, cid, strc)
			}
		}
	}

  private def normalise(explainers, cid) {
    explainers.findAll { k, v -> v.ic }
      .collect { k, v ->
        v.nIc = v.ic // TODO this depends on an already normalised IC value...
        v.nInclusion = v.inclusion / data.groupings[cid].size()
        v.nExclusion = 1 - (v.exclusion / data.groupings.findAll { kk, vv -> kk != cid }.collect { kk, vv -> vv.size() }.sum())
        v.iri = k 
        v
      }
  }

  def scoreClasses(cid) {
    def classList = data.associations.collect { k, v -> v }.flatten().unique(false) // all classes used in entity descriptions
    def explainers = [:]
    //GParsPool.withPool(4) { p ->
      classList.each {
        processClass(explainers, cid, it)
      }
    //}
    explainers = normalise(explainers, cid) // Note, this turns it into a list rather than a hashmap
    explainers
  }

  static def Write(c, fName) {
    new File(fName).text = "iri\tinclusion\texclusion\tinclusivity\texclusivity\tspecificity\n" + c.collect {
      "${it.iri}\t${it.inclusion}\t${it.exclusion}\t${it.nInclusion}\t${it.nExclusion}\t${it.nIc}"
    }.join('\n')
  }
} 
