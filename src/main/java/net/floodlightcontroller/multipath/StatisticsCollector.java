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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.statistics.OFAggregateStatisticsReply;
import org.openflow.protocol.statistics.OFAggregateStatisticsRequest;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import net.floodlightcontroller.multipath.web.StatisticsCollectorWebRoutable;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 * 
 * TODO: Connect StatisticCollector to CounterStore. Store counters
 *        in Floodlight's general counter store.
 * TODO: Use FlowCache to optimize statistic queries. To this end,
 *        query the flow cache to get the number of flows on a port.
 *        Then, only query switches/ports/flows that are known to
 *        be present at the switches. Moreover, it might be useful
 *        to query flows, and have some per flow/match statistics
 *        rather then per port statistics. Especially, once we are
 *        able to move flows to different paths.
 */
public class StatisticsCollector implements IFloodlightModule, IStatisticsCollectorService {
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(StatisticsCollector.class);
	/** Required module: The general Floodlight provider service. */
    protected IFloodlightProviderService floodlightProvider;
    /** Required module: The REST API service to provide a REST interface. */
	protected IRestApiService restApi;
    /** Required module: Floodlight's thread pool service. */
    protected IThreadPoolService threadPool;
    /** Required module: Floodlight's flow cache service. */
    protected IFlowCacheService flowCache;
    /** Flow query task to scan switches for their flow tables. */
    protected SendPeriodicFlowQueryToSwitches flowQueryTask;
    /** Map to store the query results: SwitchId -> PortId -> StatisticEntry. */
    protected ConcurrentHashMap<Long, ConcurrentHashMap<Short, StatisticEntry>> statsCache;
    
    /**
     * Scans one switch periodically for new flows in its flow table
     */
    protected class SwitchFlowTablePeriodicScanTask implements Runnable {
    	/** The switch id of the switch to query. */
        protected long switchId;

        /**
         * Default constructor.
         * 
         * @param switchId The switch id of the switch to query.
         * @param callbackHandler The callback handler for returning OFStatisticsReply messages.
         */
        protected SwitchFlowTablePeriodicScanTask(long switchId) {
            this.switchId = switchId;
        }

        @Override
        public void run() {
            querySwitchStats(switchId);
        }
    }
    
    /**
     * Scans all available switches periodically for new flows in its flow table
     */
    protected class SendPeriodicFlowQueryToSwitches implements Runnable {
        /** */
        protected boolean enableFlowQueryTask = false;
        
        /**
         * Default constructor.
         */
        protected SendPeriodicFlowQueryToSwitches() {
            this.enableFlowQueryTask = true;
        }
        
        /**
         * States whether the flow query taks is running.
         * 
         * @return <b>boolean</b> True if the flow query task is running.
         */
        public boolean isEnableFlowQueryTask() {
            return enableFlowQueryTask;
        }

        /**
         * 
         * @param enableFlowQueryTask 
         */
        public void setEnableFlowQueryTask(boolean enableFlowQueryTask) {
            this.enableFlowQueryTask = enableFlowQueryTask;
        }

        @Override
        public void run() {
            if (!enableFlowQueryTask) 
            	return;
            
            Map<Long, IOFSwitch> switches = floodlightProvider.getAllSwitchMap();
            if (switches == null) {
                return;
            }
            
            int numSwitches = switches.size();
            
            if (numSwitches == 0) {
            	return;
            }
            
            if (log.isTraceEnabled()) {
                log.trace("Sending flow scan messages to switches {}", switches.keySet());
            }
            
            int interval_ms = DEFAULT_SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC / numSwitches;
            int idx = 0;
            for (Long switchId : switches.keySet()) {
                SwitchFlowTablePeriodicScanTask scanTask = new SwitchFlowTablePeriodicScanTask(switchId);
                // Schedule the queries to different switches in a staggered way.
                threadPool.getScheduledExecutor().schedule(scanTask, interval_ms*idx, TimeUnit.MILLISECONDS);
                idx++;
            }
        }
    }
    
