package com.namely.chiefofstate

import com.namely.protobuf.chiefofstate.v1.internal.GrpcHeader
import io.grpc._

/**
 * Intercepts gRPC headers and propagate them downstream via the gRPC context
 */
object GrpcHeadersInterceptor extends ServerInterceptor {

  val REQUEST_META: Context.Key[GrpcHeader] = Context.key[GrpcHeader]("metadata")

  /**
   * intercepts the request headers
   *
   * @param call the gRPC call
   * @param headers the request headers
   * @param next the next request call
   * @tparam ReqT the type of the request
   * @tparam RespT the type of the response
   * @return
   */
  override def interceptCall[ReqT, RespT](
    call: ServerCall[ReqT, RespT],
    headers: Metadata,
    next: ServerCallHandler[ReqT, RespT]
  ): ServerCall.Listener[ReqT] = {
    val context: Context = Context.current().withValue(REQUEST_META, GrpcHeaderTransformer.transform(headers))
    Contexts.interceptCall(context, call, headers, next)
  }
}
