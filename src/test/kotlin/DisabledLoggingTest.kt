package com.github.kantis.ktor.client.structured.logging

import com.github.kantis.ktor.client.structured.logging.utils.RecordingLoggerExtension
import com.github.kantis.ktor.client.structured.logging.utils.returnsJson
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.wiremock.ListenerMode
import io.kotest.extensions.wiremock.WireMockListener
import io.kotest.matchers.collections.shouldBeEmpty
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get

class DisabledLoggingTest : FunSpec(
   {
      val wiremock = WireMockServer(wireMockConfig().dynamicPort()).also { it.start() }
      val requestLogger = extension(RecordingLoggerExtension())
      val responseLogger = extension(RecordingLoggerExtension())

      listener(WireMockListener(wiremock, ListenerMode.PER_SPEC))

      val client = HttpClient(CIO) {
         install(StructuredLogging) {
            this.responseLogger = responseLogger
            this.requestLogger = requestLogger
            requestLogging { enabled = false }
            responseLogging { enabled = false }
         }
      }

      val hostname = "http://localhost:${wiremock.port()}"

      test("Logs nothing when loggers are disabled") {
         wiremock.stubFor(get(urlEqualTo("/foo")) returnsJson "{}")

         client.get("$hostname/foo")

         requestLogger.infoLogs.shouldBeEmpty()
         requestLogger.errorLogs.shouldBeEmpty()

         responseLogger.infoLogs.shouldBeEmpty()
         responseLogger.errorLogs.shouldBeEmpty()
      }
   },
)
