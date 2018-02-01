package com.avast.cactus.grpc.client

import com.avast.cactus.grpc.client.TestApi.{GetRequest, GetResponse}
import com.avast.cactus.grpc.client.TestApiServiceGrpc.TestApiServiceFutureStub
import com.avast.cactus.grpc.{ServerError, ServerResponse}
import io.grpc._
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.stub.StreamObserver
import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class ClientTest extends FunSuite with ScalaFutures with MockitoSugar {

  private implicit val p: PatienceConfig = PatienceConfig(timeout = Span(1, Seconds))

  def randomString(length: Int): String = {
    Random.alphanumeric.take(length).mkString("")
  }

  case class MyRequest(names: Seq[String])

  case class MyResponse(results: Map[String, Int])

  trait ClientTrait {
    def get(request: MyRequest): Future[ServerResponse[MyResponse]]
  }

  test("ok path") {
    val channelName = randomString(10)

    val metadataKey = Metadata.Key.of(randomString(10), Metadata.ASCII_STRING_MARSHALLER)

    InProcessServerBuilder
      .forName(channelName)
      .directExecutor
      .addService(ServerInterceptors.intercept(
        new TestApiServiceGrpc.TestApiServiceImplBase {
          override def get(request: GetRequest, responseObserver: StreamObserver[GetResponse]): Unit = {
            responseObserver.onNext(GetResponse.newBuilder().putResults("name42", 42).build())
            responseObserver.onCompleted()
          }
        },
        new ServerInterceptor {
          override def interceptCall[ReqT, RespT](call: ServerCall[ReqT, RespT],
                                                  headers: Metadata,
                                                  next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
            if (headers.containsKey(metadataKey)) {
              next.startCall(call, headers)
            } else {
              call.close(Status.ABORTED, new Metadata())
              new ServerCall.Listener[ReqT]() {}
            }
          }
        }
      ))
      .build
      .start

    val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build

    val mapped = channel.createMappedClient[TestApiServiceFutureStub, ClientTrait](
      List(
        new ClientAsyncInterceptor {
          override def apply(v1: (Context, Metadata)): Future[(Context, Metadata)] = {
            val newMetadata = v1._2
            newMetadata.put(metadataKey, "theValue")
            Future.successful(v1.copy(_2 = newMetadata))
          }
        }
      ))

    val Right(result) = mapped.get(MyRequest(Seq("name42"))).futureValue

    assertResult(MyResponse(Map("name42" -> 42)))(result)
  }

  test("missing header - server interceptor failure") {
    val channelName = randomString(10)

    val metadataKey = Metadata.Key.of(randomString(10), Metadata.ASCII_STRING_MARSHALLER)

    InProcessServerBuilder
      .forName(channelName)
      .directExecutor
      .addService(ServerInterceptors.intercept(
        new TestApiServiceGrpc.TestApiServiceImplBase {
          override def get(request: GetRequest, responseObserver: StreamObserver[GetResponse]): Unit = {
            responseObserver.onNext(GetResponse.newBuilder().putResults("name42", 42).build())
            responseObserver.onCompleted()
          }
        },
        new ServerInterceptor {
          override def interceptCall[ReqT, RespT](call: ServerCall[ReqT, RespT],
                                                  headers: Metadata,
                                                  next: ServerCallHandler[ReqT, RespT]): ServerCall.Listener[ReqT] = {
            if (headers.containsKey(metadataKey)) {
              next.startCall(call, headers)
            } else {
              call.close(Status.ABORTED.withDescription("hello-world"), new Metadata())
              new ServerCall.Listener[ReqT]() {}
            }
          }
        }
      ))
      .build
      .start

    val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build

    val mapped = channel.createMappedClient[TestApiServiceFutureStub, ClientTrait]

    val Left(ServerError(status, _)) = mapped.get(MyRequest(Seq("name42"))).futureValue

    assertResult(Status.Code.ABORTED)(status.getCode)
    assertResult("hello-world")(status.getDescription)
  }

  test("propagation of status") {
    val channelName = randomString(10)

    InProcessServerBuilder
      .forName(channelName)
      .directExecutor
      .addService(new TestApiServiceGrpc.TestApiServiceImplBase {
        override def get(request: GetRequest, responseObserver: StreamObserver[GetResponse]): Unit = {
          responseObserver.onError(new StatusRuntimeException(Status.UNAVAILABLE.withDescription("hello-world")))
        }
      })
      .build
      .start

    val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build

    val mapped = channel.createMappedClient[TestApiServiceFutureStub, ClientTrait]

    val Left(ServerError(status, _)) = mapped.get(MyRequest(Seq("name42"))).futureValue

    assertResult(Status.Code.UNAVAILABLE)(status.getCode)
    assertResult("hello-world")(status.getDescription)
  }
}
