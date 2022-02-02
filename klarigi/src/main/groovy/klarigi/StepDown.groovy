package klarigi


public class StepDown {
  static def Run(c, cid, candidates, data, debug) {
    RunNewAlgorithm(c, cid, candidates, data, debug)
  }

  static def RunNewAlgorithm(c, cid, candidates, data, debug) {
    def totalCoverage = 0
    def stepDown = { e, icCutoff, powerCutoff, totalInclusionCutoff ->
      while(totalCoverage <= (totalInclusionCutoff*100)) {
        def ef = candidates.findAll {
          it.nIc >= icCutoff && it.nPower >= powerCutoff
        } 
        //println ef
        totalCoverage = ((ef.findAll { it.nInclusion <= c.MAX_INCLUSION && it.nExclusion <= c.MAX_EXCLUSION }.collect { it.internalIncluded }.flatten().unique(false).size()) / data.groupings[cid].size()) * 100
        def totalExclusion = 0
        if(data.groupings.size() > 1) {
          totalExclusion = (1-(((ef.collect { it.externalInclusion }.flatten().unique(false).size()) / (data.groupings.collect {k,v->v.size()}.sum() - data.groupings[cid].size()))))*100
        }
        if(debug) {
          println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
        }
        if(totalCoverage < (totalInclusionCutoff*100)) {
          totalCoverage = 0
          /*if(icCutoff <= c.BOT_IC) {
            icCutoff = c.BOT_POWER
            
            if(powerCutoff > c.BOT_POWER) {
              powerCutoff -= c.STEP
            } else {
              powerCutoff = c.TOP_POWER
              totalInclusionCutoff -= c.STEP
            }
          } else {
            icCutoff -= c.STEP
          }*/
          if(powerCutoff <= c.BOT_POWER) {
            powerCutoff = c.TOP_POWER

            if(icCutoff > c.BOT_IC) {
              icCutoff -= c.STEP
            } else {
              icCutoff = c.TOP_IC
              totalInclusionCutoff -= c.STEP
            }
            
          } else {
            powerCutoff -= c.STEP
          }
        } else { // this is very dirty, but i quickly hacked this from a recursive function. TODO exit the loop properly
          return [ef, totalCoverage, candidates]
        } 
      }
      return [ef, 0, candidates] // fail case
    }

    return stepDown(candidates, c.TOP_IC, c.TOP_POWER, c.TOP_TOTAL_INCLUSION)
  }

  static def Print(cid, res, pVals, egl, labels, s, toFile, members, printMembers) {
    def out = []
    out << "----------------"
    out << "Group: $cid ($s members)"
    if(printMembers) {
      out << "Members: " + members.join(', ')
    }
    out << "Overall inclusion: ${res[1].toDouble().round(2)}%"
    out << "Explanatory classes:"
    res[0].each { z ->
      if(egl) {
        if(pVals) {
          def ps = pVals[z.iri]
          out << "  IRI: ${labels[z.iri]} (${z.iri}), Inclusion: ${z.nInclusion.toDouble().round(2)} (p=${ps.incP}), IC: ${z.nIc.toDouble().round(2)}"
        } else {
          out << "  IRI: ${labels[z.iri]} (${z.iri}), Inclusion: ${z.nInclusion.toDouble().round(2)}, IC: ${z.nIc.toDouble().round(2)}"
        }

      } else {
        if(pVals) {
          def ps = pVals[z.iri]
          out << "  IRI: ${labels[z.iri]} (${z.iri}), Power: ${z.nPower.toDouble().round(2)} (p=${ps.powP}) (inc: ${z.nInclusion.toDouble().round(2)} (p=${ps.incP}), exc: ${z.nExclusion.toDouble().round(2)} (p=${ps.excP})), IC: ${z.nIc.toDouble().round(2)}"
        } else {
          out << "  IRI: ${labels[z.iri]} (${z.iri}), Power: ${z.nPower.toDouble().round(2)} (inc: ${z.nInclusion.toDouble().round(2)}, exc: ${z.nExclusion.toDouble().round(2)}), IC: ${z.nIc.toDouble().round(2)}"
        }
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

  static def PrintLaTeX(cid, res, pVals, egl, labels, s, toFile) {
    def out = []

    if(egl) {
      out << "\\begin{tabular}{p{5cm}|l|l}"
      out << "{\\bf $cid ($s members)} & {\\bf Inclusion} & {\\bf IC} \\\\"
    } else {
      out << "\\begin{tabular}{p{5cm}|l|l|l|l}"
      out << "{\\bf $cid ($s members)} & {\\bf Power} & {\\bf Inclusion} & {\\bf Exclusion} & {\\bf IC} \\\\"
    }

    res[0].sort { -it.nPower }.each {
      def pIri = it.iri
      if(pIri =~ 'obolibrary.org') {
        pIri = pIri.tokenize('/').last()
        pIri = pIri.replace('_', ':')
        pIri = "{\\tt $pIri}"
      } else {
        pIri = it.iri.replaceAll('_', '\\\\_')
      }

      if(egl) {
        if(pVals) {
          def ps = pVals[it.iri]
          out << "${labels[it.iri]} (${pIri}) & ${it.nInclusion.toDouble().round(2)} (p=${ps.incP}) & ${it.ic.toDouble().round(2)} \\\\"
        } else {
          out << "${labels[it.iri]} (${pIri}) & ${it.nInclusion.toDouble().round(2)} & ${it.ic.toDouble().round(2)} \\\\"
        }
      } else {
        if(pVals) {
          def ps = pVals[it.iri]
          out << "${labels[it.iri]} (${pIri}) & ${it.nPower.toDouble().round(2)} (p=${ps.powP}) & ${it.nInclusion.toDouble().round(2)} (p=${ps.incP}) & ${it.nExclusion.toDouble().round(2)} (p=${ps.excP}) & ${it.ic.toDouble().round(2)} \\\\"
        } else {
          out << "${labels[it.iri]} (${pIri}) & ${it.nPower.toDouble().round(2)} & ${it.nInclusion.toDouble().round(2)} & ${it.nExclusion.toDouble().round(2)} & ${it.ic.toDouble().round(2)} \\\\"
        }
      }
    }
    out << "{\\em Overall} & - & ${res[1].toDouble().round(2)} & - & - \\\\ "
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
