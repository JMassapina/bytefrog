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

package com.secdec.bytefrog.agent.javaagent;

import java.lang.instrument.Instrumentation;

import com.secdec.bytefrog.agent.TraceAgent;
import com.secdec.bytefrog.agent.agent.DefaultTraceAgent;
import com.secdec.bytefrog.agent.errors.ErrorHandler;
import com.secdec.bytefrog.agent.trace.ClassTransformationListener;
import com.secdec.bytefrog.agent.trace.Trace;
import com.secdec.bytefrog.agent.trace.TraceClassFileTransformer;
import com.secdec.bytefrog.common.config.RuntimeAgentConfigurationV1;
import com.secdec.bytefrog.common.config.StaticAgentConfiguration;

/**
 * Pre-main agent to hook up bytefrog and get the tracer agent going. Used with:
 * -javaagent:bytefrog-tracer.jar=host:port;logfile
 * 
 * @author RobertF
 */
public class JavaAgent
{
	/**
	 * The trace agent in use for this run.
	 */
	private static TraceAgent agent;

	/**
	 * Return the trace agent in use for this run.
	 * 
	 * @return The trace agent currently in use.
	 */
	public static TraceAgent getTraceAgent()
	{
		return agent;
	}

	/**
	 * JSR-163 preMain Agent entry method
	 * 
	 * @param options
	 * @param instrumentation
	 */
	public static void premain(String options, Instrumentation instrumentation)
	{
		StaticAgentConfiguration staticConfig = StaticAgentConfiguration.parseOptionString(options);
		if (staticConfig == null)
		{
			throw new RuntimeException("Bad agent configuration, tracing cannot run.");
		}

		// setup agent
		agent = new DefaultTraceAgent(staticConfig);

		try
		{
			// attempt to connect
			boolean connected = agent.connect(staticConfig.getConnectTimeout());

			if (!connected)
			{
				// if we didn't connect, bail out now
				ErrorHandler
						.handleError("failed to connect to HQ; continuing execution without tracing");
				return;
			}

			// don't finish configuration or exit premain until HQ tells us to
			// start
			agent.waitForStart();
		}
		catch (InterruptedException e)
		{
			ErrorHandler.handleError("interrupted in premain waiting for startup", e);
			return;
		}

		agent.prepare();
		Trace.setTraceDataCollector(agent.getDataCollector());

		// set up instrumentation after configuration is finalized
		RuntimeAgentConfigurationV1 config = agent.getConfig();

		// set up tracer instrumentation
		ClassTransformationListener ctListener = new ClassTransformationReporter(
				agent.getControlController());

		TraceClassFileTransformer transformer = new TraceClassFileTransformer(
				config.getExclusions(), config.getInclusions(), ctListener);
		instrumentation.addTransformer(transformer, true);
	}
}
