package com.github.kantis.ktor.client.structured.logging.hooks

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.ClientHook
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.HttpSendPipeline
import io.ktor.util.pipeline.PipelineContext

internal object SendHook : ClientHook<suspend SendHook.Context.(response: HttpRequestBuilder) -> Unit> {
   class Context(private val context: PipelineContext<Any, HttpRequestBuilder>) {
      suspend fun proceedWith(content: Any) = context.proceedWith(content)

      suspend fun proceed() = context.proceed()
   }

   override fun install(
      client: HttpClient,
      handler: suspend Context.(request: HttpRequestBuilder) -> Unit,
   ) {
      client.sendPipeline.intercept(HttpSendPipeline.Phases.Monitoring) {
         handler(Context(this), context)
      }
   }
}
