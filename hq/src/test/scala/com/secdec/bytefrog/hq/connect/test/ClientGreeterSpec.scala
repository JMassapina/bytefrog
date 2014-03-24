/*
 * bytefrog: a tracing framework for the JVM. For more information
 * see http://code-pulse.com/bytefrog
 *
 * Copyright (C) 2014 Applied Visions - http://securedecisions.avi.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.secdec.bytefrog.hq.connect.test

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

import scala.concurrent.Future
import scala.concurrent.Promise

import org.scalatest.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalamock.scalatest.MockFactory

import com.secdec.bytefrog.common.config.RuntimeAgentConfigurationV1
import com.secdec.bytefrog.common.connect.Connection
import com.secdec.bytefrog.common.message.MessageConstantsV1
import com.secdec.bytefrog.common.message.MessageProtocol
import com.secdec.bytefrog.hq.connect._
import com.secdec.bytefrog.hq.protocol._
import com.secdec.bytefrog.hq.config._
import com.secdec.bytefrog.hq.trace.Trace
import com.secdec.bytefrog.hq.testutil.MockedSendingHelpers

class ClientGreeterSpec extends FunSpec with ShouldMatchers with MockFactory with MockedSendingHelpers {

	def makeBytesInput(bytes: Byte*) = new DataInputStream(new ByteArrayInputStream(Array[Byte](bytes: _*)))

	trait HasMockedSending { self: ProtocolHelper =>
		def expectMessage(accept: ControlMessage => Boolean): Unit
	}

	describe("ClientGreeter") {
		it("should close the client connection when the client offers an invalid 'hello'") {

			val connector = mock[TraceControlConnector]
			val registry = mock[TraceRegistry]
			val clientClose = mockFunction[Unit]
			//some invalid input to read
			val clientInput = makeBytesInput(MessageConstantsV1.MsgError, 2)
			val clientOutput = new DataOutputStream(stub[OutputStream])

			val client = new Connection {
				def close = clientClose()
				def input = clientInput
				def output = clientOutput
			}

			val greeter = new ClientGreeter(client, connector, registry)

			clientClose.expects().once
			greeter.run
		}

		it("should close the client connection if the DataHello gives an invalid protocol version") {
			val clientClose = mockFunction[Unit]
			val clientInput = makeBytesInput(MessageConstantsV1.MsgHello, 10) //10 = invalid p. version
			val clientOutput = new DataOutputStream(stub[OutputStream])

			val client = new Connection {
				def close = clientClose()
				def input = clientInput
				def output = clientOutput
			}

			val greeter = new ClientGreeter(client, mock[TraceControlConnector], mock[TraceRegistry])
			clientClose.expects.once
			greeter.run
		}

		it("should call 'handleHello' with a MessageProtocol instance when the client provides a valid 'Hello'") {
			val clientClose = mockFunction[Unit]
			val clientInput = makeBytesInput(MessageConstantsV1.MsgHello, 1)
			val clientOutput = new DataOutputStream(stub[OutputStream])

			val client = new Connection {
				def close = clientClose()
				def input = clientInput
				def output = clientOutput
			}

			val greeterHandleHello = mockFunction[Int, Unit]
			val greeter = new ClientGreeter(client, mock[TraceControlConnector], mock[TraceRegistry]) {
				override def handleHello(pv: Int) = greeterHandleHello(pv)
			}

			clientClose.expects.never
			greeterHandleHello.expects(1).once

			greeter.run
		}

		it("should call 'handleDataHello' when the client provides a valid 'Data Hello'") {
			val runId: Byte = 12
			val clientClose = mockFunction[Unit]
			val client = new Connection {
				def close = clientClose()
				val input = makeBytesInput(MessageConstantsV1.MsgDataHello, runId)
				val output = new DataOutputStream(stub[OutputStream])
			}

			val greeterHandleDataHello = mockFunction[Byte, Unit]
			val greeter = new ClientGreeter(client, mock[TraceControlConnector], mock[TraceRegistry]) {
				override def handleDataHello(runId: Byte) = greeterHandleDataHello(runId)
			}

			clientClose.expects.never
			greeterHandleDataHello.expects(runId).once

			greeter.run
		}

		it("should close the client in 'handleHello' if the ControlConnector doesn't return a configuration") {
			val clientClose = mockFunction[Unit]
			val client = new Connection {
				def close = clientClose()
				val input = new DataInputStream(stub[InputStream])
				val output = new DataOutputStream(stub[OutputStream])
			}
			val connector = mock[TraceControlConnector]
			val pHelper = new MockedProtocolHelper(1) {
				override val controlWriter = new MockedMessageSender
			}
			val greeter = new ClientGreeter(client, connector, mock[TraceRegistry], pHelper)

			(connector.addControlConnection _).expects(*).once.returning(None)
			(pHelper.controlWriter.writeMsg).expects(*).once.onCall { msg: ControlMessage =>
				msg match {
					case ControlMessage.Error(_) => //ok
					case _ => fail("Expected an error message")
				}
			}
			clientClose.expects.once

			greeter.handleHello(1)
		}

		it("should write a configuration to the client if everything goes smoothly in 'handleHello'") {
			val clientClose = mockFunction[Unit]
			val client = new Connection {
				def close = clientClose()
				val input = new DataInputStream(stub[InputStream])
				val output = new DataOutputStream(stub[OutputStream])
			}

			val connector = mock[TraceControlConnector]
			val pHelper = new MockedProtocolHelper(1) {
				override val controlWriter = new MockedMessageSender
			}
			val greeter = new ClientGreeter(client, connector, mock[TraceRegistry], pHelper)
			val config = Configuration(1, TraceSettings(), AgentConfiguration())
			val configMsg = ControlMessage.Configuration(config)

			(connector.addControlConnection _).expects(*).once.returning(Some(configMsg))
			pHelper.controlWriter.writeMsg.expects(*).once.onCall { msg: ControlMessage =>
				msg match {
					case `configMsg` => //ok
					case _ => fail
				}
			}
			clientClose.expects.never

			greeter.handleHello(1)

		}

		it("should close a 'data' client if the TraceRegistry's getTrace Future doesn't provide a result within 500ms") {
			val runId: Byte = 1
			val clientClose = mockFunction[Unit]
			val client = new Connection {
				def close = clientClose()
				val input = new DataInputStream(stub[InputStream])
				val output = new DataOutputStream(stub[OutputStream])
			}
			val connector = mock[TraceControlConnector]
			val registry = mock[TraceRegistry]
			val traceFuture = Promise[Trace].future //it will never complete

			val greeter = new ClientGreeter(client, connector, registry)

			(registry.getTrace _).expects(runId).once.returning(traceFuture)
			clientClose.expects.once

			greeter.handleDataHello(runId)
		}

		//TODO: class TraceMockable extends Trace(1, mockControlConnection, MonitorConfiguration(), mock[])
		//
		it("should add the client to the given trace, when the TraceRegistry finds that trace")(pending)
		//		{
		//			val runId: Byte = 1
		//			val clientClose = mockFunction[Unit]
		//			val client = new Connection {
		//				def close = clientClose()
		//				val input = new DataInputStream(stub[InputStream])
		//				val output = new DataOutputStream(stub[OutputStream])
		//			}
		//			val connector = mock[TraceControlConnector]
		//			val registry = mock[TraceRegistry]
		//			val trace = mock[TraceMockable]
		//			val traceFuture = Future.successful(trace)
		//			val pHelper = new MockedProtocolHelper(1) {
		//				override val controlWriter = new MockedMessageSender
		//			}
		//			val greeter = new ClientGreeter(client, connector, registry, pHelper)
		//
		//			clientClose.expects.never
		//			(registry.getTrace _).expects(runId).once.returning(traceFuture)
		//			(trace.addDataConnection _).expects(*).once.returning(true)
		//			pHelper.controlWriter.writeMsg.expects(ControlMessage.DataHelloReply).once
		//
		//			greeter.handleDataHello(runId)
		//		}
		//
		it("should close the client connection if the Trace fails to add it")(pending)
		//		{
		//			val runId: Byte = 1
		//			val clientClose = mockFunction[Unit]
		//			val client = new Connection {
		//				def close = clientClose()
		//				val input = new DataInputStream(stub[InputStream])
		//				val output = new DataOutputStream(stub[OutputStream])
		//			}
		//			val connector = mock[TraceControlConnector]
		//			val registry = mock[TraceRegistry]
		//			val trace = mock[TraceMockable]
		//			val traceFuture = Future.successful(trace)
		//			val pHelper = new MockedProtocolHelper(1) {
		//				override val controlWriter = new MockedMessageSender
		//			}
		//			val greeter = new ClientGreeter(client, connector, registry, pHelper)
		//
		//			clientClose.expects.once
		//			(registry.getTrace _).expects(runId).once.returning(traceFuture)
		//			(trace.addDataConnection _).expects(*).once.returning(false)
		//			pHelper.controlWriter.writeMsg.expects(*).once.onCall { msg: ControlMessage =>
		//				msg match {
		//					case ControlMessage.Error(_) => //ok
		//					case _ => fail("Expected an error message")
		//				}
		//			}
		//
		//			greeter.handleDataHello(runId)
		//		}
	}
}