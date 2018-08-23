package com.avast.cactus.grpc.server

import java.util.concurrent.Callable

import cats.MonadError
import cats.data.EitherT
import cats.syntax.all._
import com.avast.cactus.grpc._
import io.grpc._

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.util.control.NonFatal
class ServerInterceptorsWrapper[F[_]](interceptors: immutable.Seq[ServerAsyncInterceptor[F]])(implicit ec: ExecutionContext,
                                                                                              F: MonadError[F, Throwable]) {
  def withInterceptors[Resp](serverCall: => F[Either[StatusException, Resp]]): F[Either[StatusException, Resp]] = {

    val context = Context.current()

    // load headers previously stored in context
    Option(MetadataContextKey.get(context)) match {
      case Some(headers) =>
        try {
          val resolvedInterceptors = {
            val s = EitherT[F, StatusException, GrpcMetadata] {
              F.pure(Right(GrpcMetadata(context, headers)))
            }

            interceptors
              .foldLeft(s) { case (prev, int) => prev.flatMap(r => EitherT(int(r)).leftMap(new StatusException(_))) }
              .value
          }

          resolvedInterceptors
            .flatMap {
              case Right(GrpcMetadata(ctx, _)) =>
                ctx
                  .call(new Callable[F[Either[StatusException, Resp]]] {
                    override def call(): F[Either[StatusException, Resp]] = {
                      serverCall
                    }
                  })

              case Left(statusException) => F.raiseError[Either[StatusException, Resp]](statusException)
            }
            .recover(ServerCommonMethods.recoverWithStatus)
        } catch {
          case NonFatal(e) =>
            F.pure {
              Left(new StatusException(Status.INTERNAL.withCause(e).withDescription("Request could not been processed")))
            }
        }

      case None =>
        F.pure {
          Left(new StatusException(Status.INTERNAL.withDescription("Could not extract metadata from the context, possible bug?")))
        }
    }

  }
}
