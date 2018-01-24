package com.avast.cactus.grpc.client

import java.util.concurrent.{Executor, ForkJoinPool}

import com.avast.cactus.grpc.client.TestApi.{GetRequest, GetResponse}
import com.google.common.util.concurrent.{Futures, ListenableFuture}
import io.grpc.Status
import org.scalactic.{Bad, Good}
import org.scalatest.FunSuite
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientMacrosTest extends FunSuite with ScalaFutures with MockitoSugar {

  case class MyRequest(names: Seq[String])

  case class MyResponse(results: Map[String, Int])

  trait ClientTrait {
    def get(request: MyRequest): Future[ServerResponse[MyResponse]]
  }

  test("ok path") {
    def callMock(req: GetRequest): ListenableFuture[GetResponse] = {
      assertResult(GetRequest.newBuilder().addNames("name42").build())(req)

      Futures.immediateFuture {
        GetResponse.newBuilder().putResults("name42", 42).build()
      }
    }

    val mapped = new ClientTrait {
      private val ex: Executor = ForkJoinPool.commonPool()

      import ClientCommonMethods._
      import com.avast.cactus.v3._

      override def get(request: MyRequest): Future[ServerResponse[MyResponse]] = {
        request.asGpb[GetRequest] match {
          case Good(req) => executeRequest[GetRequest, GetResponse, MyResponse](req, callMock, ex)
          case Bad(errors) =>
            Future.successful {
              Left {
                ServerError(Status.INVALID_ARGUMENT.withDescription(formatCactusFailures("request", errors)))
              }
            }
        }

      }
    }

    val Right(result) = mapped.get(MyRequest(Seq("name42"))).futureValue

    assertResult(MyResponse(Map("name42" -> 42)))(result)
  }
}
