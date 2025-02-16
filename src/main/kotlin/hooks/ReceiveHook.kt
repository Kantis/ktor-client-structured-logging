package io.github.kantis.ktor.client.structured.logging.hooks

import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.api.ClientHook
import io.ktor.client.statement.HttpResponseContainer
import io.ktor.client.statement.HttpResponsePipeline
import io.ktor.util.pipeline.PipelineContext

internal object ReceiveHook : ClientHook<suspend ReceiveHook.Context.(call: HttpClientCall) -> Unit> {
   class Context(private val context: PipelineContext<HttpResponseContainer, HttpClientCall>) {
      suspend fun proceed() = context.proceed()
   }

   override fun install(
      client: HttpClient,
      handler: suspend Context.(call: HttpClientCall) -> Unit,
   ) {
      client.responsePipeline.intercept(HttpResponsePipeline.Phases.Receive) {
         handler(Context(this), context)
      }
   }
}
