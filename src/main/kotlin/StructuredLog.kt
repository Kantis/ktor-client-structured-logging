package com.github.kantis.ktor.client.structured.logging
import io.ktor.http.Url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

@Serializable
internal data class StructuredRequestLog(
   val url: Url,
   val method: String,
   val headers: Map<String, String>? = null,
)

internal fun StructuredRequestLog.withBody(body: JsonPrimitive) =
   StructuredRequestLogWithBody(
      url = url,
      method = method,
      headers = headers,
      body = body,
   )

@Serializable
internal data class StructuredRequestLogWithBody(
   val url: Url,
   val method: String,
   val headers: Map<String, String>? = null,
   val body: JsonPrimitive,
)

@Serializable
internal data class StructuredRequestExceptionLog(
   val url: Url,
   val method: String,
   val cause: String,
   val message: String,
)

@Serializable
internal data class StructuredResponseLog(
   val url: Url,
   val method: String,
   val statusCode: Int,
   val headers: Map<String, String>? = null,
)

internal fun StructuredResponseLog.withBody(body: JsonPrimitive) =
   StructuredResponseLogWithBody(
      url = url,
      method = method,
      statusCode = statusCode,
      headers = headers,
      body = body,
   )

@Serializable
internal data class StructuredResponseLogWithBody(
   val url: Url,
   val method: String,
   val statusCode: Int,
   val headers: Map<String, String>? = null,
   val body: JsonPrimitive,
)
