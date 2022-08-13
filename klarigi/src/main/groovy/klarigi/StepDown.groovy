package klarigi

import java.util.concurrent.atomic.*
import groovyx.gpars.*
import groovyx.gpars.GParsPool

public class StepDown {
  static def Run(c, cid, candidates, data, egl, debug) {
    RunNewAlgorithm(c, cid, candidates, data, debug)
  }

  static def RunNewAlgorithm(c, cid, candidates, data, threads, debug) {
    def totalCoverage = 0
    def stepDown = { e, icCutoff, powerCutoff, totalInclusionCutoff ->
      while(totalCoverage <= (totalInclusionCutoff*100)) {

        def ef = candidates.findAll {
          it.nIc >= icCutoff && it.nPower >= powerCutoff
        } 

        totalCoverage = CalculateOI(c, cid, data, ef, threads, false)

        if(debug) {
          println "DEBUG: running with ic cutoff: $icCutoff exclusion cutoff: $exclusionCutoff inclusion cutoff: $inclusionCutoff total: coverage: $totalCoverage/$totalInclusionCutoff"
        }
        if(totalCoverage < (totalInclusionCutoff*100)) {
          totalCoverage = 0
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
          return [ef, CalculateOI(c, cid, data, ef, threads, true), candidates]
        } 
      }
      return [ef, 0, candidates] // fail case
    }

    return stepDown(candidates, c.TOP_IC, c.TOP_POWER, c.TOP_TOTAL_INCLUSION)
  }

  // We don't use the associations in data.associations, in case we want to use a different
  static def CalculateOI(c, cid, data, candidates, threads, total) {
    def contributingEf = candidates
    if(!total) {
      contributingEf = contributingEf.findAll { it.nInclusion <= c.MAX_INCLUSION && it.nExclusion <= c.MAX_EXCLUSION }
    }
    
    def covered = new AtomicInteger(0)
    GParsPool.withPool(threads) { p ->
      data.groupings[cid].each { ee ->
        if(contributingEf.any { it.incEnts.containsKey(ee) }) {
          covered.getAndIncrement()
        }
      } 
    }

    return (covered / data.groupings[cid].size()) * 100;
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
          out << "  ${labels[z.iri]} (${z.iri}), Inclusion: ${z.nInclusion.toDouble().round(2)} (p=${ps.incP.toDouble().round(3)}), IC: ${z.nIc.toDouble().round(2)}"
        } else {
          out << "  ${labels[z.iri]} (${z.iri}), Inclusion: ${z.nInclusion.toDouble().round(2)}, IC: ${z.nIc.toDouble().round(2)}"
        }

      } else {
        // TODO it doesn't print the pvals here/
        if(pVals) {
          def ps = pVals[z.iri]
          out << "  ${labels[z.iri]} (${z.iri}), r-score: ${z.nPower.toDouble().round(2)}, (inc: ${z.nInclusion.toDouble().round(2)} (p=${ps.incP.toDouble().round(3)}), exc: ${z.nExclusion.toDouble().round(2)} (p=${ps.excP.toDouble().round(3)})), IC: ${z.nIc.toDouble().round(2)}"
        } else {
          out << "  ${labels[z.iri]} (${z.iri}), r-score: ${z.nPower.toDouble().round(2)} (inc: ${z.nInclusion.toDouble().round(2)}, exc: ${z.nExclusion.toDouble().round(2)}), IC: ${z.nIc.toDouble().round(2)}"
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
      out << "{\\bf $cid ($s members)} & {\\bf r-score} & {\\bf Inclusion} & {\\bf Exclusion} & {\\bf IC} \\\\"
    }

    out << "\\hline \\hline"

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
          out << "${labels[it.iri]} (${pIri}) & ${it.nInclusion.toDouble().round(2)} & ${it.nIc.toDouble().round(2)} \\\\"
        } else {
          out << "${labels[it.iri]} (${pIri}) & ${it.nInclusion.toDouble().round(2)} & ${it.nIc.toDouble().round(2)} \\\\"
        }
      } else {
        if(pVals) {
          def ps = pVals[it.iri]
          out << "${labels[it.iri]} (${pIri}) & ${it.nPower.toDouble().round(2)} & ${it.nInclusion.toDouble().round(2)} (p=${ps.incP.toDouble().round(3)}) & ${it.nExclusion.toDouble().round(2)} (p=${ps.excP.toDouble().round(3)}) & ${it.nIc.toDouble().round(2)} \\\\"
        } else {
          out << "${labels[it.iri]} (${pIri}) & ${it.nPower.toDouble().round(2)} & ${it.nInclusion.toDouble().round(2)} & ${it.nExclusion.toDouble().round(2)} & ${it.nIc.toDouble().round(2)} \\\\"
        }
      }
    }
    out << "\\hline"
    if(egl) {
      out << "{\\em Overall} & - & {\\em ${res[1].toDouble().round(2)} } & - \\\\ "
    } else {
      out << "{\\em Overall} & - & {\\em ${res[1].toDouble().round(2)} } & - & - \\\\ "
    }
    out << "\\end{tabular}"
    out = out.join('\n')

    if(toFile) {
      new File(toFile).text += '\n' + out 
    } else {
      println out
    }
  }
}
