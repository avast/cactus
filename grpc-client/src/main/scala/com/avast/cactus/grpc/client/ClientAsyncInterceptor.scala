package com.avast.cactus.grpc.client

import com.avast.cactus.grpc.GrpcMetadata
import io.grpc.Status

import scala.concurrent.Future

trait ClientAsyncInterceptor extends (GrpcMetadata => Future[Either[Status, GrpcMetadata]])
