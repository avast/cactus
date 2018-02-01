package com.avast.cactus.grpc

import java.util.concurrent.Executor

import io.grpc._
import io.grpc.stub.AbstractStub

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros

package object client {

  type GrpcRequestMetadata = (Context, Metadata)
  type ClientAsyncInterceptor = GrpcRequestMetadata => Future[GrpcRequestMetadata]

  final val MetadataContextKey = Context.key[Metadata]("metadataKey" + System.currentTimeMillis()) // random name to prevent collision

  implicit class MapClient(val channel: Channel) extends AnyVal {
    def createMappedClient[GrpcClientStub <: AbstractStub[GrpcClientStub], MT](implicit ec: ExecutionContext, ex: Executor): MT =
      macro ClientMacros.mapClientToTrait[GrpcClientStub, MT]

    def createMappedClient[GrpcClientStub <: AbstractStub[GrpcClientStub], MT](interceptors: immutable.Seq[ClientAsyncInterceptor])(implicit ec: ExecutionContext, ex: Executor): MT =
      macro ClientMacros.mapClientToTraitWithInterceptors[GrpcClientStub, MT]
  }

}
