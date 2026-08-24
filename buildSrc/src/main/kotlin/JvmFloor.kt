// The oldest Java a consumer may be on. One constant rather than a literal per module: it was two
// before (the root build and the processor module), and a number written twice is a number that gets
// half-changed.
const val JVM_FLOOR = 17

// The Java the screenshot suite runs ON, which is not the same question: viddik is compiled for 21,
// so a 17 launcher cannot load it. Nothing published depends on this number.
const val SCREENSHOT_RUNTIME = 21
