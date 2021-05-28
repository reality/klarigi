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

      d longOpt: 'data', 'The data describing entities and associations. See documentation for format.', args: 1
      o longOpt: 'ontology', 'The ontology to use for explanations (should be the same as the ontology used to describe patients).', args: 1

      ic longOpt: 'ic', 'List of classes and associated information content values.', args: 1
      _ longOpt: 'save-ic', 'Save the IC values to the given file', args:1

      g longOpt: 'group', 'The group to explain.', args: 1

      _ longOpt: 'max-ic', 'Max IC to use in stepdown algorithm. Default: 0.8' 
      _ longOpt: 'min-ic', 'Min IC to use in stepdown algorithm. Default: 0.4' 
      _ longOpt: 'max-inclusion', 'Max inclusion to use in stepdown algorithm. Default: 0.95' 
      _ longOpt: 'min-inclusion', 'Min inclusion to use in stepdown algorithm. Default: 0.3' 
      _ longOpt: 'max-exclusion', 'Max exclusion to use in stepdown algorithm. Default: 0.95' 
      _ longOpt: 'min-exclusion', 'Min exclusion to use in stepdown algorithm. Default: 0.3' 
      _ longOpt: 'max-total-inclusion', 'Max total inclusion to use in stepdown algorithm. Default: 0.95 (probably don\'t want to edit this one)' 
      _ longOpt: 'step', 'Step by which to reduce coefficients in stepdown algorithm. Default: 0.05'

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
    if(!o['group'] || (o['group'] && o['group'] == '*')) {
      k.explainAllClusters()
    } else {
      k.explainCluster(o['group'])
    }
  }
}
