package com.github.kantis.ktor.client.structured.logging

import com.github.kantis.ktor.client.structured.logging.utils.RecordingLoggerExtension
import com.github.kantis.ktor.client.structured.logging.utils.returnsJson
import com.github.kantis.ktor.client.structured.logging.utils.returnsPlainText
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.http.Fault
import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.json.shouldEqualSpecifiedJson
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.inspectors.forOne
import io.kotest.inspectors.forSingle
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlin.time.Duration.Companion.milliseconds

class StructuredLoggingTest : FunSpec(
   {
      val wiremock = WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }
      val requestLogger = extension(RecordingLoggerExtension())
      val responseLogger = extension(RecordingLoggerExtension())

      listener(WireMockListener(wiremock, ListenerMode.PER_SPEC))

      val client = HttpClient(CIO) {
         install(StructuredLogging) {
            this.responseLogger = responseLogger
            this.requestLogger = requestLogger

            requestLogging {
               logBody = true
            }

            responseLogging {
               logBody = true
            }
         }
      }

      val hostname = "http://localhost:${wiremock.port()}"

      test("Logs basic request and response") {
         wiremock.stubFor(get(urlEqualTo("/foo")).willReturn(ok()))

         client.get("$hostname/foo")

         withClue("Request should be logged") {
            requestLogger.infoLogs.forOne {
               it shouldEqualSpecifiedJson
                  """
                  {
                     "url": "$hostname/foo",
                     "method": "GET"
                  }
                  """.trimIndent()
            }
         }

         withClue("Response should be logged with status code") {
            responseLogger.infoLogs.forSingle {
               it shouldEqualSpecifiedJson
                  """
                  {
                     "url": "$hostname/foo",
                     "method": "GET",
                     "statusCode": 200
                  }
                  """.trimIndent()
            }
         }
      }

      test("Logs plain text response as quoted string") {
         wiremock.stubFor(get("/foo2") returnsPlainText "Hello, World!")

         client.get("$hostname/foo2")

         eventually(100.milliseconds) {
            responseLogger.infoLogs.forSingle {
               it shouldEqualSpecifiedJson
                  """
                  {
                     "body": "Hello, World!"
                  }
                  """.trimIndent()
            }
         }
      }

      test("Logs response JSON as JSON") {
         wiremock.stubFor(get("/foo2") returnsJson """{ "foo": "bar" }""")

         client.get("$hostname/foo2")

         eventually(50.milliseconds) {
            responseLogger.infoLogs.forSingle {
               it shouldEqualSpecifiedJson
                  """
                  {
                     "body": {
                        "foo": "bar"
                     }
                  }
                  """.trimIndent()
            }
         }
      }

      test("Logs 500 responses") {
         wiremock.stubFor(get("/foo2").willReturn(WireMock.serverError()))

         client.get("$hostname/foo2")

         eventually(100.milliseconds) {
            responseLogger.infoLogs.forSingle {
               it shouldEqualSpecifiedJson
                  """
                  {
                    "url": "$hostname/foo2",
                    "method": "GET",
                    "statusCode": 500
                  }
                  """.trimIndent()
            }
         }
      }

      test("Logs timeouts") {
         wiremock.stubFor(get("/foo2").willReturn(WireMock.aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)))

         runCatching {
            client.get("$hostname/foo2")
         }

         eventually(50.milliseconds) {
            requestLogger.errorLogs.forSingle {
               it shouldEqualJson
                  """
                  {
                      "url": "$hostname/foo2",
                      "method": "GET",
                      "cause": "io.ktor.utils.io.ClosedByteChannelException",
                      "message": "Connection reset"
                  }
                  """.trimIndent()
            }
         }
      }

      test("Logs request headers") {
         wiremock.stubFor(get("/foo2").willReturn(ok()))

         client.get("$hostname/foo2") {
            header("Foo", "Bar")
         }

         requestLogger.infoLogs.forSingle {
            it shouldEqualSpecifiedJson
               """
               {
                  "url": "$hostname/foo2",
                  "method": "GET",
                  "headers": {
                     "Foo": "Bar"
                  }
               }
               """.trimIndent()
         }
      }

      test("Logs response headers") {
         wiremock.stubFor(get("/foo2").willReturn(ok().withHeader("Foo", "Bar")))

         client.get("$hostname/foo2")

         eventually(50.milliseconds) {
            responseLogger.infoLogs.forSingle {
               it shouldEqualSpecifiedJson
                  """
                  {
                     "url": "$hostname/foo2",
                     "method": "GET",
                     "statusCode": 200,
                     "headers": {
                        "Foo": "Bar"
                     }
                  }
                  """.trimIndent()
            }
         }
      }
   },
)
