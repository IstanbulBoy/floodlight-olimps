package net.floodlightcontroller.flowcache;

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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.flowcache.FlowCacheObj.Status;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.threadpool.IThreadPoolService;


import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A (more-or-less) generic persistent flow cache.
 * 
 * Based on ideas in the BetterFlowCache.class of the OpenDaylight SDNPlattform by BigSwitch.
 * 
 * @author subrata
 * @author Michael Bredel <michael.bredel@cern.ch>
 * 
 * TODO: Implement a mechanism to reuse flow cache objects. This could improve the performance. See
 *        BetterFlowCache by BigSwitch. To this end, don't delete flow cache objects directly, but mark them as
 *        unused. Have task worker that removes unused objects if necessary. Thus, do flow reconciliation.
 */
public class FlowCache implements IFloodlightModule, IFlowCacheService, IOFMessageListener, IOFSwitchListener {
    
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(FlowCache.class);
    /** */
    protected IFloodlightProviderService floodlightProvider;
    /** */
    protected IDeviceService deviceManager;
    /** */
    protected IThreadPoolService threadPool;
    /** Flow query task to scan switches for their flow tables. */
    protected SendPeriodicFlowQueryToSwitches flowQueryTask;
    /** */
    protected FlowReconcileQueryTask flowReconcileQueryTask;
    /** The pending query list. */
    protected BlockingQueue<PendingQuery> pendingQueryList;
    /** A Map of all flow cache databases. ApplicationName -> FlowCacheDB. One database per application. */
    protected ConcurrentHashMap<String, IFlowCacheDB> flowCacheDBs;
    /** Override entries if they have the same match hashes, even if the flow cache objects differ. Default is true. */
    protected boolean overrideEntries = true;
    
    /**
     * Scans one switch periodically for new flows in its flow table
     */
    protected class SwitchFlowTablePeriodicScanTask implements Runnable {
    	/** The switch id of the switch to query. */
        protected long switchId;
        /** The callback handler for returning OFStatisticsReply messages. */
        protected IOFMessageListener callbackHandler;

        /**
         * Default constructor.
         * 
         * @param switchId The switch id of the switch to query.
         * @param callbackHandler The callback handler for returning OFStatisticsReply messages.
         */
        protected SwitchFlowTablePeriodicScanTask(long switchId, IOFMessageListener callbackHandler) {
            this.switchId = switchId;
            this.callbackHandler = callbackHandler;
        }

        @Override
        public void run() {
            querySwitchStats(switchId, callbackHandler);
        }
    }
    
    /**
     * Scans all available switches periodically for new flows in its flow table
     */
    protected class SendPeriodicFlowQueryToSwitches implements Runnable {
    	/** The callback handler for returning OFStatisticsReply messages. */
        private IOFMessageListener callbackHandler;
        /** */
        protected boolean enableFlowQueryTask = false;
        
        /**
         * Default constructor.
         * 
         * @param callbackHandler The callback handler for returning OFStatisticsReply messages.
         */
        protected SendPeriodicFlowQueryToSwitches(IOFMessageListener callbackHandler) {
            this.callbackHandler = callbackHandler;
            this.enableFlowQueryTask = true;
        }
        
        public boolean isEnableFlowQueryTask() {
            return enableFlowQueryTask;
        }

        public void setEnableFlowQueryTask(boolean enableFlowQueryTask) {
            this.enableFlowQueryTask = enableFlowQueryTask;
        }

        @Override
        public void run() {
            if (!enableFlowQueryTask) 
            	return;
            
            Set<Long> switchIds = floodlightProvider.getAllSwitchDpids();
            if (switchIds == null || switchIds.isEmpty()) {
                return;
            }
            
            int numSwitches = switchIds.size();
            
            if (numSwitches == 0) {
            	return;
            }
            
            if (log.isTraceEnabled()) {
                log.trace("Sending flow scan messages to switches {}", switchIds);
            }
            
            int interval_ms = SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC / numSwitches;
            int idx = 0;
            for (Long switchId : switchIds) {
                SwitchFlowTablePeriodicScanTask scanTask = new SwitchFlowTablePeriodicScanTask(switchId, callbackHandler);
                // Schedule the queries to different switches in a staggered way.
                threadPool.getScheduledExecutor().schedule(scanTask, interval_ms*idx, TimeUnit.MILLISECONDS);
                idx++;
            }
        }
    }
    
