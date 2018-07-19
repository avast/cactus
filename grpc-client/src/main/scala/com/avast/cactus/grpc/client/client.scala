package com.avast.cactus.grpc

import java.util.concurrent.Executor

import io.grpc._
import io.grpc.stub.AbstractStub

import scala.concurrent.ExecutionContext
import scala.language.experimental.macros
import scala.language.higherKinds

package object client {

  implicit class MapClient(val channel: Channel) extends AnyVal {
    def createMappedClient[GrpcClientStub <: AbstractStub[GrpcClientStub],F[_], ClientTrait <: GrpcClient[F]](
        interceptors: ClientAsyncInterceptor*)(implicit ec: ExecutionContext, ex: Executor): ClientTrait =
      macro com.avast.cactus.grpc.client.ClientMacros.mapClientToTraitWithInterceptors[GrpcClientStub, F, ClientTrait]
  }

}
