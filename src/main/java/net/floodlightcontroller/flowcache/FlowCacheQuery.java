package net.floodlightcontroller.flowcache;

import java.util.Arrays;

import net.floodlightcontroller.packet.IPv4;

import org.openflow.protocol.OFMatch;
import org.openflow.util.HexString;

/**
 * Generic flow cache query object.
 *
 */
public class FlowCacheQuery {
	/** MAC broadcast address in byte-array representation. */
	public static final byte[] MAC_BROADCAST_ADDR = {0,0,0,0,0,0};
    /** The caller of the flow cache query. */
	public IFlowQueryHandler fcQueryHandler;
    /** The application instance name. */
	public String applInstName;
    /** The caller name */
	public String callerName;
	/** The switch to query. */
	public long switchId;
	/** Query fields. */
	public long cookie;
	public short priority;
	public int outPort;
	public int inPort;
	public byte[] dataLayerSource;
	public byte[] dataLayerDestination;
	public short dataLayerVirtualLan;
	public byte dataLayerVirtualLanPriorityCodePoint;
	public short dataLayerType;
	public byte networkTypeOfService;
	public byte networkProtocol;
	public int networkSource;
	public int networkDestination;
	public short transportSource;
	public short transportDestination;
	public int pathId;
    /** 
     * The caller opaque data. Returned unchanged in the query response
     * via the callback. The type of this object could be different for
     * different callers 
     */
    public Object callerOpaqueObj;

    /**
     * Default Constructor. Instantiates a new flow cache query object
     */
    public FlowCacheQuery(IFlowQueryHandler fcQueryHandler, String applInstName, String callerName, Object callerOpaqueObj, Long switchId) {
        this.fcQueryHandler = fcQueryHandler;
        this.applInstName = applInstName;
        this.callerName = callerName;
        this.callerOpaqueObj = callerOpaqueObj;
        this.switchId = (switchId == null) ? 0 : switchId;
        // Initialize query fields.
        this.cookie = 0;
        this.priority = 0;
        this.outPort = 0;
        this.inPort = 0;
        this.dataLayerSource = null;
        this.dataLayerDestination = null;
        this.dataLayerVirtualLan = 0;
        this.dataLayerVirtualLanPriorityCodePoint = 0;
        this.dataLayerType = 0;
        this.networkTypeOfService = 0;
        this.networkProtocol = 0;
        this.networkSource = 0;
        this.networkDestination = 0;
        this.transportSource = 0;
        this.transportDestination = 0;
    }
    
    /**
     * Convenience Constructor to instantiate a new flow cache query object from an OFMatch object.
     */
    public FlowCacheQuery(IFlowQueryHandler fcQueryHandler, String applInstName, String callerName, Object callerOpaqueObj, Long switchId, OFMatch match) {
        this.fcQueryHandler = fcQueryHandler;
        this.applInstName = applInstName;
        this.callerName = callerName;
        this.callerOpaqueObj = callerOpaqueObj;
        this.switchId = (switchId == null) ? 0 : switchId;
        // Initialize query fields.
        this.cookie = 0;
        this.priority = 0;
        this.outPort = 0;
        // Set query fields according to match fields.
        this.inPort = match.getInputPort();
        this.dataLayerSource = match.getDataLayerSource();
        this.dataLayerDestination = match.getDataLayerDestination();
        this.dataLayerVirtualLan = match.getDataLayerVirtualLan();
        this.dataLayerVirtualLanPriorityCodePoint = match.getDataLayerVirtualLanPriorityCodePoint();
        this.dataLayerType = match.getDataLayerType();
        this.networkTypeOfService = match.getNetworkTypeOfService();
        this.networkProtocol = match.getNetworkProtocol();
        this.networkSource = match.getNetworkSource();
        this.networkDestination = match.getNetworkDestination();
        this.transportSource = match.getTransportSource();
        this.transportDestination = match.getTransportDestination();
    }
    
    /**
     * Convenience Constructor to instantiate a new flow cache query object from an OFMatch object.
     */
    public FlowCacheQuery(String applInstName, String callerName, Object callerOpaqueObj, Long switchId, OFMatch match) {
        this.fcQueryHandler = null;
        this.applInstName = applInstName;
        this.callerName = callerName;
        this.callerOpaqueObj = callerOpaqueObj;
        this.switchId = (switchId == null) ? 0 : switchId;
        // Initialize query fields.
        this.cookie = 0;
        this.priority = 0;
        this.outPort = 0;
        // Wildcard match
        match = FlowCacheObj.wildcardMatch(match);
        // Set query fields according to match fields.
        this.inPort = match.getInputPort();
        this.dataLayerSource = match.getDataLayerSource();
        this.dataLayerDestination = match.getDataLayerDestination();
        this.dataLayerVirtualLan = match.getDataLayerVirtualLan();
        this.dataLayerVirtualLanPriorityCodePoint = match.getDataLayerVirtualLanPriorityCodePoint();
        this.dataLayerType = match.getDataLayerType();
        this.networkTypeOfService = match.getNetworkTypeOfService();
        this.networkProtocol = match.getNetworkProtocol();
        this.networkSource = match.getNetworkSource();
        this.networkDestination = match.getNetworkDestination();
        this.transportSource = match.getTransportSource();
        this.transportDestination = match.getTransportDestination();
    }
    
