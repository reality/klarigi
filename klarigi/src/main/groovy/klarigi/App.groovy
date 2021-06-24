/*
 * This Groovy source file was generated by the Gradle 'init' task.
 */
package klarigi

// Todo, replace this with non-deprecated version
import groovy.cli.picocli.CliBuilder
import org.semanticweb.owlapi.util.*

class App {
  static void main(String[] args) {
    def cliBuilder = new CliBuilder(
      usage: 'klarigi [<options>]',
      header: 'Options:'
    )

    cliBuilder.with {
      h longOpt: 'help', 'Print this help text and exit.'

      _ longOpt: 'similarity-mode', 'Calculate semantic similarity instead of characterising groups', type: Boolean

      d longOpt: 'data', 'The data describing entities and associations. See documentation for format.', args: 1
      o longOpt: 'ontology', 'The ontology to use for explanations (should be the same as the ontology used to describe patients).', args: 1
      _ longOpt: 'turtle', 'Indicates that the ontology is a Turtle ontology (needed for calculating IC...)', type: Boolean

      ri longOpt: 'resnik-ic', 'Use Resnik annotation frequency for information content calculation', type: Boolean
      ic longOpt: 'ic', 'List of classes and associated information content values.', args: 1
      _ longOpt: 'save-ic', 'Save the IC values to the given file', args:1

      g longOpt: 'group', 'The group to explain.', args: 1

      _ longOpt: 'max-ic', 'Max IC to use in stepdown algorithm. Default: 0.8', args: 1
      _ longOpt: 'min-ic', 'Min IC to use in stepdown algorithm. Default: 0.4', args: 1
      _ longOpt: 'max-inclusion', 'Max inclusion to use in stepdown algorithm. Default: 0.95', args: 1
      _ longOpt: 'min-inclusion', 'Min inclusion to use in stepdown algorithm. Default: 0.3', args: 1
      _ longOpt: 'max-exclusion', 'Max exclusion to use in stepdown algorithm. Default: 0.95', args: 1
      _ longOpt: 'min-exclusion', 'Min exclusion to use in stepdown algorithm. Default: 0.3', args: 1
      _ longOpt: 'max-total-inclusion', 'Max total inclusion to use in stepdown algorithm. Default: 0.95 (probably don\'t want to edit this one)', args: 1
      _ longOpt: 'step', 'Step by which to reduce coefficients in stepdown algorithm. Default: 0.05', args: 1

      _ longOpt: 'power', 'Use modification of algorithm which uses normalised power instead of inc/exc', type: Boolean

      _ longOpt: 'reclassify', 'Attempt to reclassify the input using the derived explanations. This will help give some scores about how well the explanations fit the data', type: Boolean
      _ longOpt: 'classify', 'Pass a new file of unseen examples to classify using the explanations derived (test classify)', args: 1

      _ longOpt: 'output-scores', 'Output the results of the scorer. This can be useful for debugging, or identifying coefficient settings.', type: Boolean
      _ longOpt: 'output-type', 'Pass either "latex" or "tsv" to output as LaTeX table format or TSV format respectively.', args: 1
      _ longOpt: 'output-classification-scores', 'Output classification scores and true/false labels for each group into files. Useful for generating AUCs.', type: Boolean

      _ longOpt: 'output', 'File to output results to. If not given, will print to stdout', args: 1
      _ longOpt: 'print-members', 'Print members of groups by label (first column of data file). Only works with standard output (not LaTeX)', type: Boolean

      _ longOpt: 'verbose', 'Verbose output, mostly progress', type: Boolean, args: 0
    }

    if(args[0] == '-h' || args[0] == '--help') {
      cliBuilder.usage(); return;
    }
    
    SLF4JSilencer.silence();

    def o = cliBuilder.parse(args)
    
    if(o.h) { 
      cliBuilder.usage()
    }

    def k = new Klarigi(o)
    if(!o['similarity-mode']) {
      if(!o['group'] || (o['group'] && o['group'] == '*')) {
        def allExplanations = k.explainAllClusters(o['output-scores'], o['power'])
        allExplanations.each {
          k.output(it.cluster, it.results, o['output-type'], o['print-members'], o['output'])
        }

        if(o['reclassify']) {
          k.reclassify(allExplanations, o['output-classification-scores'])
        }
        if(o['classify']) {
          k.classify(o['classify'], allExplanations, o['output-classification-scores'])
        }
      } else {
        def r = k.explainCluster(o['group'], o['power'], o['output-scores'])
        k.output(o['group'], r, o['output-type'], o['print-members'], o['output'])

        if(o['reclassify']) {
          println "Must explain all groups for --reclassify"
        }
      }
    } else {
      k.genSim(o['output'])
    }
  }
}
