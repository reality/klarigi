
public class Klarigi {
  // This is a map of entities and their cluster associations
  def groupings = [:]

  // This is a map of entities and their ontology class associations
  def associations = [:]

  // Ontology query utility classes
  def oReasoner
  def oDataFactory

  Klarigi(o) {
    loadData(o['data-file'])
    loadOntology(o['ontology-file'])
    explainerClusters(o)
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

  def explainClusters(o) {
    def scorer = new Scorer(oDataFactory, oReasoner, groupings, associations)
    def allExplanations = scorer.scoreClasses()
    def coefficients = Coefficients.Generate(o)
    def finalExplanations = StepDown.Run(coefficients)
  }
}