    /**
     * Setter for the cookie.
     * 
     * @param cookie The cookie to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setCookie(long cookie) {
    	this.cookie = cookie;
    	return this;
    }
    
    /**
     * Setter for the priority.
     * 
     * @param priority The priority to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setPriority(short priority) {
    	this.priority = priority;
    	return this;
    }
    
    /**
     * Setter for the output port.
     * 
     * @param outPort The input port to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setOutPort(int outPort) {
    	this.outPort = outPort;
    	return this;
    }
    
    /**
     * Setter for the input port.
     * 
     * @param inPort The input port to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setInPort(int inPort) {
    	this.inPort = inPort;
    	return this;
    }
    
    /**
     * Setter for the data layer source address.
     * 
     * @param dataLayerSource The MAC source address to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setDataLayerSource(byte[] dataLayerSource) {
    	this.dataLayerSource = dataLayerSource;
    	return this;
    }
    
    /**
     * Setter for the data layer destination address.
     * 
     * @param dataLayerDestination The MAC destination address to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setDataLayerDestination(byte[] dataLayerDestination) {
    	this.dataLayerDestination = dataLayerDestination;
    	return this;
    }
    
    /**
     * Setter for the VLAN id.
     * 
     * @param dataLayerVirtualLan The VLAN to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setDataLayerVirtualLan(short dataLayerVirtualLan) {
    	this.dataLayerVirtualLan = dataLayerVirtualLan;
    	return this;
    }
    
    /**
     * Setter for the VLAN priority code.
     * 
     * @param dataLayerVirtualLanPriorityCodePoint The VLAN priority code to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setDataLayerVirtualLanPriorityCodePoint(byte dataLayerVirtualLanPriorityCodePoint) {
    	this.dataLayerVirtualLanPriorityCodePoint = dataLayerVirtualLanPriorityCodePoint;
    	return this;
    }
    
    /**
     * Setter for the data layer type.
     * 
     * @param dataLayerType The data layer type to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setDataLayerType(short dataLayerType) {
    	this.dataLayerType = dataLayerType;
    	return this;
    }
    
    /**
     * Setter for the network ToS.
     * 
     * @param networkTypeOfService The network ToS to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setNetworkTypeOfService(byte networkTypeOfService) {
    	this.networkTypeOfService = networkTypeOfService;
    	return this;
    }
    
    /**
     * Setter for the network protocol.
     * 
     * @param networkProtocol The network protocol to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setNetworkProtocol(byte networkProtocol) {
    	this.networkProtocol = networkProtocol;
    	return this;
    }
    
    /**
     * Setter for the network source address, i.e. the IP address.
     * 
     * @param networkSource The network source address to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setNetworkSource(int networkSource) {
    	this.networkSource = networkSource;
    	return this;
    }
    
    /**
     * Setter for the network destination address, i.e. the IP address.
     * 
     * @param networkDestination The network destination address to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setNetworkDestination(int networkDestination) {
    	this.networkDestination = networkDestination;
    	return this;
    }
    
    /**
     * Setter for the transport source address, i.e. the port.
     * 
     * @param transportSource The transport source address to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setTransportSource(short transportSource) {
    	this.transportSource = transportSource;
    	return this;
    }
    
    /**
     * Setter for the transport destination address, i.e. the port.
     * 
     * @param transportDestination The transport destination address to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setTransportDestination(short transportDestination) {
    	this.transportDestination = transportDestination;
    	return this;
    }
    
    /**
     * Setter for the path Id.
     * 
     * @param pathId The path Id to query. 
     * @return This FlowCacheQuery object.
     */
    public FlowCacheQuery setPathId(int pathId) {
    	this.pathId = pathId;
    	return this;
    }
    
//    public OFMatch getMatch() {
//    	/* The new OpenFlow match object. */
//    	OFMatch match = new OFMatch();
//    	/* The wildcards of the new OFMatch object. */
//    	int wildcards = Wildcards.EXACT.getInt();
//    	
//    	if (outPort != 0) {
//    		return null;
//    	}
//    	
//    	// InPort
//    	if (inPort != 0) {
//    		match.setInputPort(inPort);
//    		wildcards ^= OFMatch.OFPFW_IN_PORT;
//    	} else {
//    		match.setInputPort((short)0);
//    	}
//    	// DL_DST
//    	if (dataLayerDestination != null) {
//    		match.setDataLayerDestination(dataLayerDestination);
//    		wildcards ^= OFMatch.OFPFW_DL_DST;
//    	} else {
//    		match.setDataLayerDestination(MAC_BROADCAST_ADDR);
//    	}
//    	// DL_SRC
//    	if (dataLayerSource != null) {
//    		match.setDataLayerSource(dataLayerSource);
//    		wildcards ^= OFMatch.OFPFW_DL_SRC;
//    	} else {
//    		match.setDataLayerSource(MAC_BROADCAST_ADDR);
//    	}
//    	// DL_VLAN
//    	if (dataLayerVirtualLan != 0) {
//    		match.setDataLayerVirtualLan(dataLayerVirtualLan);
//    		wildcards ^= OFMatch.OFPFW_DL_VLAN;
//    	} else {
//    		match.setDataLayerVirtualLan((short) 0);
//    	}
//    	// DL_VLAN_PCP
//    	if (dataLayerVirtualLanPriorityCodePoint != 0) {
//    		match.setDataLayerVirtualLanPriorityCodePoint(dataLayerVirtualLanPriorityCodePoint);
//    		wildcards ^= OFMatch.OFPFW_DL_VLAN_PCP;
//    	} else {
//    		match.setDataLayerVirtualLanPriorityCodePoint((byte) 0);
//    	}
//    	// DL_TYPE
//    	if (dataLayerType != 0) {
//    		match.setDataLayerType(dataLayerType);
//    		wildcards ^= OFMatch.OFPFW_DL_TYPE;
//    	} else {
//    		match.setDataLayerType((short) 0);
//    	}
//    	// NW_PROTO
//    	if (networkProtocol != 0) {
//    		match.setNetworkProtocol(networkProtocol);
//    		wildcards ^= OFMatch.OFPFW_NW_PROTO;
//    	} else {
//    		match.setNetworkProtocol((byte) 0);
//    	}
//    	// NW_TOS
//    	if (networkTypeOfService != 0) {
//    		match.setNetworkTypeOfService(networkTypeOfService);
//    		wildcards ^= OFMatch.OFPFW_NW_TOS;
//    	} else {
//    		match.setNetworkTypeOfService((byte) 0);
//    	}
//    	// NW_SRC
//    	if (networkSource != 0) {
//    		match.setNetworkSource(networkSource);
//    		wildcards ^= OFMatch.OFPFW_NW_SRC_ALL;
//    	} else {
//    		match.setNetworkSource(0);
//    	}
//    	// NW_DST
//    	if (networkDestination != 0) {
//    		match.setNetworkDestination(networkDestination);
//    		wildcards ^= OFMatch.OFPFW_NW_DST_ALL;
//    	} else {
//    		match.setNetworkDestination(0);
//    	}
//    	// TP_SRC
//    	if (transportSource != 0) {
//    		match.setTransportSource(transportSource);
//    		wildcards ^= OFMatch.OFPFW_TP_SRC;
//    	} else {
//    		match.setTransportSource((short) 0);
//    	}
//    	// TP_DST
//    	if (transportDestination != 0) {
//    		match.setTransportDestination(transportDestination);
//    		wildcards ^= OFMatch.OFPFW_TP_DST;
//    	} else {
//    		match.setTransportDestination((short) 0);
//    	}
//    	
//    	// Wildcards
//    	match.setWildcards(wildcards);
//    	
//    	return match;
//    }

