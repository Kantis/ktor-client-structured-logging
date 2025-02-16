package io.github.kantis.ktor.client.structured.logging.utils

import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import org.intellij.lang.annotations.Language

infix fun MappingBuilder.returnsJson(
   @Language("json") response: String,
): MappingBuilder = this.willReturn(WireMock.okJson(response))

infix fun MappingBuilder.returnsPlainText(response: String): MappingBuilder =
   this.willReturn(WireMock.ok(response).withHeader("Content-Type", "text/plain"))
