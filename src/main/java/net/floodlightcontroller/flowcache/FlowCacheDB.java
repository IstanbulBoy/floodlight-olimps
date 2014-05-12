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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bouncycastle.util.Arrays;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A very generic flow cache database. Hence, it is also a very slow
 * database with respect to queries.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class FlowCacheDB implements IFlowCacheDB {
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(FlowCacheDB.class);
    
    /** The name of the application that uses this flow cache database. */
    protected String appName;
	/** SwitchId -> MatchHash -> FlowCacheObj. */
    protected ConcurrentHashMap<Long, ConcurrentHashMap<Integer, FlowCacheObj>> flowCacheMatchHashMap;
    
	/**
	 * Default constructor instantiates this flow cache 
	 * database for a given application. 
	 * 
	 * @param appName
	 */
    public FlowCacheDB(String appName) {
    	this.appName = appName;
    	this.flowCacheMatchHashMap = new ConcurrentHashMap<Long, ConcurrentHashMap<Integer, FlowCacheObj>>();
    	
    	if (log.isDebugEnabled()) {
    		log.debug("New flow cache database created for {}", appName);
    	}
    }
    
    @Override
    public synchronized boolean storeEntry(long switchId, FlowCacheObj fco, boolean overrideEntries) {
    	/* Get the hash of the match object taking its wildcards into account. */
		int matchHash = fco.getId();
		
		// Add new switch to flow the flowCacheMatchHashMap.
		if (!flowCacheMatchHashMap.containsKey(switchId)) {
			flowCacheMatchHashMap.put(switchId, new ConcurrentHashMap<Integer, FlowCacheObj>());
		}

		// Get switch's flow cache map.
		ConcurrentHashMap<Integer, FlowCacheObj> switchFlowCacheMap = flowCacheMatchHashMap.get(switchId);

		// Add flow match to flow cache: matchHash -> FlowCacheObj
		if (!switchFlowCacheMap.containsKey(matchHash)) {
			switchFlowCacheMap.put(matchHash, fco);
			return true;
		} else {
			// To be sure: Check the identity of flow cache objects.
			if (!switchFlowCacheMap.get(matchHash).equals(fco)) {
				if (log.isDebugEnabled()) {
					log.debug("storeEntry: Flow cache objects for switch {} are not identical, although there hashes are!", HexString.toHexString(switchId));
				}
				if (overrideEntries) {
					switchFlowCacheMap.put(matchHash, fco);
					if (log.isDebugEnabled()) {
						log.debug("storeEntry: Overriding exsting entry.");
					}
					return true;
				} else {
					return false;
				}
			} else {
				return true;
			}
		}
    }
    
    @Override
	public synchronized FlowCacheObj getEntry(long switchId, FlowCacheObj fco) {
		return this.getEntry(switchId, fco.getId());
	}
	
    /**
	 * Simple getter for flow cache object entries
	 * 
	 * @param switchId The switchID that is associated with this flow cache object.
	 * @param matchHash The flow cache object hash of the flow to get.
	 * @return <b>FlowCacheObj</b> The flow cache entry.
	 */
	protected synchronized FlowCacheObj getEntry(long switchId, int matchHash) {
		/* The removed flow cache object. */
		FlowCacheObj getFco = null;
		
		// Get switch's flow cache map.
		ConcurrentHashMap<Integer, FlowCacheObj> switchFlowCacheMap =  flowCacheMatchHashMap.get(switchId);
		if (switchFlowCacheMap == null) {
			if (log.isDebugEnabled()) {
				log.debug("getEntry: No flow cache map found for switch {}", HexString.toHexString(switchId));
			}
			// Return null.
			return getFco;
		}
				
		// Remove flow cache object. TODO: improve performance by object reuse.
		if (switchFlowCacheMap.containsKey(matchHash)) {
			getFco = switchFlowCacheMap.get(matchHash);
		} else {
			if (log.isDebugEnabled()) {
				log.debug("getEntry: No flow cache object found for switch {}", HexString.toHexString(switchId) + ": hash " + matchHash);
			}
			// Return null.
			return getFco;
		}
				
		return getFco;
	}

	@Override
    public synchronized FlowCacheObj removeEntry(long switchId, FlowCacheObj fco) {
    	/* Get the hash of the match object taking its wildcards into account. */
		int matchHash = fco.getId();
		/* The removed flow cache object. */
		FlowCacheObj removedFco = null;
		
		// Get switch's flow cache map.
		ConcurrentHashMap<Integer, FlowCacheObj> switchFlowCacheMap =  flowCacheMatchHashMap.get(switchId);
		if (switchFlowCacheMap == null) {
    		if(log.isWarnEnabled()) {
    			log.warn("removeEntry: Could not remove flow cache object {} for switch {} from standard match hash db. Object not found. ", fco, HexString.toHexString(switchId));
    		}
			// Return null.
			return removedFco;
		}
		
		// Remove flow cache object. TODO: improve performance by object reuse.
		if (switchFlowCacheMap.containsKey(matchHash)) {
			removedFco = switchFlowCacheMap.remove(matchHash);
		} else {
    		if(log.isWarnEnabled()) {
    			log.warn("removeEntry: Could not remove flow cache object {} for switch {} from standard match hash db. Object not found. ", fco, HexString.toHexString(switchId));
    		}
			// Return null.
			return removedFco;
		}
		
		// Check for empty maps and remove them. TODO: improve performance by object reuse.
		if (switchFlowCacheMap.isEmpty()) {
			flowCacheMatchHashMap.remove(switchId);
		}
    	return removedFco;
    }
    
    @Override
    public boolean hasEntry(long switchId, FlowCacheObj fco) {
    	return this.hasEntry(switchId, fco.getId());
    }
    
    /**
	 * Checks if the database has a specific flow cache object.
	 * 
	 * @param switchId The switchID that is associated with this flow cache object.
	 * @param matchHash The flow cache object hash of the object to be checked.
	 * @return <b>boolean</b> True iff the object is in the flow cache database.
	 */
    protected boolean hasEntry(long switchId, int matchHash) {
    	if (!flowCacheMatchHashMap.containsKey(switchId))
			return false;
		
		// Set of match hashes of flows installed on the switch.
		Set<Integer> hashSet = flowCacheMatchHashMap.get(switchId).keySet();
		
		if (hashSet != null && hashSet.contains(matchHash)) {
			return true;
		} else {
			return false;
		}
    }

    @Override
    public boolean isEmpty() {
    	return flowCacheMatchHashMap.isEmpty();
    }
    
    @Override
    public Map<Long, Set<FlowCacheObj>> getAllEntries() {
    	/* New HashMap that contains all flow cache objects. */
		HashMap<Long, Set<FlowCacheObj>> resultMap = new HashMap<Long, Set<FlowCacheObj>>();
		
		for (Long switchId : this.flowCacheMatchHashMap.keySet()) {
			if (!resultMap.containsKey(switchId)) {
				resultMap.put(switchId, new HashSet<FlowCacheObj>());
			}
			resultMap.get(switchId).addAll(this.flowCacheMatchHashMap.get(switchId).values());
		}
		
		return resultMap;
    }

	@Override
	public synchronized Map<Long, Set<FlowCacheObj>> queryDB(long switchId, FlowCacheQuery query) {
		/* New HashMap that contains all flow cache objects that match the query: switchId -> SetOf FlowCacheObj. */
		HashMap<Long, Set<FlowCacheObj>> resultMap = new HashMap<Long, Set<FlowCacheObj>>();
		
		if (!this.flowCacheMatchHashMap.containsKey(switchId)) {
			if (log.isDebugEnabled()) {
				log.debug("Switch ID {} not found.", HexString.toHexString(switchId));
			}
			return null;
		}
		
		// Add all flow cache objects of the switch to the resulting map.
		if (!resultMap.containsKey(switchId)) {
			resultMap.put(switchId, new HashSet<FlowCacheObj>());
		}
		resultMap.get(switchId).addAll(this.flowCacheMatchHashMap.get(switchId).values());
		
		// Remove unwanted entries - according to the flow cache query.
		for (Iterator<FlowCacheObj> iter = resultMap.get(switchId).iterator(); iter.hasNext();) {
			// Get the flow cache object.
			FlowCacheObj fco = iter.next();
			
			// Cookie
			if (query.cookie != 0 && query.cookie != fco.getCookie()) {
				iter.remove();
				continue;
			}
			// priority
			if (query.priority != 0 && query.priority != fco.getPriority()) {
				iter.remove();
				continue;
			}
			// OutPort
			if (query.outPort != 0 && (fco.getOutPorts() == null || !fco.getOutPorts().contains(query.outPort))) {
				iter.remove();
				continue;
			}
			// InPort
			if (query.inPort != 0 && (fco.getMatch() == null || query.inPort != fco.getMatch().getInputPort())) {
				iter.remove();
				continue;
			}
			// DL_DST
			if (query.dataLayerDestination != null && !Arrays.areEqual(query.dataLayerDestination, fco.getMatch().getDataLayerDestination())) {
				iter.remove();
				continue;
			}
			// DL_SRC
			if (query.dataLayerSource != null && !Arrays.areEqual(query.dataLayerSource, fco.getMatch().getDataLayerSource())) {
				iter.remove();
				continue;
			}
			// DL_VLAN
			if (query.dataLayerVirtualLan != 0 && query.dataLayerVirtualLan != fco.getMatch().getDataLayerVirtualLan()) {
				iter.remove();
				continue;
			}
			// DL_VLAN_PCP
			if (query.dataLayerVirtualLanPriorityCodePoint != 0 && query.dataLayerVirtualLanPriorityCodePoint != fco.getMatch().getDataLayerVirtualLanPriorityCodePoint()) {
				iter.remove();
				continue;
			}
			// DL_TYPE
			if (query.dataLayerType != 0 && query.dataLayerType != fco.getMatch().getDataLayerType()) {
				iter.remove();
				continue;
			}
			// NW_PROTO
			if (query.networkProtocol != 0 && query.networkProtocol != fco.getMatch().getNetworkProtocol()) {
				iter.remove();
				continue;
			}
			// NW_TOS
			if (query.networkTypeOfService != 0 && query.networkTypeOfService != fco.getMatch().getNetworkTypeOfService()) {
				iter.remove();
				continue;
			}
			// NW_SRC
			if (query.networkSource != 0 && query.networkSource != fco.getMatch().getNetworkSource()) {
				iter.remove();
				continue;
			}
			// NW_DST
			if (query.networkDestination != 0 && query.networkDestination != fco.getMatch().getNetworkDestination()) {
				iter.remove();
				continue;
			}
			// TP_SRC
			if (query.transportSource != 0 && query.transportSource != fco.getMatch().getTransportSource()) {
				iter.remove();
				continue;
			}
			// TP_DST
			if (query.transportDestination != 0 && query.transportDestination != fco.getMatch().getTransportDestination()) {
				iter.remove();
				continue;
			}
			
			// Path ID
			if (query.pathId > 0 && query.pathId != fco.getPathId()) {
				iter.remove();
				continue;
			}
		}
		
//		// TESTING AND DEBUGGING
//		System.out.println("--------------------------");
//		for (long swId : this.flowCacheMatchHashMap.keySet()) {
//			System.out.println("SwitchID: " + HexString.toHexString(swId));
//			for (int hash : this.flowCacheMatchHashMap.get(swId).keySet()) {
//				System.out.println("   " + hash + ": " + this.flowCacheMatchHashMap.get(swId).get(hash));
//			}
//		}
		
		// Remove empty SetOf FlowCacheObj and corresponding switch IDs.
		for (long swId : resultMap.keySet()) {
			if (resultMap.get(swId) == null || resultMap.get(swId).isEmpty()) {
				resultMap.remove(swId);
			}
		}
		
		// Return new result map, or null if result map is empty.
		return (!resultMap.isEmpty()) ? resultMap : null;
	}
	
	@Override
	public void clear() {
		for (long switchId : flowCacheMatchHashMap.keySet()) {
			for (int hash : flowCacheMatchHashMap.get(switchId).keySet()) {
				flowCacheMatchHashMap.get(switchId).remove(hash);
			}
			if (flowCacheMatchHashMap.get(switchId).isEmpty()) {
				flowCacheMatchHashMap.remove(switchId);
			}
		}
	}
	
	@Override
	public String toString() {
		/* The string builder. */
		StringBuilder sb = new StringBuilder();
		
		sb.append("FlowCacheDB [");
		sb.append("appName=" + this.appName);
		sb.append("]");
		
		return sb.toString();
	}

}
