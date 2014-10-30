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
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.io.IOUtils;

import com.esri.ges.framework.i18n.BundleLogger;
import com.esri.ges.framework.i18n.BundleLoggerFactory;

public class HDFSConnection
{
	private String										filenameSuffix;
	private int												port;
	private String										filePath;
	private String										baseFilename;
	private String										host;

	Configuration											configuration				= null;
	Path															path								= null;
	FileSystem												fileSystem					= null;
	FSDataOutputStream								fsDataOutputStream	= null;
	private long											fileCount						= 0;
	private long											fileLength;
	private long											maxEventsPerFile;
	private URI												uri;
	private String										user;

	private static final BundleLogger	LOGGER							= BundleLoggerFactory.getLogger(HDFSConnection.class);

	public HDFSConnection(String host, int port, String filePath, String baseFilename, String filenameSuffix, String user, long maxEventsPerFile) throws IOException
	{
		this.host = host;
		this.port = port;
		this.filePath = filePath;
		this.baseFilename = baseFilename;
		this.filenameSuffix = filenameSuffix;
		this.maxEventsPerFile = maxEventsPerFile;
		this.user = user;

		configuration = new Configuration(false);
		configuration.setClassLoader(this.getClass().getClassLoader());
		configuration.set("fs.hdfs.impl", DistributedFileSystem.class.getCanonicalName());

		buildNewPath();
	}

	public static void main(final String[] args) throws IOException
	{
		String host = "localhost";
		int port = 8020;
		if (args.length >= 1)
			host = args[0];
		if (args.length >= 2)
			port = Integer.parseInt(args[1]);
		LOGGER.debug("Connecting to " + host + ":" + port);
		HDFSConnection conn = new HDFSConnection(host, port, "/user/cloudera", "gepOutput", "json", "cloudera", 1000000);
		conn.send("{\"hello\":1234}\n");
		conn.close();
	}

	public void send(String string) throws IOException
	{
		checkFileSystemConnection();
		fsDataOutputStream.writeBytes(string);
		fsDataOutputStream.flush();
		fileLength++;
	}

	private void checkFileSystemConnection() throws IOException
	{
		if (fsDataOutputStream == null || fileLength >= maxEventsPerFile)
		{
			close();
			fileCount++;
			fileLength = 0;
			buildNewPath();
			while (fileSystem.isFile(path))
			{
				fileCount++;
				buildNewPath();
			}
			fsDataOutputStream = fileSystem.create(path);
		}
	}

	private void buildNewPath() throws IOException
	{
		String uriString = "hdfs://" + host + ":" + port + "/" + filePath + "/" + baseFilename + fileCount + "." + filenameSuffix;
		try
		{
			uri = new URI(uriString);
		}
		catch (URISyntaxException e)
		{
			throw new IOException(LOGGER.translate("INVALID_URL", uriString));
		}
		path = new Path(uriString);
		try
		{
			if (user != null && user.trim().length() > 0)
				fileSystem = FileSystem.get(uri, configuration, user);
			else
				fileSystem = FileSystem.get(uri, configuration);
		}
		catch (InterruptedException e)
		{
			throw new IOException(LOGGER.translate("CONNECTION_INTERRUPT_ERROR", uriString));
		}
	}

	public void close()
	{
		if (fsDataOutputStream != null)
			IOUtils.closeStream(fsDataOutputStream);
		fsDataOutputStream = null;
	}

}
