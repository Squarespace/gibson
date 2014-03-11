# Gibson

[Gibson](http://en.wikipedia.org/wiki/Hackers_\(film\)) is a [Logback](http://logback.qos.ch) 
appender with instrumentation for [Splunk](http://www.splunk.com). It is a fork/rewrite of an 
earlier implementation that was a stand alone system with its own Dashboard.

```
// Groovy Example
import com.squarespace.gibson.GibsonConverter

conversionRule("gibson", GibsonConverter)
appender("STDOUT", ConsoleAppender) {
  encoder(PatternLayoutEncoder) {
    pattern = "[%thread] %gibson{signature} - %msg%n"
  }
}
root(DEBUG, ["STDOUT"])
```