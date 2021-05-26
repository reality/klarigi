public class Coefficients {
  static def Generate(o) {
    def c = [
      MAX_IC: 0.8,
      MIN_IC: 0.4,
      MAX_INCLUSION: 0.95,
      MIN_INCLUSION: 0.30,
      MAX_EXCLUSION: 0.95,
      MIN_EXCLUSION: 0.30,
      MAX_TOTAL_INCLUSION: 0.95,
      STEP: 0.05,
    ]
    c.each { k, v ->
      if(o.containsKey(k)) {
        c[k] = o[k]
      }
    }

    c
  }
}
