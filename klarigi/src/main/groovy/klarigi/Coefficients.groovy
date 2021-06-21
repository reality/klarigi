public class Coefficients {
  static def DEFAULTS = [
    MAX_IC: 0.8,
    MIN_IC: 0.4,
    MAX_INCLUSION: 0.95,
    MIN_INCLUSION: 0.30,
    MAX_EXCLUSION: 0.95,
    MIN_EXCLUSION: 0.30,
    MAX_TOTAL_INCLUSION: 0.95,
    MAX_POWER: 0.8,
    MIN_POWER: 0.1,
    STEP: 0.05,
  ]

  static def Generate(o) {
    def c = [:]

    DEFAULTS.each { k, v ->
      def optKey = k.replaceAll('_', '-').toLowerCase() // heh
      if(o[optKey]) {
        c[k] = new BigDecimal(o[optKey])
      } else {
        c[k] = v
      }
    }

    c
  }
}
