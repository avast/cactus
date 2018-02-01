package com.avast.cactus.grpc.client

import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc._

private[grpc] object ClientMetadataInterceptor extends ClientInterceptor {
  override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT],
                                          callOptions: CallOptions,
                                          next: Channel): ClientCall[ReqT, RespT] = {
    val userMetadata = Option(MetadataContextKey.get())

    new SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
      override def delegate(): ClientCall[ReqT, RespT] = super.delegate()

      override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit = {
        userMetadata.foreach(headers.merge)
        super.start(responseListener, headers)
      }
    }
  }
}
