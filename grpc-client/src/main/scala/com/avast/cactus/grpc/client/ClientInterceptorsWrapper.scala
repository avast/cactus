package com.avast.cactus.grpc.client

import java.util.concurrent.Callable

import cats.data.EitherT
import cats.implicits._
import com.avast.cactus.grpc._
import io.grpc._

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

abstract class ClientInterceptorsWrapper(interceptors: immutable.Seq[ClientAsyncInterceptor])(implicit ec: ExecutionContext) {
  def withInterceptors[Resp](clientCall: => Future[ServerResponse[Resp]]): Future[ServerResponse[Resp]] = {
    try {
      val resolvedInterceptors = {
        val s = EitherT[Future, StatusException, GrpcMetadata] {
          Future.successful(Right(GrpcMetadata(Context.current(), new Metadata())))
        }

        interceptors
          .foldLeft(s) { case (prev, int) => prev.flatMap(r => EitherT(int(r)).leftMap(new StatusException(_))) }
          .value
      }

      resolvedInterceptors
        .flatMap {
          case Right(GrpcMetadata(ctx, metadata)) =>
            ctx
              .withValue(MetadataContextKey, metadata)
              .call(new Callable[Future[ServerResponse[Resp]]] {
                override def call(): Future[ServerResponse[Resp]] = {
                  clientCall
                }
              })

          case Left(statusException) => Future.failed(statusException)
        }
        .recover {
          case e: StatusException => Left(ServerError(e.getStatus))
          case e: StatusRuntimeException => Left(ServerError(e.getStatus))
          case NonFatal(e) => Left(ServerError(Status.ABORTED.withCause(e).withDescription("Request could not been processed")))
        }
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }
}
