package com.avast.cactus.grpc.client

import cats.Monad
import com.avast.cactus.grpc.GrpcMetadata
import io.grpc._

import scala.language.higherKinds

class ClientHeadersInterceptor[F[_]: Monad] private (userHeaders: () => Map[String, String]) extends ClientAsyncInterceptor[F] {
  override def apply(m: GrpcMetadata): F[Either[Status, GrpcMetadata]] = {
    import m._

    userHeaders().foreach {
      case (key, value) =>
        headers.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
    }

    Monad[F].pure(Right(m.copy(headers = headers)))
  }
}

object ClientHeadersInterceptor {
  def apply[F[_]: Monad](userHeaders: Map[String, String]): ClientHeadersInterceptor[F] = {
    new ClientHeadersInterceptor(() => userHeaders)
  }

  def apply[F[_]: Monad](userHeaders: () => Map[String, String]): ClientHeadersInterceptor[F] = {
    new ClientHeadersInterceptor(userHeaders)
  }
}
