package com.avast.cactus.grpc

import io.grpc._

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.language.experimental.macros

package object server {

  implicit class MapService[ServerTrait](val myTrait: ServerTrait) extends AnyVal {
    def mappedToService[GrpcService <: BindableService](
        interceptors: ServerAsyncInterceptor*)(implicit ct: ClassTag[ServerTrait], ec: ExecutionContext): ServerServiceDefinition =
      macro com.avast.cactus.grpc.server.ServerMacros.mapImplToService[GrpcService]
  }

}
