package klarigi

public class StepDown {
  static def Run(c, cid, candidates, data) {
    def totalCoverage = 0
    def stepDown = { e, icCutoff, exclusionCutoff, inclusionCutoff, totalInclusionCutoff ->
      while(totalCoverage <= (totalInclusionCutoff*100)) {
        def ef = candidates.findAll {
          //println "comparing $it.normIc and $icCutoff for IC. comparing $it.nExclusion and $exclusionCutoff for exclusion"
          it.nIc >= icCutoff && it.nExclusion >= exclusionCutoff && it.nInclusion >= inclusionCutoff
        } 
        //println ef
        totalCoverage = ((ef.collect { it.internalIncluded }.flatten().unique(false).size()) / data.groupings[cid].size()) * 100
        def totalExclusion = 1-(((ef.collect { it.internalExcluded }.flatten().unique(false).size()) / (data.groupings.collect {k,v->v.size()}.sum() - data.groupings[cid].size())) * 100)
        //println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
        if(totalCoverage <= (totalInclusionCutoff*100)) {
          if(inclusionCutoff <= c.MIN_INCLUSION) {
            if(icCutoff >= c.MIN_IC) {
              icCutoff -= c.STEP
              exclusionCutoff = c.MAX_EXCLUSION
              inclusionCutoff = c.MAX_INCLUSION
              continue;
            } else {
              icCutoff = c.MAX_IC
              exclusionCutoff = c.MAX_EXCLUSION
              inclusionCutoff = c.MAX_INCLUSION
              totalInclusionCutoff -= c.STEP
              continue;
            }
          } else {
            exclusionCutoff = exclusionCutoff - c.STEP
            if(exclusionCutoff < c.MIN_EXCLUSION) { exclusionCutoff = c.MIN_EXCLUSION  }
            inclusionCutoff -= c.STEP
            continue;
          }
        } else { // this is very dirty, but i quickly hacked this from a recursive function. TODO exit the loop properly
          return [ef, totalCoverage, totalExclusion]
        } 
      }
      return [[],0,0] // fail case
    }

    return stepDown(candidates, c.MAX_IC, c.MAX_EXCLUSION, c.MAX_INCLUSION, c.MAX_TOTAL_INCLUSION)
  }

  static def Print(cid, res, labels, s, toFile, members, printMembers) {
    def out = []
    out << "----------------"
    out << "Group: $cid ($s members)"
    if(printMembers) {
      out << "Members: " + members.join(', ')
    }
    out << "Overall inclusion: ${res[1]}%"
    out << "Explanatory classes:"
    res[0].each { z ->
      out << "  IRI: ${labels[z.iri]} (${z.iri}), Inclusivity: ${z.nInclusion}, Exclusivity: ${z.nExclusion}, IC: ${z.nIc}"
    }
    out << "----------------"
    out << ""
    out = out.join('\n')

    if(toFile) {
      new File(toFile).text += '\n' + out 
    } else {
      println out
    }
  }

  static def PrintLaTeX(cid, res, labels, s, toFile) {
    def out = []
    out << "{\\bf Group: $cid ($s members), overall inclusivity: ${res[1].toDouble().round(2)})} & {\\bf Exclusion} & {\\bf Inclusion} & {\\bf IC} \\\\"
    res[0].sort { -it.nIc }.each {
      out << "${labels[it.iri]} (HP:${it.iri.tokenize('_')[1]}) & ${it.nExclusion.toDouble().round(2)} & ${it.nInclusion.toDouble().round(2)} & ${it.ic.toDouble().round(2)} \\\\"
    }
    out = out.join('\n')

    if(toFile) {
      new File(toFile).text += '\n' + out 
    } else {
      println out
    }
  }
}
