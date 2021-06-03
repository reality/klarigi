package klarigi

import slib.graph.algo.utils.GAction;
import slib.graph.algo.utils.GActionType;
import org.openrdf.model.URI;
import slib.graph.algo.accessor.GraphAccessor;
import slib.graph.algo.validator.dag.ValidatorDAG;
import slib.graph.io.conf.GDataConf;
import slib.graph.io.conf.GraphConf;
import slib.graph.io.loader.GraphLoaderGeneric;
import slib.graph.io.util.GFormat;
import slib.graph.model.graph.G;
import slib.graph.model.impl.graph.memory.GraphMemory;
import slib.graph.model.impl.repo.URIFactoryMemory;
import slib.graph.model.repo.URIFactory;
import slib.sml.sm.core.engine.SM_Engine;
import slib.sml.sm.core.metrics.ic.utils.IC_Conf_Topo;
import slib.sml.sm.core.metrics.ic.utils.ICconf;
import slib.graph.algo.extraction.utils.*
import slib.sglib.io.loader.*
import slib.sml.sm.core.metrics.ic.utils.*
import slib.sml.sm.core.utils.*
import slib.sglib.io.loader.bio.obo.*
import org.openrdf.model.URI
import slib.graph.algo.extraction.rvf.instances.*
import slib.sglib.algo.graph.utils.*
import slib.utils.impl.Timer
import slib.graph.algo.extraction.utils.*
import slib.graph.model.graph.*
import slib.graph.model.repo.*
import slib.graph.model.impl.graph.memory.*
import slib.sml.sm.core.engine.*
import slib.graph.io.conf.*
import slib.graph.model.impl.graph.elements.*
import slib.graph.algo.extraction.rvf.instances.impl.*
import slib.graph.model.impl.repo.*
import slib.graph.io.util.*
import slib.graph.io.loader.*

import slib.sml.sm.core.metrics.ic.utils.IC_Conf_Corpus;
import slib.sml.sm.core.utils.SMConstants;
import slib.sml.sm.core.utils.SMconf;
import slib.utils.ex.SLIB_Exception;
import slib.utils.impl.Timer;

public class InformationContent {
  static def DEFAULT_IC = SMConstants.FLAG_ICI_ZHOU_2008

  private engine
  private icConf
  private factory

  InformationContent(ontologyPath) {
    factory = URIFactoryMemory.getSingleton()

    /*def graphURI = factory.getURI('http://purl.obolibrary.org/obo/HP_')
    factory.loadNamespacePrefix("HP", graphURI.toString());
    G graph = new GraphMemory(graphURI)*/

    G graph = new GraphMemory()

    def dataConf = new GDataConf(GFormat.RDF_XML, ontologyPath)
    //def actionRerootConf = new GAction(GActionType.REROOTING)
    //actionRerootConf.addParameter("root_uri", "http://purl.obolibrary.org/obo/HP_0000001"); // phenotypic abnormality

    def gConf = new GraphConf()
    gConf.addGDataConf(dataConf)
    //gConf.addGAction(actionRerootConf)

    GraphLoaderGeneric.load(gConf, graph)
    def roots = new ValidatorDAG().getTaxonomicRoots(graph)

    icConf = new IC_Conf_Topo(DEFAULT_IC)
    engine = new SM_Engine(graph)
  }

  def getInformationContent(cList) {
    def res = [:]
    cList.each { c ->
      try {
        def cTerm = factory.getURI(c)
        res[c] = engine.getIC(icConf, cTerm)
      } catch(e) {}
    }
    res
  }

  static def Write(ic, path) {
    new File(path).text = ic.collect { k, v -> "$k\t$v" }.join('\n')
  }
}
