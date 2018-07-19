package com.avast.cactus.grpc.client

import java.util.concurrent.Executor

import cats.arrow.FunctionK
import com.avast.cactus.grpc._
import com.avast.cactus.grpc.client.TestApi.{GetRequest, GetResponse}
import com.avast.cactus.grpc.client.TestApiServiceGrpc.TestApiServiceFutureStub
import io.grpc._
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.stub.StreamObserver
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Milliseconds, Seconds, Span}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class ClientTest extends FunSuite with ScalaFutures with MockitoSugar {

  private implicit val p: PatienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(50, Milliseconds))

  private implicit val ex: Executor = ExecutionContext.global

  private implicit val ftf: FromTask[Future] = new FunctionK[Task, Future] {
    override def apply[A](fa: Task[A]): Future[A] = fa.runAsync
  }

  def randomString(length: Int): String = {
    Random.alphanumeric.take(length).mkString("")
  }

  case class MyRequest(names: Seq[String])

  case class MyResponse(results: Map[String, Int])

  test("ok path") {
    trait ClientTrait extends GrpcClient[Task] with AutoCloseable {
      def get(request: MyRequest): Task[ServerResponse[MyResponse]]
    }

    val channelName = randomString(10)

    val headerName = randomString(10)
    val metadataKey = Metadata.Key.of(headerName, Metadata.ASCII_STRING_MARSHALLER)

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

    val mapped = channel.createMappedClient[TestApiServiceFutureStub, Task, ClientTrait](
      ClientHeadersInterceptor(Map(headerName -> "theValue"))
    )

    val Right(result) = mapped.get(MyRequest(Seq("name42"))).runAsync.futureValue

    assertResult(MyResponse(Map("name42" -> 42)))(result)
  }

  test("missing header - server interceptor failure") {
    trait ClientTrait extends GrpcClient[Task] with AutoCloseable {
      def get(request: MyRequest): Task[ServerResponse[MyResponse]]
    }

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

    val mapped = channel.createMappedClient[TestApiServiceFutureStub, Task, ClientTrait]()

    val Left(ServerError(status, _)) = mapped.get(MyRequest(Seq("name42"))).runAsync.futureValue

    assertResult(Status.Code.ABORTED)(status.getCode)
    assertResult("hello-world")(status.getDescription)
  }

  test("propagation of status") {
    trait ClientTrait extends GrpcClient[Future] with AutoCloseable {
      def get(request: MyRequest): Future[ServerResponse[MyResponse]]
    }

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

    val mapped = channel.createMappedClient[TestApiServiceFutureStub, Future, ClientTrait]()

    val Left(ServerError(status, _)) = mapped.get(MyRequest(Seq("name42"))).futureValue

    assertResult(Status.Code.UNAVAILABLE)(status.getCode)
    assertResult("hello-world")(status.getDescription)
  }
}
