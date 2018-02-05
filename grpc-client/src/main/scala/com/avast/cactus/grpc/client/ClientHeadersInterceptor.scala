package com.avast.cactus.grpc.client

import com.avast.cactus.grpc.UserHeaderPrefix
import io.grpc._

import scala.concurrent.Future

class ClientHeadersInterceptor private (userHeaders: () => Map[String, String]) extends ClientAsyncInterceptor {
  override def apply(t: (Context, Metadata)): Future[(Context, Metadata)] = {
    val (_, headers) = t

    userHeaders().foreach {
      case (key, value) =>
        headers.put(Metadata.Key.of(s"$UserHeaderPrefix$key", Metadata.ASCII_STRING_MARSHALLER), s"$key-$value")
    }

    Future.successful(t.copy(_2 = headers))
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
