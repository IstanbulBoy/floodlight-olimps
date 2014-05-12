package net.floodlightcontroller.routing;

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
import java.util.List;

import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import net.floodlightcontroller.core.web.serializers.PathJSONSerializer;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
import net.floodlightcontroller.topology.NodePortTuple;


/**
 * Represents a path between two switches
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
@JsonSerialize(using=PathJSONSerializer.class)
public class Path implements Comparable<Path> {
	/** Check path for connectivity in forwarding order, i.e. from source to destination. */
	protected static final boolean CHECK_PATH = true;
	
	/** Logger to log ProactiveFlowPusher events. */
	protected static Logger log = LoggerFactory.getLogger(Path.class);
	/** The unique path ID. */
	protected int pathId;
	/** The path end points, defined by source and destination switches. */
    protected EndPoints endPoints;
    /** The port ID of the source port at the source switch of the path. */
    protected int srcPort;
    /** The port ID of the destination port at the destination switch of the path. */
    protected int dstPort;
    /** All links in the path. */
    protected List<Link> links;
    /** Useful if multipath routing (ECMP) available. */
    protected int pathCount;
    /** The capacity of the path, i.e. the minimum of all link capacities. */
    protected int capacity;
    
    /**
     * 
     * @author Michael Bredel <michael.bredel@cern.ch>
     */
    public enum Status {
    	/* Path is installed and contains flows. */
    	ACTIVE,
    	/* Path has no flows. */
    	INACTIVE,
    	/* Path is about to be removed from the path cache. */
    	DEPRECATED
    }
    
    /**
     * Constructor
     * 
     * @param endPoints
     * @param links
     * @param pathId
     * @param capacity
     */
    public Path(EndPoints endPoints, List<Link> links, int pathId, int capacity) {
    	this.endPoints = endPoints;
        this.links = links;
        this.pathId = pathId;
        this.capacity = capacity;
        if (links != null) {
        	this.setPorts(links);
        	if (CHECK_PATH) {
        		this.checkPath();
        	}
        }
    }

    /**
     * Convenience constructor to create path object without a previous end point object.
     * 
     * @param src
     * @param dst
     * @param links
     * @param pathId
     * @param capacity
     */
    public Path(Long src, Long dst, List<Link> links, int pathId, int capacity) {
    	this(new EndPoints(src, dst), links, pathId, capacity);
    }

    /**
     * @return the id
     */
    public EndPoints getEndPoints() {
        return this.endPoints;
    }

    /**
     * @param id the id to set
     */
    public void setEndPoints(EndPoints endPoints) {
        this.endPoints = endPoints;
    }
    
    /**
     * 
     * @return
     */
    public int getId() {
    	return this.pathId;
    }
    
    /**
     * 
     * @param pathNumber
     */
    public void setId(int pathId) {
    	this.pathId = pathId;
    }
    
    /**
     * 
     * @return
     */
    public int getCapacity() {
    	return capacity;
    }
    
    /**
     * 
     * @param capacity
     */
    public void setCapacity(int capacity) {
    	this.capacity = capacity;
    }

    /**
     * Getter for the path as Route.
     * 
     * @return <b>Route</b> The path represented as Route, i.e. as NodePortTuples directly.
     */
    @Deprecated
    public Route getRoute() {
    	/* All NodePortTuples in the path. */
    	List<NodePortTuple> switchPorts = new ArrayList<NodePortTuple>();
    	
    	for (int i = links.size()-1; i>=0; i--) {
    		Link link = this.reverseLink(links.get(i));
    		switchPorts.add(new NodePortTuple(link.getSrc(), link.getSrcPort()));
    		switchPorts.add(new NodePortTuple(link.getDst(), link.getDstPort()));
    	}
    	
        return new Route(new RouteId(endPoints.getSrc(), endPoints.getDst()), switchPorts);
    }
    
    /**
     * Getter for the Path.
     * 
     * @return <b>List&lt;Link&gt;</b> A list of all links in the path.
     */
    public List<Link> getLinks() {
        return links;
    }
    
    /**
     * Returns the source switch of the path.
     * 
     * @return <b>long</b> The switch ID of the source switch of the path.
     */
    public long getSrc() {
    	return this.endPoints.getSrc();
    }
    
    /**
     * Returns the destination switch of the path.
     * 
     * @return <b>long</b> The switch ID of the destination switch of the path.
     */
    public long getDst() {
    	return this.endPoints.getDst();
    }
    
    /**
     * Returns the source port at the source (the first) switch of the path.
     * 
     * @return <b>int</b> The port ID of the source port at the source switch of the path.
     */
    public int getSrcPort() {
    	return this.srcPort;
    }
    
    /**
     * Returns the destination port at the destination (the last) switch of path.
     * 
     * @return <b>int</b> The port ID of the destination port at the destination switch of the path.
     */
    public int getDstPort() {
    	return this.dstPort;
    }

    /**
     * 
     * @param links The list of links to set as a path.
     */
    public void setPath(List<Link> links) {
        this.links = links;
        this.setPorts(links);
    }

    @Override
    public int hashCode() {
        final int prime = 5791;
        int result = 1;
        result = prime * result + ((endPoints == null) ? 0 : endPoints.hashCode());
        result = prime * result + ((links == null) ? 0 : links.hashCode());
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
        Path other = (Path) obj;
        if (endPoints == null) {
            if (other.getEndPoints() != null)
                return false;
        } else if (!endPoints.equals(other.getEndPoints()))
            return false;
        if (links == null) {
            if (other.getLinks() != null)
                return false;
        } else if (!links.equals(other.getLinks()))
            return false;
        return true;
    }

    @Override
    public String toString() {
    	StringBuilder sb = new StringBuilder();
    	
    	sb.append("Path [");
    	sb.append("id=" + this.pathId + ",");
    	sb.append("src=" + HexString.toHexString(this.endPoints.getSrc()) + ",");
    	sb.append("dst=" + HexString.toHexString(this.endPoints.getDst()) + ",");
    	sb.append("links=" + this.links + ",");
    	sb.append("]");
    	
        return sb.toString();
    }

    /**
     * Compares the path lengths between Routes.
     */
    @Override
    public int compareTo(Path o) {
        return ((Integer)links.size()).compareTo(o.links.size());
    }
    
    /**
     * Sets the source and destination port of the path.
     * 
     * @param links The Links of that path
     */
    private void setPorts(List<Link> links) {
    	int size = links.size();
    	Link firstLink = links.get(0);
    	Link lastLink = links.get(size - 1);
    	
    	this.srcPort = firstLink.getSrcPort();
    	this.dstPort = lastLink.getDstPort();
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
    
    /**
     * Check whether a path is valid or not.
     * 
     *  - links are all connected
     *  - links are in src-to-dst order
     * 
     * @return true if the path is OK.
     */
    private boolean checkPath() {
    	// Check that ingress link in list equals the starting end point.
    	if (this.endPoints.getSrc() != this.links.get(0).getSrc()) {
    		if (log.isWarnEnabled()) {
    			log.warn("Entry link switch does not equal starting endpoint. Links in wrong order?");
    		}
    		return false;
    	}
    	// Check that egress link in list equals the ending end point.
    	if (this.endPoints.getDst() != this.links.get(this.links.size()-1).getDst()) {
    		if (log.isWarnEnabled()) {
    			log.warn("Entry link switch does not equal starting endpoint. Links in wrong order?");
    		}
    		return false;
    	}
    	
    	// Check for connectivity in forwarding order, from source to destination.
    	long formerLinkDstSwitch = -1;
    	for (Link link : links) {
    		if (formerLinkDstSwitch > 0) {
    			if (link.getSrc() != formerLinkDstSwitch) {
    				if (log.isWarnEnabled()) {
    	    			log.warn("Current link source switch does not equal former links destination switch.");
    	    		}
    				return false;
    			} else {
    				formerLinkDstSwitch = link.getDst();
    			}
    		}
    	}
    	// Return true.
    	return true;
    }
}
