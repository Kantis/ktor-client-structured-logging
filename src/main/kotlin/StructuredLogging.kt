package com.github.kantis.ktor.client.structured.logging

import com.github.kantis.ktor.client.structured.logging.hooks.ReceiveHook
import com.github.kantis.ktor.client.structured.logging.hooks.ResponseHook
import com.github.kantis.ktor.client.structured.logging.hooks.SendHook
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.ResponseHandler
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.util.copyToBoth
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral
import org.slf4j.Logger
import org.slf4j.event.Level

private val DisableLogging = AttributeKey<Unit>("DisableLogging")

private val serializer = Json {
   prettyPrint = false
   encodeDefaults = false
}

@OptIn(DelicateCoroutinesApi::class, ExperimentalSerializationApi::class, InternalAPI::class)
public val StructuredLogging: ClientPlugin<StructuredLoggingConfig> =
   createClientPlugin("StructuredLogging", ::StructuredLoggingConfig) {
      val requestLogger: Logger = pluginConfig.requestLogger
      val responseLogger: Logger = pluginConfig.responseLogger
      val filters: List<(HttpRequestBuilder) -> Boolean> = pluginConfig.filters
      val sanitizedHeaders = pluginConfig.sanitizedHeaders
      val requestLogConfig = pluginConfig.requestLogConfig
      val responseLogConfig = pluginConfig.responseLogConfig

      fun shouldBeLogged(request: HttpRequestBuilder): Boolean = filters.isEmpty() || filters.any { it(request) }

      suspend fun logRequest(request: HttpRequestBuilder): OutgoingContent? {
         val content = request.body as OutgoingContent

         val logEventBuilder =
            requestLogger.atLevel(Level.INFO).apply {
               addKeyValue("url", request.url.toString())
               addKeyValue("method", request.method.value)
               if (requestLogConfig.headers) {
                  addKeyValue("headers", serializer.encodeToString(sanitizeHeaders(request.headers.entries(), sanitizedHeaders)))
               }
            }

         return if (requestLogConfig.body) {
            val charset = content.contentType?.charset() ?: Charsets.UTF_8

            val channel = ByteChannel()
            GlobalScope.launch(Dispatchers.Default + MDCContext()) {
               try {
                  val text = channel.tryReadText(charset)
                  text?.let {
                     logEventBuilder.addKeyValue("body", text).log()
                  } ?: logEventBuilder.log()
               } catch (cause: Throwable) {
                  logEventBuilder.log()
               }
            }

            content.observe(channel)
         } else {
            logEventBuilder.log()
            null
         }
      }

      fun logRequestException(
         request: HttpRequestBuilder,
         cause: Throwable,
      ) {
         requestLogger.error(
            serializer.encodeToString(
               StructuredRequestExceptionLog(
                  Url(request.url),
                  request.method.value,
                  cause::class.qualifiedName ?: "Unknown cause",
                  cause.message ?: "No message",
               ),
            ),
         )
      }

      on(SendHook) { request ->
         if (!shouldBeLogged(request)) {
            request.attributes.put(DisableLogging, Unit)
            return@on
         }

         if (requestLogConfig.enabled) {
            val loggedRequest = try {
               logRequest(request)
            } catch (_: Throwable) {
               null
            }

            try {
               proceedWith(loggedRequest ?: request.body)
            } catch (cause: Throwable) {
               logRequestException(request, cause)
               throw cause
            } finally {
            }
         }
      }

      on(ResponseHook) { response ->
         if (responseLogConfig.enabled &&
            !responseLogConfig.body &&
            !response.call.attributes.contains(DisableLogging)
         ) {
            try {
               responseLogger.info(
                  serializer.encodeToString(
                     StructuredResponseLog(
                        response.call.request.url,
                        response.call.request.method.value,
                        response.call.response.status.value,
                        sanitizeHeaders(response.call.response.headers.entries(), sanitizedHeaders),
                     ),
                  ),
               )
//         logResponseHeader(header, response.call.response, level, sanitizedHeaders)
               proceed()
            } catch (cause: Throwable) {
//         logResponseException(header, response.call.request, cause)
//            failed = true
               throw cause
            } finally {
//            callLogger.logResponseHeader(header.toString())
//            if (failed || !requestLogConfig.body) callLogger.closeResponseLog()
            }
         }
      }

      // Setup logging for failed requests
      on(ReceiveHook) { call ->
         if (!requestLogConfig.enabled || call.attributes.contains(DisableLogging)) {
            return@on
         }

         try {
            proceed()
         } catch (cause: Throwable) {
            // TODO: Maybe we need to log something about the exception here?
            throw cause
         }
      }

      if (!responseLogConfig.enabled || !responseLogConfig.body) return@createClientPlugin

      val observer: ResponseHandler = {
         try {
            with(it.call) {
               if (!attributes.contains(DisableLogging)) {
                  val charset = it.contentType()?.charset() ?: Charsets.UTF_8
                  val responseLog = StructuredResponseLog(
                     request.url,
                     request.method.value,
                     response.status.value,
                     sanitizeHeaders(response.headers.entries(), sanitizedHeaders),
                  )

                  val body = it.rawContent.tryReadText(charset)
                  if (body.isNullOrBlank()) {
                     responseLogger.info(serializer.encodeToString(responseLog))
                  } else if (response.contentType() == ContentType.Application.Json) {
                     responseLogger.info(serializer.encodeToString(responseLog.withBody(JsonUnquotedLiteral(body))))
                  } else {
                     responseLogger.info(serializer.encodeToString(responseLog.withBody(JsonPrimitive(body))))
                  }
               }
            }
         } catch (e: Throwable) {
            e.printStackTrace()
         }
      }

      ResponseObserver.install(ResponseObserver.prepare { onResponse(observer) }, client)
   }

