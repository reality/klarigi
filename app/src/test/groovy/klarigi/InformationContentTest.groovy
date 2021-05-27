package klarigi

import spock.lang.Specification
import spock.lang.Shared

class InformationContentTest extends Specification {
  @Shared ic

  def "load_information_content"() {
    given:
      def oPath = getClass().getResource('/hp.owl').toURI().toString()
      oPath = oPath.replace('file:', '') // sigh
    when:
			ic = new InformationContent(oPath)
    then:
      ic instanceof InformationContent
  }

  def "test_information_content"() {
    given:
      def testClasses = [
        "http://purl.obolibrary.org/obo/HP_0001510": 0.5388977936177166,
        "http://purl.obolibrary.org/obo/HP_0007903": 0.8724740256012445
      ]
    when:
      def icValues = ic.getInformationContent(testClasses.collect { it.getKey() })
    then:
      icValues.each { k, v ->
        assert v == testClasses[k]
      }
  }
}
