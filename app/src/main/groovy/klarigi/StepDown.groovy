public class StepDown {
  static def Run() {
    def stepDown
    stepDown = { e, icCutoff, exclusionCutoff, inclusionCutoff, totalInclusionCutoff ->
      ef = e.findAll {
        //println "comparing $it.normIc and $icCutoff for IC. comparing $it.nExclusion and $exclusionCutoff for exclusion"
        it.nIc >= icCutoff && it.nExclusion >= exclusionCutoff && it.nInclusion >= inclusionCutoff
      } 
      //println ef
      def totalCoverage = ((ef.collect { it.internalIncluded }.flatten().unique(false).size()) / clusters[cid].size()) * 100
      def totalExclusion = 1-(((ef.collect { it.internalExcluded }.flatten().unique(false).size()) / (clusters.collect {k,v->v.size()}.sum() - clusters[cid].size())) * 100)
      //println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
      if(totalCoverage <= (totalInclusionCutoff*100)) {
        if(inclusionCutoff <= MIN_INCLUSION) {
          if(icCutoff >= MIN_IC) {
            return stepDown(e, icCutoff - STEP, MAX_EXCLUSION, MAX_INCLUSION, totalInclusionCutoff)
          } else {
            return stepDown(e, MAX_IC, MAX_EXCLUSION, MAX_INCLUSION, totalInclusionCutoff - STEP)
          }
        } else {
          def newExclusion = exclusionCutoff - STEP
          if(newExclusion < MIN_EXCLUSION) { newExclusion = MIN_EXCLUSION  }
          return stepDown(e, icCutoff, newExclusion, inclusionCutoff - STEP, totalInclusionCutoff)
        }
      } 
      return [ef, totalCoverage, totalExclusion]
    }.trampoline()
  }
}
