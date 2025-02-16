package com.github.kantis.ktor.client.structured.logging

internal fun sanitizeHeaders(
   headers: Set<Map.Entry<String, List<String>>>,
   sanitizedHeaders: List<SanitizedHeader>,
): Map<String, String> {
   val sortedHeaders = headers.toList().sortedBy { it.key }

   return sortedHeaders.associate { (key, values) ->
      val placeholder: String? = sanitizedHeaders.firstOrNull { it.predicate(key) }?.placeholder
      key to (placeholder ?: values.joinToString())
   }
}

internal class SanitizedHeader(
   val placeholder: String,
   val predicate: (String) -> Boolean,
)
