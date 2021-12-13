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
      pp longOpt: 'pp', 'Enable phenopacket input mode. If this is enabled, the --data argument should be either a single phenopacket JSON file, or a directory, from which all phenopacket JSON files will be read.', type: Boolean
      o longOpt: 'ontology', 'The ontology to use for explanations (should be the same as the ontology used to describe patients).', args: 1
      _ longOpt: 'turtle', 'Indicates that the ontology is a Turtle ontology (needed for calculating IC...)', type: Boolean

      _ longOpt: 'exclude-classes', 'A semi-colon delimited list of IRIs to exclude from scoring (and thus inclusion in explanations). Their subclasses will also be discluded.', args: 1

      ri longOpt: 'resnik-ic', 'Use Resnik annotation frequency for information content calculation', type: Boolean
      ic longOpt: 'ic', 'List of classes and associated information content values.', args: 1
      _ longOpt: 'save-ic', 'Save the IC values to the given file', args:1

      g longOpt: 'group', 'The group to explain.', args: 1
      egl longOpt: 'exclusive-group-load', 'If set to true, only the group given in -g will be loaded into the corpus', type: Boolean
      gf longOpt: 'group-file', 'You can pass a file with a list of groups to: one per line. If you do this, the --group argument will be ignored.', args: 1

      _ longOpt: 'max-ic', 'Max IC to use in stepdown algorithm. Default: 0.8', args: 1
      _ longOpt: 'min-ic', 'Min IC to use in stepdown algorithm. Default: 0.4', args: 1
      _ longOpt: 'max-inclusion', 'Max inclusion to use in stepdown algorithm. Default: 0.95', args: 1
      _ longOpt: 'min-inclusion', 'Min inclusion to use in stepdown algorithm. Default: 0.3', args: 1
      _ longOpt: 'max-exclusion', 'Max exclusion to use in stepdown algorithm. Default: 0.95', args: 1
      _ longOpt: 'min-exclusion', 'Min exclusion to use in stepdown algorithm. Default: 0.3', args: 1
      _ longOpt: 'max-total-inclusion', 'Max total inclusion to use in stepdown algorithm. Default: 0.95 (probably don\'t want to edit this one)', args: 1
      _ longOpt: 'min-power', 'Min acceptable value of power.', args: 1
      _ longOpt: 'step', 'Step by which to reduce coefficients in stepdown algorithm. Default: 0.05', args: 1
      _ longOpt: 'debug', 'Print some debug output', type: Boolean

      _ longOpt: 'power', 'Use modification of algorithm which uses normalised power instead of inc/exc', type: Boolean

      _ longOpt: 'reclassify', 'Attempt to reclassify the input using the derived explanations. This will help give some scores about how well the explanations fit the data', type: Boolean
      _ longOpt: 'classify', 'Pass a new file of unseen examples to classify using the explanations derived (test classify)', args: 1
      ecm longOpt: 'explainers-classify-mode', 'Only use the smaller set of explanatory variables for classification.', type: Boolean
      p longOpt: 'perms', 'Do permutation testing to provide p values for power, inclusion, and exclusion.', args: 1

      _ longOpt: 'output-scores', 'Output the results of the scorer. This can be useful for debugging, or identifying coefficient settings.', type: Boolean
      _ longOpt: 'output-type', 'Pass either "latex" or "tsv" to output as LaTeX table format or TSV format respectively.', args: 1
      _ longOpt: 'output-classification-scores', 'Output classification scores and true/false labels for each group into files. Useful for generating AUCs.', type: Boolean
      _ longOpt: 'output-exp-dataframe', "Output a TSV describing a 'data-frame' of categorical values for each term appearing in derived explanations. Easy to load into R and do stuff with.", type: Boolean

      _ longOpt: 'threads', 'Number of threads to use, particularly for calculating scoring. This should speed things up a lot with larger datasets.', args: 1

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

    def threads = 1
    if(o['threads']) {
      try {
        threads = Integer.parseInt(o['threads'])
      } catch(e) {
        println 'Warning: Could not parse --threads argument. Defaulting to 1.'
        threads = 1
      }
    }
    def excludeClasses = []
    if(o['exclude-classes']) {
      excludeClasses = o['exclude-classes'].tokenize()
      if(excludeClasses.size() > 0 && excludeClasses[0] =~ /:/ && excludeClasses[0].indexOf('http') == -1) { // stupid
          excludeClasses= excludeClasses.collect { 
            'http://purl.obolibrary.org/obo/' + it.replace(':', '_')
          }
      }
    }

    def k = new Klarigi(o)
    if(!o['similarity-mode']) {
      def allExplanations 
      if(o['group-file']) {
        def groups
        try {
          groups = new File(o['group-file']).text.split('\n')
        } catch(e) {
          println "Could not handle the --group-file: ${e.toString()}"
          System.exit(1)
        }

        allExplanations = k.explainClusters(groups, excludeClasses, o['output-scores'], o['power'], threads, o['debug'])
      } else if(o['group'] && o['group'] != '*') {
        allExplanations = k.explainClusters([o['group']], excludeClasses, o['output-scores'], o['power'], threads, o['debug'])
      } else {
        allExplanations = k.explainAllClusters(o['output-scores'], excludeClasses, o['power'], threads, o['debug'])
      }

      def pVals = [:]
      if(o['perms']) {
        pVals = k.permutationTest(allExplanations, excludeClasses, threads, Integer.parseInt(o['perms']))
      }

      allExplanations.each {
        k.output(it.cluster, it.results, pVals[it.cluster], o['egl'], o['output-type'], o['print-members'], o['output'])
      }

      if(o['output-exp-dataframe']) {
        k.writeDataframe('train', allExplanations)
      }
      
      if(o['reclassify']) {
        k.reclassify(allExplanations, o['output-classification-scores'], o['ecm'])
      }
      if(o['classify']) {
        k.classify(o['classify'], allExplanations, o['output-classification-scores'], o['ecm'])

        if(o['output-exp-dataframe']) {
          k.writeDataframe('test', allExplanations)
        }
      }

    } else {
      k.genSim(o['output'], o['group'])
    }
  }
}
