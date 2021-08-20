package klarigi

import spock.lang.Specification
import spock.lang.Shared

class KlarigiTest extends Specification {
	@Shared k 
  @Shared s
  @Shared explanations

  def "load_klarigi"() {
    given:
      def o = [
				'data': getClass().getResource('/patient_omim_map.txt').toURI(),
				'ontology': getClass().getResource('/hp.owl').toURI(),
        'ic': getClass().getResource('/ic.lst').toURI()
			]
    when:
			k = new Klarigi(o)
    then:
      k instanceof Klarigi
  }

	def "test_associations"() {
    when:
      def p3 = ["http://purl.obolibrary.org/obo/HP_0001510":true, "http://purl.obolibrary.org/obo/HP_0031960": true]
    then:
      k.data.associations.size() == 20 
    then:
      k.data.associations["3"] == p3
  }

	def "test_groupings"() {
    when: 
      def omims = ["OMIM:604271","OMIM:172870","OMIM:615121","OMIM:275450"]
    then: 
      k.data.groupings.size() == 4
    then:
      omims.findAll { k.data.groupings.containsKey(it) }.size() == omims.size()
	}

  def "test_ic"() {
    when:
      def testHp = "http://purl.obolibrary.org/obo/HP_0010593"
      def testValue = Float.parseFloat('0.95533967') // Note that this is rounded w.r.t what's in the file.
    then:
      k.data.ic.size() == 15629
    then:
      k.data.ic[testHp] == testValue
  }

  def "test_ontology"(){
    when:
      k
    then:
      k.ontoHelper.reasoner.isConsistent() 
  }

  def "test_scorer"() {
    when:
      s = new Scorer(k.ontoHelper, k.data)
    then:
      s instanceof Scorer
  }

  def "test_scoring"() {
    when:
      def clusterId = "OMIM:604271"
      explanations = s.scoreClasses(clusterId)
      def items = [
        [ 
          term: "http://purl.obolibrary.org/obo/HP_0004322",
          nIc: Float.parseFloat("0.7463796")
        ],
        [
          term: "http://purl.obolibrary.org/obo/HP_0000002",
          nIc: Float.parseFloat("0.7054534")
        ]
      ]
      def nonEmptyTerm = "" 
      def parentTerm = "http://purl.obolibrary.org/obo/HP_0000002"
    then:
      explanations.size() == 205 // so it's all our 47 classes plus their transitive superclasses
    then:
      items.each { item ->
        def filledExp = explanations.find { it.iri == item.term }
        assert filledExp.nExclusion == 1
        assert filledExp.nInclusion == 0.6
        assert filledExp.nIc == item.nIc
        assert filledExp.ic == item.nIc
        assert filledExp.exclusion == 0
        assert filledExp.externalIncluded.size() == 0
        assert filledExp.internalIncluded == ["1","2","4"]
      }
  }

  def "test_explain"() {
    given:
      def clusterId = "OMIM:604271"
      def exClass = "http://purl.obolibrary.org/obo/HP_0001510" // growth delay
      def overallInc = 100
      def nIc = Float.parseFloat("0.7017193")
      def inclusion = 5
      def exclusion = 0
      def nInclusion = 1
      def nExclusion = 1
      def internalIncluded = ["0","1","2","3","4"]
    when:
      def res = StepDown.Run(k.coefficients, clusterId, explanations, k.data)
    then:
      res[0][0].iri == exClass
      res[0][0].internalIncluded == internalIncluded
      res[0][0].inclusion == inclusion
      res[0][0].exclusion == exclusion
      res[0][0].nInclusion == nInclusion
      res[0][0].nExclusion == nExclusion
      res[1] == overallInc
  }
}
