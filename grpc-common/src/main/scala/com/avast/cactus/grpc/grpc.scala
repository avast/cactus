package com.avast.cactus

import io.grpc.{Context, Metadata, Status}

package object grpc {

  private[grpc] val MetadataContextKey = ContextKeys.get[Metadata]("headers")

  type ServerResponse[Resp] = Either[ServerError, Resp]

  case class ServerError(status: Status, headers: Metadata = new Metadata())

  case class GrpcMetadata(context: Context, headers: Metadata) {
    def withContext(f: Context => Context): GrpcMetadata = copy(context = f(context))

    def withHeaders(f: Metadata => Metadata): GrpcMetadata = copy(headers = f(headers))
  }

}
