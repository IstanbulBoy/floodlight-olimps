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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.appaware.AppEntry;
import net.floodlightcontroller.appaware.IAppAwareService;
import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.cli.utils.Utils;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.flowcache.IFlowQueryHandler;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.multipath.IStatisticsCollectorService;
import net.floodlightcontroller.multipath.StatisticEntry;
import net.floodlightcontroller.packet.IPv4;
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
public class ShowAppsCmd implements ICommand {
	/** Required Module: Floodlight Provider Service. */
	private IFloodlightProviderService floodlightProvider;
	/** Required Module: */
	private IFlowCacheService flowCache;
	/** */
	private IAppAwareService appAware;
	/** Required Module: */
	private IStatisticsCollectorService statisticsCollector;
	/** The command string. */
	private String commandString = "show apps";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ShowAppsCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.flowCache = context.getServiceImpl(IFlowCacheService.class);
		this.statisticsCollector = context.getServiceImpl(IStatisticsCollectorService.class);
		this.appAware = context.getServiceImpl(IAppAwareService.class);
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
		
		Map<OFMatch, AppEntry> apps = this.appAware.getAllApplication();
		result.append(appsToTableString(apps));
		
		// Return
		return result.toString();
	}
	
	/**
	 * 
	 * @param routeIds
	 * @return
	 */
	private String appsToTableString(Map<OFMatch, AppEntry> apps) {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("Application");
        header.add("FileSize");
        header.add("Start time");
        header.add("Duration");
        //header.add("Finishing Time");
        header.add("Source IP");
        header.add("Destination IP");
        header.add("Dst Transport Port");
        header.add("Status");
        
        
        // Add header to string table.
        stringTable.setHeader(header);
        
        if (apps == null || apps.isEmpty()) {
        	return stringTable.toString();
        }
        
        for (Map.Entry<OFMatch, AppEntry> entry : apps.entrySet()) {
        	List<String> row = new LinkedList<String>();
        	row.add(entry.getValue().getName());
        	row.add(String.valueOf(entry.getValue().getFileSize()) + " MByte");
        	row.add(Utils.parseDate(entry.getValue().getStartTime()));
        	row.add((System.currentTimeMillis() - entry.getValue().getStartTime())/1000 + " sec");
        	//row.add("unknown");
        	row.add(IPv4.fromIPv4Address(entry.getKey().getNetworkSource()));
        	row.add(IPv4.fromIPv4Address(entry.getKey().getNetworkDestination()));
        	row.add(String.valueOf(entry.getKey().getTransportDestination()));
        	if (entry.getValue().isActive()) {
        		row.add("active");
        	} else {
        		row.add("");
        	}
        	stringTable.addRow(row);
        }
        
        // Return string table as a string.
        return stringTable.toString();
	}
	
	/**
	 * Calculates the finishing time, i.e. the time the link should be empty again.
	 * Takes the total number of bytes to transfer, the bytes already transfered, and 
	 * capacity of a path into account.
	 * 
	 * @param link The link we want to calculate the finishing time for.
	 * @param flowCacheObjects Information regarding the flows on this link.
	 * @return <b>int</b> virtual finishing time, i.e. the time the link should be empty again.
	 */
	private int calculateFinishingTime(ArrayList<FlowCacheObj> flowCacheObjects) {
		/* The finishing time of the link. */
		double finishingTime = 0;
		/* The application information, i.e. the file size to transfer. */
		AppEntry appEntry = null;
		
//		// Get the port/link capacity.
//		OFPhysicalPort srcPort = this.floodlightProvider.getSwitches().get(link.getSrc()).getPort(link.getSrcPort());
//		// Link capacity equals the capacity of the sending port.
//		int linkCapacity = net.olimps.floodlightcontroller.util.Utils.getPortCapacity(srcPort);
		int linkCapacity = 1000;
		
		for (FlowCacheObj fco : flowCacheObjects) {
			if (fco.isActive()) {
				// Check if we have some statistics for the flow.
				StatisticEntry statEntry = (StatisticEntry) fco.getAttribute(FlowCacheObj.Attribute.STATISTIC);
				long transferedBits = 0;
				if (statEntry != null) {
					transferedBits = statEntry.getByteCount() * 8 /1000/1000;
				}
				// Find the application information.
				appEntry = appAware.getApplication(fco.getMatch());
				if (appEntry != null && appEntry.getFileSize() > 0) {
					finishingTime += Math.max(0, (appEntry.getFileSize() * 8 - transferedBits) / linkCapacity); 
				}
			}
		}
		
		// Return positive finishing time or 0.
		return (int) ((finishingTime > 0) ? finishingTime : 0);
	}
	
}
