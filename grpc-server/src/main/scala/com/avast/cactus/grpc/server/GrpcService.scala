package com.avast.cactus.grpc.server

import io.grpc.{BindableService, ServerInterceptor, ServerInterceptors, ServerServiceDefinition}

trait GrpcService[Service <: BindableService] extends BindableService

class DefaultGrpcService[A <: BindableService](service: A, interceptors: ServerInterceptor*) extends GrpcService[A] {
  override val bindService: ServerServiceDefinition = ServerInterceptors.intercept(service, interceptors: _*)
}
