public class Coefficients {
  static def DEFAULTS = [
    TOP_IC: 0.8,
    BOT_IC: 0.4,

    TOP_TOTAL_INCLUSION: 0.95,
    TOP_POWER: 0.8,
    BOT_POWER: 0.05,

    MIN_INCLUSION: 0,
    MIN_EXCLUSION: 0,
    MIN_IC: 0.5,

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
