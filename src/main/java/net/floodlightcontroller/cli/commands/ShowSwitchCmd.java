package net.floodlightcontroller.cli.commands;

/*
* Copyright (c) 2013, California Institute of Technology
* ALL RIGHTS RESERVED.
* Based on Government Sponsored Research DE-SC0007346
* Author Michael Bredel <michael.bredel@cern.ch>
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*     http://www.apache.org/licenses/LICENSE-2.0
* 
* THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
* "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
* LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
* A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
* HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
* INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
* BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
* OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
* AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
* LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
* WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
* POSSIBILITY OF SUCH DAMAGE.
* 
* Neither the name of the California Institute of Technology
* (Caltech) nor the names of its contributors may be used to endorse
* or promote products derived from this software without specific prior
* written permission.
*/

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.cli.utils.Utils;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.flowcache.IFlowQueryHandler;
import net.floodlightcontroller.multipath.IStatisticsCollectorService;

import org.openflow.protocol.OFPort;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.util.HexString;

import org.restlet.resource.ClientResource;
import org.restlet.resource.ResourceException;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;


/**
 * The "show switch" command shows information about switches
 * that are connected to Floodlight.
 * 
 * The "show switch" command uses the Floodlight REST API
 * to retrieve the needed information.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ShowSwitchCmd implements ICommand, IFlowQueryHandler {
	/** */
	private IFloodlightProviderService floodlightProvider;
	/** */
	private IFlowCacheService flowCache;
	/** */
	private IStatisticsCollectorService statisticsCollector;
	/** The command string. */
	private String commandString = "show switch";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	/** PortId -> FlowCount*/
	private Map<Integer, Integer> portFlowCount = new HashMap<Integer, Integer>();
	
	/**
	 * Compares ports by port numbers.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	private class PortComparator implements Comparator<OFSwitchPort> {
		@Override
		public int compare(OFSwitchPort arg0, OFSwitchPort arg1) {
			if (arg0.getPortNumber() > arg1.getPortNumber())
				return 1;
			if (arg0.getPortNumber() < arg1.getPortNumber())
				return -1;
			
			return 0;
		}
		
	}
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ShowSwitchCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.flowCache = context.getServiceImpl(IFlowCacheService.class);
		this.statisticsCollector = context.getServiceImpl(IStatisticsCollectorService.class);
	}

	@Override
	public String getCommandString() {
		return commandString;
	}
	
	@Override
	public String getArguments() {
		return arguments;
	}

	@Override
	public String getHelpText() {
		return help;
	}
	
	@Override
	public Collection<Completer> getCompleter() {
		return null;
	}

	@Override
	public synchronized String execute(IConsole console, String arguments) {
		/* The Restlet client resource, accessed using the REST API. */
		ClientResource cr = new ClientResource("http://localhost:8080/wm/core/controller/switches/json");
		/* The resulting string. */
		String result = "";
		
		// If no argument is given
		if (arguments.length() == 0 || arguments.trim().equalsIgnoreCase("all"))
			return this.execute(console, cr);
		
		try {
			Long switchID = this.parseSwitchId(arguments);
			IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(switchID);
			result = handleSwitchDetails(iofSwitch);
		} catch (Exception e) {
			System.out.println("Switch not found: ");
			e.printStackTrace();
		}
		
		// Return string.
		return result;
	}
	
	/**
	 * Executes a command without any arguments.
	 * 
	 * @param console The console where the command was initialized.
	 * @param clientResource The client resource retrieved by using the REST API.
	 * @return A string that might be returned by the command execution.
	 */
	public String execute(IConsole console, ClientResource clientResource) {
		/* A List of JSON data objects retrieved by using the REST API. */
        List<Map<String,Object>> jsonData = new ArrayList<Map<String,Object>>();
		/* The resulting string. */
		String result = "";
		
		try {	
			jsonData = this.parseJson(clientResource.get().getText());			
			result = this.jsonToTableString(jsonData);
		} catch (ResourceException e) {
			System.out.println("Resource not found");
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Parses a JSON string and decomposes all JSON arrays and objects. Stores the
	 * resulting strings in a nested Map of string objects.
	 * 
	 * @param jsonString
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String,Object>> parseJson(String jsonString) throws IOException {
		/* The Jackson JSON parser. */
        JsonParser jp;
        /* The Jackson JSON factory. */
        JsonFactory f = new JsonFactory();
        /* The Jackson object mapper. */
        ObjectMapper mapper = new ObjectMapper();
        /* A list of JSON data objects retrieved by using the REST API. */
        List<Map<String,Object>> jsonData = new ArrayList<Map<String,Object>>();
        
        try {
            jp = f.createJsonParser(jsonString);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }
       
        // Move to the first object in the array.
        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_ARRAY) {
        	throw new IOException("Expected START_ARRAY instead of " + jp.getCurrentToken());
        }
        
        // Retrieve the information from JSON
        while (jp.nextToken() == JsonToken.START_OBJECT) {
        	jsonData.add(mapper.readValue(jp, Map.class));
        }
        
        // Close the JSON parser.
        jp.close();
        
        // Return.
        return jsonData;
	}
	
	/**
	 * Creates a string table and returns a formated string that
	 * shows the device information as a table.
	 * 
	 * @param jsonData A map of nested JSON data strings.
	 * @return A formated string that shows the device information as a table.
	 * @throws IOException
	 */
	private String jsonToTableString(List<Map<String,Object>> jsonData) throws IOException {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("Switch DPID");
        header.add("Switch Alias");
        header.add("Active");
        header.add("Core Switch");
        header.add("Last Connect Time");
        header.add("IP Address");
        header.add("Port");
        header.add("Controller ID");
        header.add("Max Packets");
        header.add("Max Tables");
        
        // Add header to string table.
        stringTable.setHeader(header);
        
        // Generate table entries and add them to string table.
		for (Map<String, Object> entry : jsonData) {
	        List<String> row = new LinkedList<String>();
			row.add((String)entry.get("dpid"));
			row.add("");
			row.add("");
			row.add("");
			row.add(this.parseDate(entry.get("connectedSince").toString()));
			row.add(this.parseIpAddress(entry.get("inetAddress").toString()));
			row.add(this.parsePort(entry.get("inetAddress").toString()));
			row.add("");
			row.add("");
			row.add("");
			try {
				stringTable.addRow(row);
			} catch (IndexOutOfBoundsException e){
				e.printStackTrace();
			}
		}

		// Return.
        return stringTable.toString();
	}
	
	/**
	 * 
	 * @param iofSwitch
	 */
	private String handleSwitchDetails(IOFSwitch iofSwitch) {
		/* */
		String result;
		/* */
		List<FlowCacheQuery> flowCacheQueryList = new ArrayList<FlowCacheQuery>();
		
		if (iofSwitch.getEnabledPorts() == null)
			return "Switch has no ports enabled";
		
		for ( OFSwitchPort port : iofSwitch.getEnabledPorts()) {
			FlowCacheQuery fcq = new FlowCacheQuery(this, IFlowCacheService.DEFAULT_DB_NAME, "showswitchcmd", null, iofSwitch.getId())
				.setOutPort(port.getPortNumber());
			flowCacheQueryList.add(fcq);
		}

		List<Future<FlowCacheQueryResp>> futureList = this.flowCache.queryDB(flowCacheQueryList);
		
		while (!futureList.isEmpty()) {
			Iterator<Future<FlowCacheQueryResp>> iter = futureList.iterator();
			while (iter.hasNext()) {
				Future<FlowCacheQueryResp> future = iter.next();
				try {
					FlowCacheQueryResp fcqr = future.get(1, TimeUnit.SECONDS);
					if (fcqr != null && fcqr.queryObj != null) {
						this.portFlowCount.put(fcqr.queryObj.outPort, fcqr.flowCacheObjList.size());
					}
					iter.remove();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				} catch (TimeoutException e) {
					iter.remove();
				}
			}
		}
		
		result = this.switchToDetailString(iofSwitch, portFlowCount);
		
		this.portFlowCount.clear();
		
		return result;
		
	}
	
	/**
	 * 
	 * @param iofSwitch
	 * @return
	 */
	private String switchToDetailString(IOFSwitch iofSwitch, Map<Integer, Integer> portFlowCount) {
		StringBuilder sb = new StringBuilder();
		
		sb.append(iofSwitch.getStringId() + "\n");
		sb.append(Utils.parseDate(iofSwitch.getConnectedSince()) + "\n");
		sb.append(iofSwitch.getInetAddress().toString() + "\n");
		
		if (!iofSwitch.getAttributes().isEmpty() ) {
			sb.append("Switch Attributes:\n");
			for (Object attributeKey : iofSwitch.getAttributes().keySet()) {
				Object attributeValue = iofSwitch.getAttributes().get(attributeKey);
				if (!(attributeValue instanceof Collection)) {
					if (!("FastWildcards".equalsIgnoreCase((String)attributeKey))) {
						sb.append("  " + attributeKey + ": " + attributeValue + "\n");
					} else {
						sb.append("  " + attributeKey + ": " + Wildcards.of((Integer)attributeValue) + "\n");
					}
				}
			}
		}
		
		if (iofSwitch.getDescriptionStatistics() != null) {
			OFDescriptionStatistics descStats = iofSwitch.getDescriptionStatistics();
			sb.append("\nSwitch Description:\n");
			sb.append("  DataPath:     " + descStats.getDatapathDescription() + "\n");
			sb.append("  Hardware:     " + descStats.getHardwareDescription() + "\n");
			sb.append("  Software:     " + descStats.getSoftwareDescription() + "\n");
			sb.append("  Manufacturer: " + descStats.getManufacturerDescription() + "\n");
			sb.append("  SerialNumber: " + descStats.getSerialNumber() + "\n");
		}
		
		sb.append("\nPorts:\n");
		sb.append(this.portsToStringTable(iofSwitch.getId(), iofSwitch.getEnabledPorts(), portFlowCount));
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @return
	 */
	private String portsToStringTable(long switchId, Collection<OFSwitchPort> collection, Map<Integer, Integer> portFlowCount) {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        stringTable.setOffset(2);
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("Port");
        header.add("Port Status");
        header.add("Line Status");
        header.add("Capacity");
        header.add("No. of Flows");
        header.add("Bit Rate");
        header.add("Packet Rate");
        
        // Add header to string table.
        stringTable.setHeader(header);
        
        // Sort ports by port number.
        Collections.sort((List<OFSwitchPort>)collection, new PortComparator());
        
        for ( OFSwitchPort port : collection) {
        	int portId = port.getPortNumber();
        	// Only print OF ports, but neglect e.g. the control port.
        	if ( portId == (OFPort.OFPP_ALL.getValue()|OFPort.OFPP_CONTROLLER.getValue()|OFPort.OFPP_FLOOD.getValue()|OFPort.OFPP_IN_PORT.getValue()|OFPort.OFPP_LOCAL.getValue()|OFPort.OFPP_NORMAL.getValue()|OFPort.OFPP_TABLE.getValue()) ) {
        		continue;
        	}
			
        	List<String> row = new LinkedList<String>();
        	row.add(port.getName() + ": (" + portId + ")");
        	row.add(parsePortStatus(port.getOFPhysicalPort().getConfig()));
        	row.add(parseLinkStatus(port.getOFPhysicalPort().getConfig()));
        	row.add(Utils.parseCapacity(port.getCurrentPortSpeed()));
        	if (portFlowCount.get(portId) != null) {
				row.add(String.valueOf(portFlowCount.get(portId)));
			} else {
				row.add("0");
			}
        	row.add(Utils.parseBitRate(statisticsCollector.getBitRate(switchId, port.getPortNumber())));
        	row.add(statisticsCollector.getPacketRate(switchId, port.getPortNumber()) + " P/s");
        	try {
				stringTable.addRow(row);
			} catch (IndexOutOfBoundsException e){
				e.printStackTrace();
			}
        }
        
        // Return string table as a string.
        return stringTable.toString();
	}
	
	/**
	 * Parses the physical port configuration for the port status.
	 * 
	 * @param portConfig The configuration value from the physical OpenFlow port.
	 * @return A string that states whether the port is up or down.
	 */
	private String parsePortStatus(int portConfig) {
		if ((OFPortConfig.OFPPC_PORT_DOWN.getValue() & portConfig) > 0)
        	return "down";
		
		return "up";
	}
	
	/**
	 * Parses the physical port configuration for the link status.
	 * 
	 * @param portConfig The configuration value from the physical OpenFlow port.
	 * @return A string that states whether the link is up or down.
	 */
	private String parseLinkStatus(int portConfig) {
		if ((OFPortState.OFPPS_LINK_DOWN.getValue() & portConfig) > 0)
        	return "down";
		
		return "up";
	}
	
	/**
	 * Parses a date and returns a formated date string in the
	 * form: "yyyy-MM-dd HH:mm:ss z", where z is the time zone.
	 * 
	 * @param dateString The date string from JSON that needs to be converted into a formated string.
	 * @return A formated date string.
	 */
	private String parseDate(String dateString) {
		SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		Date date = new Date(Long.parseLong(dateString));
		return dateformat.format(date);
	}
	
	/**
	 * Parses the JSON Internet address string and retrieves
	 * the port.
	 * 
	 * @param inetAddress The Internet address string form JSON
	 * @return A string containing the port.
	 */
	private String parsePort(String inetAddress) {
		int index = inetAddress.indexOf(":");
		return inetAddress.substring(index+1);
	}
	
	/**
	 * Parses the JSON Internet address string and retrieves
	 * the IP address.
	 * 
	 * @param inetAddress The Internet address string form JSON
	 * @return A string containing the IP address.
	 */
	private String parseIpAddress(String inetAddress) {
		int index = inetAddress.indexOf(":");
		return inetAddress.substring(1, index);
	}
	
	/**
	 * 
	 * @param switchIdString
	 * @return
	 */
	private long parseSwitchId(String switchIdString) {
		/* */
		long switchId;
		
		try {
			switchId = HexString.toLong(switchIdString);
			floodlightProvider.getSwitch(switchId);
		} catch (NumberFormatException e) {
			switchId = -1L;
		}
		
		return switchId;
	}

	@Override
	public void flowQueryRespHandler(FlowCacheQueryResp resp) {
		this.portFlowCount.put(resp.queryObj.outPort, resp.flowCacheObjList.size());
	}

}
