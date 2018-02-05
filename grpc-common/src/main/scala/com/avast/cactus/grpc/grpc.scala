package com.avast.cactus

import io.grpc.Context.{Key => ContextKey}
import io.grpc.{Context, Metadata, Status}

package object grpc {

  private[grpc] val UserHeaderPrefix: String = "userheader-"

  type ServerResponse[Resp] = Either[ServerError, Resp]

  case class ServerError(status: Status, headers: Metadata = new Metadata())

  case class GrpcMetadata(context: Context, headers: Metadata)

  implicit class ContextOperations(val c: Context) extends AnyVal {
    def put[A](key: ContextKey[A], value: A): Unit = {
      c.withValue(key, value)
      ()
    }

    def get[A](key: ContextKey[A]): Option[A] = {
      Option(key.get(c))
    }
  }

}
