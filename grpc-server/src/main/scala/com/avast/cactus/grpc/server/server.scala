package com.avast.cactus.grpc

import cats.effect.Effect
import io.grpc._

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
    def mappedToService[Service <: BindableService](interceptors: ServerAsyncInterceptor[F]*)(implicit ct: ClassTag[ServerTrait[F]],
                                                                                              ec: ExecutionContext,
                                                                                              ef: Effect[F]): MappedGrpcService[Service] =
      macro com.avast.cactus.grpc.server.ServerMacros.mapImplToService[Service, F]
  }

}
