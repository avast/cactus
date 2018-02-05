package com.avast.cactus.grpc

import java.util.concurrent.Executor

import io.grpc._
import io.grpc.stub.AbstractStub

import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros

package object client {

  private[client] val MetadataContextKey = ContextKeys.get[Metadata]("headers")

  type GrpcRequestMetadata = (Context, Metadata)
  type ClientAsyncInterceptor = GrpcRequestMetadata => Future[GrpcRequestMetadata]

  implicit class MapClient(val channel: Channel) extends AnyVal {
    def createMappedClient[GrpcClientStub <: AbstractStub[GrpcClientStub], MT](interceptors: ClientAsyncInterceptor*)(implicit ec: ExecutionContext, ex: Executor): MT =
      macro ClientMacros.mapClientToTraitWithInterceptors[GrpcClientStub, MT]
  }

}
