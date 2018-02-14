package com.avast.cactus.grpc.server

import com.avast.cactus.grpc.GrpcMetadata
import io.grpc.Status

import scala.concurrent.Future

trait ServerAsyncInterceptor extends (GrpcMetadata => Future[Either[Status, GrpcMetadata]])
