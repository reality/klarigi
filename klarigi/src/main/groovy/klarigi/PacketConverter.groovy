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
      pDict['phenotypicFeatures'].collect { it.type.id },
      pDict['diseases'].collect { term.id }
    ] 
  }
}
