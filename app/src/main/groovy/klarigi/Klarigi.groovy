
public class Klarigi {
  // This is a map of entities and their cluster associations
  def groupings = [:]

  // This is a map of entities and their ontology class associations
  def associations = [:]

  // Ontology query utility classes
  def oReasoner
  def oDataFactory

  Klarigi(dataFile, ontologyFile) {
    loadData(dataFile)
    loadOntology(ontologyFile)
    explainerClusters()
  }

  def loadData(dataFile) {
    dataFile.splitEachLine('\t') {
      def (entity, terms, group) = it

      associations[entity] = terms.tokenize(';')

      if(!groupings.containsKey(group)) {
        groupings[group] = []
      }
      groupings[group] << entity
    }
  }

  def explainClusters() {
    def scorer = new Scorer(oDataFactory, oReasoner, groupings, associations)
    def allExplanations = scorer.scoreClasses()
    def finalExplanations = StepDown.Run()
  }
}
