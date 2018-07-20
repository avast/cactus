package com.avast.cactus.grpc.server

import io.grpc.{BindableService, ServerInterceptor, ServerInterceptors, ServerServiceDefinition}

sealed trait MappedGrpcService[Service <: BindableService] extends BindableService

class DefaultMappedGrpcService[A <: BindableService](service: A, interceptors: ServerInterceptor*) extends MappedGrpcService[A] {
  override val bindService: ServerServiceDefinition = ServerInterceptors.intercept(service, interceptors: _*)
}
