package io.github.kantis.ktor.client.structured.logging.utils

import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.slf4j.Logger

class RecordingLoggerExtension(
   private val mockLogger: Logger = mockk<Logger>(relaxed = true),
) : TestCaseExtension, Logger by mockLogger {
   val infoLogs: MutableList<String> = mutableListOf()
   val errorLogs: MutableList<String> = mutableListOf()

   override suspend fun intercept(
      testCase: TestCase,
      execute: suspend (TestCase) -> TestResult,
   ): TestResult {
      clearMocks(mockLogger)
      infoLogs.clear()
      errorLogs.clear()
      every { mockLogger.info(capture(infoLogs)) } just Runs
      every { mockLogger.error(capture(errorLogs)) } just Runs

      return execute(testCase)
   }
}
