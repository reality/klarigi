package klarigi

import org.semanticweb.owlapi.model.IRI 
import java.util.concurrent.*
import groovyx.gpars.*
import groovyx.gpars.GParsPool

public class Scorer {
  private def ontoHelper
  private def data
  private def c 
  private def sc
  private def preScore
  private def excludeClasses

  Scorer(ontoHelper, coefficients, data, excludeClasses, threads) {
    this.ontoHelper = ontoHelper
    this.data = data
    this.c = coefficients
    this.excludeClasses = extendExcludeClasses(excludeClasses)

    this.precalculateScores(threads)
  }

  private def precalculateScores(threads) {
    println 'Precalculating'
    def ass = new ConcurrentHashMap()

    // TODO add excludeclasses here
    def scMap = [:]
    def toProcess = []
    data.allAssociations.each { iri ->
      toProcess << iri
      def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(iri))
      scMap[iri] = []
      scMap[iri] << iri
      ontoHelper.reasoner.getSuperClasses(ce, false).each { n ->
        n.getEntities().each { sc ->
          def dIri = sc.getIRI().toString()
          scMap[iri] << dIri
          toProcess << dIri
        }
      }
    }

    toProcess = toProcess.unique(false)

    println "Processing ${toProcess.size()}"

    toProcess.each { iri ->
      ass[iri] = [:]
      data.groupings.each { cid, v ->
        ass[iri][cid] = [ incEnts: new ConcurrentHashMap(), inc: 0, exc: 0 ]
      }
    }

// can further speed up by cutting out the minimum here
// the point here is that we have excluded searches. every iteration is Business

// so we only want one +1 per person per thing
    GParsPool.withPool(threads) { p ->
    data.associations.eachParallel { e, terms ->
      terms.each { t, v ->
        scMap[t].each { dt ->
          data.egroups[e].each { g ->
            ass[dt][g].incEnts[e] = true
          }
        } 
      }
    }
    }

    data.groupings.each { cid, v ->
      toProcess.each { iri ->
        ass[iri][cid].inc = ass[iri][cid].incEnts.size()
        ass[iri][cid].exc = data.groupings[cid].size() - ass[iri][cid].inc
      }
    }

    println 'done preprocessing'
    println ass

    this.preScore = ass
  }

  // so what if we could build a list of the classes of interest and their subclasses, and then go through it once 
  private def processClass(explainers, cid, c) {
    if(excludeClasses.contains(c)) { return; }
		if(explainers.containsKey(c)) { return; }

    def v = [
      iri: c, 
      nIc: data.ic[c],
      nInclusion: this.preScore[c][cid].inc / data.groupings[cid].size(),
      nExclusion: 0
    ]
    
    def allInclusion = this.preScore[c].collect { k, vv -> vv.inc }.sum()

    // G_j / E
    def prop = ((data.groupings[cid].size()) / data.associations.size())

    v.nExclusion = (this.preScore[c][cid].inc / allInclusion)
    v.nExclusion = v.nExclusion - prop

    v.nPower = 0 
    if(v.nInclusion + v.nExclusion > 0) {
      v.nPower = (v.nInclusion * (v.nExclusion)) / (v.nInclusion + (v.nExclusion))
    }

    println this.preScore[c][cid].inc
    println v
    explainers[c]  = v
	}

  private def extendExcludeClasses(excludeClasses) {
    excludeClasses.collect { getSubclasses(it) + it }.flatten().unique(false)
  }

  private def getSubclasses(String iri) {
		def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(iri))
		ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
  }

  private def normalise(explainers, cid, returnAll) {
      /*.collect { k, v ->
        v.nIc = v.ic // TODO this depends on an already normalised IC value...
        v.nInclusion = v.inclusion / data.groupings[cid].size()

        v.nExclusion = 1
        if(data.groupings.size() > 1) {
          v.nExclusion = 0
          if((v.inclusion + v.externalInclusion) > 0) {
            v.nExclusion = v.inclusion / (v.inclusion + v.externalInclusion)
          }

          def prop = ((data.groupings[cid].size()) / data.associations.size())
          v.nExclusion -= prop

          v.nPower = 0 
          if(v.nInclusion + v.nExclusion > 0) {
            v.nPower = (v.nInclusion * (1-v.nExclusion)) / (v.nInclusion + (1-v.nExclusion))
          }
        } else {
          v.nPower = v.nInclusion
        }

        v.iri = k 
        v
      }*/
    explainers.findAll { k, v -> v.nIc }
      .collect{ k, v -> v }
      .findAll { v ->
        if(!returnAll) {
          if(data.groupings.size() > 1) {
            return v.nIc >= c.MIN_IC && 
              v.nPower >= c.MIN_POWER && 
              v.nExclusion >= c.MIN_EXCLUSION && 
              v.nInclusion >= c.MIN_INCLUSION
          } else {
            return v.nIc >= c.MIN_IC && v.nInclusion >= c.MIN_INCLUSION
          }
        } else {
          true
        }
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
    scoreClasses(cid, excludeClasses, threads, classes, false)
  }

  def scoreClasses(cid, threads, classes, returnAll) {
    excludeClasses = extendExcludeClasses(excludeClasses)
    def explainers = new ConcurrentHashMap()
    GParsPool.withPool(threads) { p ->
      classes.eachParallel {
        processClass(explainers, cid, excludeClasses, it)
      }
    }

    // Note, this turns it into a list rather than a hashmap
    explainers = normalise(explainers, cid, returnAll) 
    explainers
  }

  def scoreAllClasses(cid, threads) {
    scoreAllClasses(cid, threads, false)
  }

  def scoreAllClasses(cid, threads, returnAll) {
    // TODO i think we can use the allclasses?
    // or perhaps we're only interested in classes in this cluster?
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
    explainers = normalise(explainers, cid, returnAll) // Note, this turns it into a list rather than a hashmap
    explainers
  }

  static def Write(cid, s, labels, fName) {
    new File(fName).text = "iri\tinclusion\texclusion\tinclusivity\texclusivity\tpower\tspecificity\n" + s.collect {
      "${it.iri}\t${it.inclusion}\t${it.exclusion}\t${it.nInclusion}\t${it.nExclusion}\t${it.nPower}\t${it.nIc}"
    }.join('\n')
  }

  static def WriteLaTeX(cid, res, labels, fName) {
    def out = []

    out << "{\\bf Class} & {\\bf Power} & {\\bf Inclusivity} & & {\\bf Exclusivity} & {\\bf Specificity} \\\\"
    out << "\\hline \\hline"

    res.sort { -it.nPower }.each {
      def pIri = it.iri
      if(pIri =~ 'obolibrary.org') {
        pIri = pIri.tokenize('/').last()
        pIri = pIri.replace('_', ':')
        pIri = "{\\tt $pIri}"
      } else {
        pIri = it.iri.replaceAll('_', '\\\\_')
      }

        out << "${labels[it.iri]} (${pIri}) & ${it.nPower.toDouble().round(2)} & ${it.nInclusion.toDouble().round(2)} & ${it.nExclusion.toDouble().round(2)} & ${it.nIc.toDouble().round(2)} \\\\"
    }
    out << "\\end{tabular}"
    out = out.join('\n')

    if(fName) {
      new File(fName).text += '\n' + out 
    } else {
      println out
    }
  }
} 
