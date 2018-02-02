package com.avast.cactus.grpc.server

import java.util.concurrent.ExecutionException

import com.avast.cactus.grpc.server.TestApi.{GetRequest, GetResponse}
import com.avast.cactus.grpc.server.TestApiServiceGrpc.{TestApiServiceFutureStub, TestApiServiceImplBase}
import com.avast.cactus.grpc._
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc._
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.stub.StreamObserver
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.FunSuite
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class ServerTest extends FunSuite with MockitoSugar with Eventually {

  private implicit val p: PatienceConfig = PatienceConfig(timeout = Span(1, Seconds))

  def randomString(length: Int): String = {
    Random.alphanumeric.take(length).mkString("")
  }

  case class MyRequest(names: Seq[String])

  case class MyResponse(results: Map[String, Int])

  case class MyContext(theHeader: String)

  trait MyApi {
    def get(request: MyRequest, ctx: MyContext): Future[Either[Status, MyResponse]]
  }

  test("ok path") {
    val channelName = randomString(10)
    val headerValue = randomString(10)

    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.eq(MyContext(theHeader = headerValue))))
      .thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))

    val service = impl.mappedTo[TestApiServiceImplBase].withInterceptors(ServerMetadataInterceptor)

    InProcessServerBuilder
      .forName(channelName)
      .directExecutor
      .addService(service)
      .build
      .start

    val stub: TestApiServiceFutureStub = {
      val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build
      TestApiServiceGrpc
        .newFutureStub(channel)
        .withInterceptors(
          new ClientInterceptorTest(
            Map(
              "theHeader" -> headerValue
            )))
    }

    val result = stub.get(GetRequest.newBuilder().addNames("name42").build()).get()

    assertResult(GetResponse.newBuilder().putResults("name42", 42).build())(result)
  }

//  test("missing headers") {
//    val channelName = randomString(10)
//    val headerValue = randomString(10)
//
//    val impl = mock[MyApi]
//    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.eq(MyContext(theHeader = headerValue))))
//      .thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))
//
//    val service = impl.mappedTo[TestApiServiceImplBase]
//
//    InProcessServerBuilder
//      .forName(channelName)
//      .directExecutor
//      .addService(service)
//      .build
//      .start
//
//    val stub: TestApiServiceFutureStub = {
//      val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build
//      TestApiServiceGrpc
//        .newFutureStub(channel)
//        .withInterceptors(new ClientInterceptorTest(Map.empty))
//    }
//
//    try {
//      stub.get(GetRequest.newBuilder().addNames("name42").build()).get()
//      fail("Exception should have been thrown")
//    } catch {
//      case e: ExecutionException if e.getCause.isInstanceOf[StatusRuntimeException] => //ok
//    }
//  }

//  test("propagation of status") {
//    val impl = mock[MyApi]
//    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.any()))
//      .thenReturn(Future.successful(Left(Status.UNAVAILABLE)))
//
//    val service = impl.mappedTo[TestApiServiceImplBase]
//
//    val responseObserver = mock[StreamObserver[GetResponse]]
//    doNothing().when(responseObserver).onNext(ArgumentMatchers.any())
//    doNothing().when(responseObserver).onCompleted()
//
//    service.get(GetRequest.newBuilder().addNames("name42").build(), responseObserver)
//
//    eventually {
//      verify(impl, times(1)).get(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.any())
//
//      val captor = ArgumentCaptor.forClass[StatusException, StatusException](classOf[StatusException])
//      verify(responseObserver, times(1)).onError(captor.capture())
//
//      assertResult(Status.UNAVAILABLE)(captor.getValue.getStatus)
//    }
//  }
//
//  test("propagation of failure") {
//    val impl = mock[MyApi]
//    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.any()))
//      .thenReturn(Future.failed(new RuntimeException("failure")))
//
//    val service = impl.mappedTo[TestApiServiceImplBase]
//
//    val responseObserver = mock[StreamObserver[GetResponse]]
//    doNothing().when(responseObserver).onNext(ArgumentMatchers.any())
//    doNothing().when(responseObserver).onCompleted()
//
//    service.get(GetRequest.newBuilder().addNames("name42").build(), responseObserver)
//
//    eventually {
//      verify(impl, times(1)).get(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.any())
//
//      val captor = ArgumentCaptor.forClass[StatusException, StatusException](classOf[StatusException])
//      verify(responseObserver, times(1)).onError(captor.capture())
//
//      val status = captor.getValue.getStatus
//
//      assertResult(Status.Code.INTERNAL)(status.getCode)
//      assertResult(s"java.lang.RuntimeException: failure")(status.getDescription)
//    }
//  }

  private class ClientInterceptorTest(userHeaders: Map[String, String]) extends ClientInterceptor {
    override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT],
                                            callOptions: CallOptions,
                                            next: Channel): ClientCall[ReqT, RespT] = {

      new SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
        override def delegate(): ClientCall[ReqT, RespT] = super.delegate()

        override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit = {
          userHeaders.foreach {
            case (key, value) =>
              headers.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), s"$UserHeaderPrefix$key-$value")
          }

          super.start(responseListener, headers)
        }
      }
    }
  }

}
