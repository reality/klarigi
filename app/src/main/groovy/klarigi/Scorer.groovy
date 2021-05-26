public class Scorer {
  private def df
  private def reasoner
  private def groupings
  private def associations
  private def explainers = [:]
  Scorer(df, reasoner, groupings, associations) {
    this.df = df
    this.reasoner = reasoner
    this.groupings = groupings
    this.associations = associations
  }


  private def processClass(c) {
		if(explainers.containsKey(c)) { return; }
		def ce = df.getOWLClass(IRI.create(c))
		def subclasses = reasoner.getSubClasses(ce, false).collect { n -> n.getEntities().collect { it.getIRI().toString() } }.flatten()
		explainers[c] = [
			ic: ic[c],
			internalIncluded: clusters[cid].findAll { pid -> profiles[pid].any { pc -> subclasses.contains(pc) } },
      externalIncluded: clusters.collect { lcid, p ->
        if(lcid == cid) { return []; }
        p.findAll { pid ->
          profiles[pid].any { pc -> subclasses.contains(pc) }
        }
      }.flatten()
		]
    explainers[c].inclusion = explainers[c].internalIncluded.size()
    explainers[c].exclusion = explainers[c].externalIncluded.size()

		reasoner.getSuperClasses(ce, true).each { n ->
			n.getEntities().each { sc ->
				def strc = sc.getIRI().toString()
        if(limitExpToFacet && limitFacet && !fMap[limitFacet].contains(strc)) {
          return; 
        }
				processClass(strc)
			}
		}
	}

  private def normalise() {
    explainers = explainers.findAll { k, v -> v.ic }
    explainers = explainers.collect { k, v ->
      v.nIc = v.ic
      v.nInclusion = v.inclusion / clusters[cid].size()
      v.nExclusion = 1 - (v.exclusion / clusters.findAll { kk, vv -> kk != cid }.collect { kk, vv -> vv.size() }.sum())
      v.iri = k 
      v
    }
  }

  def scoreClasses(classes) {
    classes.each {
      processClass(c)
    }
    normalise()
    explainers
  }
} 
