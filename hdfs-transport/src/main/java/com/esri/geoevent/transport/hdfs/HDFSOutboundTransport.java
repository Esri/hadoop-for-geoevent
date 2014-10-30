/*
  Copyright 1995-2013 Esri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

  For additional information, contact:
  Environmental Systems Research Institute, Inc.
  Attn: Contracts Dept
  380 New York Street
  Redlands, California, USA 92373

  email: contracts@esri.com
 */

package com.esri.geoevent.transport.hdfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

import com.esri.ges.core.ConfigurationException;
import com.esri.ges.core.component.ComponentException;
import com.esri.ges.core.component.RunningState;
import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;
import com.esri.ges.transport.OutboundTransportBase;
import com.esri.ges.transport.TransportDefinition;

public class HDFSOutboundTransport extends OutboundTransportBase
{
	private static final BundleLogger	LOGGER												= BundleLoggerFactory.getLogger(HDFSOutboundTransport.class);

	private static final String				HOST_NAME_PROPERTY						= "outputHostName";
	private static final String				PORT_PROPERTY									= "outputPort";
	private static final String				FILE_PATH_PROPERTY						= "outputFilePath";
	private static final String				BASE_FILENAME_PROPERTY				= "outputBaseFilename";
	private static final String				FILENAME_SUFFIX_PROPERTY			= "outputFilenameSuffix";
	private static final String				MAX_EVENTS_PER_FILE_PROPERTY	= "outputMaxEventsPerFile";
	private static final String				USER_NAME_PROPERTY						= "outputUserName";

	private String										host													= "localhost";
	private int												port													= 8020;
	private String										filePath											= "/user/cloudera";
	private String										baseFilename									= "gepOutput";
	private String										filenameSuffix								= "json";
	private String										userName											= null;
	private int												maxEventsPerFile							= 10000;

	private String										errorMessage;

	private HDFSConnection						hdfsClient;

	private Charset										charset												= Charset.forName("UTF-8");
	private CharsetDecoder						decoder;

	public HDFSOutboundTransport(TransportDefinition definition) throws ComponentException
	{
		super(definition);
	}

	protected void readProperties() throws ConfigurationException
	{
		if (hasProperty(HOST_NAME_PROPERTY))
			host = getProperty(HOST_NAME_PROPERTY).getValueAsString();
		else
			host = "localhost";

		if (hasProperty(PORT_PROPERTY))
			port = ((Integer) getProperty(PORT_PROPERTY).getValue());
		else
			port = 27017;

		if (hasProperty(FILE_PATH_PROPERTY))
		{
			filePath = getProperty(FILE_PATH_PROPERTY).getValueAsString();
			while (filePath.length() > 1 && filePath.endsWith("/"))
				filePath = filePath.substring(0, filePath.length() - 1);
		}
		else
			filePath = "/user/cloudera";

		if (hasProperty(BASE_FILENAME_PROPERTY))
			baseFilename = getProperty(BASE_FILENAME_PROPERTY).getValueAsString();
		else
			baseFilename = "gepOutput";

		if (hasProperty(FILENAME_SUFFIX_PROPERTY))
			filenameSuffix = getProperty(FILENAME_SUFFIX_PROPERTY).getValueAsString();
		else
			filenameSuffix = "json";

		if (hasProperty(USER_NAME_PROPERTY))
			userName = getProperty(USER_NAME_PROPERTY).getValueAsString();
		else
			userName = null;

		if (hasProperty(MAX_EVENTS_PER_FILE_PROPERTY))
			maxEventsPerFile = ((Integer) getProperty(MAX_EVENTS_PER_FILE_PROPERTY).getValue());
		else
			maxEventsPerFile = 10000;

	}

	private void applyProperties() throws IOException
	{
		hdfsClient = new HDFSConnection(host, port, filePath, baseFilename, filenameSuffix, userName, maxEventsPerFile);
	}

	@Override
	public void afterPropertiesSet()
	{
		try
		{
			readProperties();
			if (getRunningState() == RunningState.STARTED)
			{
				cleanup();
				applyProperties();
			}
		}
		catch (Exception error)
		{
			errorMessage = error.getMessage();
			LOGGER.error(errorMessage, error);
			setRunningState(RunningState.ERROR);
		}
	}

	@Override
	public void receive(ByteBuffer buffer, String channelId)
	{
		if (this.getRunningState() == RunningState.STARTED)
		{
			try
			{
				String json = convertToString(buffer);
				hdfsClient.send(json);
			}
			catch (Exception error)
			{
				LOGGER.error("RECEIVE_ERROR", error.getMessage());
				LOGGER.info(error.getMessage(), error);
			}
		}
	}

	private String convertToString(ByteBuffer buffer)
	{
		if (decoder == null)
			decoder = charset.newDecoder();
		try
		{
			CharBuffer charBuffer = decoder.decode(buffer);
			String decodedBuffer = charBuffer.toString();
			return decodedBuffer;
		}
		catch (CharacterCodingException error)
		{
			LOGGER.error("DECODE_ERROR", error.getMessage());
			LOGGER.info(error.getMessage(), error);
			buffer.clear();
			return null;
		}
	}

	private void cleanup()
	{
		errorMessage = "";
		if (hdfsClient != null)
		{
			hdfsClient.close();
			hdfsClient = null;
		}
	}

	@Override
	public synchronized void start()
	{
		if (isRunning())
			return;
		try
		{
			this.setRunningState(RunningState.STARTING);
			applyProperties();
			this.setRunningState(RunningState.STARTED);
		}
		catch (IOException error)
		{
			LOGGER.error("START_ERROR", error.getMessage());
			LOGGER.info(error.getMessage(), error);
			errorMessage = error.getMessage();
			this.setRunningState(RunningState.ERROR);
		}
	}

	@Override
	public synchronized void stop()
	{
		this.setRunningState(RunningState.STOPPING);
		cleanup();
		this.setRunningState(RunningState.STOPPED);
	}

	@Override
	public String getStatusDetails()
	{
		return errorMessage;
	}
}
