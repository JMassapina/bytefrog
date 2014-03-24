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

package com.secdec.bytefrog.agent.protocol;

import com.secdec.bytefrog.agent.control.ConfigurationHandler;
import com.secdec.bytefrog.agent.control.ConfigurationReader;
import com.secdec.bytefrog.agent.control.ConfigurationReaderV1;
import com.secdec.bytefrog.agent.control.ControlMessageHandler;
import com.secdec.bytefrog.agent.control.ControlMessageProcessor;
import com.secdec.bytefrog.agent.control.ControlMessageProcessorV1;
import com.secdec.bytefrog.agent.init.ControlConnectionHandshake;
import com.secdec.bytefrog.agent.init.ControlConnectionHandshakeV1;
import com.secdec.bytefrog.agent.init.DataConnectionHandshake;
import com.secdec.bytefrog.agent.init.DataConnectionHandshakeV1;
import com.secdec.bytefrog.common.message.MessageProtocol;
import com.secdec.bytefrog.common.message.MessageProtocolV1;

/**
 * ProtocolVersion implementation for version 1.
 * @author RobertF
 */
public class ProtocolVersion1 implements ProtocolVersion
{
	private final MessageProtocol messageProtocol;
	private final ConfigurationReader configurationReader;
	private final ControlConnectionHandshake controlConnectionHandshake;
	private final DataConnectionHandshake dataConnectionHandshake;

	public ProtocolVersion1()
	{
		messageProtocol = new MessageProtocolV1();
		configurationReader = new ConfigurationReaderV1();
		controlConnectionHandshake = new ControlConnectionHandshakeV1(messageProtocol,
				configurationReader);
		dataConnectionHandshake = new DataConnectionHandshakeV1(messageProtocol);
	}

	@Override
	public MessageProtocol getMessageProtocol()
	{
		return messageProtocol;
	}

	@Override
	public ConfigurationReader getConfigurationReader()
	{
		return configurationReader;
	}

	@Override
	public ControlConnectionHandshake getControlConnectionHandshake()
	{
		return controlConnectionHandshake;
	}

	@Override
	public DataConnectionHandshake getDataConnectionHandshake()
	{
		return dataConnectionHandshake;
	}

	@Override
	public ControlMessageProcessor getControlMessageProcessor(ControlMessageHandler handler,
			ConfigurationHandler configHandler)
	{
		return new ControlMessageProcessorV1(configurationReader, handler, configHandler);
	}
}
