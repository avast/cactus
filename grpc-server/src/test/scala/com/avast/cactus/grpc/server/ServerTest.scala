package com.avast.cactus.grpc.server

import java.util.concurrent.ExecutionException

import com.avast.cactus.grpc._
import com.avast.cactus.grpc.server.TestApi.{GetRequest, GetResponse}
import com.avast.cactus.grpc.server.TestApiServiceGrpc.{TestApiServiceFutureStub, TestApiServiceImplBase}
import io.grpc.ForwardingClientCall.SimpleForwardingClientCall
import io.grpc._
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import org.mockito.ArgumentMatchers
import org.mockito.Mockito._
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

  case class MyContext(theHeader: String, theHeader2: String)

  case class MyContext2Content(i: Int, s: String)

  case class MyContext2(theHeader: String, content: MyContext2Content)

  trait MyApi {
    def get(request: MyRequest): Future[Either[Status, MyResponse]]

    def get2(request: MyRequest, ctx: MyContext): Future[Either[Status, MyResponse]]

    def get3(request: MyRequest, ctx: MyContext2): Future[Either[Status, MyResponse]]
  }

  test("ok path") {
    val channelName = randomString(10)
    val headerValue = randomString(10)

    // format: OFF
    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))).thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))
    when(impl.get2(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.eq(MyContext(theHeader = headerValue, theHeader2 = headerValue))))
      .thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))
    // format: ON

    val service = impl.mappedToService[TestApiServiceImplBase]()

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
              "theHeader" -> headerValue,
              "theHeader2" -> headerValue
            )))
    }

    // get
    {
      val result = stub.get(GetRequest.newBuilder().addNames("name42").build()).get()
      assertResult(GetResponse.newBuilder().putResults("name42", 42).build())(result)
    }

    //get2
    {
      val result = stub.get2(GetRequest.newBuilder().addNames("name42").build()).get()
      assertResult(GetResponse.newBuilder().putResults("name42", 42).build())(result)
    }
  }

  test("missing headers") {

    val channelName = randomString(10)
    val headerValue = randomString(10)

    // format: OFF
    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))).thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))
    when(impl.get2(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.eq(MyContext(theHeader = headerValue, theHeader2 = headerValue))))
      .thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))
    // format: ON

    val service = impl.mappedToService[TestApiServiceImplBase]()

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
        .withInterceptors(new ClientInterceptorTest(Map.empty))
    }

    // get
    {
      val result = stub.get(GetRequest.newBuilder().addNames("name42").build()).get()
      assertResult(GetResponse.newBuilder().putResults("name42", 42).build())(result)
    }

    try {
      stub.get2(GetRequest.newBuilder().addNames("name42").build()).get()
      fail("Exception should have been thrown")
    } catch {
      case e: ExecutionException if e.getCause.isInstanceOf[StatusRuntimeException] => //ok
    }
  }

  test("headers visible in interceptors") {
    val channelName = randomString(10)
    val headerValue = randomString(10)

    // format: OFF
    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))).thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))
    when(impl.get2(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.eq(MyContext(theHeader = headerValue, theHeader2 = headerValue))))
      .thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))
    // format: ON

    val service = impl.mappedToService[TestApiServiceImplBase](new ServerAsyncInterceptor {
      override def apply(m: GrpcMetadata): Future[Either[Status, GrpcMetadata]] = {
        if (m.headers.keys().contains(s"theheader2")) {
          Future.successful(Right(m))
        } else {
          Future.successful(Left(Status.INVALID_ARGUMENT))
        }
      }
    })

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
              "theHeader" -> headerValue,
              "theHeader2" -> headerValue
            )))
    }

    // get
    {
      val result = stub.get(GetRequest.newBuilder().addNames("name42").build()).get()
      assertResult(GetResponse.newBuilder().putResults("name42", 42).build())(result)
    }

  }

  test("ok path with advanced context") {
    val channelName = randomString(10)
    val headerValue = randomString(10)

    val cont = MyContext2Content(42, "jenda")

    val impl = mock[MyApi]
    val context = MyContext2(theHeader = headerValue, content = cont)

    when(impl.get3(ArgumentMatchers.eq(MyRequest(Seq("name42"))), ArgumentMatchers.eq(context)))
      .thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))

    val service = impl
      .mappedToService[TestApiServiceImplBase](new ServerAsyncInterceptor {
        override def apply(m: GrpcMetadata): Future[Either[Status, GrpcMetadata]] = {
          Future.successful {
            Right(m.copy(context = m.context.withValue(ContextKeys.get[MyContext2Content]("content"), cont)))
          }
        }
      })

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

    // get3
    {
      val result = stub.get3(GetRequest.newBuilder().addNames("name42").build()).get()
      assertResult(GetResponse.newBuilder().putResults("name42", 42).build())(result)
    }
  }

  test("propagation of status") {
    val channelName = randomString(10)

    // format: OFF
    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42")))))
      .thenReturn(Future.successful(Left(Status.UNAVAILABLE.withDescription("jenda"))))
    // format: ON

    val service = impl.mappedToService[TestApiServiceImplBase]()

    InProcessServerBuilder
      .forName(channelName)
      .directExecutor
      .addService(service)
      .build
      .start

    val stub: TestApiServiceFutureStub = {
      val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build
      TestApiServiceGrpc.newFutureStub(channel)
    }

    try {
      stub.get(GetRequest.newBuilder().addNames("name42").build()).get()
      fail("Exception should have been thrown")
    } catch {
      case e: ExecutionException if e.getCause.isInstanceOf[StatusRuntimeException] =>
        val status = e.getCause.asInstanceOf[StatusRuntimeException].getStatus
        assertResult(Status.Code.UNAVAILABLE)(status.getCode)
        assertResult("jenda")(status.getDescription)
    }
  }

  test("propagation of failure") {
    val channelName = randomString(10)

    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))).thenReturn(Future.failed(new RuntimeException("failure")))

    val service = impl.mappedToService[TestApiServiceImplBase]()

    InProcessServerBuilder
      .forName(channelName)
      .directExecutor
      .addService(service)
      .build
      .start

    val stub: TestApiServiceFutureStub = {
      val channel = InProcessChannelBuilder.forName(channelName).directExecutor.build
      TestApiServiceGrpc.newFutureStub(channel)
    }

    try {
      stub.get(GetRequest.newBuilder().addNames("name42").build()).get()
      fail("Exception should have been thrown")
    } catch {
      case e: ExecutionException if e.getCause.isInstanceOf[StatusRuntimeException] =>
        val status = e.getCause.asInstanceOf[StatusRuntimeException].getStatus
        assertResult(Status.Code.INTERNAL)(status.getCode)
        assertResult(s"java.lang.RuntimeException: failure")(status.getDescription)
    }

  }

  private class ClientInterceptorTest(userHeaders: Map[String, String]) extends ClientInterceptor {
    override def interceptCall[ReqT, RespT](method: MethodDescriptor[ReqT, RespT],
                                            callOptions: CallOptions,
                                            next: Channel): ClientCall[ReqT, RespT] = {

      new SimpleForwardingClientCall[ReqT, RespT](next.newCall(method, callOptions)) {
        override def delegate(): ClientCall[ReqT, RespT] = super.delegate()

        override def start(responseListener: ClientCall.Listener[RespT], headers: Metadata): Unit = {
          userHeaders.foreach {
            case (key, value) =>
              headers.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value)
          }

          super.start(responseListener, headers)
        }
      }
    }
  }

}
