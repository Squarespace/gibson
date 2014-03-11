/*
 * Copyright 2012-2014 Will Benedict, Felix Berger, Roger Kapsi, Doug
 * Jones and Squarespace Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */ 

package com.squarespace.gibson;

import org.slf4j.Marker;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * The {@link GibsonConverter} calculates the unique "signature" (hash) of 
 * log statement(s) that have an {@link Exception} associated with them.
 * 
 * The converter takes an optional argument for a property name.
 * 
 <code>
// Groovy Example
import com.squarespace.gibson.GibsonConverter

conversionRule("gibson", GibsonConverter)
appender("STDOUT", ConsoleAppender) {
  encoder(PatternLayoutEncoder) {
    pattern = "[%thread] %gibson{signature} - %msg%n"
  }
}
root(DEBUG, ["STDOUT"])
</code>
 * 
 * @see http://logback.qos.ch/manual/layouts.html#customConversionSpecifier
 */
public class GibsonConverter extends ClassicConverter {

  /**
   * The default value of the signature property. A non {@code null} value 
   * will yield to to an output such as {@code property=signature}.
   */
  private static final String DEFAULT_PROPERTY = null;
  
  /**
   * The default value for things that don't have a signature.
   */
  private static final String DEFAULT_VALUE = "";
  
  /**
   * @see #DEFAULT_PROPERTY
   */
  private volatile String property = DEFAULT_PROPERTY;
  
  /**
   * @see #DEFAULT_VALUE
   */
  private final String defaultValue = DEFAULT_VALUE;
  
  @Override
  public void start() {
    String option = getFirstOption();
    
    if (option != null && !option.isEmpty()) {
      this.property = option;
    }
    
    super.start();
  }

  @Override
  public void stop() {
    this.property = DEFAULT_PROPERTY;
    super.stop();
  }

  @Override
  public String convert(ILoggingEvent event) {
    String signature = signature(event);
    
    if (signature != null) {
      String property = this.property;
      if (property != DEFAULT_PROPERTY) {
        return property + "=" + signature;
      }
      
      return signature;
    }
    
    return defaultValue;
  }
  
  private static String signature(ILoggingEvent evt) {
    // Skip LoggingEvents that don't have a StackTrace
    IThrowableProxy proxy = evt.getThrowableProxy();
    if (proxy == null) {
      return null;
    }
    
    // Skip LoggingEvents that originate from Gibson itself.
    Marker marker = evt.getMarker();
    if (marker != null && marker.equals(Gibson.MARKER)) {
      return null;
    }
    
    return GibsonUtils.signature(evt);
  }
}
