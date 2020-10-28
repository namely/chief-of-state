package com.namely.chiefofstate

import com.namely.chiefofstate.test.helpers.CustomActorTestkit
import org.scalamock.scalatest.MockFactory
import io.superflat.lagompb.protobuf.v1.core.FailureResponse
import io.grpc.Status.Code
import scala.util.Failure
import com.google.protobuf.any.Any
import com.google.rpc.status.{Status => RpcStatus}
import akka.grpc.GrpcServiceException
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.adapter._

class GrpcServiceImplSpec extends CustomActorTestkit("application.conf") with MockFactory {
  ".transformFailedReply" should {
    "handle custom errors" in {
      val actorSystem: ActorSystem = testKit.system.toClassic
      val impl = new GrpcServiceImpl(actorSystem, null, null, null)(null)

      val rpcStatus: RpcStatus = RpcStatus()
        .withCode(Code.DATA_LOSS.value())
        .withMessage("some message")

      val failureResponse: FailureResponse = FailureResponse()
        .withCustom(Any.pack(rpcStatus))

      val actual: GrpcServiceException = intercept[GrpcServiceException]{
        throw impl.transformFailedReply(failureResponse).get
      }

      actual.getStatus.getCode.value shouldBe(rpcStatus.code)
      actual.getStatus.getDescription shouldBe(rpcStatus.message)

    }
    "call parent for other errors" in {
      val actorSystem: ActorSystem = testKit.system.toClassic
      val impl = new GrpcServiceImpl(actorSystem, null, null, null)(null)

      val failureResponse: FailureResponse = FailureResponse()

      val actual: GrpcServiceException = intercept[GrpcServiceException]{
        throw impl.transformFailedReply(failureResponse).get
      }

      actual.getStatus.getCode.value shouldBe(Code.INTERNAL.value())
    }
  }
}
