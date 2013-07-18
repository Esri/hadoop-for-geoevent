package com.esri.geoevent.transport.hdfs;

import com.esri.ges.core.component.ComponentException;
import com.esri.ges.transport.Transport;
import com.esri.ges.transport.TransportServiceBase;
import com.esri.ges.transport.util.XmlTransportDefinition;

public class HDFSOutboundTransportService extends TransportServiceBase
{
	public HDFSOutboundTransportService()
	{
		definition = new XmlTransportDefinition(getResourceAsStream("outboundtransport-definition.xml"));
	}

	@Override
	public Transport createTransport() throws ComponentException
	{
		return new HDFSOutboundTransport(definition);
	}
}
