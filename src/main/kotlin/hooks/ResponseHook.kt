package com.github.kantis.ktor.client.structured.logging.hooks

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.ClientHook
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.util.pipeline.PipelineContext

internal object ResponseHook : ClientHook<suspend ResponseHook.Context.(response: HttpResponse) -> Unit> {
   class Context(private val context: PipelineContext<HttpResponse, Unit>) {
      suspend fun proceed() = context.proceed()
   }

   override fun install(
      client: HttpClient,
      handler: suspend Context.(response: HttpResponse) -> Unit,
   ) {
      client.receivePipeline.intercept(HttpReceivePipeline.Phases.State) {
         handler(Context(this), subject)
      }
   }
}
