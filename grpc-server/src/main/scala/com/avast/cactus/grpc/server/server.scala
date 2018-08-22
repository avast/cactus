package com.avast.cactus.grpc

import cats.effect.Effect
import cats.~>
import io.grpc._
import mainecoon.FunctorK

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.ClassTag

package object server {

  implicit class MapService[F[_], ServerTrait[_[_]] <: GrpcService](val myTrait: ServerTrait[F]) extends AnyVal {

    /**
      * @param interceptors Async interceptors to be prepended before the service.
      * @param ec ExecutionContext for callbacks
      * @tparam Service Generated gRPC service stub
      * @return Instance of `MappedGrpcService[Service]`
      */
    // there should theoretically be ClientTrait <: GrpcServer bound here but it's solved in the macro itself - it caused some problems here
    def mappedToService[Service <: BindableService](
        interceptors: ServerAsyncInterceptor[F]*)(implicit ct: ClassTag[ServerTrait[F]], ec: ExecutionContext, ef: Effect[F]): MappedGrpcService[Service] =
      macro com.avast.cactus.grpc.server.ServerMacros.mapImplToService[Service, F]
  }

  implicit val interceptorFunctorK: FunctorK[ServerAsyncInterceptor] = new FunctorK[ServerAsyncInterceptor] {
    override def mapK[F[_], G[_]](af: ServerAsyncInterceptor[F])(fToG: ~>[F, G]): ServerAsyncInterceptor[G] =
      (v1: GrpcMetadata) => fToG { af.apply(v1) }
  }

}
