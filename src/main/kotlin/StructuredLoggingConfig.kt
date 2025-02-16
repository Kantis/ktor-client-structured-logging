package io.github.kantis.ktor.client.structured.logging

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.utils.io.KtorDsl
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

@KtorDsl
public class StructuredLoggingConfig {
   internal val sanitizedHeaders = mutableListOf<SanitizedHeader>()
   internal var filters = mutableListOf<(HttpRequestBuilder) -> Boolean>()
   internal var requestLogConfig: RequestLogConfig = RequestLogConfig(true, true, false, Level.INFO)
   internal var responseLogConfig: RequestLogConfig = RequestLogConfig(true, true, false, Level.INFO)
   internal var requestLogger = LoggerFactory.getLogger("io.github.kantis.ktor.client.structured.logging.RequestLogger")
   internal var responseLogger = LoggerFactory.getLogger("io.github.kantis.ktor.client.structured.logging.ResponseLogger")

   /**
    * Allows you to filter log messages for calls matching a [predicate].
    *
    * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig.filter)
    */
   public fun filter(predicate: (HttpRequestBuilder) -> Boolean) {
      filters.add(predicate)
   }

   /**
    * Allows you to sanitize sensitive headers to avoid their values appearing in the logs.
    * In the example below, Authorization header value will be replaced with '***' when logging:
    * ```kotlin
    * sanitizeHeader { header -> header == HttpHeaders.Authorization }
    * ```
    *
    * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.logging.LoggingConfig.sanitizeHeader)
    */
   public fun sanitizeHeader(
      placeholder: String = "***",
      predicate: (String) -> Boolean,
   ) {
      sanitizedHeaders.add(SanitizedHeader(placeholder, predicate))
   }

   public fun requestLogging(configurer: RequestLogConfigurer.() -> Unit) {
      requestLogConfig = RequestLogConfigurer().apply { configurer(this) }.let {
         RequestLogConfig(it.enabled, it.logHeaders, it.logBody, it.level, it.exceptionLevel)
      }
   }

   public fun responseLogging(configurer: RequestLogConfigurer.() -> Unit) {
      responseLogConfig = RequestLogConfigurer().apply { configurer(this) }.let {
         RequestLogConfig(it.enabled, it.logHeaders, it.logBody, it.level, it.exceptionLevel)
      }
   }
}

public class RequestLogConfigurer {
   public var enabled: Boolean = true
   public var logHeaders: Boolean = true
   public var logBody: Boolean = false
   public var level: Level = Level.INFO
   public var exceptionLevel: Level = Level.ERROR
}

internal data class RequestLogConfig(
   val enabled: Boolean,
   val headers: Boolean,
   val body: Boolean,
   val level: Level,
   val exceptionLevel: Level = Level.ERROR,
)
