package io.github.kantis.ktor.client.structured.logging

import io.github.kantis.ktor.client.structured.logging.utils.RecordingLoggerExtension
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.inspectors.forOne
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlin.time.Duration.Companion.milliseconds

class SanitizeHeadersTest : FunSpec(
   {
      val wiremock = WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }
      val requestLogger = extension(RecordingLoggerExtension())
      val responseLogger = extension(RecordingLoggerExtension())

      listener(WireMockListener(wiremock, ListenerMode.PER_SPEC))

      val client = HttpClient(CIO) {
         install(StructuredLogging) {
            this.responseLogger = responseLogger
            this.requestLogger = requestLogger
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
         }
      }

      val hostname = "http://localhost:${wiremock.port()}"

      test("Sanitizes matching request headers") {
         wiremock.stubFor(get(urlEqualTo("/foo")).willReturn(ok()))

         client.get("$hostname/foo") {
            header(HttpHeaders.Authorization, "Bearer secret")
            header("Foo", "Bar")
         }

         requestLogger.infoLogs.forOne {
            it shouldEqualSpecifiedJson
               """
               {
                  "url": "$hostname/foo",
                  "method": "GET",
                  "headers": {
                     "Authorization": "***",
                     "Foo": "Bar"
                  }
               }
               """
         }
      }

      test("Sanitizes matching response headers") {
         wiremock.stubFor(get(urlEqualTo("/foo")).willReturn(ok().withHeader("Authorization", "Bearer secret")))

         client.get("$hostname/foo")

         eventually(100.milliseconds) {
            responseLogger.infoLogs.forOne {
               it shouldEqualSpecifiedJson
                  """
                  {
                     "url": "$hostname/foo",
                     "method": "GET",
                     "statusCode": 200,
                     "headers": {
                        "Authorization": "***"
                     }
                  }
                  """
            }
         }
      }
   },
)
