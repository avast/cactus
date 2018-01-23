package com.avast.cactus.grpc.server

import com.avast.cactus.grpc.server.TestApi.{GetRequest, GetResponse}
import com.avast.cactus.grpc.server.TestApiServiceGrpc.TestApiServiceImplBase
import io.grpc.stub.StreamObserver
import io.grpc.{Status, StatusException}
import org.mockito.Mockito._
import org.mockito.{ArgumentCaptor, ArgumentMatchers}
import org.scalatest.FunSuite
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ServerMacrosTest extends FunSuite with MockitoSugar with Eventually {

  case class MyRequest(names: Seq[String])

  case class MyResponse(results: Map[String, Int])

  trait MyApi {
    def get(request: MyRequest): Future[Either[Status, MyResponse]]
  }

  private implicit val p: PatienceConfig = PatienceConfig(timeout = Span(1, Seconds))

  test("ok path") {
    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))).thenReturn(Future.successful(Right(MyResponse(Map("name42" -> 42)))))

    val service = impl.mappedTo[TestApiServiceImplBase]

    val responseObserver = mock[StreamObserver[GetResponse]]
    doNothing().when(responseObserver).onNext(ArgumentMatchers.any())
    doNothing().when(responseObserver).onCompleted()

    service.get(GetRequest.newBuilder().addNames("name42").build(), responseObserver)

    eventually {
      verify(impl, times(1)).get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))
      verify(responseObserver, times(1)).onNext(ArgumentMatchers.eq(GetResponse.newBuilder().putResults("name42", 42).build()))
    }
  }

  test("propagation of status") {
    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))).thenReturn(Future.successful(Left(Status.UNAVAILABLE)))

    val service = impl.mappedTo[TestApiServiceImplBase]

    val responseObserver = mock[StreamObserver[GetResponse]]
    doNothing().when(responseObserver).onNext(ArgumentMatchers.any())
    doNothing().when(responseObserver).onCompleted()

    service.get(GetRequest.newBuilder().addNames("name42").build(), responseObserver)

    eventually {
      verify(impl, times(1)).get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))

      val captor = ArgumentCaptor.forClass[StatusException, StatusException](classOf[StatusException])
      verify(responseObserver, times(1)).onError(captor.capture())

      assertResult(Status.UNAVAILABLE)(captor.getValue.getStatus)
    }
  }

  test("propagation of failure") {
    val impl = mock[MyApi]
    when(impl.get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))).thenReturn(Future.failed(new RuntimeException("failure")))

    val service = impl.mappedTo[TestApiServiceImplBase]

    val responseObserver = mock[StreamObserver[GetResponse]]
    doNothing().when(responseObserver).onNext(ArgumentMatchers.any())
    doNothing().when(responseObserver).onCompleted()

    service.get(GetRequest.newBuilder().addNames("name42").build(), responseObserver)

    eventually {
      verify(impl, times(1)).get(ArgumentMatchers.eq(MyRequest(Seq("name42"))))

      val captor = ArgumentCaptor.forClass[StatusException, StatusException](classOf[StatusException])
      verify(responseObserver, times(1)).onError(captor.capture())

      val status = captor.getValue.getStatus

      assertResult(Status.Code.INTERNAL)(status.getCode)
      assertResult(s"java.lang.RuntimeException: failure")(status.getDescription)
    }
  }
}
