package net.floodlightcontroller.topology;

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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.floodlightcontroller.routing.Link;

/**
 * TODO: Make the corresponding methods of the parent class visible and remove this one. 
 */
public class OlimpsCluster extends Cluster {
	
	/**
	 * Add a node (switch) to the cluster.
	 * 
	 * This method as the exact same functionality as the
	 * parent method. However, the parent method is not visible
	 * for whatever reason.
	 */
    public void add(long n) {
        if (links.containsKey(n) == false) {
            links.put(n, new HashSet<Link>());
            if (n < id) id = n;
        }
    }

	/**
	 * Add a link to the cluster.
	 * 
	 * This method as the exact same functionality as the
	 * parent method. However, the parent method is not visible
	 * for whatever reason.
	 */
    public void addLink(Link l) {
        if (links.containsKey(l.getSrc()) == false) {
            links.put(l.getSrc(), new HashSet<Link>());
            if (l.getSrc() < id) id = l.getSrc();
        }
        links.get(l.getSrc()).add(l);

        if (links.containsKey(l.getDst()) == false) {
            links.put(l.getDst(), new HashSet<Link>());
            if (l.getDst() < id) id = l.getDst();
        }
        links.get(l.getDst()).add(l);
     }
    
    /**
     * Remove a link from the cluster.
     * 
     * @param link The link that is removed.
     */
    public void delLink(Link link) {
    	/* Source node id. */
    	long srcId = link.getSrc();
    	/* Destination node id. */
    	long dstId = link.getDst();
    	
    	if (links.containsKey(srcId) == true) {
    		links.get(srcId).remove(link);
    		if (links.get(srcId).isEmpty())
    			links.remove(srcId);
    		if (srcId == id && !links.isEmpty()) 
    			id = Collections.min(links.keySet());
    	}
    	
    	if (links.containsKey(dstId) == true) {
    		links.get(dstId).remove(link);
    		if (links.get(dstId).isEmpty())
    			links.remove(dstId);
    		if (dstId == id && !links.isEmpty()) 
    			id = Collections.min(links.keySet());
    	}
    }
    
    /**
     * Returns the number of nodes in the cluster.
     * 
     * @return <b>int</b> number of nodes.
     */
    public int size() {
    	return links.size();
    }
    
    /**
     * Returns true if the cluster is empty and false otherwise.
     * 
     * @return <b>boolean</b> States if the cluster is empty.
     */
    public boolean isEmpty() {
    	return links.isEmpty();
    }
    
    /**
     * Clone the current cluster and return a new, independend Cluster object
     * 
     * @return <b>Cluster</b> A new Cluster object, copied by value.
     */
    @Override
    public OlimpsCluster clone() {
    	/* The new Cluster object. */
    	OlimpsCluster cluster = new OlimpsCluster();
    	/* The new Link map of the new Cluster object. */
    	Map<Long, Set<Link>> links = new HashMap<Long, Set<Link>>();
    	
    	// copy the current cluster id.
    	cluster.setId(id);
    	
    	// copy the map and the link sets containing the current cluster links.
    	for (Entry<Long, Set<Link>> entry : this.links.entrySet()) {
    		links.put(entry.getKey(), new HashSet<Link>(entry.getValue()));
    	}
    	cluster.links = links;
    	
    	return cluster;
    }
}