    /**
     * The Class PendingQuery. Represents a FCQueryObj and
     * the timestamp of its instantiation.
     */
    protected class PendingQuery {
        /** The query obj. */
        protected FlowCacheQuery  queryObj;
        /** The query receive time stamp. */
        protected long queryRcvdTimeStamp_ms;
        /** The pending switch response. */
        //protected ArrayList<PendingSwitchResp> pendingSwitchResp;

        /**
         * Default Constructor. Instantiates a new pending query.
         *
         * @param query the query
         */
        protected PendingQuery(FlowCacheQuery query) {
            queryObj = query;
            queryRcvdTimeStamp_ms = System.currentTimeMillis();
        }
    }
    
    /**
     * Worker task that handles asynchronous flow queries.
     * 
     * TODO: Why Thread and not Runnable?
     */
    protected class FlowReconcileQueryTask extends Thread {
    	/** Pending query object that is handled. */
    	PendingQuery pendingQuery;
    	/** Corresponding query object. */
    	FlowCacheQuery queryObj;
    	
    	@Override
    	public void run() {
            while (true) {
				try {
					pendingQuery = pendingQueryList.take();
					queryObj = pendingQuery.queryObj;
					FlowCacheQueryResp queryResp = new FlowCacheQueryResp(queryObj);

					if (log.isTraceEnabled()) {
						log.trace("Handle query: {}", queryObj);
					}
					
					// Do the actual query, i.e. search for corresponding flow cache entries.
					IFlowCacheDB flowCacheDb;
					if (queryObj.applInstName != null && flowCacheDBs.containsKey(queryObj.applInstName)) {
						 flowCacheDb = flowCacheDBs.get(queryObj.applInstName);
					} else {
						flowCacheDb = flowCacheDBs.get(FlowCache.DEFAULT_DB_NAME);
					}
					
					queryResp.switchId = queryObj.switchId;
					Map<Long, Set<FlowCacheObj>> resultMap = flowCacheDb.queryDB(queryObj.switchId, queryObj);
					if (resultMap != null) {
						queryResp.flowCacheObjList.addAll(resultMap.get(queryObj.switchId));
					} else {
						continue;
					}

					// Pass query result to query handler.
					if (queryObj.fcQueryHandler != null) {
						queryObj.fcQueryHandler.flowQueryRespHandler(queryResp);
					}
				} catch (Exception e) {
					log.warn("Exception in doReconcile(): {}", e.getMessage());
					e.printStackTrace();
				}
			}
            	
    	}
    }
    
    /**
     * TESTING
     * 
     * @author Michael Bredel <michael.bredel@cern.ch>
     */
    public class SearcherCallable implements Callable<FlowCacheQueryResp> {
    	/** */
    	private FlowCacheQuery fcq;
    	/** */
    	private FlowCacheQueryResp resp;
    	
    	/**
    	 * Constructor.
    	 * 
    	 * @param fcq
    	 */
    	public SearcherCallable(FlowCacheQuery fcq) {
    		this.fcq = fcq;
    		this.resp = new FlowCacheQueryResp(fcq);
    	}

