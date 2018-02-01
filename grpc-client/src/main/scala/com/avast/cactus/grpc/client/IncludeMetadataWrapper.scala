package com.avast.cactus.grpc.client

import java.util.concurrent.Callable

import com.avast.cactus.grpc.ServerResponse
import io.grpc.{Context, Metadata}

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

private[grpc] class IncludeMetadataWrapper(interceptors: immutable.Seq[ClientAsyncInterceptor])(implicit ec: ExecutionContext) {
  def withInterceptors[Req, Resp](clientCall: => Future[ServerResponse[Resp]]): Future[ServerResponse[Resp]] = {
    val resolvedInterceptors = interceptors.foldLeft(Future.successful((Context.current(), new Metadata()))) {
      case (prev, int) => prev.flatMap(int)
    }

    resolvedInterceptors.flatMap {
      case (ctx, metadata) =>
        ctx
          .withValue(MetadataContextKey, metadata)
          .call(new Callable[Future[ServerResponse[Resp]]] {
            override def call(): Future[ServerResponse[Resp]] = {
              clientCall
            }
          })
    }
  }
}
