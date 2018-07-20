package com.avast.cactus.grpc

import io.grpc._
import monix.execution.Scheduler

import scala.collection.JavaConverters._
import scala.language.experimental.macros
import scala.language.higherKinds
import scala.reflect.ClassTag

package object server {

  implicit class MapService[ServerTrait, F[_]](val myTrait: ServerTrait) extends AnyVal {
    def mappedToService[Service <: BindableService](interceptors: ServerAsyncInterceptor*)(implicit ct: ClassTag[ServerTrait],
                                                                                           sch: Scheduler): MappedGrpcService[Service] =
      macro com.avast.cactus.grpc.server.ServerMacros.mapImplToService[Service, F]
  }

}
