package klarigi


public class StepDown {
  static def Run(c, cid, candidates, data, debug) {
    def totalCoverage = 0
    def stepDown = { e, icCutoff, exclusionCutoff, inclusionCutoff, totalInclusionCutoff ->
      while(totalCoverage <= (totalInclusionCutoff*100)) {
        def ef = candidates.findAll {
          //println "comparing $it.normIc and $icCutoff for IC. comparing $it.nExclusion and $exclusionCutoff for exclusion"
          it.nIc >= icCutoff && it.nExclusion >= exclusionCutoff && it.nInclusion >= inclusionCutoff && it.nPower > 0
        } 
        //println ef
        totalCoverage = ((ef.collect { it.internalIncluded }.flatten().unique(false).size()) / data.groupings[cid].size()) * 100
        /*def totalExclusion = 100
        if(data.groupings.size() > 1) {
          totalExclusion = (1-(((ef.collect { it.internalExcluded }.flatten().unique(false).size()) / (data.groupings.collect {k,v->v.size()}.sum() - data.groupings[cid].size()))))*100
        }*/
        if(debug) {
          println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
        }
        if(totalCoverage < (totalInclusionCutoff*100)) {
          totalCoverage = 0 // OMG
          if(inclusionCutoff <= c.MIN_INCLUSION) {
            if(icCutoff > c.MIN_IC) {
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
          return [ef, totalCoverage, candidates]
        } 
      }
      return [[],0,0,candidates] // fail case
    }

    return stepDown(candidates, c.MAX_IC, c.MAX_EXCLUSION, c.MAX_INCLUSION, c.MAX_TOTAL_INCLUSION)
  }

  static def RunNewAlgorithm(c, cid, candidates, data, debug) {
    def totalCoverage = 0
    def stepDown = { e, icCutoff, powerCutoff, totalInclusionCutoff ->
      while(totalCoverage <= (totalInclusionCutoff*100)) {
        def ef = candidates.findAll {
          //println "comparing $it.normIc and $icCutoff for IC. comparing $it.nExclusion and $exclusionCutoff for exclusion"
          it.nIc >= icCutoff && it.nPower >= powerCutoff
        } 
        //println ef
        totalCoverage = ((ef.collect { it.internalIncluded }.flatten().unique(false).size()) / data.groupings[cid].size()) * 100
        /*def totalExclusion = 100
        if(data.groupings.size() > 1) {
          totalExclusion = (1-(((ef.collect { it.internalExcluded }.flatten().unique(false).size()) / (data.groupings.collect {k,v->v.size()}.sum() - data.groupings[cid].size()))))*100
        }*/
        if(debug) {
          println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
        }
        if(totalCoverage < (totalInclusionCutoff*100)) {
          totalCoverage = 0
          if(powerCutoff <= c.MIN_POWER) {
            if(icCutoff > c.MIN_IC) {
              icCutoff -= c.STEP
              powerCutoff = c.MAX_POWER
              continue;
            } else {
              icCutoff = c.MAX_IC
              powerCutoff = c.MAX_POWER
              totalInclusionCutoff -= c.STEP
              continue;
            }
          } else {
            powerCutoff -= c.STEP
            continue;
          }
        } else { // this is very dirty, but i quickly hacked this from a recursive function. TODO exit the loop properly
          return [ef, totalCoverage, candidates]
        } 
      }
      return [[],0,0, candidates] // fail case
    }

    return stepDown(candidates, c.MAX_IC, c.MAX_POWER, c.MAX_TOTAL_INCLUSION)
  }

  static def Print(cid, res, pVals, labels, s, toFile, members, printMembers) {
    def out = []
    out << "----------------"
    out << "Group: $cid ($s members)"
    if(printMembers) {
      out << "Members: " + members.join(', ')
    }
    out << "Overall inclusion: ${res[1].toDouble().round(2)}%"
    out << "Explanatory classes:"
    res[0].each { z ->
      if(pVals) {
        def ps = pVals[z.iri]
        out << "  IRI: ${labels[z.iri]} (${z.iri}), Power: ${z.nPower.toDouble().round(2)} (p<=${ps.powP}) (inc: ${z.nInclusion.toDouble().round(2)} (p<=${ps.incP}), exc: ${z.nExclusion.toDouble().round(2)} (p<=${ps.excP})), IC: ${z.nIc.toDouble().round(2)}"
      } else {
        out << "  IRI: ${labels[z.iri]} (${z.iri}), Power: ${z.nPower.toDouble().round(2)} (inc: ${z.nInclusion.toDouble().round(2)}, exc: ${z.nExclusion.toDouble().round(2)}), IC: ${z.nIc.toDouble().round(2)}"
      }
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

  static def PrintTSV(cid, res, labels, s, toFile) {
    def out = []
    def iris = res[0].collect { it.iri }
    out << "$cid\t${iris.join(';')}"

    if(toFile) {
      new File(toFile).text += '\n' + out 
    } else {
      println out
    }

  }

  static def PrintLaTeX(cid, res, labels, s, toFile) {
    def out = []
    out << "\\begin{tabular}{p{10cm}|c|c|c|c}"
    out << "{\\bf Group: $cid ($s members)} & {\\bf Power} & {\\bf Exclusion} & {\\bf Inclusion} & {\\bf IC} \\\\"
    res[0].sort { -it.nIc }.each {
      def pIri = it.iri
      if(pIri =~ 'obolibrary.org') {
        pIri = pIri.tokenize('/').last()
        pIri = pIri.replace('_', ':')
        pIri = "{\\tt $pIri}"
      } else {
        pIri = it.iri.replaceAll('_', '\\\\_')
      }

      out << "${labels[it.iri]} (${pIri}) & ${it.nPower.toDouble().round(2)} & ${it.nExclusion.toDouble().round(2)} & ${it.nInclusion.toDouble().round(2)} & ${it.ic.toDouble().round(2)} \\\\"
    }
    out << "{\\em Overall} & - & ${res[2].toDouble().round(2)} & - & - \\\\ "
    out << "\\hline"
    out << "\\end{tabular}"
    out = out.join('\n')

    if(toFile) {
      new File(toFile).text += '\n' + out 
    } else {
      println out
    }
  }
}
