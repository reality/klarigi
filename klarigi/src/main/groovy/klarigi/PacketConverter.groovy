import groovy.json.*

// Convert a PhenoPacket to Klarigi's input form
public class PacketConverter {
  public static def Load(File f) {
    new JsonSlurper().parse(f) 
  }

  // pDict is a dict describing one subject description
  public static def Convert(pDict) {
    [
      pDict['id'],
      pDict['phenotypicFeatures'].collect { it.type.id }.join(';'),
      pDict['diseases'].collect { it.term.id }.join(';')
    ] 
  }

  public static def Save(triples, String fName) {
    new File(fName).text = triples.collect {
      it.join('\t')
    }.join('\n')
  }
}
