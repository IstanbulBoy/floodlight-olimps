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
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openflow.protocol.OFMatch;
import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.cli.utils.Utils;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.flowcache.IFlowQueryHandler;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.multipath.IStatisticsCollectorService;
import net.floodlightcontroller.routing.EndPoints;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Path;


/**
 * The "show host" command shows information about hosts
 * that are connected to switches controlled by Floodlight.
 * 
 * The "show host" command uses the Floodlight.context service
 * to directly address the corresponding Floodlight module to
 * retrieve the needed information.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ShowPathCmd implements ICommand, IFlowQueryHandler {
	/** Required Module: Floodlight Provider Service. */
	private IFloodlightProviderService floodlightProvider;
	/** Required Module: */
	private IPathFinderService pathFinder;
	/** Required Module: */
	private IPathCacheService pathCache;
	/** Required Module: */
	private IFlowCacheService flowCache;
	/** Required Module: */
	private IStatisticsCollectorService statisticsCollector;
	/** The command string. */
	private String commandString = "show path";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ShowPathCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.flowCache = context.getServiceImpl(IFlowCacheService.class);
		this.pathFinder = context.getServiceImpl(IPathFinderService.class);
		this.pathCache = context.getServiceImpl(IPathCacheService.class);
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
	public String execute(IConsole console, String arguments) {
		/* The resulting string. */
		StringBuilder result = new StringBuilder();
		/* Argument elements. */
		String[] argumentElements = arguments.trim().split(" ");
		if (argumentElements.length == 1) {
			if (argumentElements[0] == "") {
				argumentElements = new String[0];
			}
		}
		
		// Switch by number of arguments.
		switch (argumentElements.length) {
			case 0:
				if (!pathCache.isEmpty()) {
					return this.endPointsToTableString(pathCache.getAllEndPoints());
				}
				break;
			case 1:
				// See if we have a path ID or a switch ID
				long argument = Utils.parseSwitchId(argumentElements[0]);
				if (argument > 0 && floodlightProvider.getAllSwitchMap().containsKey(argument)) {
					// If switch ID -> print all route IDs that contain that switch
					Set<EndPoints> endPointsSet = new HashSet<EndPoints>();
					endPointsSet.addAll(pathCache.getAllEndPoints());
					Iterator<EndPoints> iterator = endPointsSet.iterator();
					while (iterator.hasNext()) {
						EndPoints endPoints = iterator.next();
					    if (endPoints.getSrc() != argument && endPoints.getDst() != argument) {
					        iterator.remove();
					    }
					}
					return this.endPointsToTableString(pathCache.getAllEndPoints());
				} else {
					// If path ID -> print path details, i.e. links
					try {
						argument = Long.parseLong(argumentElements[0]);
						return this.pathToString(pathCache.getPath((int) argument));
					} catch (NumberFormatException e) {
						return "Usage: show path [<SRC_SWITCH> <DST_SWITCH> <PATH_ID]>";
					} catch (IOException e) {
						e.printStackTrace();
					}

				}
				break;
			case 2:
				// Print all paths of the given source-destination combination.
				long srcSwitchId = Utils.parseSwitchId(argumentElements[0]);
				long dstSwitchId = Utils.parseSwitchId(argumentElements[1]);
		        // Trigger path calculation.
		        pathFinder.hasPath(srcSwitchId, dstSwitchId);
				try {
					return this.pathToString(srcSwitchId, dstSwitchId);
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 3:
				// Print path details, i.e. links
				long pathNumber = Long.parseLong(argumentElements[2]);
				try {
					return this.pathToString(pathCache.getPath((int) pathNumber));
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			default:
				return "Usage: show path [<SRC_SWITCH> <DST_SWITCH> <PATH_ID]>";
		}
		
		// Return
		return result.toString();
	}
	
	/**
	 * 
	 * @param routeIds
	 * @return
	 */
	private String endPointsToTableString(Set<EndPoints> endPointsSet) {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("Src Switch");
        header.add("Dst Switch");
        header.add("No. of Paths");
        
        // Add header to string table.
        stringTable.setHeader(header);
        
        for (EndPoints endPoints : endPointsSet) {
        	List<String> row = new LinkedList<String>();
        	row.add(HexString.toHexString(endPoints.getSrc()));
        	row.add(HexString.toHexString(endPoints.getDst()));
        	row.add(String.valueOf(pathCache.getAllPaths(endPoints.getSrc(), endPoints.getDst()).size()));
        	stringTable.addRow(row);
        }
        
        // Return string table as a string.
        return stringTable.toString();
	}
	
	/**
	 * 
	 * @param routeId
	 * @return
	 * @throws IOException
	 */
	private String pathToString(long srcSwitchId, long dstSwitchId) throws IOException {
		/* The string builder. */
		StringBuilder sb = new StringBuilder();
		/* The string table that contains all the path information as strings. */
        StringTable stringTable = new StringTable();
        stringTable.setOffset(3);

		sb.append("Src Switch: " + HexString.toHexString(srcSwitchId) + "\n");
		sb.append("Dst Switch: " + HexString.toHexString(dstSwitchId) + "\n");
		sb.append("No. of Paths: " + (pathCache.getAllPaths(srcSwitchId, dstSwitchId) == null ? 0 : pathCache.getAllPaths(srcSwitchId, dstSwitchId).size()) + "\n");
		sb.append("Paths:" + "\n");
		
		// Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("Path Id");
        header.add("Capacity");
        header.add("No. of Links");
        header.add("No. of Flows");
        header.add("Utilization");
        // Add header to string table.
        stringTable.setHeader(header);
        // Populate table.
		Set<Path> paths = pathCache.getAllPaths(srcSwitchId, dstSwitchId);
		if (paths != null) {
			for (Path path : paths) {
			     // Query flows
				Set<FlowCacheObj> flowCacheObjs = this.queryFlows(path);				
		        // Create new table row.
				List<String> row = new LinkedList<String>();
				row.add(String.valueOf(path.getId()));
				row.add(Utils.parseCapacity(path.getCapacity()));
				row.add(String.valueOf(path.getLinks().size()));
				row.add(String.valueOf(flowCacheObjs.size()));
				row.add("unknown");
				stringTable.addRow(row);
				flowCacheObjs.clear();
			}
		}
		// Add table to string builder.
		sb.append(stringTable.toString());
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param links
	 * @return
	 * @throws IOException
	 */
	private String pathToString(Path path) throws IOException {
		/* The string builder. */
		StringBuilder sb = new StringBuilder();
		/* The string table that contains all the links information as strings. */
        StringTable linksStringTable = new StringTable();
        linksStringTable.setOffset(3);
        /* The string table that contains all the flow information as strings. */
        StringTable flowStringTable = new StringTable();
        flowStringTable.setOffset(3);
        
        if (path == null) {
        	return "Path not found";
        }
        
        sb.append("Path ID: " + path.getId() + "\n");
        sb.append("Src Switch: " + HexString.toHexString(path.getEndPoints().getSrc()) + " - " + "PORT" + "\n");
		sb.append("Dst Switch: " + HexString.toHexString(path.getEndPoints().getDst()) + " - " + "PORT" + "\n");
		sb.append("Capacity: " + Utils.parseCapacity(path.getCapacity()) + "\n");
		sb.append("Utilization: " + "unknown" + "\n");
		sb.append("Links:" + "\n");
        
        // Generate header data for links table.
        List<String> header = new LinkedList<String>();
        //header.add("Link ID");
        header.add("Source Switch");
        header.add("Source Port");
        header.add("Destination Switch");
        header.add("Destination Port");
        header.add("Capacity");
        header.add("Bit Rate");
        header.add("Packet Rate");
        // Add header to string table.
        linksStringTable.setHeader(header);
        for (Link link : path.getLinks()) {
            // Populate table.
            List<String> row = new LinkedList<String>();
        	// Get attributes
        	IOFSwitch srcSwitch = floodlightProvider.getSwitch(link.getSrc());
        	IOFSwitch dstSwitch = floodlightProvider.getSwitch(link.getDst());
        	OFSwitchPort srcPort = srcSwitch.getPort(link.getSrcPort());
        	OFSwitchPort dstPort = dstSwitch.getPort(link.getDstPort());
        	// Create StringTable
        	row.add(HexString.toHexString(link.getSrc()));
			row.add(srcPort.getName() + " (" + String.valueOf(link.getSrcPort()) + ")");
			row.add(HexString.toHexString(link.getDst()));
			row.add(dstPort.getName() + " (" + String.valueOf(link.getDstPort()) + ")");
			row.add(Utils.parseCapacity(path.getCapacity()));
        	row.add(Utils.parseBitRate(statisticsCollector.getBitRate(link.getSrc(), link.getSrcPort())));
        	row.add(statisticsCollector.getPacketRate(link.getSrc(), link.getSrcPort()) + " P/s");
			linksStringTable.addRow(row);
        }
        
        sb.append(linksStringTable.toString());

        // Query flows
        Set<FlowCacheObj> flowCacheObjs = this.queryFlows(path);
        sb.append("Flows:" + "\n");
        // Generate header data for flow table.
        header = new LinkedList<String>();
        //header.add("Link ID");
        header.add("Cookie");
        header.add("Priority");
        header.add("Match");
        header.add("Duration");
        header.add("Status");
        // Add header to string table.
        flowStringTable.setHeader(header);
        // Populate table.
        for (FlowCacheObj fco : flowCacheObjs) {
        	// Create StringTable
        	List<String> row = new LinkedList<String>();
        	row.add("0x" + Long.toHexString(fco.getCookie()));
        	row.add(String.valueOf(fco.getPriority()));
        	row.add(parseMatch(fco.getMatch()));
        	row.add(this.parseDate(System.currentTimeMillis() - fco.getTimestamp()));
        	row.add(fco.getStatus().toString());
        	flowStringTable.addRow(row);
        }
        sb.append(flowStringTable.toString());
        flowCacheObjs.clear();
        
		// Return as a string.
        return sb.toString();
	}
	
	/**
	 * 
	 * @param path
	 */
	private Set<FlowCacheObj> queryFlows(Path path) {
		/* A set of flow cache objects queried from the flow cache. */
	    Set<FlowCacheObj> flowCacheObjs = new HashSet<FlowCacheObj>();
	    
        // Query flows.
        FlowCacheQuery fcq;
        List<FlowCacheQuery> flowCacheQueryList = new ArrayList<FlowCacheQuery>();
        for ( Link link : path.getLinks()) {
			fcq = new FlowCacheQuery(this, IFlowCacheService.DEFAULT_DB_NAME, "showpathcmd", null, link.getSrc())
				.setPathId(path.getId());
			flowCacheQueryList.add(fcq);
			fcq = new FlowCacheQuery(this, IFlowCacheService.DEFAULT_DB_NAME, "showpathcmd", null, link.getDst())
				.setPathId(path.getId());
			flowCacheQueryList.add(fcq);
		}
        List<Future<FlowCacheQueryResp>> futureList = this.flowCache.queryDB(flowCacheQueryList);
        
        if (futureList == null) {
        	// Error.
        	return null;
        }
        
        while (!futureList.isEmpty()) {
			Iterator<Future<FlowCacheQueryResp>> iter = futureList.iterator();
			while (iter.hasNext()) {
				Future<FlowCacheQueryResp> future = iter.next();
				try {
					FlowCacheQueryResp fcqr = future.get(10, TimeUnit.SECONDS);
					if (fcqr != null && fcqr.flowCacheObjList != null) {
						flowCacheObjs.addAll(fcqr.flowCacheObjList);
					}
					iter.remove();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				} catch (TimeoutException e) {
					iter.remove();
				}
			}
		}
        
        // Reduce flow cache objects by match.
        Set<OFMatch> matchSet = new HashSet<OFMatch>();
        for (Iterator<FlowCacheObj> iter = flowCacheObjs.iterator(); iter.hasNext();) {
        	FlowCacheObj fco = iter.next();
        	if (matchSet.contains(fco.getMatch())) {
        		iter.remove();
        	} else {
        		matchSet.add(fco.getMatch());
        	}
        }
        
        return flowCacheObjs;
	}
	
	/**
	 * 
	 * @param ofm
	 * @return
	 */
	private String parseMatch(OFMatch ofm) {
		String ofmString = ofm.toString();
		int from   = "OFMatch[".length();
		int length = ofmString.length() - "]".length();
		return ofmString.substring(from, length);
	}
	
	/**
	 * 
	 * @param date
	 * @return
	 */
	private String parseDate(long timestamp) {
		SimpleDateFormat dateformat;
		String unit = "";
		
		if (timestamp < (60 * 1000)) {
			dateformat = new SimpleDateFormat("ss");
			unit = " s";
		} else if (timestamp < (3600 * 1000)) {
			dateformat = new SimpleDateFormat("mm:ss");
			unit = " m";
		} else {
			dateformat = new SimpleDateFormat("HH:mm:ss");
			unit = " h";
		}
		Date date = new Date ();
		date.setTime(timestamp);
		return dateformat.format(date) + unit;
	}
	
	@Override
	public void flowQueryRespHandler(FlowCacheQueryResp resp) {
		// NO-OP.
	}
	
}
