package com.avast.cactus.grpc.client

import com.avast.cactus.grpc.GrpcMetadata
import io.grpc.Status

import scala.language.higherKinds

trait ClientAsyncInterceptor[F[_]] extends (GrpcMetadata => F[Either[Status, GrpcMetadata]])