    @Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IStatisticsCollectorService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IStatisticsCollectorService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IThreadPoolService.class);
        l.add(IFlowCacheService.class);
        l.add(IPathFinderService.class);
        l.add(IRestApiService.class);
        return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
        flowCache = context.getServiceImpl(IFlowCacheService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        flowQueryTask = new SendPeriodicFlowQueryToSwitches();
        statsCache = new ConcurrentHashMap<Long, ConcurrentHashMap <Short, StatisticEntry>>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		restApi.addRestletRoutable(new StatisticsCollectorWebRoutable()); 
		threadPool.getScheduledExecutor().scheduleAtFixedRate(
				flowQueryTask,
				DEFAULT_SWITCH_FLOW_TBL_SCAN_INITIAL_DELAY_MSEC, 
				DEFAULT_SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC,
                TimeUnit.MILLISECONDS);
	}
	
	@Override
	public long getPacketRate(long switchId, int port) {
		/* Strip the physical port number from the given (virtual) port. */
		short phyPortId = OFSwitchPort.physicalPortIdOf(port);
		
		if (statsCache.get(switchId) == null)
			return 0;
		if (statsCache.get(switchId).get(phyPortId) == null)
			return 0;
		return statsCache.get(switchId).get(phyPortId).getPacketRate();
	}

	@Override
	public long getBitRate(long switchId, int port) {
		/* Strip the physical port number from the given (virtual) port. */
		short phyPortId = OFSwitchPort.physicalPortIdOf(port);
		
		if (statsCache.get(switchId) == null)
			return 0;
		if (statsCache.get(switchId).get(phyPortId) == null)
			return 0;
		return statsCache.get(switchId).get(phyPortId).getByteRate() * 8;
	}

	@Override
	public int getFlowCount(long switchId, int port) {
		/* Strip the physical port number from the given (virtual) port. */
		short phyPortId = OFSwitchPort.physicalPortIdOf(port);
		
		if (statsCache.get(switchId) == null)
			return 0;
		if (statsCache.get(switchId).get(phyPortId) == null)
			return 0;
		return statsCache.get(switchId).get(phyPortId).getFlowCount();
	}
	
	@Override
	public StatisticEntry getStatisticEntry(long switchId, int port) {
		/* Strip the physical port number from the given (virtual) port. */
		short phyPortId = OFSwitchPort.physicalPortIdOf(port);
		
		if (this.statsCache.contains(switchId)) {
			if (this.statsCache.get(switchId).contains(phyPortId)) {
				return this.statsCache.get(switchId).get(phyPortId);
			}
			return null;
		}
		return null;
	}
	
	/**
	 * Queries the flow table of a specific switch by sending a flow statistics requests.
	 * 
	 * @param switchId The unique ID of the switch to query.
	 */
	protected void querySwitchStats(long switchId) {
		this.queryPortStats(switchId);
		this.queryFlowStats(switchId);
	}
	
	/**
	 * 
	 * @param switchId The unique ID of the switch to query.
	 */
	protected void queryPortStats(long switchId) {
		/* The switch we want to query. */
		IOFSwitch iofSwitch = floodlightProvider.getSwitch(switchId);
		
		if ((iofSwitch == null) || (!iofSwitch.isConnected())) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to send port-stats request to switch Id {}", switchId);
			}
			return;
		} else {
			if (log.isTraceEnabled()) {
				log.trace("Sending port-stats request to switch {} Id={}", iofSwitch, switchId);
			}
		}

		// Aggregate statistics request for each port per switch.
		Set<Short> phyPortNumbers = new HashSet<Short>();
		for (OFSwitchPort port : iofSwitch.getPorts()) {
			short pyhPortNumber = port.getOFPhysicalPort().getPortNumber();
			if (!phyPortNumbers.contains(pyhPortNumber)) {
				phyPortNumbers.add(pyhPortNumber);
			}
		}
		
		for (short phyPortNumber : phyPortNumbers) {
			OFStatisticsRequest req = new OFStatisticsRequest();
			req.setStatisticType(OFStatisticsType.AGGREGATE);
			int requestLength = req.getLengthU();
			
			OFAggregateStatisticsRequest aggregateStatsReq = new OFAggregateStatisticsRequest();
			OFMatch match = new OFMatch();
			// All flows, i.e. all matches.
			match.setWildcards(0xffffffff);
			aggregateStatsReq.setMatch(match);
			// Specific port
			aggregateStatsReq.setOutPort(phyPortNumber);
			// All tables.
			aggregateStatsReq.setTableId(TABLE_ALL);
			req.setStatistics(Collections.singletonList((OFStatistics)aggregateStatsReq));
			requestLength += aggregateStatsReq.getLength();
			// Set request length
			req.setLengthU(requestLength);

			try {
				Future<List<OFStatistics>> future = iofSwitch.queryStatistics(req);
				List<OFStatistics> values = future.get(DEFAULT_SWITCH_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
				
				// Store the replied information in the local statistics cache.            
				for (OFStatistics stats : values) {
					OFAggregateStatisticsReply statsReply = (OFAggregateStatisticsReply) stats;
					if (statsReply.getFlowCount() > 0) {
						this.storeOrUpdateToStatsCache(switchId, phyPortNumber, statsReply);
					} else {
						this.removeFromStatsCache(switchId, phyPortNumber);
					}
				}
            
			} catch (Exception e) {
				log.error("Failure to send aggregate stats request to switch {}, {}", iofSwitch, e);
			}
		}
		
	}
	
	/**
	 * 
	 * @param switchId The unique ID of the switch to query.
	 */
	protected void queryFlowStats(long switchId) {
		/* The switch we want to query. */
		IOFSwitch iofSwitch = floodlightProvider.getSwitch(switchId);
		
		if ((iofSwitch == null) || (!iofSwitch.isConnected())) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to send flow-stats request to switch Id {}", switchId);
			}
			return;
		} else {
			if (log.isTraceEnabled()) {
				log.trace("Sending flow-stats request to switch {} Id={}", iofSwitch, switchId);
			}
		}
		
		OFStatisticsRequest req = new OFStatisticsRequest();
		req.setStatisticType(OFStatisticsType.FLOW);
		int requestLength = req.getLengthU();
					
		OFFlowStatisticsRequest flowStatsReq = new OFFlowStatisticsRequest();
		OFMatch match = new OFMatch();
		// All flows, i.e. all matches.
		match.setWildcards(FlowCacheObj.WILD_ALL);
		flowStatsReq.setMatch(match);
		// Specific port
		flowStatsReq.setOutPort(OFPort.OFPP_NONE.getValue());
		// All tables.
		flowStatsReq.setTableId(TABLE_ALL);
		req.setStatistics(Collections.singletonList((OFStatistics)flowStatsReq));
		requestLength += flowStatsReq.getLength();
		// Set request length
		req.setLengthU(requestLength);
		
		try {
			Future<List<OFStatistics>> future = iofSwitch.queryStatistics(req);
			List<OFStatistics> values = future.get(DEFAULT_SWITCH_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
		
			// Store the replied information in the flow cache.
			for (OFStatistics stats : values) {
				FlowCacheQueryResp fcqr = null;
				OFFlowStatisticsReply statsReply = (OFFlowStatisticsReply) stats;
				Future<FlowCacheQueryResp> flowCacheFuture = this.flowCache.queryDB(new FlowCacheQuery(null, "statisticsCollector", null, iofSwitch.getId(), statsReply.getMatch()));
				try {
					fcqr = flowCacheFuture.get(DEFAULT_FLOW_CACHE_QUERY_TIMEOUT_SEC, TimeUnit.SECONDS);
				} catch (InterruptedException | ExecutionException | TimeoutException e) {
					if (log.isDebugEnabled()) {
						log.debug("Flow not found: " + e);
					}
					// Flow not found. Thus, trigger flow cache update.
					this.flowCache.querySwitchFlowTable(iofSwitch.getId());
					return;
				}
				
				// If we have found the flow cache object, add or update the statistics.
				if (fcqr != null && fcqr.flowCacheObjList.size() == 1) {
					FlowCacheObj fco = fcqr.flowCacheObjList.get(0);
					if (fco.hasAttribute(FlowCacheObj.Attribute.STATISTIC)) {
						((StatisticEntry) fco.getAttribute(FlowCacheObj.Attribute.STATISTIC)).updateStatistics(statsReply);
					} else {
						fco.setAttribute(FlowCacheObj.Attribute.STATISTIC, new StatisticEntry(statsReply));
					}
				}
				
			}
		} catch (Exception e) {
			log.error("Failure to send flow stats request to switch {}, {}", iofSwitch, e);
			e.printStackTrace();
		}
	}
	
	/**
	 * Stores or updates a statistics reply message to a switch-port pair.
	 * 
	 * @param switchId The switch ID of the queried switch.
	 * @param port The port ID of the queried port.
	 * @param statsReply The received statistics reply message.
	 */
	private void storeOrUpdateToStatsCache(long switchId, short port, OFAggregateStatisticsReply statsReply) {
		if (!statsCache.containsKey(switchId)) {
			statsCache.put(switchId, new ConcurrentHashMap<Short, StatisticEntry>());
		} 
		
		// Add stats to stats cache.
		if (statsCache.get(switchId).containsKey(port)) {
			// Update existing entry.
			statsCache.get(switchId).get(port).updateStatistics(statsReply);
			if (log.isDebugEnabled()) {
				log.debug("Updated statistic entry for switch-port {}-{}.", HexString.toHexString(switchId), port);
			}
		} else {
			// Store new entry.
			statsCache.get(switchId).put(port, new StatisticEntry(statsReply));
			if (log.isDebugEnabled()) {
				log.debug("Stored statistic entry for switch-port {}-{}.", HexString.toHexString(switchId), port);
			}
		}
	}
	
	/**
	 * Removes a statistic entry from the cache. Moreover it removes
	 * the corresponding maps if they are empty.
	 * 
	 * @param switchId The switch ID of the queried switch.
	 * @param port The port ID of the queried port.
	 * @param statsReply The received statistics reply message.
	 */
	private StatisticEntry removeFromStatsCache(long switchId, short port) {
		if (!statsCache.containsKey(switchId)) {
			return null;
		}
		
		// Remove stats from stats cache.
		StatisticEntry stats = statsCache.get(switchId).remove(port);
		
		// Remove empty hash maps.
		if (statsCache.get(switchId).isEmpty()) {
			statsCache.remove(switchId);
		}
		
		if (log.isDebugEnabled() && stats != null) {
			log.debug("Removed a statistic entry from the stats-cache for switch-port {}-{}.", HexString.toHexString(switchId), port);
		}
			
		// Return stats or null.
		return stats;
	}
 
}
