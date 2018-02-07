package com.avast.cactus.grpc.server

import com.avast.cactus.grpc._
import io.grpc._

import scala.collection.JavaConverters._

object ServerMetadataInterceptor extends ServerInterceptor {
  override def interceptCall[ReqT, RespT](serverCall: ServerCall[ReqT, RespT],
                                          headers: Metadata,
                                          next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {

    val headersValues = headers.keys.asScala.filterNot(_.endsWith(Metadata.BINARY_HEADER_SUFFIX))
      .map {
        k =>
          val value = headers.get(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER))

          k -> value
      }

    val context = headersValues.foldLeft(Context.current()) {
      case (ctx, (key, value)) => ctx.withValue(ContextKeys.get[String](key), value)
    }.withValue(MetadataContextKey, headers)

    Contexts.interceptCall(context, serverCall, headers, next)
  }
}
