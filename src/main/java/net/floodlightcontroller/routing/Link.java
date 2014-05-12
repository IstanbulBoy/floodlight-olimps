/**
*    Copyright 2011, Big Switch Networks, Inc. 
*    Originally created by David Erickson, Stanford University
* 
*    Licensed under the Apache License, Version 2.0 (the "License"); you may
*    not use this file except in compliance with the License. You may obtain
*    a copy of the License at
*
*         http://www.apache.org/licenses/LICENSE-2.0
*
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
*    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
*    License for the specific language governing permissions and limitations
*    under the License.
**/

package net.floodlightcontroller.routing;

import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.topology.NodePortTuple;

import org.openflow.util.HexString;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

public class Link {
	/** */
	private NodePortTuple srcTuple;
	/** */
	private NodePortTuple dstTuple;

    /**
     * Default constructor.
     * 
     * @param srcId The source switch Id.
     * @param srcPort The (virtual) source port.
     * @param dstId The destination switch Id.
     * @param dstPort The (virtual) destination port.
     */
    public Link(long srcId, int srcPort, long dstId, int dstPort) {
    	this.srcTuple = new NodePortTuple(srcId, srcPort);
    	this.dstTuple = new NodePortTuple(dstId, dstPort);
    }

    @JsonProperty("src-switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getSrc() {
        return srcTuple.getNodeId();
    }

    @JsonProperty("src-port")
    public int getSrcPort() {
        return srcTuple.getPortId();
    }

    @JsonProperty("dst-switch")
    @JsonSerialize(using=DPIDSerializer.class)
    public long getDst() {
        return dstTuple.getNodeId();
    }
    @JsonProperty("dst-port")
    public int getDstPort() {
        return dstTuple.getPortId();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (dstTuple.getNodeId() ^ (dstTuple.getNodeId() >>> 32));
        result = prime * result + dstTuple.getPortId();
        result = prime * result + (int) (srcTuple.getNodeId() ^ (srcTuple.getNodeId() >>> 32));
        result = prime * result + srcTuple.getPortId();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Link other = (Link) obj;
        if (dstTuple.getNodeId() != other.dstTuple.getNodeId())
            return false;
        if (dstTuple.getPortId() != other.dstTuple.getPortId())
            return false;
        if (srcTuple.getNodeId() != other.srcTuple.getNodeId())
            return false;
        if (srcTuple.getPortId() != other.srcTuple.getPortId())
            return false;
        return true;
    }


    @Override
    public String toString() {
        return "Link [src=" + HexString.toHexString(srcTuple.getNodeId()) 
                + " outPort="
                + OFSwitchPort.physicalPortIdOf(srcTuple.getPortId()) + "(" + OFSwitchPort.vlanIdOf(srcTuple.getPortId()) + ")"
                + ", dst=" + HexString.toHexString(dstTuple.getNodeId())
                + ", inPort="
                + OFSwitchPort.physicalPortIdOf(dstTuple.getPortId()) + "(" + OFSwitchPort.vlanIdOf(dstTuple.getPortId()) + ")"
                + "]";
    }
}


