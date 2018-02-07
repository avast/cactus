package com.avast.cactus.grpc.client

import com.avast.cactus.grpc.GrpcMetadata
import io.grpc._

import scala.concurrent.Future

class ClientHeadersInterceptor private (userHeaders: () => Map[String, String]) extends ClientAsyncInterceptor {
  override def apply(m: GrpcMetadata): Future[Either[Status, GrpcMetadata]] = {
    import m._

    userHeaders().foreach {
      case (key, value) =>
        headers.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
    }

    Future.successful(Right(m.copy(headers = headers)))
  }
}

object ClientHeadersInterceptor {
  def apply(userHeaders: Map[String, String]): ClientHeadersInterceptor = {
    new ClientHeadersInterceptor(() => userHeaders)
  }

  def apply(userHeaders: () => Map[String, String]): ClientHeadersInterceptor = {
    new ClientHeadersInterceptor(userHeaders)
  }
}