internal suspend fun OutgoingContent.observe(log: ByteWriteChannel): OutgoingContent =
   when (this) {
      is OutgoingContent.ByteArrayContent -> {
         log.writeFully(bytes())
         log.flushAndClose()
         this
      }

      is OutgoingContent.ReadChannelContent -> {
         val responseChannel = ByteChannel()
         val content = readFrom()

         content.copyToBoth(log, responseChannel)
         LoggedContent(this, responseChannel)
      }

      is OutgoingContent.WriteChannelContent -> {
         val responseChannel = ByteChannel()
         val content = toReadChannel()
         content.copyToBoth(log, responseChannel)
         LoggedContent(this, responseChannel)
      }

      is OutgoingContent.ContentWrapper -> copy(delegate().observe(log))
      is OutgoingContent.NoContent, is OutgoingContent.ProtocolUpgrade -> {
         log.flushAndClose()
         this
      }
   }

@OptIn(DelicateCoroutinesApi::class)
private fun OutgoingContent.WriteChannelContent.toReadChannel(): ByteReadChannel =
   GlobalScope.writer(Dispatchers.Default) { writeTo(channel) }.channel

internal class LoggedContent(
   private val originalContent: OutgoingContent,
   private val channel: ByteReadChannel,
) : OutgoingContent.ReadChannelContent() {
   override val contentType: ContentType? = originalContent.contentType
   override val contentLength: Long? = originalContent.contentLength
   override val status: HttpStatusCode? = originalContent.status
   override val headers: Headers = originalContent.headers

   override fun <T : Any> getProperty(key: AttributeKey<T>): T? = originalContent.getProperty(key)

   override fun <T : Any> setProperty(
      key: AttributeKey<T>,
      value: T?,
   ) = originalContent.setProperty(key, value)

   override fun readFrom(): ByteReadChannel = channel
}

internal suspend inline fun ByteReadChannel.tryReadText(charset: Charset): String? =
   try {
      readRemaining().readText(charset = charset)
   } catch (cause: Throwable) {
      null
   }
