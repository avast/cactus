package com.avast.cactus.grpc.client

import cats.data.EitherT
import cats.effect.Async
import cats.syntax.all._
import com.avast.cactus.grpc._
import io.grpc._

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.util.control.NonFatal

abstract class ClientInterceptorsWrapper[F[_]](interceptors: immutable.Seq[ClientAsyncInterceptor[F]]) {

  protected implicit def F: Async[F]
  protected implicit def ec: ExecutionContext

  def withInterceptors[Resp](clientCall: Context => F[ServerResponse[Resp]]): F[ServerResponse[Resp]] = {
    try {
      val resolvedInterceptors: F[Either[StatusException, GrpcMetadata]] = {
        val s = EitherT[F, StatusException, GrpcMetadata] {
          F.pure(Right(GrpcMetadata(Context.current(), new Metadata())))
        }

        interceptors
          .foldLeft(s) { case (prev, int) => prev.flatMap(r => EitherT(int(r)).leftMap(new StatusException(_))) }
          .value
      }

      resolvedInterceptors
        .flatMap[ServerResponse[Resp]] {
          case Right(GrpcMetadata(ctx, metadata)) =>

            clientCall {
              ctx.withValue(MetadataContextKey, metadata)
            }

//            F.unit.flatMap[ServerResponse[Resp]] { _ =>
//              ctx
//                .withValue(MetadataContextKey, metadata)
//                .call(() => { clientCall })
//            }

          case Left(statusException) => F.raiseError(statusException)
        }
        .recover {
          case e: StatusException => Left(ServerError(e.getStatus))
          case e: StatusRuntimeException => Left(ServerError(e.getStatus))
          case NonFatal(e) => Left(ServerError(Status.ABORTED.withCause(e).withDescription("Request could not be processed")))
        }
    } catch {
      case NonFatal(e) => F.raiseError(e)
    }
  }
}
