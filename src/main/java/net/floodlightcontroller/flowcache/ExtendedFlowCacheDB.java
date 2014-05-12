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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The extended flow cache database allows for efficiently query for ports.
 * This is useful in case we want to deal with port and link statistics.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ExtendedFlowCacheDB implements IFlowCacheDB {
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(ExtendedFlowCacheDB.class);
    
    /** The name of the application that uses this flow cache database. */
    protected String appName;
    /** A standard flow cache database to store MatchHashes -> FlowCacheObj. */
    protected FlowCacheDB flowCacheDB;
    /** SwitchId -> OutPortId -> SetOf FlowCacheObj. */
	protected ConcurrentHashMap<Long, ConcurrentHashMap<Integer, Set<FlowCacheObj>>> flowCacheOutPortMap;
    
	/**
	 * Default constructor instantiates this flow cache database for a given application. 
	 * 
	 * @param appName
	 */
    public ExtendedFlowCacheDB(String appName) {
    	this.flowCacheDB = new FlowCacheDB(appName);
    	this.appName = appName;
    	this.flowCacheOutPortMap = new ConcurrentHashMap<Long, ConcurrentHashMap<Integer, Set<FlowCacheObj>>>();
    	
    	if (log.isDebugEnabled()) {
    		log.debug("New flow cache database created for {}", appName);
    	}
    }
    
    @Override
    public synchronized boolean storeEntry(long switchId, FlowCacheObj fco, boolean overrideEntries) {
    	/* States whether the flow cache object was successfully stored or not. */
    	boolean result = false;
    	
    	if (this.hasEntry(switchId, fco)) {
    		return false;
    	}
    	
    	// Store entry in port map.
    	result = this.storeOutPortEntry(switchId, fco);
    	if (!result) {
    		// Return false;
    		return result;
    	}
    	
    	// Store entry in the standard match hash database.
    	result = this.flowCacheDB.storeEntry(switchId, fco, overrideEntries);
    	if (!result && !this.flowCacheDB.hasEntry(switchId, fco)) {
    		// Remove flow cache object from port map.
    		this.removeOutPortEntry(switchId, fco);
    		// Return false;
    		return result;
    	}
    	
    	// Return true, i.e. the entry is successfully stored in both storage areas.
    	return result;
    }
    
    @Override
    public FlowCacheObj getEntry(long switchId, FlowCacheObj fco) {
    	// TODO
    	return null;
    }
    
    @Override
    public synchronized FlowCacheObj removeEntry(long switchId, FlowCacheObj fco) {
    	// Remove the flow cache object from the MatchHash map.
    	FlowCacheObj removedFco = this.flowCacheDB.removeEntry(switchId, fco);
    	
    	if (removedFco == null) {
    		// Remove the flow cache object from the OutPort map.
    		if (removeOutPortEntry(switchId, fco)) {
        		return fco;
        	} else {
        		if(log.isWarnEnabled()) {
        			log.warn("removeEntry: Could not remove flow cache object {} for switch {} from port map. Object not found. ", fco, HexString.toHexString(switchId));
        		}
        	}
    	} else {
    		// Remove the flow cache object from the OutPort map.
    		if (removeOutPortEntry(switchId, removedFco)) {
    			return removedFco;
    		} else {
    			if(log.isWarnEnabled()) {
    				log.warn("Could not remove flow cache object {} from port map. Object not found. ", fco);
    			}
    		}
    	}
    	
    	// Return the flow cache object found, or null.
    	return removedFco;
    }
    
    @Override
    public boolean hasEntry(long switchId, FlowCacheObj fco) {
    	/* States if the port map contains the flow cache object.*/
    	boolean result = false;
    	/* Is entry in standard match hash database? */
		boolean resultDb = this.flowCacheDB.hasEntry(switchId, fco);
		/* Get a set of ports from the flow cache object, if available. */
		Set<Integer> outPortSet = this.getOutPortSet(switchId, fco);	
		
		if (outPortSet != null) {
			for (int port : outPortSet) {
				// Get switch's flow cache map.
				ConcurrentHashMap<Integer, Set<FlowCacheObj>> switchFlowCacheMap = flowCacheOutPortMap.get(switchId);
				if (switchFlowCacheMap == null) {
					return false;
				}
				// Get switch's port FlowCacheObj set.
				Set<FlowCacheObj> portFlowCacheSet = switchFlowCacheMap.get(port);
				if (portFlowCacheSet == null) {
					continue;
				}
				
				if (portFlowCacheSet.contains(fco)) {
					return true;
				}
			}
		}
		
		// Debugging and error output.
		if (resultDb != result) {
			if (log.isErrorEnabled()) {
				if (resultDb) {
					log.error("Flow cache object {} is present in standard match hash db, but not in port map.", fco);
				} else {
					log.error("Flow cache object {} is present in port map, but not in standard match hash db.", fco);
				}
			}
		}
		
		// Return.
		return result && resultDb;
    }
    
    @Override
    public boolean isEmpty() {
    	return this.flowCacheDB.isEmpty() && flowCacheOutPortMap.isEmpty();
    }
    
    @Override
    public Map<Long, Set<FlowCacheObj>> getAllEntries() {
    	return this.flowCacheDB.getAllEntries();
    }

	@Override
	public Map<Long, Set<FlowCacheObj>> queryDB(long switchId, FlowCacheQuery query) {
		if (this.isOutPortQuery(query)) {
			return queryDbByOutPorts(switchId, query);
		}
		// Return the standard match hash database query.
		return this.flowCacheDB.queryDB(switchId, query);
	}
	
	@Override
	public void clear() {
		// Clear standard match hash database.
		this.flowCacheDB.clear();
		
		// Clear port map.
		for (long switchId : flowCacheOutPortMap.keySet()) {
			for (int outPort : flowCacheOutPortMap.get(switchId).keySet()) {
				flowCacheOutPortMap.get(switchId).get(outPort).clear();
				if (flowCacheOutPortMap.get(switchId).get(outPort).isEmpty()) {
					flowCacheOutPortMap.get(switchId).remove(outPort);
				}
			}
			if (flowCacheOutPortMap.get(switchId).isEmpty()) {
				flowCacheOutPortMap.remove(switchId);
			}
		}
	}
	
	@Override
	public String toString() {
		/* The string builder. */
		StringBuilder sb = new StringBuilder();
		
		sb.append("ExtendedFlowCacheDB [");
		sb.append("appName=" + this.appName);
		sb.append("]");
		
		return sb.toString();
	}
	
	/**
	 * 
	 * @param switchId
	 * @param fco
	 * @return
	 */
	private boolean storeOutPortEntry(long switchId, FlowCacheObj fco) {
		/* Get a set of ports from the flow cache object, if available. */
		Set<Integer> outPortSet = this.getOutPortSet(switchId, fco);
		
		if (outPortSet != null) {
			for (int port : outPortSet) {
				// Add new switch to the flowCacheOutPortMap.
				if (!flowCacheOutPortMap.containsKey(switchId)) {
					flowCacheOutPortMap.put(switchId, new ConcurrentHashMap<Integer, Set<FlowCacheObj>>());
				}
				// Get switch's flow cache map.
				ConcurrentHashMap<Integer, Set<FlowCacheObj>> switchFlowCacheMap = flowCacheOutPortMap.get(switchId);
				
				// Add new port to switch's flow cache map.: port -> SetOf FlowCacheObj.
				if (!switchFlowCacheMap.containsKey(port)) {
					switchFlowCacheMap.put(port, new HashSet<FlowCacheObj>());
				}
				// Get switch's port FlowCacheObj set.
				Set<FlowCacheObj> portFlowCacheSet = switchFlowCacheMap.get(port);
				
				// Add flow match to flow cache: port -> SetOf FlowCacheObj.
				if (!portFlowCacheSet.add(fco)) {
					if(log.isWarnEnabled()) {
	        			log.warn("storeOutPortEntry: Could not store object {} for switch {} on port map. ", fco, HexString.toHexString(switchId));
	        		}
					// If its fails, remove the ports already installed.
					this.removeOutPortEntry(switchId, fco);
					// Return false.
					return false;
				}
			}
		} else {
			return false;
		}

		// Return true.
		return true;
	}
	
	/**
	 * 
	 * @param switchId
	 * @param fco
	 * @return
	 */
	private boolean removeOutPortEntry(long switchId, FlowCacheObj fco) {
		/* Everything went fine? */
		boolean result = true;
		/* Get a set of ports from the flow cache object, if available. */
		Set<Integer> outPortSet = this.getOutPortSet(switchId, fco);	
		
		if (outPortSet != null) {
			for (int port : outPortSet) {
				// Get switch's flow cache map.
				ConcurrentHashMap<Integer, Set<FlowCacheObj>> switchFlowCacheMap = flowCacheOutPortMap.get(switchId);
				if (switchFlowCacheMap == null) {
					return false;
				}
				// Get switch's port FlowCacheObj set.
				Set<FlowCacheObj> portFlowCacheSet = switchFlowCacheMap.get(port);
				if (portFlowCacheSet == null) {
					continue;
				}
				
				// Remove flow cache object.
				result = result && portFlowCacheSet.remove(fco);
			
				if (portFlowCacheSet.isEmpty()) {
					switchFlowCacheMap.remove(port);
				}
			
				if (switchFlowCacheMap.isEmpty()) {
					flowCacheOutPortMap.remove(switchId);
				}
			}
		} else {
			return false;
		}
		
		// Return true, iff all flow are successfully removed.
		return result;
	}
	
	/**
	 * Checks whether we are looking for an output port only.
	 * 
	 * @param query The flow cache query object.
	 * @return <b>boolean</b> True iff we are only querying for an output port.
	 */
	private boolean isOutPortQuery(FlowCacheQuery query) {
		if (query.cookie > 0)
			return false;
		if (query.priority >0)
			return false;
		if (query.inPort > 0)
			return false;
		if (query.dataLayerSource != null) 
			return false;
		if (query.dataLayerDestination != null)
			return false;
		if (query.dataLayerType > 0)
			return false;
		if (query.dataLayerVirtualLan > 0)
			return false;
		if (query.dataLayerVirtualLanPriorityCodePoint > 0)
			return false;
		if (query.networkSource > 0)
			return false;
		if (query.networkDestination > 0) 
			return false;
		if (query.networkTypeOfService > 0)
			return false;
		if (query.networkProtocol > 0)
			return false;
		if (query.transportSource > 0)
			return false;
		if (query.transportSource > 0)
			return false;
		
		return true;
	}
	
	/**
	 * 
	 * @return
	 */
	private Map<Long, Set<FlowCacheObj>> queryDbByOutPorts(long switchId, FlowCacheQuery query) {
		/* New HashMap that contains all flow cache objects that match the query: switchId -> SetOf FlowCacheObj. */
		HashMap<Long, Set<FlowCacheObj>> resultMap = new HashMap<Long, Set<FlowCacheObj>>();
		
		// Check if we know the switch.
		if (!this.flowCacheOutPortMap.containsKey(switchId)) {
			if (log.isDebugEnabled()) {
				log.debug("Switch ID {} not found.", HexString.toHexString(switchId));
			}
			return null;
		}
		
		// Check if we know the port.
		if (!this.flowCacheOutPortMap.get(switchId).containsKey(query.outPort)) {
			if (log.isDebugEnabled()) {
				log.debug("Port {} not found for Switch ID {} not found.", query.outPort, HexString.toHexString(switchId));
			}
			return null;
		}
		
		// Populate result map.
		if (!this.flowCacheOutPortMap.get(switchId).get(query.outPort).isEmpty()) {
			resultMap.put(switchId, this.flowCacheOutPortMap.get(switchId).get(query.outPort));
		}
		
		// Return new result map, or null if result map is empty.
		return !resultMap.isEmpty() ? resultMap : null;
	}
	
	/**
	 * Finds all ports of a given flow cache object on a specific switch. Returns
	 * the set of ports or null, if no ports can be found.
	 * 
	 * @param switchId
	 * @param fco 
	 * @return A set of output ports for a given flow cache object. Or null, if 
	 *         the object can not be found or has no output ports.
	 */
	private Set<Integer> getOutPortSet(long switchId, FlowCacheObj fco) {
		/* The resulting set of output ports. */
		Set<Integer> outPortSet = new HashSet<Integer>();
		
		// Check if the flow cache object already has some output ports.
		if (fco.getOutPorts() != null)
			outPortSet.addAll(fco.getOutPorts());
		// Add output ports contained by actions.
		if (FlowCacheObj.actionsToOutPorts(fco) != null)
			outPortSet.addAll(FlowCacheObj.actionsToOutPorts(fco));		
		// Check if we can get some output ports by an already stored object.
		FlowCacheObj getFco = this.flowCacheDB.getEntry(switchId, fco);
		if (getFco != null && getFco.getOutPorts() != null) {
			outPortSet.addAll(getFco.getOutPorts());
			outPortSet.addAll(FlowCacheObj.actionsToOutPorts(getFco));
		}
		
		if (outPortSet != null && outPortSet.size() > 0) {
			return outPortSet;
		} else {
			return null;
		}
	}
}
