package klarigi

import spock.lang.Specification
import spock.lang.Shared

class KlarigiTest extends Specification {
	@Shared k 

  def "load_klarigi"() {
    given:
      def o = [
				'data': getClass().getResource('/patient_omim_map.txt').toURI(),
				'ontology': getClass().getResource('/hp.owl').toURI()
			]
    when:
			k = new Klarigi(o)
    then:
      k instanceof Klarigi
  }

	def "test_associations"() {
    when:
      def p3 = ["HP:0001510", "HP:0031960"]
    then:
      k.associations.size() == 20 
    then:
      k.associations["3"] == p3
  }

	def "test_groupings"() {
    when: 
      def omims = ["OMIM:604271","OMIM:172870","OMIM:615121","OMIM:275450"]
    then: 
      k.groupings.size() == 4
    then:
      omims.findAll { k.groupings.containsKey(it) }.size() == omims.size()
	}
}
