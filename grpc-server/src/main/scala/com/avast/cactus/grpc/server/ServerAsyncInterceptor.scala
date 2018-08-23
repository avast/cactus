package com.avast.cactus.grpc.server

import com.avast.cactus.grpc.GrpcMetadata
import io.grpc.Status

import scala.language.higherKinds

trait ServerAsyncInterceptor[F[_]] extends (GrpcMetadata => F[Either[Status, GrpcMetadata]])
