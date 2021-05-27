package klarigi

public class StepDown {
  static def Run(c, cid, candidates, data) {
    def stepDown
    stepDown = { e, icCutoff, exclusionCutoff, inclusionCutoff, totalInclusionCutoff ->
      def ef = e.findAll {
        //println "comparing $it.normIc and $icCutoff for IC. comparing $it.nExclusion and $exclusionCutoff for exclusion"
        it.nIc >= icCutoff && it.nExclusion >= exclusionCutoff && it.nInclusion >= inclusionCutoff
      } 
      //println ef
      def totalCoverage = ((ef.collect { it.internalIncluded }.flatten().unique(false).size()) / data.groupings[cid].size()) * 100
      def totalExclusion = 1-(((ef.collect { it.internalExcluded }.flatten().unique(false).size()) / (data.groupings.collect {k,v->v.size()}.sum() - data.groupings[cid].size())) * 100)
      //println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
      if(totalCoverage <= (totalInclusionCutoff*100)) {
        if(inclusionCutoff <= c.MIN_INCLUSION) {
          if(icCutoff >= c.MIN_IC) {
            return stepDown(e, icCutoff - c.STEP, c.MAX_EXCLUSION, c.MAX_INCLUSION, totalInclusionCutoff)
          } else {
            return stepDown(e, c.MAX_IC, c.MAX_EXCLUSION, c.MAX_INCLUSION, totalInclusionCutoff - c.STEP)
          }
        } else {
          def newExclusion = exclusionCutoff - c.STEP
          if(newExclusion < c.MIN_EXCLUSION) { newExclusion = c.MIN_EXCLUSION  }
          return stepDown(e, icCutoff, newExclusion, inclusionCutoff - c.STEP, totalInclusionCutoff)
        }
      } 
      return [ef, totalCoverage, totalExclusion]
    }.trampoline()

    return stepDown(candidates, c.MAX_IC, c.MAX_EXCLUSION, c.MAX_INCLUSION, c.MAX_TOTAL_INCLUSION)
  }
}
