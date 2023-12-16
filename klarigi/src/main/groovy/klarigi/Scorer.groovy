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
  private def excludeClasses
  private def includeOnlyClasses
  private def o
  private def scMap = [:]

  public  def preScore

  Scorer(ontoHelper, coefficients, data, o) {
    this.setup(ontoHelper, coefficients, data, o)
    this.precalculateScores()
  }

  Scorer(ontoHelper, coefficients, data, o, manToProcess) {
    this.setup(ontoHelper, coefficients, data, o)
    this.precalculateScores(manToProcess)
  }

  private def setup(ontoHelper, coefficients, data, o) {
    this.ontoHelper = ontoHelper
    this.data = data
    this.c = coefficients
    this.excludeClasses = extendExcludeClasses(o['exclude-classes'])
    this.includeOnlyClasses = extendExcludeClasses(o['include-only-classes'])
    this.o = o
  }

  private def precalculateScores() {
    if(o['verbose']) { "[...] Performing scorer precalculation" }
    precalculateScores(false)
    if(o['verbose']) { "[...] Done scorer precalculation" }
  }

  private def precalculateScores(manToProcess) {
    def ass = new ConcurrentHashMap()

    // TODO add excludeclasses here
    def toProcess = []

    def tp = data.allAssociations
    if(manToProcess) { tp = manToProcess }
    tp.each { iri ->
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

    toProcess.each { iri ->
      ass[iri] = [:]
      data.groupings.each { cid, v ->
        ass[iri][cid] = [ incEnts: new ConcurrentHashMap(), inc: 0, exc: 0 ]
      }
    }

    if(!o['include-all']) {
      toProcess = toProcess.findAll { data.ic[it] >= c.MIN_IC }
    }

    // so we only want one +1 per person per thing
    def i = 0
    GParsPool.withPool(o['threads']) { p ->
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

    def z = 0
    GParsPool.withPool(o['threads']) { p ->
    data.groupings.eachParallel { cid, v ->
      toProcess.each { iri ->
        ass[iri][cid].inc = ass[iri][cid].incEnts.size()
        ass[iri][cid].exc = data.groupings[cid].size() - ass[iri][cid].inc
      }
    }
    }

    this.preScore = ass
  }

  // Score a class in the context of a given group, and add a dict of this info to the explainers 
  // @Param c String class IRI
  private def processClass(explainers, cid, c) {
    if(excludeClasses.contains(c)) { return; }
    if(includeOnlyClasses && !includeOnlyClasses.contains(c)) { return; }
		if(explainers.containsKey(c)) { return; } // Skip if we already have have a result for this class.
    if(!this.preScore.containsKey(c)) { return; } // For example, if it's in a list we're asking about but not in the dataset, because of permutation sampling

    // This is the object that contains the scores for this class.
    def v = [
      iri: c,

      // Normalised information content score.
      nIc: data.ic[c],

      // All inclusion (total entities annotated with this class across all clusters) (used in the calculation of the exclusion score)
      allInclusion: this.preScore[c].collect { k, vv -> vv.inc }.sum(),

      // Normalised inclusion score (proportion of entities in the group annotated with the term)
      nInclusion: this.preScore[c][cid].inc / data.groupings[cid].size(),

      incEnts: this.preScore[c][cid].incEnts,

      // fExclusion is unweighted exclusion score, nExclusion is the final score
      fExclusion: 0,
      nExclusion: 0,

      // The expression score. Legacy naming!
      nPower: 0
    ]
    
    if(v.allInclusion == 0) { // another edge case when calling the shots for precalc in permutation
      return;
    }

    // Initial, unweighted, 
    v.fExclusion = (this.preScore[c][cid].inc / v.allInclusion)

    // This adds the subtraction of G_j / E (the proportion of entities in the corpus that are associated with the currently considered group) to form the final exclusion score.
    v.nExclusion = v.fExclusion - ((data.groupings[cid].size()) / data.associations.size())

    /*if(v.fExclusion == 1) { 
      v.nExclusion = 1
    }*/

    // Calculate the harmonic mean of exclusion and inclusion, forming the expression score.
    v.nPower = 0 
    if(v.nInclusion + v.nExclusion > 0) {
      v.nPower = 2 * ((v.nInclusion * (v.nExclusion)) / (v.nInclusion + (v.nExclusion)))
    }

    explainers[c] = v
	}
  
  // Extend given classes to exclude from scoring with their subclasses. 
  private def extendExcludeClasses(excludeClasses) {
    excludeClasses.collect { getSubclasses(it) + it }.flatten().unique(false)
  }

  private def getSubclasses(String iri) {
		def ce = ontoHelper.dataFactory.getOWLClass(IRI.create(iri))
		ontoHelper.reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten() 
  }

  private def normalise(explainers, cid, returnAll) {
    GParsPool.withPool(o['threads']) { p ->
    explainers.findAllParallel { k, v -> v.nIc }
      .collectParallel{ k, v -> 
        if(o['egl']) { v.nPower = v.nInclusion }
        v 
      }.findAllParallel { v ->
        if(!returnAll) {
          if(data.groupings.size() > 1) {
            return v.nIc >= c.MIN_IC && 
              v.nPower >= c.MIN_R_SCORE && 
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
  }

  def scoreClasses(cid, classes) {
    scoreClasses(cid, classes, false)
  }

  def scoreClasses(cid, classes, returnAll) {
    if(o['verbose']) { "[...] Scoring ${classes.size()} candidates for $cid"}
    def explainers = new ConcurrentHashMap()
    GParsPool.withPool(o['threads']) { p ->
    classes.eachParallel {
      classes.each {
        processClass(explainers, cid, it)
      }
    }
    }

    // Note, this turns it into a list rather than a hashmap
    explainers = normalise(explainers, cid, returnAll) 

    if(o['verbose']) { "[...] Done scoring for $cid"}

    explainers
  }

  def scoreAllClasses(cid) {
    scoreAllClasses(cid, false)
  }

  def scoreAllClasses(cid, returnAll) {
    // No point running expensive unique here since we check in processClass whether we already have it
    def relevant
    GParsPool.withPool(o['threads']) { p ->
      relevant = data.allAssociations.collectParallel { scMap[it] }.flatten()
    }

    def explainers = new ConcurrentHashMap()
    GParsPool.withPool(o['threads']) { p ->
      relevant.eachParallel {
        processClass(explainers, cid, it)
      }
    }
    explainers = normalise(explainers, cid, returnAll) // Note, this turns it into a list rather than a hashmap
    explainers
  }

  static def Write(cid, s, labels, fName) {
    new File(fName).text = "iri\tallinclusion\tfullexclusion\tinclusivity\texclusivity\tr-score\tic\n" + s.collect {
      "${it.iri}\t${it.allInclusion}\t${it.fExclusion}\t${it.nInclusion}\t${it.nExclusion}\t${it.nPower}\t${it.nIc}"
    }.join('\n')
  }

  static def WriteLaTeX(cid, res, labels, fName) {
    def out = []

    out << "\\begin{tabular}"
    out << "{\\bf Class} & {\\bf r-score} & {\\bf Inclusion} & & {\\bf Exclusion} & {\\bf IC} \\\\"
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
