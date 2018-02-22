package com.avast.cactus.grpc

import java.util.concurrent.Executor

import io.grpc._
import io.grpc.stub.AbstractStub

import scala.concurrent.ExecutionContext
import scala.language.experimental.macros

package object client {

  implicit class MapClient(val channel: Channel) extends AnyVal {
    def createMappedClient[GrpcClientStub <: AbstractStub[GrpcClientStub], ClientTrait](
        interceptors: ClientAsyncInterceptor*)(implicit ec: ExecutionContext, ex: Executor): ClientTrait =
      macro com.avast.cactus.grpc.client.ClientMacros.mapClientToTraitWithInterceptors[GrpcClientStub, ClientTrait]
  }

}
