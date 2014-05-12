package net.floodlightcontroller.multipath;

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.Set;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.floodlightcontroller.appaware.AppEntry;
import net.floodlightcontroller.appaware.IAppAwareService;
import net.floodlightcontroller.configuration.IConfigurationListener;
import net.floodlightcontroller.configuration.IConfigurationService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.multipath.web.PathFinderWebRoutable;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.EndPoints;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.topology.Cluster;
import net.floodlightcontroller.topology.OlimpsCluster;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.IOlimpsTopologyService;
import net.floodlightcontroller.util.Utils;

/**
 * Pathfinder calculates and selects paths between source
 * and destination nodes in a network. The paths can be used
 * to install flow rules on the related switches.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class PathFinder implements IFloodlightModule, ITopologyListener, IPathFinderService, IConfigurationListener {
	/** The maximum link weight. */
    public static final int MAX_LINK_WEIGHT = 10000;
    /** The maximum path weight. */
    public static final int MAX_PATH_WEIGHT = Integer.MAX_VALUE - MAX_LINK_WEIGHT - 1;
    /** The unique name of this configuration listener. */
	public static final String CONFIGURATOR_NAME = "PathFinder";
    
	/** Logger to log ProactiveFlowPusher events. */
	protected static Logger log = LoggerFactory.getLogger(PathFinder.class);
	/** Required Module: Floodlight Provider Service. */
	protected IFloodlightProviderService floodlightProvider;
	/** Required Module: Topology Manager module. We listen to the topologyManager for changes of the topology. */
	protected IOlimpsTopologyService topologyManager;
	/** Required Module: Floodlight REST API Service. */
	protected IRestApiService restApi;
	/** Required Module: The flow cache service*/
	protected IFlowCacheService flowCache;
	/** Required Module: Stores all paths established in the topology. */
	protected IPathCacheService pathCache;
	/** Required Module: The statistics collector service. */
	protected IStatisticsCollectorService statisticsCollector;
	/** Required Module: The application aware network manager. */
	protected IAppAwareService appAware;
	/** Required Module: The configuration manager. */
    protected IConfigurationService configManager;
    /** Required Module: The multipath service. */
    protected IMultipathService multipath;
	/** Counter for round robin path selection in a map: [EndPoints->Counter]. */
	protected ConcurrentHashMap<EndPoints, Integer> pathCounter;
	/** A set of available path selectors. */
	protected Set<IPathSelector> pathSelectors;
	/** The current active path selector. */
	protected IPathSelector currentPathSelector;
	/** A set of available path calculators. */
	protected Set<IPathCalculator> pathCalculators;
	/** The current active path calculator. */
	protected IPathCalculator currentPathCalculator;
	
	/**
	 * Selects a path using the shortest path, i.e. no multipathing.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class ShortestPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder The path finder service.
		 */
		public ShortestPathSelector(IPathFinderService pathFinder) {
			this.pathFinder = pathFinder;
		}
		
		@Override
		public String getName() {
			return "shortestpathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* The number of hops on the best path. */
			int bestPathHops = Integer.MAX_VALUE;
			/* Get pre-calculated paths. */
			Set<Path> paths = pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			for (Path path : paths) {
				if (path.getLinks().size() < bestPathHops) {
					bestPath = path;
					bestPathHops = path.getLinks().size();
				}
			}
			
			return bestPath;
			
		}
	}
	
	/**
	 * Selects a path randomly.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class RandomPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 */
		public RandomPathSelector(IPathFinderService pathFinder) {
			this.pathFinder = pathFinder;
		}
		
		@Override
		public String getName() {
			return "randompathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* A Random number generator. */
			Random random = new Random();
			/* A path array to select path in round robin manner. */
			Path[] pathArray = null;
			/* Get pre-calculated paths. */
			Set<Path> paths = pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			if (paths != null && !paths.isEmpty()) {
				pathArray = (Path[])paths.toArray(new Path[paths.size()]);
			} else {
				return null;
			}
			
			// calculate the path number to install: A random number between 0 and pathArray.length-1.
			int counter = random.nextInt(pathArray.length);
			
			//select one of the paths randomly.
			if (pathArray.length > 0) {
				return pathArray[counter];
			} else {
				return null;
			}
		}
	}
	
	/**
	 * Selects a path based on the hashes of the source IP address,
	 * i.e. (hash(src_ip)) mod (no. of paths).
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class HashIpPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 */
		public HashIpPathSelector(IPathFinderService pathFinder) {
			this.pathFinder = pathFinder;
		}
		
		@Override
		public String getName() {
			return "hashippathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* A path array to select path in round robin manner. */
			Path[] pathArray = null;
			/* Get pre-calculated paths. */
			Set<Path> paths = pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			if (paths != null && !paths.isEmpty()) {
				pathArray = (Path[])paths.toArray(new Path[paths.size()]);
			} else {
				return null;
			}
			
			// calculate the path number to install: A IP-match modulo no. of paths.
			int counter = match.getNetworkSource() % paths.size();
			
			//select one of the paths in round robin manner.
			if (pathArray.length > 0) {
				return pathArray[counter];
			} else {
				return null;
			}
		}
	}
	
	/**
	 * Selects a path based on the hashes of the transport layer ports,
	 * i.e. (hash(src_port)  + hash(dst_port)) mod (no. of paths).
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class HashPortPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 */
		public HashPortPathSelector(IPathFinderService pathFinder) {
			this.pathFinder = pathFinder;
		}
		
		@Override
		public String getName() {
			return "hashportpathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* A path array to select path in round robin manner. */
			Path[] pathArray = null;
			/* Get pre-calculated paths. */
			Set<Path> paths = pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			if (paths != null && !paths.isEmpty()) {
				pathArray = (Path[])paths.toArray(new Path[paths.size()]);
			} else {
				return null;
			}
			
			// calculate the path number to install: A port-match modulo no. of paths.
			int transportSrc = match.getTransportSource() & 0xffff;
			int transportDst = match.getTransportDestination() & 0xffff;
			int counter = (transportSrc + transportDst) % paths.size();
			
			//select one of the paths in round robin manner.
			if (pathArray.length > 0) {
				return pathArray[counter];
			} else {
				return null;
			}
		}
	}
	
	/**
	 * Selects a path in round robin manner.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class RoundRobinPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		/** All paths established in the topology. */
		private IPathCacheService pathCache;
		/** Counter for round robin path selection in a map: [EndPoints->Counter]. */
		private ConcurrentHashMap<EndPoints, Integer> pathCounter;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 * @param pathCache Floodlight path cache service.
		 */
		public RoundRobinPathSelector(IPathFinderService pathFinder, IPathCacheService pathCache) {
			this.pathFinder = pathFinder;
			this.pathCache = pathCache;
			this.pathCounter = new ConcurrentHashMap<EndPoints, Integer>();
		}

		@Override
		public String getName() {
			return "roundrobinpathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* A path array to select path in round robin manner. */
			Path[] pathArray = null;
			/* A source-destination pair of a path. */
			EndPoints endPoints = null;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			if (paths != null && !paths.isEmpty()) {
				pathArray = (Path[])paths.toArray(new Path[paths.size()]);
				endPoints = pathArray[0].getEndPoints();
			} else {
				return null;
			}
			
			// calculate the path number to install: pathCounter mod numberOfPaths
			if (!pathCounter.containsKey(endPoints)) {
				pathCounter.put(endPoints, 0);
			}
			int counter = pathCounter.get(endPoints) % pathCache.getAllPaths(srcSwitchId, dstSwitchId).size();
			pathCounter.put(endPoints, (counter + 1));
			
			//select one of the paths in round robin manner.
			if (pathArray.length > 0) {
				return pathArray[counter];
			} else {
				return null;
			}
		}
	}
	
	/**
	 * Selects a path based on flow cache information. Chooses the
	 * path with the least number of flows already mapped to it.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class FlowUtilizationPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		/** Required Module: Floodlight flow cache service. */
		private IFlowCacheService flowCache;
		
		/**
		 * Constructor.
		 *
		 * @param pathFinder Floodlight path finder service.
		 * @param flowCache Floodlight flow cache service.
		 */
		public FlowUtilizationPathSelector(IPathFinderService pathFinder, IFlowCacheService flowCache) {
			this.pathFinder = pathFinder;
			this.flowCache = flowCache;
		}

		@Override
		public String getName() {
			return "flowutilizationpathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* The number of flows on the best path. */
			int bestPathFlows = Integer.MAX_VALUE;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			// If we do not have any paths (yet).
			if (paths == null) {
				return null;
			}

			for (Path path : paths) {
				int currentPathFlows = 0;
				 // Query flows
				Set<FlowCacheObj> flowCacheObjs = this.queryFlows(path);
				if (flowCacheObjs != null)
					currentPathFlows = flowCacheObjs.size();
				
				if (currentPathFlows < bestPathFlows) {
					bestPathFlows = currentPathFlows;
					bestPath = path;
				}
			}

			return (bestPath != null) ? bestPath : null;
		}
		
		/**
		 * 
		 * @param path
		 * @return
		 */
		private Set<FlowCacheObj> queryFlows(Path path) {
			/* A set of flow cache objects queried from the flow cache. */
		    Set<FlowCacheObj> flowCacheObjs = new HashSet<FlowCacheObj>();
		    
	        // Query flows.
	        FlowCacheQuery fcq;
	        List<FlowCacheQuery> flowCacheQueryList = new ArrayList<FlowCacheQuery>();
	        for ( Link link : path.getLinks()) {
				fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, "showpathcmd", null, link.getSrc())
					.setPathId(path.getId());
				flowCacheQueryList.add(fcq);
				fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, "showpathcmd", null, link.getDst())
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
	}
	
	/**
	 * Selects a path that has the highest capacity.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class CapacityPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 */
		public CapacityPathSelector(IPathFinderService pathFinder) {
			this.pathFinder = pathFinder;
		}

		@Override
		public String getName() {
			return "capacitypathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* Link capacity / flow. */
			double bestCapacity = Double.MIN_VALUE;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			// If we do not have any paths (yet).
			if (paths == null) {
				return null;
			}
			
			// Calculate path capacity.
			for (Path path : paths) {
				int pathCapacity = path.getCapacity();
				
				if (pathCapacity > bestCapacity) {
					bestCapacity = pathCapacity;
					bestPath = path;
				}
				
			}
			
			return (bestPath != null) ? bestPath : null;
		}
	}

	/**
	 * Selects a path based on the capacity and the flows already
	 * mapped to it.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class FlowUtilizationAndCapacityPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		/** Required Module: Floodlight provider Service. */
		private IFloodlightProviderService floodlightProvider;
		/** Required Module: Floodlight flow cache service. */
		private IFlowCacheService flowCache;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 * @param floodlightProvider Floodlight provider service.
		 * @param flowCache Floodlight flow cache service.
		 */
		public FlowUtilizationAndCapacityPathSelector(IPathFinderService pathFinder, IFloodlightProviderService floodlightProvider, IFlowCacheService flowCache) {
			this.pathFinder = pathFinder;
			this.floodlightProvider = floodlightProvider;
			this.flowCache = flowCache;
		}

		@Override
		public String getName() {
			return "flowutilizationandcapacitypathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* Link capacity / flow. */
			double bestCapacityToFlowRatio = Double.MIN_VALUE;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, srcSwitchId);
			
			// If we do not have any paths (yet).
			if (paths == null) {
				return null;
			}
			
			// Calculate path capacity to flow ratio. The higher the better.
			for (Path path : paths) {
				double pathCapacityToFlowRatio = Double.MAX_VALUE;
				List<Link> links = path.getLinks();
				
				// Calculate the link capacity to flow ratio. The minimal link ratio determines the path ratio.
				for (Link link : links) {
					int linkFlows = 0;
					int linkCapacity = 0;
					
					// Query the flow cache to collect the flow information in the future.
					FlowCacheQuery fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, "pathfinder", null, link.getSrc())
						.setOutPort(link.getSrcPort());
					Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
					
					//OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).toOFPhysicalPort();
					OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).getOFPhysicalPort();
					// Link capacity equals the capacity of the sending port.
					linkCapacity = Utils.getPortCapacity(srcPort);
					
					// Collect the future.
					try {
						FlowCacheQueryResp fcqr = future.get(1, TimeUnit.SECONDS);
						if (fcqr != null) {
							linkFlows = fcqr.flowCacheObjList.size();
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					} catch (TimeoutException e) {
						e.printStackTrace();
					}
					
					if (linkFlows != 0) {
						if (linkCapacity / linkFlows < pathCapacityToFlowRatio) {
							pathCapacityToFlowRatio = linkCapacity / linkFlows;
						}
					} else {
						if (linkCapacity < pathCapacityToFlowRatio) {
							// If link is empty us it in any case.
							//pathCapacityToFlowRatio = Double.MAX_VALUE;
							// If link is empty but its capacity is below the fair share of a better link, don't use it
							pathCapacityToFlowRatio = linkCapacity;
						}
					}
						
				}
				
				if (pathCapacityToFlowRatio > bestCapacityToFlowRatio) {
					bestCapacityToFlowRatio = pathCapacityToFlowRatio;
					bestPath = path;
				}

			}
			
			return (bestPath != null) ? bestPath : null;
		}
	}
	
	/**
	 * Selects a path based on statistics cache information. Chooses the
	 * path with the highest available bandwidth.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class AvailableBandwidthPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		/** Required Module: Floodlight provider Service. */
		private IFloodlightProviderService floodlightProvider;
		/** Required Module: Floodlight statistic collector. */
		private IStatisticsCollectorService statisticsCollector;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 * @param floodlightProvider Floodlight provider service.
		 * @param statisticsCollector Floodlight statistics collector.
		 */
		public AvailableBandwidthPathSelector(IPathFinderService pathFinder, IFloodlightProviderService floodlightProvider, IStatisticsCollectorService statisticsCollector) {
			this.pathFinder = pathFinder;
			this.floodlightProvider = floodlightProvider;
			this.statisticsCollector = statisticsCollector;
		}

		@Override
		public String getName() {
			return "availablebandwidthpathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* Best available bandwidth, i.e. capacity - pathBitRate. */
			long bestAvailableBandwidth = 0;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			// If we do not have any paths (yet).
			if (paths == null) {
				return null;
			}
			
			// Calculate path available bandwidth. The higher the better.
			for (Path path : paths) {
				long pathAvailableBandwidth = Long.MAX_VALUE;
				List<Link> links = path.getLinks();
				
				// Calculate the link capacity to flow ratio. The minimal link ratio determines the path ratio.
				for (Link link : links) {
					long linkBitRate = 0;
					long linkCapacity = 0;
					
					//OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).toOFPhysicalPort();
					OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).getOFPhysicalPort();
					// Link capacity equals the capacity of the sending port. In [bit/s].
					linkCapacity = (long) Utils.getPortCapacity(srcPort) * 1000 * 1000;
					
					// Query the statistic collector to get the port statistics.
					linkBitRate = this.statisticsCollector.getBitRate(link.getSrc(), OFSwitchPort.physicalPortIdOf(link.getSrcPort()));
					
					// Get the paths available bandwidth.
					if (linkCapacity - linkBitRate < pathAvailableBandwidth) {
						pathAvailableBandwidth = linkCapacity - linkBitRate;
						pathAvailableBandwidth = (pathAvailableBandwidth < 0) ? 0 : pathAvailableBandwidth;
					}
						
				}			
				
				if (pathAvailableBandwidth > bestAvailableBandwidth) {
					bestAvailableBandwidth = pathAvailableBandwidth;
					bestPath = path;
				}

			}
			
			return (bestPath != null) ? bestPath : null;
		}
	}
	
	/**
	 * Selects a path based on a complex strategy that combines the
	 * number of flows on a path and the path capacity.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class StrategyPathSelector implements IPathSelector {
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		/** Required Module: Floodlight provider Service. */
		private IFloodlightProviderService floodlightProvider;
		/** Required Module: Floodlight statistic collector. */
		private IStatisticsCollectorService statisticsCollector;
		/** Required Module: Floodlight flow cache service. */
		private IFlowCacheService flowCache;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 * @param floodlightProvider Floodlight provider service.
		 * @param statisticsCollector Floodlight statistic collector.
		 * @param flowCache Floodlight flow cache service.
		 */
		public StrategyPathSelector(IPathFinderService pathFinder, IFloodlightProviderService floodlightProvider, IStatisticsCollectorService statisticsCollector, IFlowCacheService flowCache) {
			this.pathFinder = pathFinder;
			this.floodlightProvider = floodlightProvider;
			this.statisticsCollector = statisticsCollector;
			this.flowCache = flowCache;
		}

		@Override
		public String getName() {
			return "strategypathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, dstSwitchId);
			/* Best available bandwidth, i.e. capacity - pathBitRate. */
			long bestAvailableBandwidth = 0;
			/* Best link capacity / flow. */
			double bestCapacityToFlowRatio = Double.MIN_VALUE;
			/* Best number of hops. */
			int bestNumberOfHops = Integer.MAX_VALUE;

			// If we do not have any paths (yet).
			if (paths == null || paths.isEmpty()) {
				return null;
			}
			
			// Calculate path capacity to flow ratio. The higher the better.
			for (Path path : paths) {
				List<Link> links = path.getLinks();
				long pathAvailableBandwidth = Long.MAX_VALUE;
				double pathCapacityToFlowRatio = Double.MAX_VALUE;
				int pathNumberOfHops = links.size();
				
				for (Link link : links) {
					int linkFlows = 0;
					long linkBitRate = 0;
					long linkCapacity = 0;
					
					// Query the flow cache to collect the flow information in the future.
					FlowCacheQuery fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, "pathfinder", null, link.getSrc())
						.setOutPort(link.getSrcPort());
					Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
					
					//OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).toOFPhysicalPort();
					OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).getOFPhysicalPort();
					// Link capacity equals the capacity of the sending port.
					linkCapacity = (long) Utils.getPortCapacity(srcPort) * 1000 * 1000;
					
					// Query the statistic collector to get the port statistics.
					linkBitRate = this.statisticsCollector.getBitRate(link.getSrc(), OFSwitchPort.physicalPortIdOf(link.getSrcPort()));
					
					// Get the paths available bandwidth.
					if (linkCapacity - linkBitRate < pathAvailableBandwidth) {
						pathAvailableBandwidth = linkCapacity - linkBitRate;
						pathAvailableBandwidth = (pathAvailableBandwidth < 0) ? 0 : pathAvailableBandwidth;
					}
					
					// Collect the future and calculate the capacity-to-flow-ratio.
					try {
						FlowCacheQueryResp fcqr = future.get(1, TimeUnit.SECONDS);
						if (fcqr != null) {
							linkFlows = fcqr.flowCacheObjList.size();
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					} catch (TimeoutException e) {
						e.printStackTrace();
					}
					
					if (linkFlows != 0) {
						if (linkCapacity / linkFlows < pathCapacityToFlowRatio) {
							pathCapacityToFlowRatio = linkCapacity / linkFlows;
						}
					} else {
						if (linkCapacity < pathCapacityToFlowRatio) {
							// If link is empty us it in any case.
							//pathCapacityToFlowRatio = Double.MAX_VALUE;
							// If link is empty but its capacity is below 80% of the fair share of a better link, don't use it
							pathCapacityToFlowRatio = linkCapacity * 1.2;
						}
					}
				}
				
				// Choose path by available bandwidth.
				if (pathAvailableBandwidth > bestAvailableBandwidth) {
					bestAvailableBandwidth = pathAvailableBandwidth;
					bestCapacityToFlowRatio = pathCapacityToFlowRatio;
					bestNumberOfHops = pathNumberOfHops;
					bestPath = path;
				} else if (pathAvailableBandwidth == bestAvailableBandwidth) {
					// This happens, e.g. if the path is empty.
					// Choose path by the capacity-to-flow-ratio.
					if (pathCapacityToFlowRatio > bestCapacityToFlowRatio) {
						bestCapacityToFlowRatio = pathCapacityToFlowRatio;
						bestNumberOfHops = pathNumberOfHops;
						bestPath = path;
					} else if (pathCapacityToFlowRatio == bestCapacityToFlowRatio) {
						// Choose path by number of hops.
						if (pathNumberOfHops < bestNumberOfHops) {
							bestNumberOfHops = pathNumberOfHops;
							bestPath = path;
						} else if (bestNumberOfHops == pathNumberOfHops) {
							// Choose path round robin or random ?
							bestPath = path;
						}
					}
				}
			}
			
			return (bestPath != null) ? bestPath : null;
		}
	}
	
	/**
	 * Selects a path based on additional information regarding the file size
	 * to be transfered, provided by the application. It Chooses the
	 * path with the smallest virtual finishing time, calculated by the total
	 * number of bytes to transfer, the bytes already transfered, and the
	 * capacity of a path.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class AppAwarePathSelector implements IPathSelector {
		/** Required Module: Floodlight provider Service. */
		private IFloodlightProviderService floodlightProvider;
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		/** Required Module: Floodlight flow cache service. */
		private IFlowCacheService flowCache;
		/** Required Module: Floodlight application awareness service. */
		private IAppAwareService appAware;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 * @param floodlightProvider Floodlight provider service.
		 * @param flowCache Floodlight flow cache service.
		 * @param appAware Floodlight application awareness service.
		 */
		public AppAwarePathSelector(IPathFinderService pathFinder, IFloodlightProviderService floodlightProvider, IFlowCacheService flowCache, IAppAwareService appAware) {
			this.floodlightProvider = floodlightProvider;
			this.pathFinder = pathFinder;
			this.flowCache = flowCache;
			this.appAware = appAware;
		}

		@Override
		public String getName() {
			return "appawarepathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Do nothing.
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* The finishing time on the best path. */
			int bestPathFinishingTime = Integer.MAX_VALUE;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			// If we do not have any paths (yet).
			if (paths == null) {
				return null;
			}
			
			// Get the application entry
			AppEntry appEntry = appAware.getApplication(match);
			
			// If the application is not known, e.g. for reverse paths, use a default path selector.
			if (appEntry == null) {
				IPathSelector backupPathSelector = new FlowUtilizationPathSelector(pathFinder, flowCache);
				return backupPathSelector.selectPath(srcSwitchId, dstSwitchId, match);
			} else {
				appEntry.setActive(true);
			}

			// For each path, calculate the virtual finishing time.
			for (Path path : paths) {
				int currentPathFinishingTime = 0;
				int linkFinishingTime = 0;
				List<Link> links = path.getLinks();
				
				for (Link link : links) {
					FlowCacheQuery fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, this.getName(), null, link.getSrc())
						.setOutPort(OFSwitchPort.physicalPortIdOf(link.getSrcPort()));
					Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
					try {
						FlowCacheQueryResp fcqr = future.get(1, TimeUnit.SECONDS);
						if (fcqr != null && fcqr.flowCacheObjList != null) {
							linkFinishingTime = calculateFinishingTime(link, fcqr.flowCacheObjList); // * fcqr.flowCacheObjList.size();
							if (linkFinishingTime > currentPathFinishingTime) {
								currentPathFinishingTime = linkFinishingTime;
							}
						} else {
							// Do nothing, since there is no flow on that particular link.
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					} catch (TimeoutException e) {
						e.printStackTrace();
					}
				}
				
				if (currentPathFinishingTime < bestPathFinishingTime) {
					bestPathFinishingTime = currentPathFinishingTime;
					bestPath = path;
				}
			}

			return bestPath;
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
		private int calculateFinishingTime(Link link, ArrayList<FlowCacheObj> flowCacheObjects) {
			/* The finishing time of the link. */
			double finishingTime = 0;
			/* The application information, i.e. the file size to transfer. */
			AppEntry appEntry = null;
			/* Consider some overhead on the link. */
			double capacityWeigth = 0.8;
			
			// Get the port/link capacity.
			//OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).toOFPhysicalPort();
			OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).getOFPhysicalPort();
			// Link capacity equals the capacity of the sending port.
			int linkCapacity = Utils.getPortCapacity(srcPort);
			
			for (FlowCacheObj fco : flowCacheObjects) {
				if (fco.isActive()) {
					// Check if we have some statistics for the flow.
					StatisticEntry statEntry = (StatisticEntry) fco.getAttribute(FlowCacheObj.Attribute.STATISTIC);
					long transferedMBits = 0;
					if (statEntry != null) {
						transferedMBits = statEntry.getByteCount() * 8 /1000/1000;
					}
					// Find the application information.
					appEntry = appAware.getApplication(fco.getMatch());
					if (appEntry != null && appEntry.getFileSize() > 0) {
						finishingTime += Math.max(0, (appEntry.getFileSize() * 8 - transferedMBits) / ( capacityWeigth * linkCapacity)); 
					}
				}
			}
			
			// Return positive finishing time or 0.
			return (int) ((finishingTime > 0) ? finishingTime : 0);
		}
		
	}
	
	/**
	 * Selects a path based on additional information regarding the file size
	 * to be transfered, provided by the application. It Chooses the
	 * path with the smallest virtual finishing time, calculated by the total
	 * number of bytes to transfer, the bytes already transfered, and the
	 * capacity of a path. In addition it has a default route for small flows.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class AppAwareDefaultRoutePathSelector implements IPathSelector {
		/** Required Module: Floodlight provider Service. */
		private IFloodlightProviderService floodlightProvider;
		/** The path finder service to get the paths between source and destination nodes. */
		private IPathFinderService pathFinder;
		/** Required Module: Floodlight flow cache service. */
		private IFlowCacheService flowCache;
		/** Required Module: Floodlight application awareness service. */
		private IAppAwareService appAware;
		/** The path ID of the default route for small flows between source and destination switch. */
		private HashSet<Integer> defaultRouteIDs;
		
		/**
		 * Constructor.
		 * 
		 * @param pathFinder Floodlight path finder service.
		 * @param floodlightProvider Floodlight provider service.
		 * @param flowCache Floodlight flow cache service.
		 * @param appAware Floodlight application awareness service.
		 */
		public AppAwareDefaultRoutePathSelector(IPathFinderService pathFinder, IFloodlightProviderService floodlightProvider, IFlowCacheService flowCache, IAppAwareService appAware) {
			this.floodlightProvider = floodlightProvider;
			this.pathFinder = pathFinder;
			this.flowCache = flowCache;
			this.appAware = appAware;
			this.defaultRouteIDs = new HashSet<Integer>();
		}

		@Override
		public String getName() {
			return "appawaredefaultroutepathselector";
		}
		
		@Override
		public void setArgs(String args) {
			// Space separated values.
			String[] argElements = args.split(" ");
			// The destination switch of the default route.
			long dstSwitchId;
			// The route ID of the default route.
			int dstIp;
			/* The destination switch port. */
			short dstPort;
			
			switch (argElements.length) {
				case 3:
					dstSwitchId = HexString.toLong(argElements[0]);
					dstIp = IPv4.toIPv4Address(argElements[1]);
					dstPort = Short.valueOf(argElements[2]);
					
					this.installDefaultPaths(dstSwitchId, dstIp, dstPort);
					break;
				default:
					// No argument given.
			}
		}
		
		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId) {
			return this.selectPath(srcSwitchId, dstSwitchId, null);
		}

		@Override
		public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
			/* The best path, i.e. the one with the least flows on its links. */
			Path bestPath = null;
			/* The finishing time on the best path. */
			int bestPathFinishingTime = Integer.MAX_VALUE;
			/* Get pre-calculated paths. */
			Set<Path> paths = this.pathFinder.getPaths(srcSwitchId, dstSwitchId);
			
			// If we do not have any paths (yet).
			if (paths == null) {
				return null;
			}
			
			// If we only have one path.
			if (paths.size() == 1) {
				return (Path) paths.toArray()[0];
			}
			
			// Get the application entry
			AppEntry appEntry = appAware.getApplication(match);
			
			// If the application is not known, e.g. for reverse paths, use a default path selector.
			if (appEntry == null) {
				IPathSelector backupPathSelector = new FlowUtilizationPathSelector(pathFinder, flowCache);
				return backupPathSelector.selectPath(srcSwitchId, dstSwitchId, match);
			} else {
				appEntry.setActive(true);
			}

			// For each path, calculate the virtual finishing time of the large flows.
			for (Path path : paths) {
				
				if (this.defaultRouteIDs.contains(path.getId()))
					continue;
				
				int currentPathFinishingTime = 0;
				int linkFinishingTime = 0;
				List<Link> links = path.getLinks();
				
				for (Link link : links) {
					FlowCacheQuery fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, this.getName(), null, link.getSrc())
						.setOutPort(OFSwitchPort.physicalPortIdOf(link.getSrcPort()));
					Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
					try {
						FlowCacheQueryResp fcqr = future.get(1, TimeUnit.SECONDS);
						if (fcqr != null && fcqr.flowCacheObjList != null) {
							linkFinishingTime = calculateFinishingTime(link, fcqr.flowCacheObjList); // * fcqr.flowCacheObjList.size();
							if (linkFinishingTime > currentPathFinishingTime) {
								currentPathFinishingTime = linkFinishingTime;
							}
						} else {
							// Do nothing, since there is no flow on that particular link.
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ExecutionException e) {
						e.printStackTrace();
					} catch (TimeoutException e) {
						e.printStackTrace();
					}
				}
				
				if (currentPathFinishingTime < bestPathFinishingTime) {
					bestPathFinishingTime = currentPathFinishingTime;
					bestPath = path;
				}
			}

			return bestPath;
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
		private int calculateFinishingTime(Link link, ArrayList<FlowCacheObj> flowCacheObjects) {
			/* The finishing time of the link. */
			double finishingTime = 0;
			/* The application information, i.e. the file size to transfer. */
			AppEntry appEntry = null;
			/* Consider some overhead on the link. */
			double capacityWeigth = 0.8;
			
			// Get the port/link capacity.
			OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).getOFPhysicalPort();
			// Link capacity equals the capacity of the sending port.
			int linkCapacity = Utils.getPortCapacity(srcPort);
			
			for (FlowCacheObj fco : flowCacheObjects) {
				if (fco.isActive()) {
					// Check if we have some statistics for the flow.
					StatisticEntry statEntry = (StatisticEntry) fco.getAttribute(FlowCacheObj.Attribute.STATISTIC);
					long transferedMBits = 0;
					if (statEntry != null) {
						transferedMBits = statEntry.getByteCount() * 8 /1000/1000;
					}
					// Find the application information.
					appEntry = appAware.getApplication(fco.getMatch());
					if (appEntry != null && appEntry.getFileSize() > 0) {
						finishingTime += Math.max(0, (appEntry.getFileSize() * 8 - transferedMBits) / ( capacityWeigth * linkCapacity)); 
					}
				}
			}
			
			// Return positive finishing time or 0.
			return (int) ((finishingTime > 0) ? finishingTime : 0);
		}
		
		/**
		 * 
		 * @param dstNode
		 * @param dstIp
		 * @param dstPort
		 */
		private void installDefaultPaths(long dstNode, int dstIp, short dstPort) {
			for (long srcNode : this.floodlightProvider.getAllSwitchDpids()) {
				if (srcNode == dstNode)
					continue;
				
				Set<Path> paths = this.pathFinder.getPaths(srcNode, dstNode);
				if (paths.isEmpty())
					continue;
				
				// Add the first path to the defaults path set.
				Path defaultPath = (Path) paths.toArray()[0];
				this.defaultRouteIDs.add(defaultPath.getId());
				
				int wildcards = OFMatch.OFPFW_ALL &
						~OFMatch.OFPFW_DL_TYPE &
						~OFMatch.OFPFW_NW_DST_MASK;
				
				// The now OF match.
				OFMatch match = new OFMatch();
				match.setWildcards(wildcards);
				match.setDataLayerType(Ethernet.TYPE_IPv4);
				match.setNetworkDestination(dstIp);
				
				///
				/// TESTING
				///
				
				// Install the last hop.
				IOFSwitch iofSwitch = floodlightProvider.getSwitch(defaultPath.getEndPoints().getDst());
				this.addFlowMod(iofSwitch, match, dstPort);
				
				// Install the path.
		        List<Link> links = defaultPath.getLinks();
		        boolean moveOn = true;
		        long switchId;
		        int outPort;
		        // Install links in reverse order, i.e. begin at the last (destination) switch.
		        ListIterator<Link> iter = links.listIterator(links.size());
		    	// Get the last link.
		    	Link link = iter.previous();
		    	
		        do {
		        	// Get the current switchId in the path.
		        	switchId = link.getSrc();
		        	outPort = link.getSrcPort();
		            // Strip the physical output port id.
		    		short phyOutPortId = OFSwitchPort.physicalPortIdOf(outPort);
		            //Get the current switch in the path.
		            iofSwitch = floodlightProvider.getSwitch(switchId);
		            
		            if (iter.hasPrevious()) {
		                // Get the next link.
		            	link = iter.previous();
		        	} else {
		        		outPort = link.getSrcPort();
		        		moveOn = false;
		        	}
		            
		            this.addFlowMod(iofSwitch, match, phyOutPortId);
		            
		        } while (moveOn);
			}
		}
		
		/**
		 * 
		 * @param iofSwitch
		 * @param match
		 * @param phyOutPortId
		 */
		private void addFlowMod(IOFSwitch iofSwitch, OFMatch match, short phyOutPortId) {
			// Add the flow
			OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
			// Set idle timeout.
		    flowMod.setIdleTimeout((short) 0);
		    // The cookie
		    flowMod.setCookie(5);
		    // Buffered packet to apply to (or -1). Not meaningful for OFPFC_DELETE.
	        flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
	        // Fields to match.
	        flowMod.setMatch(match);
	        // priority
	        flowMod.setPriority((short) 0);
	    	
	        // Actions
	        short actionsLength = 0;
	        List<OFAction> actions = new ArrayList<OFAction>();
	        // Set the output action.
	        actions.add(new OFActionOutput(phyOutPortId, (short) 0xffff));
	        actionsLength += OFActionOutput.MINIMUM_LENGTH;
	        flowMod.setActions(actions);
	        // Length
	        flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + actionsLength));
		    
	        try {
	        	iofSwitch.write(flowMod, null);
				iofSwitch.flush();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Calculates link disjoint paths using Dijkstra's algorithm.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class DijkstraPathCalculator implements IPathCalculator {
		
		@Override
		public String getName() {
			return "dijkstrapathcalculator";
		}
		
		@Override
		public Set<Path> caluclatePaths(long srcNode, long dstNode, Cluster topologyCluster) {
			/* The set of paths between source and destination node. */
			Set<Path> newPaths = new HashSet<Path>();
			/* Whether the tree starts at the source or destination. */
			boolean destinationRooted = true;
			/* Temporary cluster containing all links of the current topology that are not part of a route. */
			OlimpsCluster cluster = ((OlimpsCluster) topologyCluster).clone();
			/* The costs of a given link. */
			Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
			/* States if there are more paths available in the current topology. */
			boolean hasPath = true;
			
			// Calculate and set link costs.
			for (Entry<Long, Set<Link>> entry : topologyCluster.getLinks().entrySet()) {
				for (Link link : entry.getValue()) {
					linkCost.put(link, 1);
				}
			}
			
			// calculate paths
			while (hasPath) {
				if (cluster.isEmpty())
					break;
					
				// Calculate the shortest path between two nodes for the given temporary cluster 
				Path path = calculateShortestPath(cluster, linkCost, srcNode, dstNode, destinationRooted);
				
				// Add path to path cache. If there is no path anymore - stop searching
				if (path != null) {
					newPaths.add(path);
				} else {
					hasPath = false;
					break;
				}
				
				// Remove links from temporary cluster.
				for (Link link : path.getLinks()) {
					if (destinationRooted) {
						cluster.delLink(this.reverseLink(link));
					} else {
						cluster.delLink(link);
					}
				}
			}
			
			return (newPaths.isEmpty()) ? null : newPaths;
		}
		
		/**
		 * Calculates the shorts paths between to nodes using Dijkstra's algorithm.
		 * 
		 * @param cluster The current topology cluster.
		 * @param linkCost The costs of a given link.
		 * @param srcNode The source node of the path.
		 * @param dstNode The destination node of the path.
		 * @param isDstRooted Whether the tree starts at the source or destination.
		 * @return <b>Route</b> The path from source to destination, represented by a list of NodePortTuples.
		 */
		protected Path calculateShortestPath(OlimpsCluster cluster, Map<Link, Integer> linkCost, long srcNode, long dstNode, boolean isDstRooted) {
			/* The shortest path between source and destination node. */
			//List<NodePortTuple> path = new ArrayList<NodePortTuple>();
			LinkedList<Link> links = new LinkedList<Link>();
	        
	        // calculate the spanning tree using dijkstra's algorithm.
			BroadcastTree tree = dijkstra(cluster, srcNode, linkCost, isDstRooted);

			// temporary destination node
			long dst = dstNode;
			long src = dstNode;
			
			while (tree.getTreeLink(dst) != null) {			
				if (isDstRooted == true) {
					links.addFirst(this.reverseLink(tree.getTreeLink(dst)));
					dst = tree.getTreeLink(dst).getDst();
				} else {
					// TODO: check if this is correct!
					links.add(tree.getTreeLink(src));
					src = tree.getTreeLink(src).getSrc();
				}
			}
			
			if (!links.isEmpty()) {
				return new Path(srcNode, dstNode, links, 0, caculatePathCapacity(links));
			} else {
				return null;
			}
		}
		
		/**
		 * Calculates a broadcast tree using Dijkstra's algorithm
		 * 
		 * @param cluster The current topology cluster.
		 * @param root The root node.
		 * @param linkCost The link costs
		 * @param isDstRooted States whether the broadcast tree has its root at the destination node (or the source node).
		 * @return <b>BroadcastTree</b>
		 */
		protected BroadcastTree dijkstra(OlimpsCluster cluster, long root, Map<Link, Integer> linkCost, boolean isDstRooted) {
			/* */
			HashMap<Long, Link> nexthoplinks = new HashMap<Long, Link>();
			/* */
			HashMap<Long, Integer> cost = new HashMap<Long, Integer>();
			/* */
			HashMap<Long, Boolean> seen = new HashMap<Long, Boolean>();
			/* */
			PriorityQueue<NodeDist> nodeq = new PriorityQueue<NodeDist>();
			/* */
			int weight;

			// initialize nexthoplinks and costs
			for (Long node : cluster.getLinks().keySet()) {
				nexthoplinks.put(node, null);
				cost.put(node, MAX_PATH_WEIGHT);
			}
			
			nodeq.add(new NodeDist(root, 0));
			cost.put(root, 0);
			while (nodeq.peek() != null) {
				NodeDist n = nodeq.poll();
				Long cnode = n.getNode();
				int cdist = n.getDist();
				if (cdist >= MAX_PATH_WEIGHT)
					break;
				if (cluster.getLinks().get(cnode) == null)
					break;
				if (seen.containsKey(cnode))
					continue;
				seen.put(cnode, true);

				for (Link link : cluster.getLinks().get(cnode)) {
					Long neighbor;

					if (isDstRooted == true)
						neighbor = link.getSrc();
					else
						neighbor = link.getDst();

					// links directed toward cnode will result in this condition
					// if (neighbor == cnode) continue;

					if (linkCost == null || linkCost.get(link) == null)
						weight = 1;
					else
						weight = linkCost.get(link);

					int ndist = cdist + weight;
					if (ndist < cost.get(neighbor)) {
						cost.put(neighbor, ndist);
						nexthoplinks.put(neighbor, link);
						nodeq.add(new NodeDist(neighbor, ndist));
					}
				}
			}
			return new BroadcastTree(nexthoplinks, cost);
		}
		
	    /**
	     * Since Dijkstra returns links in reversed order, i.e. form destination to source,
	     * we need to invert them again.
	     * 
	     * @param link The links to be reversed.
	     * @return <b>Link</b> A new link in reversed order, i.e. reversedLink.DST == link.SRC and vice versa.
	     */
	    private Link reverseLink(Link link) {
	    	return new Link(link.getDst(), link.getDstPort(), link.getSrc(), link.getSrcPort());
	    }
		
	}
	
	/**
	 * Calculates all link disjoint paths using a brute force algorithm.
	 * 
	 * @author Michael Bredel <michael.bredel@caltech.edu>
	 */
	protected class BruteForcePathCalculator implements IPathCalculator {
		
		/**
		 * Compares path sizes, i.e. the number of links in a path.
		 */
		private class PathComparator implements Comparator<List<Link>> {
			@Override
			public int compare(List<Link> arg0, List<Link> arg1) {
				if (arg0.size() > arg1.size())
					return 1;
				if (arg0.size() < arg1.size())
					return -1;
				
				return 0;
			}
		}
		
		@Override
		public String getName() {
			return "bruteforcepathcalculator";
		}

		@Override
		public Set<Path> caluclatePaths(long srcSwitchId, long dstSwitchId, Cluster cluster) {
			Set<Path> newPaths = new HashSet<Path>();
			Set<Long> switches = cluster.getNodes();
			Map<Link, Boolean> links = this.setLinksActive(cluster);
			// Search for all paths.
			Set<List<Link>> allPaths = searchAllPaths(srcSwitchId, dstSwitchId, links.keySet(), switches);
			// Search for all link disjoint paths.
			Set<List<Link>> linkDisjointPaths = searchLinkDisjointPath(allPaths);
			
			for (List<Link> path : linkDisjointPaths) {
				newPaths.add(new Path(srcSwitchId, dstSwitchId, path, 0, caculatePathCapacity(path)));
			}
			
			return (newPaths.isEmpty()) ? null : newPaths;
		}
		
		/**
		 * Search for all available paths between a source and destination node.
		 * 
		 * @param srcNode The source node of the paths.
		 * @param dstNode The destination node of the paths.
		 * @param allLinks All links in the cluster.
		 * @param allNodes All nodes in the cluster.
		 * @return <b>Set of List of Links</b> A set of all possible paths betwen source and destination, represented as a list of links.
		 */
		protected Set<List<Link>> searchAllPaths(long srcNode, long dstNode, Set<Link> allLinks, Set<Long> allNodes) {
			// The new paths from srcNode to dstNode.
			Set<List<Link>> allPaths = new HashSet<List<Link>>();
			// Clone the available links such that we can modify them.
			Set<Link> availableLinks = new HashSet<Link>();
			availableLinks.addAll(allLinks);
			// Clone the visited nodes such that we can modify them.
			Set<Long> visitedNodes = new HashSet<Long>();
			visitedNodes.addAll(allNodes);
			
			// Just to make sure the first node is in the visited nodes list.
			if (!visitedNodes.contains(srcNode)) {
				allNodes.add(srcNode);
				visitedNodes.add(srcNode);
			}
			
			// For all links that originate at the source node.
			for (Link link : this.getSwitchLinks(srcNode, allLinks)) {
				List<Link> currentPath = new ArrayList<Link>();
				
				if (!availableLinks.contains(link))
					continue;
				
				long nextNode = link.getDst();
				
				if (nextNode == dstNode) {
					currentPath.add(link);
					allPaths.add(currentPath);
				} else {
					availableLinks.remove(link);
					visitedNodes.add(nextNode);
					Set<List<Link>> nextPaths = searchAllPaths(nextNode, dstNode, availableLinks, visitedNodes);
					
					for (List<Link> path : nextPaths) {
						if (path.isEmpty()) 
							continue;
						currentPath.add(link);
						currentPath.addAll(path);
						allPaths.add(new ArrayList<Link>(currentPath));
						currentPath.clear();
					}
				}
			}
			
			return allPaths;
		}
		
		/**
		 * Search for link disjoint paths.
		 * 
		 * @param allPaths All possible paths between a source and destination node.
		 * @return <b>Set of List of Links</b> A set of link disjoint paths betwen source and destination, represented as a list of links. 
		 */
		protected Set<List<Link>> searchLinkDisjointPath(Set<List<Link>> allPaths) {
			Set<List<Link>> paths = new HashSet<List<Link>>();
			Set<List<Link>> currentPaths = new HashSet<List<Link>>();
			double meanHops = 0;
			double varHops = 0;
			
			// Sort paths by size.
			List<List<Link>> allPathsList = new ArrayList<List<Link>>(allPaths);
			Collections.sort(allPathsList, new PathComparator());
			
			for (List<Link> currentPath : allPathsList) {
				currentPaths.clear();
				currentPaths.add(currentPath);
				for (List<Link> path : allPathsList) {
					if (checkLinkDisjointPath(currentPaths, path)) {
						currentPaths.add(path);
					}
				}
				
				if (currentPaths.size() > paths.size()) {
					paths.clear();
					paths.addAll(currentPaths);
					meanHops = caluclateMeanHops(currentPaths);
					varHops = calculateVarHops(currentPaths);
					continue;
				}
				
				if (currentPaths.size() == paths.size()) {
					// Check for minimal hops (mean and variance)
					double curMeanHops = caluclateMeanHops(currentPaths);
					if (curMeanHops < meanHops) {
						paths.clear();
						paths.addAll(currentPaths);
						meanHops = caluclateMeanHops(currentPaths);
						varHops = calculateVarHops(currentPaths);
					} else {
						double curVarHops = calculateVarHops(currentPaths);
						if (curVarHops < varHops) {
							paths.clear();
							paths.addAll(currentPaths);
							meanHops = caluclateMeanHops(currentPaths);
							varHops = calculateVarHops(currentPaths);
						}
					}
				}
			}
			
			return paths;
		}
		
		/**
		 * Checks whether two paths are link disjoint.
		 * 
		 * @param path1 The first path to analyze.
		 * @param path2 The second path to analyze.
		 * @return <b>boolean</b> True if the paths are link disjoint.
		 */
		private boolean checkLinkDisjointPath(Set<List<Link>> paths, List<Link> path) {
			
			for (List<Link> currentPath : paths) {
				for (Link link : currentPath) {
					if (path.contains(link)) {
						return false;
					}
				}
			
				for (Link link : path) {
					if (currentPath.contains(link))
						return false;
				}
			}
			
			return true;
		}
		
		/**
		 * Gets all links that belongs to a source switch.
		 * 
		 * @param switchId The switch ID we are looking for.
		 * @param links All available links.
		 * @return <b>Set of Link</b> The links that originate at a given switch.
		 */
		private Set<Link> getSwitchLinks(long switchId, Set<Link> links) {
			Set<Link> switchLinks = new HashSet<Link>();
			
			for (Link link : links) {
				if (link.getSrc() == switchId)
					switchLinks.add(link);
			}
			
			return switchLinks;
		}
		
		/**
		 * Calculates the mean number of links of a set of paths.
		 * 
		 * @param paths A set of paths
		 * @return <b>double</b> The mean number of links.
		 */
		private double caluclateMeanHops(Set<List<Link>> paths) {
			int hops = 0; 
			for (List<Link> path : paths) {
				hops += path.size();
			}
			return hops / paths.size();
		}
		
		/**
		 * 
		 * @param paths
		 * @return
		 */
		private double calculateVarHops(Set<List<Link>> paths) {
			double meanHops = caluclateMeanHops(paths);
			double varHops = 0;
			for (List<Link> path : paths) {
				varHops += Math.pow((path.size() - meanHops), 2);
			}
			return varHops;
		}
		
		/**
		 * Sets all links active, i.e. these links can be used for a
		 * path calculation
		 * 
		 * @param cluster The current topology cluster.
		 * @return <b>A Map of Link->Boolean</b> A map of active links.
		 * 
		 * TODO: Do we still need that?
		 */
		private Map<Link, Boolean> setLinksActive(Cluster cluster) {
			Map<Link, Boolean> linkMap = new HashMap<Link, Boolean>();	
			
			for (Set<Link> links : cluster.getLinks().values()) {
				for (Link link : links) {
					linkMap.put(link, true);
				}
			}
			
			return linkMap;
		}
		
	}
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IPathFinderService.class);
	    l.add(IRoutingService.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(IPathFinderService.class, this);
        m.put(IRoutingService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		l.add(IOlimpsTopologyService.class);
		l.add(IPathCacheService.class);
	    l.add(IFlowCacheService.class);
	    l.add(IStatisticsCollectorService.class);
	    l.add(IAppAwareService.class);
	    l.add(IConfigurationService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		topologyManager = context.getServiceImpl(IOlimpsTopologyService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		flowCache = context.getServiceImpl(IFlowCacheService.class);
		pathCache = context.getServiceImpl(IPathCacheService.class);
		statisticsCollector = context.getServiceImpl(IStatisticsCollectorService.class);
		appAware = context.getServiceImpl(IAppAwareService.class);
		configManager = context.getServiceImpl(IConfigurationService.class);
		pathCounter = new ConcurrentHashMap<EndPoints, Integer>();
		pathSelectors = new HashSet<IPathSelector>();
		pathCalculators = new HashSet<IPathCalculator>();
		
		// Select default path selector.
		currentPathSelector = new RoundRobinPathSelector(this, this.pathCache);
		// Select the default path calculator.
		currentPathCalculator = new DijkstraPathCalculator();
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		configManager.addListener(this);
		topologyManager.addListener(this);
		restApi.addRestletRoutable(new PathFinderWebRoutable()); 
		// Add path selectors to map.
		pathSelectors.add(new ShortestPathSelector(this));
		pathSelectors.add(new RandomPathSelector(this));
		pathSelectors.add(new HashIpPathSelector(this));
		pathSelectors.add(new HashPortPathSelector(this));
		pathSelectors.add(new RoundRobinPathSelector(this, this.pathCache));
		pathSelectors.add(new FlowUtilizationPathSelector(this, this.flowCache));
		pathSelectors.add(new CapacityPathSelector(this));
		pathSelectors.add(new FlowUtilizationAndCapacityPathSelector(this, this.floodlightProvider, this.flowCache));
		pathSelectors.add(new AvailableBandwidthPathSelector(this, this.floodlightProvider, this.statisticsCollector));
		pathSelectors.add(new StrategyPathSelector(this, this.floodlightProvider, this.statisticsCollector, this.flowCache));
		pathSelectors.add(new AppAwarePathSelector(this, this.floodlightProvider, this.flowCache, this.appAware));
		pathSelectors.add(new AppAwareDefaultRoutePathSelector(this, this.floodlightProvider, this.flowCache, this.appAware));
		// Add path calculators to map.
		pathCalculators.add(new DijkstraPathCalculator());
		pathCalculators.add(new BruteForcePathCalculator());
		
	}
    
	///
	/// IPathFinderService
	///
    
    @Override
    public boolean hasPath(long srcNode, long dstNode) {
    	if (!pathCache.containsPath(srcNode, dstNode)) {
			this.calculatePaths(srcNode, dstNode);
        }
    	if (!pathCache.containsPath(srcNode, dstNode)) {
    		return false;
    	} else {
    		return true;
    	}
    }
    
    @Override
    public Path getPath(long srcSwitchId, long dstSwitchId, OFMatch match) {
    	// If the source switch equals a destination switch.
    	if (srcSwitchId == dstSwitchId) {
    		return new Path(srcSwitchId, dstSwitchId, null, 0, 0);
    	}
    	// Make sure we calculated a path.
    	if (!pathCache.containsPath(srcSwitchId, dstSwitchId)) {
    		this.calculatePaths(srcSwitchId, dstSwitchId);
    	}
    	return this.currentPathSelector.selectPath(srcSwitchId, dstSwitchId, match);
    }
	
	@Override
	public Set<Path> getPaths() {
		/* Set of routes containing paths from source to destination. */
		Set<Path> paths = new HashSet<Path>();
	        
		for (EndPoints endPoints : this.pathCache.getAllEndPoints()) {
			paths.addAll(this.pathCache.getAllPaths(endPoints.getSrc(), endPoints.getDst()));
		}
			
		return paths;
	}

	@Override
	public Set<Path> getPaths(long srcNode, long dstNode) {
        if (srcNode == dstNode)
            return null;
        
        if (!pathCache.containsPath(srcNode, dstNode)) {
			this.calculatePaths(srcNode, dstNode);
        }
        
        return this.pathCache.getAllPaths(srcNode, dstNode);
	}
	
	@Override
	public void calculatePaths(long srcNode, long dstNode) {
		/* Cluster containing all links of the current topology. */
		OlimpsCluster topologyCluster = topologyManager.getTopologyCluster();
		
		// Remove previous entries for this path.
		if (pathCache.containsPath(srcNode, dstNode))
			pathCache.removePath(srcNode, dstNode);
		
		// Calculate the paths and put them into the path cache.
		Set<Path> pathSet = this.currentPathCalculator.caluclatePaths(srcNode, dstNode, topologyCluster);
		if (pathSet != null) {
			for (Path path : pathSet) {
				pathCache.addPath(path);
			}
		}
	}

	@Override
	public Set<IPathSelector> getAllPathSelector() {
		return this.pathSelectors;
	}
	
	@Override
	public IPathSelector getPathSelector() {
		return this.currentPathSelector;
	}

	@Override
	public synchronized IPathSelector setPathSelector(String name, String args) {		
		for (IPathSelector pathSelector : this.pathSelectors) {
			if (pathSelector.getName().equalsIgnoreCase(name)) {
				this.currentPathSelector = pathSelector;
				if (log.isInfoEnabled()) {
					if (args != null && !args.equalsIgnoreCase("")) {
						log.info("Changed path selector to '{}' with args '{}'.", name, args);
					} else {
						log.info("Changed path selector to '{}'.", name);
					}
				}
				
				// Set the path selector arguments.
				this.currentPathSelector.setArgs(args);
				
				return this.currentPathSelector;
			}
		}
		
		if (log.isWarnEnabled()) {
			log.warn("Path selector {} not found. Using default path selector {} instead", name, this.currentPathSelector.getName());
		}
		return null;
	}
	
	@Override
	public Set<IPathCalculator> getAllPathCalculator() {
		return this.pathCalculators;
	}
	
	@Override
	public IPathCalculator getPathCalculator() {
		return this.currentPathCalculator;
	}
	
	@Override
	public synchronized IPathCalculator setPathCalculator(String name) {
		boolean found = false;
		
		for (IPathCalculator pathCalculator : this.pathCalculators) {
			if (pathCalculator.getName().equalsIgnoreCase(name)) {
				this.currentPathCalculator = pathCalculator;
				found = true;
				break;
			}
		}
		
		if (!found && log.isWarnEnabled()) {
			log.warn("Path calculator {} not found. Using path calculator {} instead", name, this.currentPathCalculator.getName());
		}
		
		return this.currentPathCalculator;
	}
	
	///
	/// ITopologyListener
	///

	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		for (LDUpdate ldu : linkUpdates) {
			// Only recalculate paths if it is NOT a LINK_UPDATE
			if (!ldu.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)) {
				// TODO: Only recalculate paths that are affected.
				// TODO: Do a flow reconciliation.
				for (EndPoints endPoints : pathCache.getAllEndPoints()) {
					calculatePaths(endPoints.getSrc(), endPoints.getDst());
				}
			}
		}
	}
	
	///
	/// IConfigurationListener
	///

	@Override
	public String getName() {
		return CONFIGURATOR_NAME;
	}

	@Override
	public JsonNode getJsonConfig() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = mapper.createObjectNode();
		try {
			// Write the path selector.
			rootNode.put("selector", this.currentPathSelector.getName());
			// Write the path calculator.
			rootNode.put("calculator", this.currentPathCalculator.getName());
			// Write any other PathFinder configuration.
			// ... here.
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Creating the PathFinder configuration for JSON failed. ", e);
			}
		}
		
		// Return root node if it contains some configuration. Otherwise return null.
		return (rootNode.size() > 0) ? (JsonNode) rootNode : null;
	}

	@Override
	public void putJsonConfig(JsonNode jsonNode) {
		Iterator<Entry<String, JsonNode>> iter = jsonNode.fields();
		while (iter.hasNext()) {
			Entry<String, JsonNode> entry = iter.next();
			String fieldname = entry.getKey().toLowerCase();
			JsonNode child = entry.getValue();
			switch(fieldname) {
				case "selector":
					String name = child.asText().trim().toLowerCase();
					String args = "";
					this.setPathSelector(name, args);
					break;
				case "calculator":
					this.setPathCalculator(child.asText().trim().toLowerCase());
					break;
				default:
					if (log.isWarnEnabled()) {
						log.warn("Reading the PathFinder for {} configuration from JSON failed.", fieldname);
					}
			}	
		}
	}
	
	///
	/// Local methods
	///
	
	/**
	 * Calculates the path capacity.
     * 
     * @param links A path represented by a list of links.
     * @return <b>int</b> The path capacity in Mbps.
     */
    private int caculatePathCapacity(List<Link> links) {
    	/* The initial path capacity. */
    	int pathCapacity = Integer.MAX_VALUE;
    	
    	if (links == null || links.isEmpty()) {
    		return 0;
    	}
    	
    	for (Link link : links) {
			int linkCapacity = 0;
			
			//OFPhysicalPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort()).toOFPhysicalPort();
			OFSwitchPort srcPort = this.floodlightProvider.getSwitch(link.getSrc()).getPort(link.getSrcPort());
			// Link capacity equals the capacity of the sending port.
			linkCapacity = srcPort.getCurrentPortSpeed();
			
			if (linkCapacity < pathCapacity) {
				pathCapacity = linkCapacity;
			}
				
		}
		
		return pathCapacity;	
    }
	
}
