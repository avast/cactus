package com.avast.cactus.grpc.server

import java.util.concurrent.Callable

import cats.data.EitherT
import cats.implicits._
import com.avast.cactus.grpc._
import io.grpc.{Context, Metadata, Status, StatusException}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ServerInterceptorsWrapper(interceptors: immutable.Seq[ServerAsyncInterceptor])(implicit ec: ExecutionContext) {
  def withInterceptors[Resp](serverCall: => Future[Either[StatusException, Resp]]): Future[Either[StatusException, Resp]] = {

    val context = Context.current()

    // load headers previously stored in context
    Option(MetadataContextKey.get(context)) match {
      case Some(headers) =>
        try {
          val resolvedInterceptors = {
            val s = EitherT[Future, StatusException, GrpcMetadata] {
              Future.successful(Right(GrpcMetadata(context, headers)))
            }

            interceptors
              .foldLeft(s) { case (prev, int) => prev.flatMap(r => EitherT(int(r)).leftMap(new StatusException(_))) }
              .value
          }

          resolvedInterceptors
            .flatMap {
              case Right(GrpcMetadata(ctx, _)) =>
                ctx
                  .call(new Callable[Future[Either[StatusException, Resp]]] {
                    override def call(): Future[Either[StatusException, Resp]] = {
                      serverCall
                    }
                  })

              case Left(statusException) => Future.failed(statusException)
            }
            .recover(ServerCommonMethods.recoverWithStatus)
        } catch {
          case NonFatal(e) =>
            Future.successful {
              Left(new StatusException(Status.INTERNAL.withCause(e).withDescription("Request could not been processed")))
            }
        }

      case None =>
        Future.successful {
          Left(new StatusException(Status.INTERNAL.withDescription("Could not extract metadata from the context, possible bug?")))
        }
    }

  }
}
