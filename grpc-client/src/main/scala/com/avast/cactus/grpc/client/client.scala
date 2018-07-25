package com.avast.cactus.grpc

import java.util.concurrent.Executor

import io.grpc._
import io.grpc.stub.AbstractStub

import scala.concurrent.ExecutionContext
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.ClassTag

package object client {

  implicit class MapClient(val channel: Channel) extends AnyVal {
    /**
      * @param interceptors List of async interceptors to be attached to the client.
      * @param ec ExecutionContext for callbacks.
      * @param ex Executor for (potentially blocking) conversion between
      * @tparam GrpcClientStub Generated gRPC client async stub.
      * @tparam F F-type (see readme)
      * @tparam ClientTrait Your client trait
      * @return Instance of `ClientTrait[F]`
      */
    // there should theoretically be ClientTrait <: GrpcClient bound here but it's solved in the macro itself - it caused some problems here
    def createMappedClient[GrpcClientStub <: AbstractStub[GrpcClientStub], F[_], ClientTrait[_[_]]](
        interceptors: ClientAsyncInterceptor*)(implicit ec: ExecutionContext, ex: Executor, ct: ClassTag[ClientTrait[F]]): ClientTrait[F] =
      macro com.avast.cactus.grpc.client.ClientMacros.mapClientToTraitWithInterceptors[GrpcClientStub, F, ClientTrait]
  }

}