		@Override
		public FlowCacheQueryResp call() throws Exception {
			// Do the actual query, i.e. search for corresponding flow cache entries.
			IFlowCacheDB flowCacheDb;
			if (fcq.applInstName != null && flowCacheDBs.containsKey(fcq.applInstName)) {
				 flowCacheDb = flowCacheDBs.get(fcq.applInstName);
			} else {
				flowCacheDb = flowCacheDBs.get(FlowCache.DEFAULT_DB_NAME);
			}
			
			if (fcq.switchId != 0) {
				// Query one switch.
				resp.switchId = fcq.switchId;
				Map<Long, Set<FlowCacheObj>> resultMap = flowCacheDb.queryDB(fcq.switchId, fcq);
				if (resultMap != null) {
					resp.flowCacheObjList.addAll(resultMap.get(fcq.switchId));
				}
			} else {
				resp.switchId = 0;
				// Query all switches.
				for (long switchId : floodlightProvider.getAllSwitchDpids()) {
					Map<Long, Set<FlowCacheObj>> resultMap = flowCacheDb.queryDB(switchId, fcq);
					if (resultMap != null) {
						resp.flowCacheObjList.addAll(resultMap.get(switchId));
						// TODO: Dirty hack. We should return a list of FlowCacheQueryResp objects, or they should contain a list of switches.
						resp.switchId = switchId;
					}
				}
			}
			
			// Return query result.
			return (!resp.flowCacheObjList.isEmpty()) ? resp : null;
		}
    	
    }