    @Override
    public String toString() {
    	/* The string builder. */
		StringBuilder sb = new StringBuilder();
		
		sb.append("FlowCacheQuery [");		
		sb.append("fcQueryCaller=" + fcQueryHandler + ",");
		sb.append("applInstName=" + applInstName + ",");
		sb.append("callerName=" + callerName + ",");
		sb.append("callerOpaqueObj=" + callerOpaqueObj + ",");
		if (switchId != 0)
			sb.append("switch=" + HexString.toHexString(switchId) + ",");
		if (cookie != 0)
			sb.append("cookie=0x" + Long.toHexString(cookie) + ",");
		if (priority != 0)
			sb.append("priority=" + priority + ",");
		if (inPort != 0)
			sb.append("inPort=" + inPort + ",");
		if (outPort != 0)
			sb.append("outPort=" + outPort + ",");
		if (dataLayerSource != null)
			sb.append("dataLayerSource=" + HexString.toHexString(dataLayerSource) + ",");
		if (dataLayerDestination != null)
			sb.append("dataLayerDestination=" + HexString.toHexString(dataLayerDestination) + ",");
		if (dataLayerVirtualLan != 0)
			sb.append("dataLayerVirtualLan=" + dataLayerVirtualLan + ",");
		if (dataLayerVirtualLanPriorityCodePoint != 0)
			sb.append("dataLayerVirtualLanPriorityCodePoint=" + dataLayerVirtualLanPriorityCodePoint + ",");
		if (dataLayerType != 0)
			sb.append("dataLayerType=" + dataLayerType + ",");
		if (networkTypeOfService != 0)
			sb.append("networkTypeOfService=" + networkTypeOfService + ",");
		if (networkProtocol != 0)
			sb.append("networkProtocol=" + networkProtocol + ",");
		if (networkSource != 0)
			sb.append("networkSource=" + IPv4.fromIPv4Address(networkSource) + ",");
		if (networkDestination != 0)
			sb.append("networkDestination=" + IPv4.fromIPv4Address(networkDestination) + ",");
		if (transportSource != 0)
			sb.append("transportSource=" + transportSource + ",");
		if (transportDestination != 0)
			sb.append("transportDestination=" + transportDestination + ",");
		// Remove tailing comma.
		sb.replace(sb.lastIndexOf(","), sb.lastIndexOf(",")+2, "");
		
		sb.append("]");
		
		return sb.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((applInstName == null) ? 0 : applInstName.hashCode());
        result = prime * result + ((callerName == null) ? 0 : callerName.hashCode());
        result = prime * result + ((callerOpaqueObj == null) ? 0 : callerOpaqueObj.hashCode());
        result = prime * result + ((fcQueryHandler == null) ? 0 : fcQueryHandler.hashCode());
        result = prime * result + inPort;
        result = prime * result + ((dataLayerSource == null) ? 0 : Arrays.hashCode(dataLayerSource));
        result = prime * result + ((dataLayerDestination == null) ? 0 : Arrays.hashCode(dataLayerDestination));
        result = prime * result + dataLayerVirtualLan;
        result = prime * result + dataLayerVirtualLanPriorityCodePoint;
        result = prime * result + dataLayerType;
        result = prime * result + networkTypeOfService;
        result = prime * result + networkProtocol;
        result = prime * result + networkSource;
        result = prime * result + networkDestination;
        result = prime * result + transportSource;
        result = prime * result + transportDestination;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
        	return true;
        }
        if (obj == null) {
        	return false;
        }
        if (getClass() != obj.getClass()) {
        	return false;
        }
        
