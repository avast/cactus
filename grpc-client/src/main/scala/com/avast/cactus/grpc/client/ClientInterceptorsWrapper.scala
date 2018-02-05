package com.avast.cactus.grpc.client

import java.util.concurrent.Callable

import com.avast.cactus.grpc.{GrpcMetadata, ServerError, ServerResponse}
import io.grpc.{Context, Metadata, Status}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

abstract class ClientInterceptorsWrapper(interceptors: immutable.Seq[ClientAsyncInterceptor])(implicit ec: ExecutionContext) {
  def withInterceptors[Req, Resp](clientCall: => Future[ServerResponse[Resp]]): Future[ServerResponse[Resp]] = {
    try {
      val resolvedInterceptors = interceptors.foldLeft(Future.successful(GrpcMetadata(Context.current(), new Metadata()))) {
        case (prev, int) => prev.flatMap(int)
      }

      resolvedInterceptors
        .flatMap {
          case GrpcMetadata(ctx, metadata) =>
            ctx
              .withValue(MetadataContextKey, metadata)
              .call(new Callable[Future[ServerResponse[Resp]]] {
                override def call(): Future[ServerResponse[Resp]] = {
                  clientCall
                }
              })
        }
        .recover {
          case NonFatal(e) => Left(ServerError(Status.ABORTED.withCause(e).withDescription("Request could not been processed")))
        }
    } catch {
      case NonFatal(e) => Future.failed(e)
    }
  }
}
