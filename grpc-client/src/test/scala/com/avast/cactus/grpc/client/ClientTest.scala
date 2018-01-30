package com.avast.cactus.grpc.client

import com.avast.cactus.grpc.client.TestApi.{GetRequest, GetResponse}
import com.avast.cactus.grpc.client.TestApiServiceGrpc.TestApiServiceFutureStub
import com.avast.cactus.grpc.{ServerError, ServerResponse}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.stub.StreamObserver
import io.grpc.{Status, StatusRuntimeException}
import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class ClientTest extends FunSuite with ScalaFutures with MockitoSugar {

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

    InProcessServerBuilder
      .forName(channelName)
      .directExecutor
      .addService(new TestApiServiceGrpc.TestApiServiceImplBase {
        override def get(request: GetRequest, responseObserver: StreamObserver[GetResponse]): Unit = {
          responseObserver.onNext(GetResponse.newBuilder().putResults("name42", 42).build())
          responseObserver.onCompleted()
        }
      })
      .build
      .start

    val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build

    val mapped = channel.createMappedClient[TestApiServiceFutureStub, ClientTrait]

    val Right(result) = mapped.get(MyRequest(Seq("name42"))).futureValue

    assertResult(MyResponse(Map("name42" -> 42)))(result)
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
