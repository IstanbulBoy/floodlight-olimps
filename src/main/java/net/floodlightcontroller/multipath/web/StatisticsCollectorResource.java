package net.floodlightcontroller.multipath.web;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.multipath.IStatisticsCollectorService;
import net.floodlightcontroller.multipath.StatisticEntry;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Path;

import org.openflow.protocol.OFMatch;
import org.restlet.resource.Get;
import org.restlet.resource.ResourceException;
import org.restlet.resource.ServerResource;

/**
 * The StatisticsCollectorResource provides the information for the
 * StatisticsColloector REST API. It allows for querying port and flow
 * statistics.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class StatisticsCollectorResource extends ServerResource {
	/** The Floodlight provider service. */
	protected IFloodlightProviderService floodlightProvider;
	/** The path finder service. */
	protected IPathFinderService pathFinderService;
	/** The flow cache service. */
	protected IFlowCacheService flowCacheService;
	/** The statistics collector service. */
	protected IStatisticsCollectorService statisticsCollectorService;
	
	/**
	 * Contains the statistics of a given switch port.
	 */
	protected class PortStatistics {
		/** The switch Id as a hex string. */
		protected String switchId;
		/** The switch alias. */
		protected String alias;
		/** The port Id. */
		protected String portId;
		/** The port speed. */
		protected String portSpeed;
		/** The current packet rate on that port. */
		protected String packetRate;
		/** The current bit rate [in Mbps] on that port. */
		protected String bitRate;
		/** The current number of flows on that port. */
		protected String flowCount;
		
		/** Getter for the switch Id. */
		public String getSwitchId() {
			return switchId;
		}
		
		/** Getter for the switch alias. */
		public String getAlias() {
			return alias;
		}
		
		/** Getter for the port Id. */
		public String getPortId() {
			return portId;
		}
		
		/** Getter for the port speed. */
		public String getPortSpeed() {
			return portSpeed;
		}
		
		/** Getter for the current packet rate at a port. */
		public String getPacketRate() {
			return packetRate;
		}
		
		/** Getter for the current bit rate at a port [in Mbps]. */
		public String getBitRate() {
			return bitRate;
		}
		
		/** Getter for the current number of flow at a port. */
		public String getFlowCount() {
			return flowCount;
		}
		
	}
	
	/**
	 * Contains the statistics of a given flow that traverses
	 * the network.
	 */
	protected class FlowStatistics {
		/** The source IP address of that flow. */
		protected String srcIp;
		/** The destination IP Address of that flow. */
		protected String dstIp;
		/** The source port of that flow. */
		protected String srcPort;
		/** The destination port of that flow. */
		protected String dstPort;
		/** The path, i.e. a list of switches (their aliases) on the path. */
		protected String[] path;
		/** The path capacity [in Mbps]. */
		protected String capacity;
		/** The current packet rate of that flow. */
		protected String packetRate;
		/** The current bit rate [in Mbps] of that flow. */
		protected String bitRate;
		
		/** Getter for the source IP address of a flow. */
		public String getSrcIp() {
			return srcIp;
		}
		
		/** Getter for the destination IP address of a flow. */
		public String getDstIp() {
			return dstIp;
		}
		
		/** Getter for the source port of a flow. */
		public String getSrcPort() {
			return srcPort;
		}
		
		/** Getter for the destination port of a flow. */
		public String getDstPort() {
			return dstPort;
		}
		
		/** Getter for the path, i.e. a list of all switches (their aliases) on a path. */
		public String[] getPath() {
			return path;
		}
		
		/** Getter for the path capacity [in Mbps]. */
		public String getCapacity() {
			return capacity;
		}
		
		/** Getter for the current packet rate of a flow. */
		public String getPacketRate() {
			return packetRate;
		}
		
		/** Getter for the current bit rate of a flow [in Mbps]. */
		public String getBitRate() {
			return bitRate;
		}
	}
	
	@Override
	public void doInit() throws ResourceException {
	    super.doInit();
		pathFinderService = (IPathFinderService) getContext().getAttributes().get(IPathFinderService.class.getCanonicalName());
		flowCacheService = (IFlowCacheService) getContext().getAttributes().get(IFlowCacheService.class.getCanonicalName());
		statisticsCollectorService = (IStatisticsCollectorService) getContext().getAttributes().get(IStatisticsCollectorService.class.getCanonicalName());
		floodlightProvider = (IFloodlightProviderService) getContext().getAttributes().get(IFloodlightProviderService.class.getCanonicalName());
	}

	@Get("json")
    public Object retrieve() {
		/* The request attribute: "flows" or "ports" */
		String op = (String) getRequestAttributes().get("op");
		
		// Return all known flow statistics.
		if (op.equalsIgnoreCase("flows")) {
			return getFlowStatistics();
		}
		
		// Return all known port statistics.
		if (op.equalsIgnoreCase("ports")) {
			return gePortStatistics();
		}
		
		// no known options found
		return "{\"status\" : \"failure\", \"details\" : \"invalid operation\"}";
    }
	
	/**
	 * Creates a list of port statistics objects.
	 * 
	 * @return <b>List of PortStatistics</b> A list of port statistics objects.
	 */
	private List<PortStatistics> gePortStatistics() {
		/* The resulting list of port statistic objects. */
		ArrayList<PortStatistics> portStats = new ArrayList<PortStatistics>();
		
		for (IOFSwitch iofSwitch : this.floodlightProvider.getAllSwitchMap().values()) {
			long switchId = iofSwitch.getId();
			for (OFSwitchPort ofsPort : iofSwitch.getPorts()) {
				int port = ofsPort.getPortNumber();
				
				if (port <= 0)
					continue;
				
				PortStatistics ps = new PortStatistics();
				ps.switchId   = iofSwitch.getStringId();
				ps.alias      = (String) iofSwitch.getAttribute("alias");
				//ps.portId     = String.valueOf(port);
				//ps.portId     = String.valueOf(OFSwitchPort.physicalPortIdOf(port));
				ps.portId     = OFSwitchPort.stringOf(port);
				ps.portSpeed  = String.valueOf(ofsPort.getCurrentPortSpeed());
				ps.bitRate    = String.valueOf(this.statisticsCollectorService.getBitRate(switchId, port) / 1000 / 1000);
				ps.packetRate = String.valueOf(this.statisticsCollectorService.getPacketRate(switchId, port));
				ps.flowCount  = String.valueOf(this.statisticsCollectorService.getFlowCount(switchId, port));
				portStats.add(ps);
			}
		}
		
		return portStats;
	}
	
	/**
	 * Creates a list of flow statistics objects.
	 * 
	 * @return <b>List of FlowStatistics</b> A list of flow statistics objects.
	 */
	private List<FlowStatistics> getFlowStatistics() {
		/* A list of flow statistics objects. */
		ArrayList<FlowStatistics> flowStats = new ArrayList<FlowStatistics>();
		
		for (Path path: this.pathFinderService.getPaths()) {
			Set<FlowCacheObj> flowCacheObjs = queryFlows(path);
			
			if (flowCacheObjs == null)
				continue;
	
			for (FlowCacheObj fco : flowCacheObjs) {
				StatisticEntry statsEntry = (StatisticEntry) fco.getAttribute(FlowCacheObj.Attribute.STATISTIC);
				FlowStatistics fs = new FlowStatistics();
				fs.srcIp    = IPv4.fromIPv4Address(fco.getMatch().getNetworkSource());
				fs.dstIp    = IPv4.fromIPv4Address(fco.getMatch().getNetworkDestination());
				fs.srcPort  = String.valueOf((int) fco.getMatch().getTransportSource() & 0xffff);
				fs.dstPort  = String.valueOf((int) fco.getMatch().getTransportDestination() & 0xffff);
				fs.path     = getPath(path);
				fs.capacity = String.valueOf(path.getCapacity());
				if (statsEntry != null) {
					fs.bitRate    = String.valueOf(statsEntry.getByteRate() * 8 / 1000 / 1000);
					fs.packetRate = String.valueOf(statsEntry.getPacketRate());
				} else {
					fs.bitRate    = "0";
					fs.packetRate = "0";
				}
				flowStats.add(fs);
			}
			
		}
		
		return flowStats;
	}
	
	/**
	 * Creates an array of switch names (IDs or aliases) that are
	 * part of an path.
	 * 
	 * @param path The path to get the switches from.
	 * @return <b>String[]</b> An array containing the names (IDs or aliases) of the switches in a path.
	 */
	private String[] getPath(Path path) {
		/* A switch in the path. */
		IOFSwitch iofSwitch = null;
		/* The switch alias. */
		String switchAlias = null;
		/* A List containing the names (IDs or aliases) of the switches in a path. */
		ArrayList<String> pathString = new ArrayList<String>();
		
		for (Link link : path.getLinks()) {
			// Check the link source node.
			iofSwitch = this.floodlightProvider.getSwitch(link.getSrc());
			switchAlias = (String) iofSwitch.getAttribute("alias");
			if (switchAlias == null)
				switchAlias = iofSwitch.getStringId();
			
			if (!pathString.contains(switchAlias))
				pathString.add(switchAlias);
			switchAlias = null;
			
			// Check the link destination node.
			iofSwitch = this.floodlightProvider.getSwitch(link.getDst());
			switchAlias = (String) iofSwitch.getAttribute("alias");
			if (switchAlias == null)
				switchAlias = iofSwitch.getStringId();
			
			if (!pathString.contains(switchAlias))
				pathString.add(switchAlias);
			switchAlias = null;
		}
		
		return pathString.toArray(new String[pathString.size()]);
	}
	
	/**
	 * Creates a set of flow cache objects that contain information
	 * of all the flows on a path.
	 * 
	 * @param path The path to get the flows from.
	 * @return <b>Set of FlowCacheObj</b> A set of flow cache objects containing information of the flows on a path.
	 */
	private Set<FlowCacheObj> queryFlows(Path path) {
		/* A set of flow cache objects queried from the flow cache. */
	    Set<FlowCacheObj> flowCacheObjs = new HashSet<FlowCacheObj>();
	    
        // Query flows.
        FlowCacheQuery fcq;
        List<FlowCacheQuery> flowCacheQueryList = new ArrayList<FlowCacheQuery>();
        for ( Link link : path.getLinks()) {
			fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, "StatisticsCollectorResource", null, link.getSrc())
				.setPathId(path.getId());
			flowCacheQueryList.add(fcq);
			fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, "StatisticsCollectorResource", null, link.getDst())
				.setPathId(path.getId());
			flowCacheQueryList.add(fcq);
		}
        List<Future<FlowCacheQueryResp>> futureList = this.flowCacheService.queryDB(flowCacheQueryList);
        
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
				} catch (Exception e) {
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
	
}
