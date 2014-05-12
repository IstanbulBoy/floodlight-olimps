package net.floodlightcontroller.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.floodlightcontroller.core.IOFSwitch.PortChangeEvent;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.util.LinkedHashSetWrapper;
import net.floodlightcontroller.util.OrderedCollection;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the ports of this switch.
 *
 * Provides methods to query and update the stored ports. The class ensures
 * that every port name and port number is unique. When updating ports
 * the class checks if port number <-> port name mappings have change due
 * to the update. If a new port P has number and port that are inconsistent
 * with the previous mapping(s) the class will delete all previous ports
 * with name or number of the new port and then add the new port.
 *
 * Port names are stored as-is but they are compared case-insensitive
 *
 * The methods that change the stored ports return a list of
 * PortChangeEvents that represent the changes that have been applied
 * to the port list so that IOFSwitchListeners can be notified about the
 * changes.
 *
 * Implementation notes:
 * - We keep several different representations of the ports to allow for
 *   fast lookups
 * - Ports are stored in unchangeable lists. When a port is modified new
 *   data structures are allocated.
 * - We use a read-write-lock for synchronization, so multiple readers are
 *   allowed.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class OFPortManager {
	/** */
	protected static final Logger log = LoggerFactory.getLogger(OFPortManager.class);
	/** */
	private final ReentrantReadWriteLock lock;
	/** A list of physical ports known to the switch. */
	private Map<Short, Set<OFSwitchPort>> ofpPorts2virtPorts;
	/** A list of physical port numbers known to the switch. */
	private Map<Short, OFPhysicalPort> ofpPorts;
	/** */
	private List<OFSwitchPort> portList;
	/** */
    private List<OFSwitchPort> enabledPorts;
    /** */
    private List<Integer> enabledPortNumbers;
    /** */
    private Map<String, OFSwitchPort> portsByName;
    /** */
    private Map<Integer, OFSwitchPort> portsByNumber;
	
	/**
	 *  Default constructor.
	 */
	public OFPortManager() {
        this.lock = new ReentrantReadWriteLock();
//        this.ofpPorts = Collections.emptyMap();
//        this.ofpPortNumberList = Collections.emptyList();
//        this.portList = Collections.emptyList();
//        this.enabledPorts = Collections.emptyList();
//        this.enabledPortNumbers = Collections.emptyList();
//        this.portsByName = Collections.emptyMap();
//        this.portsByNumber = Collections.emptyMap();
      this.ofpPorts2virtPorts = new HashMap<Short, Set<OFSwitchPort>>();
      this.ofpPorts = new HashMap<Short, OFPhysicalPort>();
      this.portList = new ArrayList<OFSwitchPort>();
      this.enabledPorts = new ArrayList<OFSwitchPort>();
      this.enabledPortNumbers = new ArrayList<Integer>();
      this.portsByName = new HashMap<String, OFSwitchPort>();
      this.portsByNumber = new HashMap<Integer, OFSwitchPort>();
	}
	
	/**
	 * Handles port status message. Hence, it is related to OFPhysicalPort port changes.
	 * 
	 * @param ps
	 * @return
	 */
	public OrderedCollection<PortChangeEvent> handlePortStatusMessage(OFPortStatus ps) {
		if (ps == null) {
            throw new NullPointerException("OFPortStatus message must not be null");
        }
		
		lock.writeLock().lock();
		try {
			OFPhysicalPort ofpPort = ps.getDesc();
			OFPortReason reason = OFPortReason.fromReasonCode(ps.getReason());
            if (reason == null) {
                throw new IllegalArgumentException("Unknown PortStatus reason code " + ps.getReason());
            }
            
            if (log.isDebugEnabled()) { 
            	log.debug("Handling OFPortStatus: {} for {}", reason, ofpPort.toString());
            }
            
            switch (reason) {
            	case OFPPR_DELETE:
            		return handlePortStatusDelete(ofpPort, reason);
            	case OFPPR_ADD:
            		return handlePortStatusAdd(ofpPort, reason);
            	case OFPPR_MODIFY:
            		return handlePortStatusModify(ofpPort, reason);
            	default:
            		throw new IllegalArgumentException("Unknown PortStatus reason code " + ps.getReason());
            }
            
		} finally {
            lock.writeLock().unlock();
        }
	}
	
	/**
	 * 
	 * @param ofsPort
	 */
	public void addVirtualPort(OFSwitchPort ofsPort) {
		if (ofsPort == null)
			return;
		if (this.portList.contains(ofsPort))
			return;
		
		lock.writeLock().lock();
		try {
			short ofpPortId = ofsPort.getOFPhysicalPort().getPortNumber();
			
			// Add ports to collections.
			if (ofpPorts2virtPorts.get(ofpPortId) == null)
				this.ofpPorts2virtPorts.put(ofpPortId, new HashSet<OFSwitchPort>());
			this.ofpPorts2virtPorts.get(ofpPortId).add(ofsPort);
			this.ofpPorts.put(ofpPortId, ofsPort.getOFPhysicalPort());
			
			this.portList.add(ofsPort);
			this.portsByName.put(ofsPort.getName().toLowerCase(), ofsPort);
			this.portsByNumber.put(ofsPort.getPortNumber(), ofsPort);
			if (ofsPort.isEnabled()) {
				this.enabledPorts.add(ofsPort);
				this.enabledPortNumbers.add(ofsPort.getPortNumber());
			}
			
			// remove default virtual port if necessary.
			OFSwitchPort ofsDefaultPort = OFSwitchPort.create(ofsPort.getOFPhysicalPort(), (short) 0);
			if (this.portList.contains(ofsDefaultPort) && this.ofpPorts2virtPorts.get(ofpPortId).size() > 1)
				this.removeVirtualPort(ofsDefaultPort);
			
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * 
	 * @param ofsPort
	 */
	public void removeVirtualPort(OFSwitchPort ofsPort) {
		if (ofsPort == null)
			return;
		
		lock.writeLock().lock();
		try {
			int ofsPortId = ofsPort.getPortNumber();
			short ofpPortId = ofsPort.getOFPhysicalPort().getPortNumber();
			// Remove ports from collections.
			this.ofpPorts2virtPorts.get(ofpPortId).remove(ofsPort);
			this.portList.remove(ofsPort);
			this.portsByName.remove(ofsPort.getName().toLowerCase());
			this.portsByNumber.remove(ofsPortId);
			this.enabledPorts.remove(ofsPort);
			this.enabledPortNumbers.remove((Integer) ofsPortId); // Needs to casted in order to remove by the object, and not by the index.
			
			// Add a default virtual port if necessary.
			if (this.ofpPorts2virtPorts.get(ofpPortId).isEmpty()) {
				// Add default virtual port with VLAN 0.
				OFSwitchPort ofsDefaultPort = OFSwitchPort.create(ofsPort.getOFPhysicalPort(), (short) 0);
				this.addVirtualPort(ofsDefaultPort);
			}
			
		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Get a physical port by its number.
	 * 
	 * @param portNumber
	 * @return
	 */
	public OFPhysicalPort getPhysicalPort(short portNumber) {
		lock.readLock().lock();
        try {
            return this.ofpPorts.get(portNumber);
        } finally {
            lock.readLock().unlock();
        }
	}
	
	/**
	 * 
	 * @return
	 */
	public Collection<OFPhysicalPort> getPhysicalPorts() {
		lock.readLock().lock();
        try {
            return this.ofpPorts.values();
        } finally {
            lock.readLock().unlock();
        }
	}
	
	/**
	 * Get a port by its name.
	 * 
	 * @param name
	 * @return
	 */
	public OFSwitchPort getPort(String name) {
		 lock.readLock().lock();
	     try {
	         return portsByName.get(name.toLowerCase());
	     } finally {
	         lock.readLock().unlock();
	     }
	}

	/**
	 * Get a port by its number.
	 * 
	 * @param portNumber
	 * @return
	 */
	public OFSwitchPort getPort(int portNumber) {
		lock.readLock().lock();
	    try {
	        return portsByNumber.get(portNumber);
	    } finally {
	        lock.readLock().unlock();
	    }
	}

	/**
	 * 
	 * @return
	 */
	public List<OFSwitchPort> getPorts() {
		List<OFSwitchPort> newPortList = new ArrayList<OFSwitchPort>();
        lock.readLock().lock();
        try {
        	newPortList.addAll(portList);
        	return newPortList;
        } finally {        	
            lock.readLock().unlock();
        }
    }
	
	/**
	 * 
	 * @param phyPortNumber
	 * @return
	 */
	public Set<OFSwitchPort> getPorts(short phyPortNumber) {
		return this.ofpPorts2virtPorts.get(phyPortNumber);
	}

	/**
	 * 
	 * @return
	 */
	public List<OFSwitchPort> getEnabledPorts() {
		lock.readLock().lock();
        try {
            return enabledPorts;
        } finally {
            lock.readLock().unlock();
        }
	}
	
	/**
	 * 
	 * @return
	 */
	public List<Integer> getEnabledPortNumbers() {
		lock.readLock().lock();
        try {
            return enabledPortNumbers;
        } finally {
            lock.readLock().unlock();
        }
	}
	
	/**
     * Compare the current ports of this switch to the newPorts list and
     * return the changes that would be applied to transform the current
     * ports to the new ports. No internal data structures are updated
     * see {@link #compareAndUpdatePorts(List, boolean)}
     *
     * @param newPorts the list of new ports
     * @return The list of differences between the current ports and
     * newPortList
     */
    public OrderedCollection<PortChangeEvent> updateOFPhysicalPorts(Collection<OFPhysicalPort> newPorts) {
    	if (newPorts == null) {
            throw new NullPointerException("newPorts must not be null");
        }
    	OrderedCollection<PortChangeEvent> events = new LinkedHashSetWrapper<PortChangeEvent>();
    	
    	lock.writeLock().lock();
    	try {
    		// Add new physical ports to map.
    		for (OFPhysicalPort ofpPort : newPorts) {
    			if (!this.ofpPorts2virtPorts.containsKey(ofpPort)) {
    				// Add new default port.
    				this.handlePortStatusAdd(ofpPort, OFPortReason.OFPPR_ADD);
    			}
    		}
    		
    		// Remove deleted ports from map.
    		for (Short ofpPortId : this.ofpPorts2virtPorts.keySet()) {
    			OFPhysicalPort ofpPort = this.ofpPorts.get(ofpPortId);
    			if (!newPorts.contains(ofpPort)) {
    				Set<OFSwitchPort> ofSwitchPortSet = this.ofpPorts2virtPorts.remove(ofpPort);
    				this.ofpPorts.remove(ofpPort.getPortNumber());
    				
    				// Add a port change event for every OFSwitchPort that is removed.
    				for (OFSwitchPort switchPort : ofSwitchPortSet) {
    					PortChangeEvent ev = new PortChangeEvent(switchPort, PortChangeType.DELETE);
    					events.add(ev);
    				}
    			}
    		}
    	} finally {
            lock.writeLock().unlock();
        }

    	// Return a collection of events.
    	return events;
    }
    
    /**
     * 
     * @param newPorts
     * @return
     */
    public OrderedCollection<PortChangeEvent> updateOFSwitchPorts(Collection<OFSwitchPort> newPorts) {
    	if (newPorts == null) {
            throw new NullPointerException("newPorts must not be null");
        }
    	OrderedCollection<PortChangeEvent> events = new LinkedHashSetWrapper<PortChangeEvent>();
    	
        lock.writeLock().lock();
        try {
        	// Add new physical ports to map.
    		for (OFSwitchPort switchPort : newPorts) {
    			if (this.portList.contains(switchPort)) {
    				// TODO: Maybe do some checks, e.g. OFPhysicalPort
    				continue;
    			} else {
    				// Check if we know the physical port.
    				if (!this.ofpPorts2virtPorts.containsKey(switchPort.getOFPhysicalPort())) {
    					if (log.isErrorEnabled()) {
    						log.error("Physical port not there!");
    					}
    				} else {
    					this.portList.add(switchPort);
        				this.portsByName.put(switchPort.getName().toLowerCase(), switchPort);
        				//this.portsByNumber.put(switchPort.getPortNumber(), switchPort);
        				this.portsByNumber.put(switchPort.getPortNumber(), switchPort);
        				if (switchPort.isEnabled()) {
        					this.enabledPorts.add(switchPort);
        					this.enabledPortNumbers.add(switchPort.getPortNumber());
        				}
        				
        				// Add port change events
        				events.addAll(getSinglePortChanges(switchPort));
    				}
    			}
    		}
    		
    		// Remove deleted ports from map.
    		for (OFSwitchPort switchPort : this.portList) {
    			if (!newPorts.contains(switchPort)) {
    				this.portList.remove(switchPort);
    				this.portsByName.remove(switchPort.getName().toLowerCase());
    				this.portsByNumber.remove(switchPort.getPortNumber());
    				this.enabledPorts.remove(switchPort);
    				this.enabledPortNumbers.remove(switchPort.getPortNumber());
    				
    				this.ofpPorts2virtPorts.get(switchPort.getOFPhysicalPort()).remove(switchPort);
    				
    				if (this.ofpPorts2virtPorts.get(switchPort.getOFPhysicalPort()).isEmpty()) {
    					this.ofpPorts2virtPorts.remove(switchPort.getOFPhysicalPort());
    					this.ofpPorts.remove(switchPort.getOFPhysicalPort().getPeerFeatures());
    				}
    				
    				// Add port change events
    				events.addAll(getSinglePortChanges(switchPort));
    			}
    		}
        	
        } finally {
            lock.writeLock().unlock();
        }
        
        // Return a collection of events.
    	return events;
    }
	
    /**
	 * Compare the current ports of this switch to the newPorts list and
	 * return the changes that would be applied to transfort the current
	 * ports to the new ports. No internal data structures are updated
	 * see {@link #compareAndUpdatePorts(List, boolean)}
	 *
	 * @param newPorts the list of new physical ports
	 * @return The list of differences between the current ports and
	 * newPortList
	 */
	public OrderedCollection<PortChangeEvent> comparePorts(Collection<OFSwitchPort> newPorts) {
		if (newPorts == null) {
            throw new NullPointerException("newPorts must not be null");
        }
    	lock.writeLock().lock();
    	
    	OrderedCollection<PortChangeEvent> events = new LinkedHashSetWrapper<PortChangeEvent>();
    	try {
    		// Check for new port in the newPorts list.
    		for (OFSwitchPort switchPort : newPorts) {
    			if (switchPort == null) {
                    throw new NullPointerException("portList must not contain null values");
                }
    			
    			// Check for changes of that port.
    			events.addAll(getSinglePortChanges(switchPort));
    		}
    		
    		// Check for removed ports. 
    		for (OFSwitchPort switchPort : this.portList) {
    			if (!newPorts.contains(switchPort)) {
    				PortChangeEvent ev = new PortChangeEvent(switchPort, PortChangeType.DELETE);
                    events.add(ev);
    			}
    		}
    	} finally {
            lock.writeLock().unlock();
        }

    	// Return a collection of events.
    	return events;
	}

	/**
	 * Given a new or modified port newPort, returns the list of
	 * PortChangeEvents to "transform" the current ports stored by
	 * this switch to include / represent the new port. The ports stored
	 * by this switch are <b>NOT</b> updated.
	 *
	 * This method acquires the readlock and is thread-safe by itself.
	 * Most callers will need to acquire the write lock before calling
	 * this method though (if the caller wants to update the ports stored
	 * by this switch)
	 *
	 * @param newPort the new or modified port.
	 * @return the list of changes
	 */
	private OrderedCollection<PortChangeEvent> getSinglePortChanges(OFSwitchPort newPort) {
		if (newPort == null) {
            throw new NullPointerException("newPort must not be null");
        }
	    OrderedCollection<PortChangeEvent> events = new LinkedHashSetWrapper<PortChangeEvent>();
	    
	    lock.readLock().lock();
	    try {
	        // Check if we have a port by the same number in our old map.
	        OFSwitchPort prevPort = portsByNumber.get(newPort.getPortNumber());
	        if (newPort.equals(prevPort)) {
	            // nothing has changed
	            return events;
	        }
	
	        if (prevPort != null && prevPort.getName().equals(newPort.getName())) {
	            // A simple modify of a exiting port
	            // A previous port with this number exists and it's name
	            // also matches the new port. Find the differences
	            if (prevPort.isEnabled() && !newPort.isEnabled()) {
	                events.add(new PortChangeEvent(newPort, PortChangeType.DOWN));
	            } else if (!prevPort.isEnabled() && newPort.isEnabled()) {
	                events.add(new PortChangeEvent(newPort, PortChangeType.UP));
	            } else {
	                events.add(new PortChangeEvent(newPort, PortChangeType.OTHER_UPDATE));
	            }
	            return events;
	        }
	
	        if (prevPort != null) {
	            // There exists a previous port with the same port
	            // number but the port name is different (otherwise we would
	            // never have gotten here)
	            // Remove the port. Name-number mapping(s) have changed
	            events.add(new PortChangeEvent(prevPort, PortChangeType.DELETE));
	        }
	
	        // We now need to check if there exists a previous port sharing
	        // the same name as the new/updated port.
	        prevPort = portsByName.get(newPort.getName().toLowerCase());
	        if (prevPort != null) {
	            // There exists a previous port with the same port
	            // name but the port number is different (otherwise we
	            // never have gotten here).
	            // Remove the port. Name-number mapping(s) have changed
	            events.add(new PortChangeEvent(prevPort, PortChangeType.DELETE));
	        }
	
	        // We always need to add the new port. Either no previous port
	        // existed or we just deleted previous ports with inconsistent
	        // name-number mappings
	        events.add(new PortChangeEvent(newPort, PortChangeType.ADD));
	    } finally {
	        lock.readLock().unlock();
	    }
	    
	    // Return a collection of events.
	    return events;
	}

	/**
     * Handle a OFPortStatus delete message for the given port.
     * Updates the internal port maps/lists of this switch and returns
     * the PortChangeEvents caused by the delete. If the given port
     * exists as it, it will be deleted. If the name<->number for the
     * given port is inconsistent with the ports stored by this switch
     * the method will delete all ports with the number or name of the
     * given port.
     *
     * This method will increment error/warn counters and log
     *
     * @param ofpPort The port from the port status message that should be deleted.
     * @param reason
     * @return ordered collection of port changes applied to this switch
     */
	private OrderedCollection<PortChangeEvent> handlePortStatusDelete(OFPhysicalPort ofpPort, OFPortReason reason) {
		OrderedCollection<PortChangeEvent> events = new LinkedHashSetWrapper<PortChangeEvent>();
		
		lock.writeLock().lock();
		try {	
			Set<OFSwitchPort> switchPorts = this.ofpPorts2virtPorts.get(ofpPort.getPortNumber());

			if (switchPorts != null) {
				for (OFSwitchPort ofsPort : switchPorts) {
					int ofsPortId = ofsPort.getPortNumber();
					// Remove ports from collections.
					this.portList.remove(ofsPort);
					this.portsByName.remove(ofsPort.getName().toLowerCase());
					this.portsByNumber.remove(ofsPortId);
					this.enabledPorts.remove(ofsPort);
					this.enabledPortNumbers.remove((Integer) ofsPortId); // Needs to casted in order to remove by the object, and not by the index.
					// Add a port change event
					events.add(new PortChangeEvent(ofsPort, PortChangeType.DELETE));
				}
			}
			
			// Remove all corresponding physical Ports.
			this.ofpPorts2virtPorts.remove(ofpPort);
			this.ofpPorts.remove(ofpPort.getPortNumber());
		} finally {
			lock.writeLock().unlock();
		}
		
		// Return a collection of events.
		return events;
	}
	
	/**
	 * 
	 * @param ofpPort The port from the port status message that should be deleted.
	 * @param reason
	 * @return
	 */
	private OrderedCollection<PortChangeEvent> handlePortStatusAdd(OFPhysicalPort ofpPort, OFPortReason reason) {
		OrderedCollection<PortChangeEvent> events = new LinkedHashSetWrapper<PortChangeEvent>();
		
		lock.writeLock().lock();
		try {
			// Create a new default OFSwitchPort.
			OFSwitchPort switchPort = OFSwitchPort.fromOFPhysicalPort(ofpPort);
			// Add default port to collections.
			this.ofpPorts2virtPorts.put(ofpPort.getPortNumber(), new HashSet<OFSwitchPort>());
			this.ofpPorts2virtPorts.get(ofpPort.getPortNumber()).add(switchPort);
			this.ofpPorts.put(ofpPort.getPortNumber(), ofpPort);
			
			this.portList.add(switchPort);
			this.portsByName.put(switchPort.getName().toLowerCase(), switchPort);
			this.portsByNumber.put(switchPort.getPortNumber(), switchPort);
			if (switchPort.isEnabled()) {
				this.enabledPorts.add(switchPort);
				this.enabledPortNumbers.add(switchPort.getPortNumber());
			}
			
			// Add a port change event
			PortChangeEvent ev = new PortChangeEvent(switchPort, PortChangeType.ADD);
			events.add(ev);
		} finally {
			lock.writeLock().unlock();
		}
		
		// Return a collection of events.
		return events;
	}
	
	/**
	 * 
	 * @param ofpPort The port from the port status message that should be deleted.
	 * @param reason
	 * @return
	 */
	private OrderedCollection<PortChangeEvent> handlePortStatusModify(OFPhysicalPort ofpPort, OFPortReason reason) {
		// TODO!
		return new LinkedHashSetWrapper<PortChangeEvent>();
	}
	
}
