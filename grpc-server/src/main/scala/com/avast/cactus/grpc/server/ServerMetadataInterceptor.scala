package com.avast.cactus.grpc.server

import io.grpc._
import com.avast.cactus.grpc._
import scala.collection.JavaConverters._

private[grpc] object ServerMetadataInterceptor extends ServerInterceptor {
  override def interceptCall[ReqT, RespT](serverCall: ServerCall[ReqT, RespT],
                                          headers: Metadata,
                                          next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {

    val headersValues = headers.keys.asScala
        .collect{
          case k if k.startsWith(UserHeaderPrefix) =>
            val Array(key, value) = headers.get(Metadata.Key.of(k, Metadata.ASCII_STRING_MARSHALLER)).split("-",2)

            key -> value
        }


//      .filterNot(_.endsWith(Metadata.BINARY_HEADER_SUFFIX)).map { plainKey =>
//      val Array(key, value) = headers.get(Metadata.Key.of(plainKey, Metadata.ASCII_STRING_MARSHALLER)).split("-",2)
//
//      key -> value
//    }

    val context = headersValues.foldLeft(Context.current()) {
      case (ctx, (key, value)) => ctx.withValue(ContextKeys.get[String](key), value)
    }.withValue(MetadataContextKey, headers)

    Contexts.interceptCall(context, serverCall, headers, next)
  }
}
