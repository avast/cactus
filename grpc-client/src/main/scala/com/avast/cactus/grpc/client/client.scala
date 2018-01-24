package com.avast.cactus.grpc

import io.grpc.Context.{Key => ContextKey}
import io.grpc.Metadata.{Key => MetadataKey}
import io.grpc.{Context, Metadata, Status, StatusRuntimeException}

import scala.concurrent.Future

package object client {

  type GrpcRequestMetadata = (Context, Metadata)
  type ClientAsyncInterceptor = GrpcRequestMetadata => Future[GrpcRequestMetadata]

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