        FlowCacheQuery other = (FlowCacheQuery) obj;
        
        if (applInstName == null) {
            if (other.applInstName != null) {
            	return false;
            }
        } else if (!applInstName.equals(other.applInstName)) {
        	return false;
        }
        
        if (callerName == null) {
            if (other.callerName != null) {
            	return false;
            }
        } else if (!callerName.equals(other.callerName)) {
        	return false;
        }
        
        if (callerOpaqueObj == null) {
            if (other.callerOpaqueObj != null) {
            	return false;
            }
        } else if (!callerOpaqueObj.equals(other.callerOpaqueObj)) {
        	return false;
        }
        
        if (fcQueryHandler == null) {
            if (other.fcQueryHandler != null) {
            	return false;
            }
        } else if (!fcQueryHandler.equals(other.fcQueryHandler)) {
            return false;
        }
        
        if (inPort != other.inPort) {
        	return false;
        }
        
        if (dataLayerSource == null || other.dataLayerSource == null) {
        	return false;
        } else if (!Arrays.equals(dataLayerSource, other.dataLayerSource)) {
        	return false;
        }
        
        if (dataLayerDestination == null || other.dataLayerDestination == null) {
        	return false;
        } else if (!Arrays.equals(dataLayerDestination, other.dataLayerDestination)) {
        	return false;
        }
        
        if (dataLayerVirtualLan != other.dataLayerVirtualLan) {
        	return false;
        }
        
        if (dataLayerVirtualLanPriorityCodePoint != other.dataLayerVirtualLanPriorityCodePoint) {
        	return false;
        }
        
        if (dataLayerType != other.dataLayerType) {
        	return false;
        }
        
        if (networkTypeOfService != other.networkTypeOfService) {
        	return false;
        }
        
        if (networkProtocol != other.networkProtocol) {
        	return false;
        }
        
        if (networkSource != other.networkSource) {
        	return false;
        }
        
        if (networkDestination != other.networkDestination) {
        	return false;
        }
        
        if (transportSource != other.transportSource) {
        	return false;
        }
        
        if (transportDestination != other.transportDestination) {
        	return false;
        }
        
        return true;
    }
    
}