	@Override
	public String getName() {
		return "flowcache";
	}
	
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFlowCacheService.class);
        return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IFlowCacheService.class, this);
        return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IDeviceService.class);
        l.add(IThreadPoolService.class);
        return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        deviceManager = context.getServiceImpl(IDeviceService.class);
        threadPool = context.getServiceImpl(IThreadPoolService.class);
		flowQueryTask = new SendPeriodicFlowQueryToSwitches(this);
		flowReconcileQueryTask = new FlowReconcileQueryTask();
		pendingQueryList = new LinkedBlockingQueue<PendingQuery>();
		flowCacheDBs = new ConcurrentHashMap<String, IFlowCacheDB>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFSwitchListener(this);
		floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
		floodlightProvider.addOFMessageListener(OFType.STATS_REPLY, this);
		threadPool.getScheduledExecutor().scheduleAtFixedRate(
				flowQueryTask,
				SWITCH_FLOW_TBL_SCAN_INITIAL_DELAY_MSEC, 
				SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC,
                TimeUnit.MILLISECONDS);
		flowReconcileQueryTask.start();
		// Register a default flow cache database, e.g. for flow-mods already on the switch
		//this.registerFlowCacheDB(DEFAULT_DB_NAME, new ExtendedFlowCacheDB(DEFAULT_DB_NAME)); // ExtendedFlowCacheDB still has errors.
		this.registerFlowCacheDB(DEFAULT_DB_NAME, new FlowCacheDB(DEFAULT_DB_NAME));
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
        	case FLOW_REMOVED:
        		OFFlowRemoved flowRemMsg = (OFFlowRemoved) msg;
        		processFlowModRemovalMsg(sw, flowRemMsg, cntx);
        		break;
        	case STATS_REPLY:
        		OFStatisticsReply statsReplyMsg = (OFStatisticsReply) msg;
        		processStatsReplyMsg(sw, statsReplyMsg, cntx);
        		break;
        	default:
        		if (log.isDebugEnabled()) {
        			log.debug("Ignoring mesg type: {}", msg.getType());
        		}
        		break;
		}
		return Command.CONTINUE;
	}	

	@Override
	public void switchAdded(long switchId) {
		if (log.isTraceEnabled()) {
            log.trace("Handling switch added notification for {}", switchId);
        }

        // Query the switch for its flow entries.
        querySwitchFlowTable(switchId);
        return;
	}

	@Override
	public void switchRemoved(long switchId) {
		if (log.isTraceEnabled()) {
            log.trace("Handling switch removed notification for {}", switchId);
        }
        /* Delete all the flows in the flow cache that has this removed switch
         * as the source switch as we are not going to get flow-mod removal
         * notifications from this switch. If the switch reconnects later with
         * active entries then we would query the flow table of the switch and
         * re-populate the flow cache. This is done in addedSwitch method.
         */
        this.deleteFlowCacheBySwitch(switchId);
	}

	@Override
	public void switchActivated(long switchId) {
		// TODO Auto-generated method stub
	}

	@Override
	//public void switchPortChanged(long switchId, ImmutablePort port, PortChangeType type) {
	public void switchPortChanged(long switchId, OFSwitchPort port, PortChangeType type) {
		// TODO Auto-generated method stub
	}

	@Override
	public void switchChanged(long switchId) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void registerFlowCacheDB(String appName, IFlowCacheDB flowCacheDB) {
		flowCacheDBs.put(appName, flowCacheDB);
	}
	
	@Override
	public IFlowCacheDB unRegisterFlowCacheDB(String appName) {
		return flowCacheDBs.remove(appName);
	}

	@Override
	public synchronized FlowCacheObj addFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions) {
		/* Create a flow cache object. */
		FlowCacheObj fco = new FlowCacheObj(cookie, priority, match, actions);
		// Check if we already have non-active flow cache object.
		if (this.hasFlow(appName, switchId, fco)) {
			fco = this.getFlowCacheDB(appName).getEntry(switchId, fco);
			fco.setActions(actions);
			fco.setCookie(cookie);
			return fco;
		}
		// Store the new flow object in the corresponding flow cache database
		return this.addFlow(appName, switchId, fco);
	}
	
	/**
	 * Convenience method to add a new flow from the flow cache. It returns the corresponding
	 * flow cache object or null if no flow was stored.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param fco flow cache object to add.
	 * @return <b>FlowCacheObj</b> The flow cache object that was stored in the flow cache - or null if no flow was stored.
	 */
	protected synchronized FlowCacheObj addFlow(String appName, long switchId, FlowCacheObj fco) {
		// Store the new flow object in the corresponding flow cache database
		if (appName != null && flowCacheDBs.containsKey(appName)) {
			if (this.flowCacheDBs.get(appName).storeEntry(switchId, fco, overrideEntries)){
				return fco;
			} else {
				return null;
			}
		} else {
			// TODO: Try to find the application name using the cookie (if any).
			if (log.isWarnEnabled()) {
				log.warn("addFlow: No flow cache db found. Storing entry in db: " + DEFAULT_DB_NAME);
			}
			if (this.flowCacheDBs.get(DEFAULT_DB_NAME).storeEntry(switchId, fco, overrideEntries)) {
				return fco;
			} else {
				return null;
			}
		}
	}

	@Override
	public synchronized FlowCacheObj removeFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions) {
		// Create a flow cache object that is identical to the one we want to delete.
		FlowCacheObj fco = new FlowCacheObj(cookie, priority, match, actions);
		// Remove the flow cache object from the actual database and return.
		return this.getFlowCacheDB(appName).removeEntry(switchId, fco);
	}

	/**
	 * Convenience method to remove a flow from the flow cache. It returns the corresponding
	 * flow cache object or null if no flow was removed.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param fco flow cache object to remove.
	 * @return <b>FlowCacheObj</b> The flow cache object that was removed from the flow cache - or null if no flow was removed.
	 */
	protected synchronized FlowCacheObj removeFlow(String appName, long switchId, FlowCacheObj fco) {
		// Remove the flow cache object from the actual database and return.
		return this.getFlowCacheDB(appName).removeEntry(switchId, fco);
	}
	
	@Override
	public FlowCacheObj getFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions) {
		// Create a flow cache object that is identical to the one we want to delete.
		FlowCacheObj fco = new FlowCacheObj(cookie, priority, match, actions);
		// Remove the flow cache object from the actual database and return.
		return this.getFlowCacheDB(appName).getEntry(switchId, fco);
	}
	
	/**
	 * Convenience method to get a flow from the flow cache. It returns the corresponding
	 * flow cache object or null if no flow was found.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param fco flow cache object to remove.
	 * @return <b>FlowCacheObj</b> The flow cache object that was removed from the flow cache - or null if no flow was removed.
	 */
	protected synchronized FlowCacheObj getFlow(String appName, long switchId, FlowCacheObj fco) {
		// Remove the flow cache object from the actual database and return.
		return this.getFlowCacheDB(appName).getEntry(switchId, fco);
	}
	
	@Override
	public boolean hasFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions) {
		// Create a flow cache object that is identical to the one we want to delete.
		FlowCacheObj fco = new FlowCacheObj(cookie, priority, match, actions);
		// Check whether the flow cache object is present in  the actual database or not.
		return this.getFlowCacheDB(appName).hasEntry(switchId, fco);
	}

	/**
	 * Convenience method to check whether a flow cache object is present in the flow cache.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param fco flow cache object to check..
	 * @return <b>boolean</b> True if the the flow cache object is present in the flow cache.
	 */
	protected boolean hasFlow(String appName, long switchId, FlowCacheObj fco) {
		return this.getFlowCacheDB(appName).hasEntry(switchId, fco);
	}
	
	@Override
	public Map<Long, Set<FlowCacheObj>> getAllFlows(String appName) {
		return this.getFlowCacheDB(appName).getAllEntries();
	}
	
	@Override
	public Map<Long, Set<FlowCacheObj>> getAllFlows() {
		/* The resulting map containing all flow cache objects: SwitchId -> SetOf FlowCacheObj. */
		HashMap<Long, Set<FlowCacheObj>> result = new HashMap<Long, Set<FlowCacheObj>>();
		
		// For all flow cache databases.
		for (IFlowCacheDB flowCacheDb : flowCacheDBs.values()) {
			Map<Long, Set<FlowCacheObj>> allEntries = flowCacheDb.getAllEntries();
			
			// Add all entries of the database to the resulting map.
			for (long switchId : allEntries.keySet()) {
				if (!result.containsKey(switchId)) {
					result.put(switchId, new HashSet<FlowCacheObj>());
				}
				result.get(switchId).addAll(allEntries.get(switchId));
			}
		}
		
		return result;
	}
	
	@Override
	public void deleteFlowCacheBySwitch(long switchId) {
        for (IFlowCacheDB flowCacheDB : flowCacheDBs.values()) {
        	// Get all entries of that database: SwitchID -> SetOf FlowCacheObj.
        	Map<Long, Set<FlowCacheObj>> allEntries = flowCacheDB.getAllEntries();
        	
        	if (allEntries != null && allEntries.get(switchId) != null) {
        		for (FlowCacheObj fco : allEntries.get(switchId)) {
        			flowCacheDB.removeEntry(switchId, fco);
        		}
        	}
        	
        }
	}
	
	@Override
	public void submitFlowCacheQuery(FlowCacheQuery flowCacheQuery) {
		if (log.isDebugEnabled()) {
            log.debug("submit Query: {}", flowCacheQuery);
        }
		
		PendingQuery pq = new PendingQuery(flowCacheQuery);
        boolean retCode = pendingQueryList.offer(pq);
        if (!retCode) {
            log.warn("Failed to post a query {}", pq);
        }
	}
	
	@Override
	public Future<FlowCacheQueryResp> queryDB(FlowCacheQuery flowCacheQuery) {
		return this.threadPool.getScheduledExecutor().submit(new SearcherCallable(flowCacheQuery));
	}
	
	@Override
	public List<Future<FlowCacheQueryResp>> queryDB(List<FlowCacheQuery> flowCacheQueryList) {
		/* The task list. */
		Set<SearcherCallable> callableSet = new HashSet<SearcherCallable>();
		
		// Populate the task list with callable objects.
		for(FlowCacheQuery fcq : flowCacheQueryList) {
			callableSet.add(new SearcherCallable(fcq));
		}
		
		try {
			return this.threadPool.getScheduledExecutor().invokeAll(callableSet, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT_UNIT);
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void querySwitchFlowTable(long switchId) {
		querySwitchStats(switchId, this);
	}
	
	/**
	 * Queries the flow table of a specific switch by sending a flow statistics request.
	 * The flow statistic responses have to be handled by the callback Hander listening
	 * to STATS_REPLY messages.
	 * 
	 * @param switchId The unique ID of the switch to query.
	 * @param callbackHandler The callback hander handling the statistic response messages.
	 */
	protected void querySwitchStats(long switchId, IOFMessageListener callbackHandler) {
		/* */
		IOFSwitch sw = floodlightProvider.getSwitch(switchId);
		
		if ((sw == null) || (!sw.isConnected())) {
			if (log.isDebugEnabled()) {
				log.debug("Failed to send flow-stats request to switch Id {}", switchId);
			}
			return;
		} else {
			if (log.isTraceEnabled()) {
				log.trace("Sending flow scan request to switch {} Id={}", sw, switchId);
			}
		}
		
		OFStatisticsRequest req = new OFStatisticsRequest();
		req.setStatisticType(OFStatisticsType.FLOW);
		int requestLength = req.getLengthU();

		OFFlowStatisticsRequest specificReq = new OFFlowStatisticsRequest();
		OFMatch match = new OFMatch();
		match.setWildcards(FlowCacheObj.WILD_ALL);
		specificReq.setMatch(match);
		specificReq.setOutPort(OFPort.OFPP_NONE.getValue());
		specificReq.setTableId((byte) 0xff);
		req.setStatistics(Collections.singletonList((OFStatistics) specificReq));
		requestLength += specificReq.getLength();

		req.setLengthU(requestLength);
		try {
			sw.sendStatsQuery(req, sw.getNextTransactionId(), callbackHandler);
		} catch (Exception e) {
			log.error("Failure to send stats request to switch {}, {}", sw, e);
		}
	}
	
	
	/**
	 * Gets the names of all applications that stored the
	 * given flow cache object.
	 * 
	 * @param switchId he unique ID of the switch to query.
	 * @param fco The flow cache object we are looking for.
	 * @return A set of application names that have the given flow.
	 */
	protected Set<String> getAppNames(long switchId, FlowCacheObj fco) {
		/* A set of application names that hold the flow cache object. */
		HashSet<String> resultAppNames = new HashSet<String>();
		
		for (String appName : this.flowCacheDBs.keySet()) {
			if (this.hasFlow(appName, switchId, fco)) {
				resultAppNames.add(appName);
			}
		}
		
		// Return a list of applications containing the flow cache object, or null if not found.
		return (resultAppNames.size() > 0) ? resultAppNames : null;
	}
	
	/**
	 * Handles flow removed messages and removes flows from the flow cache. 
	 * Makes sure the flow is removed, in case if the installing application 
	 * does not handle flow remove messages properly, i.e. it stores the flow 
	 * in the flow cache, but does not remove it
	 * 
	 * @param sw The switch that has sent the flow removed message.
	 * @param flowRemMsg The actual message.
	 * @param cntx The Floodlight context.
	 */
	protected void processFlowModRemovalMsg(IOFSwitch sw, OFFlowRemoved flowRemMsg, FloodlightContext cntx) {
        if (log.isTraceEnabled()) {
            log.trace("Recvd. flow-mod removal message from switch {}, wildcard=0x{} fm={}",
                new Object[]{HexString.toHexString(sw.getId()), 
                             Integer.toHexString(flowRemMsg.getMatch().getWildcards()),
                             flowRemMsg.getMatch().toString()});
        }
        
        /* Get the unique switch id. */
        long switchId = sw.getId();
        
        // Create a new flow cache object.
        FlowCacheObj fco = new FlowCacheObj(flowRemMsg.getCookie(), flowRemMsg.getPriority(), flowRemMsg.getMatch(), null);
        // Remove flow from flow cache. TODO: Map cookie to application name! For better performance.
        Set<String> appNames = getAppNames(switchId, fco);
        if (appNames != null) {
        	for (String appName : appNames) {
        		this.removeFlow(appName, switchId, fco);
        	}
        }
	}
	
	/**
	 * Handles flow statistic reply messages. Checks if the flow table entries of the
	 * switch are already stored in the flow cache. If not, it installs new flow cache
	 * objects to the flow cache.
	 * 
	 * @param iofSwitch The switch that has sent the statistic reply message.
	 * @param statsReplyMsg The actual message.
	 * @param cntx The Floodlight context.
	 */
	protected void processStatsReplyMsg(IOFSwitch iofSwitch, OFStatisticsReply statsReplyMsg, FloodlightContext cntx) {
    	/* The yet unknown application name. */
    	String appName = null;
    	/* */
    	List<? extends OFStatistics> statsList = statsReplyMsg.getStatistics();
    	/* */
    	Set<Integer> statsMatches = new HashSet<Integer>();
    	/* The unique switch ID of the switch that has sent the statistic reply message. */
    	long switchId = iofSwitch.getId();
        
    	if (log.isTraceEnabled()) {
            log.trace("Recvd. stats reply message from {} count = {}", iofSwitch.getStringId(), statsReplyMsg.getStatistics().size());
        }
        
        for (OFStatistics stats : statsList) {
        	/* OF Flow statistics. */
        	OFFlowStatisticsReply statsReply = (OFFlowStatisticsReply) stats;
        	/* The flow match of the reply message. */
        	OFMatch match = statsReply.getMatch();
        	/* The priority of the reply message. */
        	short priority = statsReply.getPriority();
        	/* The cookie of the reply message. Used to find the matching application. */
        	long cookie = statsReply.getCookie();
        	/* A list of actions of the reply message. */
        	List<OFAction> actions = statsReply.getActions();
        	
        	/* 
        	 * Skip storing ARP packet in flow-cache since it is a fully specified
             * entry (no wildcard) and it is expected to expire in short time frame
             * (less than 5s). Note that ARP flow entries are programmed only for
             * ARP response packets from the ARP responder to the ARP query sender.
             */
            if (match.getDataLayerType() == Ethernet.TYPE_ARP) {
                continue;
            }
            
        	// Add stats matches to matches list
        	statsMatches.add(FlowCacheObj.wildcardMatch(match).hashCode());
            
            // Create a new flow cache object.
            FlowCacheObj fco = new FlowCacheObj(cookie, priority, match, actions);
            
            // Check if this flow cache object is NOT known already. TODO: Map cookie to application name! For better performance.
            if (this.getAppNames(switchId, fco) == null || this.getAppNames(switchId, fco).isEmpty()) {
            	// Set the flow cache object as "ACTIVE".
            	fco.setStatus(Status.ACTIVE);
            	// Store flow cache object in database.
            	this.getFlowCacheDB(appName).storeEntry(switchId, fco, overrideEntries);
            } else {
            	// If it is known already, get the object and mark it as "ACTIVE".
            	this.getFlowCacheDB(appName).getEntry(switchId, fco).setStatus(Status.ACTIVE);
            }    
        }
        
        // Check: If not found in stats reply: Mark active flow cache objects as "UNCERTAIN", remove "UNCERTAIN" objects.
        Map<Long, Set<FlowCacheObj>> allEntries = this.getFlowCacheDB(appName).getAllEntries();
        if (statsMatches.size() > 0) {
        	for (FlowCacheObj fco : allEntries.get(switchId)) {
        		if (!statsMatches.contains(fco.getMatch().hashCode())) {
        			// The flow stored in the flow cache is not contained in the stats reply.
        			if (fco.getStatus() == Status.UNCERTAIN) {
        				// Remove flow.
        				this.removeFlow(appName, switchId, fco);
            		} else {
            			// Mark flow as UNCERTAIN and query switch again.
            			fco.setStatus(Status.UNCERTAIN);
            			this.querySwitchFlowTable(switchId);
            		}
        		}
        	}
        }
	}
	
	/**
	 * Gets the application specific flow cache database, if any. Or the default flow cache database.
	 * 
	 * @param appName The name of the application.
	 * @return <b>IFlowCacheDB</b> The actual flow cache database associated with a name - or the default flow cache database.
	 */
	private IFlowCacheDB getFlowCacheDB(String appName) {
		// TODO: Map cookie to application name! For better performance and for stats reply messages.
		if (appName != null && flowCacheDBs.containsKey(appName)) {
    		return flowCacheDBs.get(appName);
    	} else {
    		if (log.isDebugEnabled()) {
    			log.debug("processStatsReplyMsg: No flow cache db found. Try to find entry in db: " + DEFAULT_DB_NAME);
    		}
    		return flowCacheDBs.get(DEFAULT_DB_NAME);
    	}
	}
	
}
