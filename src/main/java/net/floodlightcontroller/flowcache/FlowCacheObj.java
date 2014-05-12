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
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionEnqueue;
import org.openflow.protocol.action.OFActionOutput;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class FlowCacheObj {
	/** The logger. */
	protected static  Logger log = LoggerFactory.getLogger(FlowCacheObj.class);
	
	/** MAC broadcast address in byte-array representation. */
	public static final byte[] MAC_BROADCAST_ADDR = {0,0,0,0,0,0};
	/** The Match all wildcard. Should be moved to Wildcards Class. */
    public static final int WILD_ALL = 0x3FFFFF;
    /** The Match none wildcard. Should be moved to Wildcards Class. */
    public static final int WILD_NONE = 0x0;

	/** The flow cache object id, i.e. its hash value. */
	private int id;
	/** The priority of the flow-mod. */
	private int priority;
	/** The cookie of the flow-mod.*/
	private long cookie;
	/** The OpenFlow match.*/
	private OFMatch match;
	/** A list of OpenFlow actions. */
	private List<OFAction> actions;
	/** A list of output ports. */
	private Set<Integer> outPorts;
	/** Arbitrary attributes of an flow cache object, e.g. to store statistics. */
	private Map<Attribute, Object> attributes;
	/** The status of this flow cache object. */
	private Status status;
	/** The creation timestamp of this object. */
	private long timestamp;
	/** The path Id if the flow is mapped to a path. */
	private int pathId;
	/** The idle timeout of the flow. */
	private short idleTimeout;
	/** The hard timeout of the flow. */
	private short hardTimeout;
	
	/**
	 * The status of a flow cache object.
	 * 
	 * @author Michael Bredel <michael.bredel@cern.ch>
	 */
	public static enum Status {
		/** Flow cache object is installed on a switch. */
		ACTIVE,
		/** Flow is in the flow cache waiting to be installed on a switch. */
		PENDING,
		/** Flow cache object is NOT installed on a switch and NOT used. */
		INACTIVE,
		/** Flow cache object was not found on the switch using a stats request/reply message. */
		UNCERTAIN
	}
	
	/**
	 * Attributes that can be stored to a flow cache object.
	 * 
	 * @author Michael Bredel <michael.bredel@cern.ch>
	 */
	public static enum Attribute {
		/** Stores statistic entries, e.g. packets and bytes, to flow. */
		STATISTIC,
		/** Stores application information, i.e. bytes to transmit to flow. */
		APPAWARE
	}
	
	/**
	 * Creates a set of unique output ports of the flow cache object, 
	 * derived by its actions.
	 * 
	 * @param fco The flow cache object.
	 * @return <b>outPorts</b> A set of output ports.
	 */
	public static Set<Integer> actionsToOutPorts(FlowCacheObj fco) {
		/* The list of actions of the flow cache object. */
		List<OFAction> actions = fco.getActions();
		/* A new set of output ports of the give flow cache object. */
		Set<Integer> outPorts = new HashSet<Integer>();
		
		if (actions == null || actions.size() == 0) {
			return null;
		}
		
		for (OFAction action : actions) {
			switch(action.getType()) {
				case OUTPUT:
					outPorts.add((int) ((OFActionOutput) action).getPort());
					break;
				case OPAQUE_ENQUEUE:
            	    outPorts.add((int) ((OFActionEnqueue) action).getPort());
            	    break;
				default:
					if (log.isDebugEnabled()) {
						log.debug("Could not decode action: {}", action);
					}
					break;
			}
		}
		
		// Return set of output ports.
		return outPorts;
	}
	
	/**
	 * Creates an OFMatch object with default values for 
	 * all wildcarded match fields.Thus, we can use the 
	 * OFMatch.hashCode() function to calculate a unique 
	 * hash that is based on the used match field only.
	 * 
	 * The function works on the assigned OFMatch object, i.e.
	 * call-by-reference! It does not create a new OFMatch object,
	 * e.g. by using the .clone() function.
	 * 
	 * @param match The OFMatch object that needs to be wildcarded.
	 * @return <b>match</b> The same OFMatch object (call-by-reference!)
	 * with default values for wildcarded match fields.
	 */
	public static OFMatch wildcardMatch(OFMatch match) {
		/* The wildcards of the OFMatch object. */
        Wildcards wildcards = match.getWildcardObj();
        
        if (wildcards.isWildcarded(Wildcards.Flag.DL_DST))
        	match.setDataLayerDestination(MAC_BROADCAST_ADDR);
        if (wildcards.isWildcarded(Wildcards.Flag.DL_SRC))
        	match.setDataLayerSource(MAC_BROADCAST_ADDR);
        if (wildcards.isWildcarded(Wildcards.Flag.DL_TYPE))
        	match.setDataLayerType((short) 0);
        if (wildcards.isWildcarded(Wildcards.Flag.DL_VLAN))
        	match.setDataLayerVirtualLan((short) 0);
        if (wildcards.isWildcarded(Wildcards.Flag.DL_VLAN_PCP))
        	match.setDataLayerVirtualLanPriorityCodePoint((byte) 0);
        if (wildcards.isWildcarded(Wildcards.Flag.IN_PORT))
        	match.setInputPort((short) 0);
        if (wildcards.isWildcarded(Wildcards.Flag.NW_DST))
        	match.setNetworkDestination(0);
        if (wildcards.isWildcarded(Wildcards.Flag.NW_PROTO))
        	match.setNetworkProtocol((byte) 0);
        if (wildcards.isWildcarded(Wildcards.Flag.NW_SRC))
        	match.setNetworkSource(0);
        if (wildcards.isWildcarded(Wildcards.Flag.NW_TOS))
        	match.setNetworkTypeOfService((byte) 0);
        if (wildcards.isWildcarded(Wildcards.Flag.TP_DST))
        	match.setTransportDestination((short) 0);
        if (wildcards.isWildcarded(Wildcards.Flag.TP_SRC))
        	match.setTransportSource((short) 0);
        
        return match;
	}
	
	/**
	 * Default constructor.
	 * 
	 * @param priority
	 * @param cookie
	 * @param match
	 * @param actions
	 */
	public FlowCacheObj(long cookie, int priority, OFMatch match, List<OFAction> actions) {
		this.priority = priority;
		this.cookie   = cookie;
		this.actions  = actions;
		// Clone the match object and set default field for wildcarded matches.
		this.match    = FlowCacheObj.wildcardMatch(match.clone());
		this.outPorts = FlowCacheObj.actionsToOutPorts(this);
		this.id       = this.hashCode();
		this.status   = Status.INACTIVE;
		this.timestamp = System.currentTimeMillis();
	}
	
	/**
	 * Convenience constructor to create a flow cache object from an OFFlowMod.
	 * 
	 * @param flowMod
	 */
	public FlowCacheObj(OFFlowMod flowMod) {
		this(flowMod.getCookie(), flowMod.getPriority(), FlowCacheObj.wildcardMatch(flowMod.getMatch().clone()), flowMod.getActions());
		this.setIdleTimeout(flowMod.getIdleTimeout());
		this.setHardTimeout(flowMod.getHardTimeout());
	}
	
	/**
	 * Convenience constructor to create a flow cache object from a OFFlowRemoved message.
	 * 
	 * @param flowRemovedMsg
	 */
	public FlowCacheObj(OFFlowRemoved flowRemovedMsg) {
		this(flowRemovedMsg.getCookie(), flowRemovedMsg.getPriority(), FlowCacheObj.wildcardMatch(flowRemovedMsg.getMatch().clone()), null);
	}
	
	/**
	 * Setter for arbitrary attribute object.
	 * 
	 * @param attribute A unique attribute.
	 * @param obj The arbitrary attribute object.
	 */
	public void setAttribute(Attribute attribute, Object obj) {
		if (attributes == null) {
			attributes = new HashMap<Attribute, Object>();
		}
		
		attributes.put(attribute, obj);
	}
	
	/**
	 * Getter for an arbitrary attribute object.
	 * 
	 * @param attribute A unique attribute.
	 * @return <b>obj</b> The arbitrary attribute object.
	 */
	public Object getAttribute(Attribute attribute) {
		if (attributes == null)
			return null;
		return attributes.get(attribute);
	}
	
	/**
	 * States whether an attribute name exists or not.
	 * 
	 * @param name The unique name of the attribute.
	 * @return True if the attribute name exists.
	 */
	public boolean hasAttribute(Attribute attribute) {
		if (attributes == null)
			return false;
		return attributes.containsKey(attribute) ? true : false;
	}
	
	/**
	 * Checks whether this flow cache object is installed on a switch or not.
	 * 
	 * @return <b>boolean</b> True if the flow cache object is installed on a switch. 
	 */
	public boolean isActive() {
		return this.status == Status.ACTIVE;
	}
	
	/**
	 * Checks whether this flow cache object is pending or not.
	 * 
	 * @return <b>boolean</b> True if the flow cache object is about to get installed on a switch. 
	 */
	public boolean isPending() {
		return this.status == Status.PENDING;
	}

	/**
	 * Setter for the status of the flow cache object.
	 * 
	 * @param status The new status of the flow cache object.
	 */
	public void setStatus(Status status) {
		this.status = status;
	}

	/**
	 * Getter for the status of the flow cache object.
	 * 
	 * @return <b>Status</b> The status of the flow cache object, e.g. if it is active or not.
	 */
	public Status getStatus() {
		return this.status;
	}
	
	/**
	 * Setter for the path Id of the flow cache object.
	 * 
	 * @param pathId The new path Id of the flow cache object.
	 */
	public void setPathId(int pathId) {
		this.pathId = pathId;
	}
	
	/**
	 * Getter for the path Id of the flow cache object.
	 * 
	 * @return <b>int</b> The path Id  of the flow cache object.
	 */
	public int getPathId() {
		return this.pathId;
	}
	
	/**
	 * Getter for the Id of the flow cache object. The Id equals
	 * the hash value of this object.
	 * 
	 * @return <b>id</b> The Id, i.e. the hash value, of the flow cache object.
	 */
	public int getId() {
		return id;
	}
	
	/**
	 * Setter for the priority.
	 * 
	 * @param priority A short value representing the priority.
	 */
	public void setPriority(short priority) {
		this.priority = priority;
	}
	
	/**
	 * Getter for the flow priority.
	 * 
	 * @return <b>priority</b> An integer value representing the flow-mod priority.
	 */
	public int getPriority() {
		return priority;
	}
	
	/**
	 * Setter for the cookie.
	 * 
	 * @param cookie A long value representing the cookie.
	 */
	public void setCookie(long cookie) {
		this.cookie = cookie;
	}
	
	/**
	 * Getter for the cookie;
	 * 
	 * @return <b>cookie</b> A long value representing the cookie.
	 */
	public long getCookie() {
		return cookie;
	}
	
	/**
	 * Getter for the flow match.
	 * 
	 * @return <b>match</b> An OFMatch object.
	 */
	public OFMatch getMatch() {
		return match;
	}
	
	/**
	 * Setter for the actions.
	 * 
	 * @param actions A list of OFAction objects.
	 */
	public void setActions(List<OFAction> actions) {
		this.actions = actions;
	}
	
	/**
	 * Getter for the actions.
	 * 
	 * @return <b>actions</b> A list of OFAction objects.
	 */
	public List<OFAction> getActions() {
		return actions;
	}
	
	/**
	 * Setter for the output ports.
	 * 
	 * @param outPorts A set of output ports.
	 */
	public void setOutPorts(Set<Integer> outPorts) {
		this.outPorts = outPorts;
	}
	
	/**
	 * Getter for the output ports.
	 * 
	 * @return <b>outPorts</b> A set of output ports.
	 */
	public Set<Integer> getOutPorts() {
		return outPorts;
	}
	
	/**
	 * Setter for the idle timeout.
	 * 
	 * @param idleTimeout The idle timeout of the flow.
	 */
	public void setIdleTimeout(short idleTimeout) {
		this.idleTimeout = idleTimeout;
	}
	
	/**
	 * Getter for the idle timeout.
	 * 
	 * @return <b>short</b> The idle timeout of the flow.
	 */
	public short getIdleTimeout() {
		return idleTimeout;
	}
	
	/**
	 * Setter for the hard timeout.
	 * 
	 * @param hardTimeout The hard timeout of the flow.
	 */
	public void setHardTimeout(short hardTimeout) {
		this.hardTimeout = hardTimeout;
	}
	
	/**
	 * Getter for the hard timeout.
	 * 
	 * @return <b>short</b> The hard timeout of the flow.
	 */
	public short getHardTimeout() {
		return hardTimeout;
	}
	
	/**
	 * Getter for the creation timestamp of the flow cache object.
	 * 
	 * @return <b>long</b> The timestamp when this flow cache object was created.
	 */
	public long getTimestamp() {
		return timestamp;
	}
	
	@Override
    public int hashCode() {
        final int prime = 131;
        int result = 1;
        result = prime * result + match.hashCode();
        result = prime * result + priority;
        // We don't need the actions, since the object is already identified by match and priority (and cookie).
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
        if (!(obj instanceof FlowCacheObj)) {
            return false;
        }
        
        FlowCacheObj other = (FlowCacheObj) obj;
        
        if (priority != other.priority) {
        	return false;
        }
        if (cookie != other.cookie) {
        	return false;
        }
        if (!match.equals(other.match)) {
        	return false;
        }
        if (actions != null && other.actions != null) {
        	for (OFAction action : actions) {
        		if (!other.actions.contains(action)) {
        			return false;
        		}
        	}
        	for (OFAction action : other.actions) {
        		if (!actions.contains(action)) {
        			return false;
        		}
        	}
        }
        
        return true;
	}
	
	@Override
	public String toString() {
		/* The string builder. */
		StringBuilder sb = new StringBuilder();
		
		sb.append("FlowCacheObj [");
		sb.append("pathId=" + pathId + ",");
		sb.append("matchHash=" + id + ",");
		sb.append("active=" + this.isActive() + ",");
		//sb.append("timestamp=" + timestamp + ",");
		sb.append("cookie=0x" + Long.toHexString(cookie) + ",");
		sb.append("priority=" + priority + ",");
		sb.append("outPorts=" + outPorts + ",");
		sb.append("match=" + match + ",");
		sb.append("actions=" + actions);
		if (this.attributes != null && this.attributes.size() > 0) {
			sb.append("hasAttributes=true");
		}
		sb.append("]");
		
		return sb.toString();
	}
}
