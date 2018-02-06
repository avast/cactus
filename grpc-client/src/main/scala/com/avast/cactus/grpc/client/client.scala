package com.avast.cactus.grpc

import java.util.concurrent.Executor

import io.grpc._
import io.grpc.stub.AbstractStub

import scala.concurrent.{ExecutionContext, Future}
import scala.language.experimental.macros

package object client {

  type ClientAsyncInterceptor = GrpcMetadata => Future[Either[Status, GrpcMetadata]]

  implicit class MapClient(val channel: Channel) extends AnyVal {
    def createMappedClient[GrpcClientStub <: AbstractStub[GrpcClientStub], MT](interceptors: ClientAsyncInterceptor*)(implicit ec: ExecutionContext, ex: Executor): MT =
      macro ClientMacros.mapClientToTraitWithInterceptors[GrpcClientStub, MT]
  }

}
