public class Coefficients {
  static def DEFAULTS = [
    TOP_IC: 0.7,
    BOT_IC: 0.4,

    TOP_OVERALL_INCLUSION: 1,
    TOP_R_SCORE: 0.7,
    BOT_R_SCORE: 0.1,

    MAX_INCLUSION: 1,
    MAX_EXCLUSION: 1,

    MIN_INCLUSION: 0,
    MIN_EXCLUSION: 0.05,
    MIN_IC: 0.4,
    MIN_R_SCORE: 0.1,

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
