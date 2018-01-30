package com.avast.cactus

import io.grpc.Context.{Key => ContextKey}
import io.grpc.{Context, Metadata, Status}

package object grpc {

  case class ServerError(status: Status, headers: Metadata = new Metadata())

  type ServerResponse[Resp] = Either[ServerError, Resp]

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
