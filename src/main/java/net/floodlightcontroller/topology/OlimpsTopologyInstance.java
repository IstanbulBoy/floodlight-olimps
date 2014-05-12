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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.routing.BroadcastTree;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.routing.RouteId;
//import net.floodlightcontroller.topology.Cluster;
import net.floodlightcontroller.topology.NodePortTuple;
//import net.floodlightcontroller.topology.TopologyInstance;
//import net.floodlightcontroller.util.ClusterDFS;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class OlimpsTopologyInstance extends TopologyInstance {
	/** A map that contains spanning tress from each node. */
    protected Map<Long, BroadcastTree> topologyDestinationRootedTrees;
    /** A map that contains all node-port tuples which are part of the spanning tree. */
    protected Set<NodePortTuple> topologyBroadcastNodePorts;
    /** The general spanning tree for the whole topology. */
    protected BroadcastTree broadcastTree;
    /** Cluster that contains all switches. */
    protected OlimpsCluster cluster;
    /** OpenFlow clusters contains a set of (directly and indirectly) connected OpenFlow switches. */
    protected Set<OlimpsCluster> ofClusters;
    protected Map<Long, OlimpsCluster> switchOfClusterMap;
    /** Data structure contains all links, i.e. within an OpenFlow island, between OpenFlow islands, and tunnel links. */
    protected Map<NodePortTuple, Set<Link>> allSwitchPortLinks;
    
//    /**
//     * Constructor. Instantiates all class attributes.
//     */    
//    public TopologyInstance(Map<Long, Set<Short>> switchPorts,
//            Set<NodePortTuple> blockedPorts,
//            Map<NodePortTuple, Set<Link>> switchPortLinks,
//            Set<NodePortTuple> broadcastDomainPorts,
//            Set<NodePortTuple> tunnelPorts) {
//    	super(switchPorts, blockedPorts, switchPortLinks, broadcastDomainPorts, tunnelPorts);
//    	
//        allSwitchPortLinks = new HashMap<NodePortTuple, Set<Link>>(switchPortLinks);
//    	topologyDestinationRootedTrees = new HashMap<Long, BroadcastTree>();
//    	topologyBroadcastNodePorts = new HashSet<NodePortTuple>();
//    	switchOfClusterMap = new HashMap<Long, Cluster>();
//    	ofClusters = new HashSet<Cluster>();
//    	cluster = new Cluster();
//    }
    
    /**
     * Constructor. Instantiates all class attributes.
     */   
    public OlimpsTopologyInstance(Map<Long, Set<Integer>> switchPorts,
            Set<NodePortTuple> blockedPorts,
            Map<NodePortTuple, Set<Link>> openflowLinks,
            Map<NodePortTuple, Set<Link>> broadcastDomainLinks,
            Set<NodePortTuple> tunnelPorts) {
    	super(switchPorts, blockedPorts, openflowLinks, broadcastDomainLinks.keySet(), tunnelPorts);
    	
    	allSwitchPortLinks = new HashMap<NodePortTuple, Set<Link>>();
    	topologyDestinationRootedTrees = new HashMap<Long, BroadcastTree>();
    	topologyBroadcastNodePorts = new HashSet<NodePortTuple>();
    	switchOfClusterMap = new HashMap<Long, OlimpsCluster>();
    	ofClusters = new HashSet<OlimpsCluster>();
    	cluster = new OlimpsCluster();
    
    	// re-assemble the switchPortLinks data structure as allSwitchPortLinks.
        for(NodePortTuple npt: openflowLinks.keySet()) {
            allSwitchPortLinks.put(npt, new HashSet<Link>(openflowLinks.get(npt)));
        }
        for(NodePortTuple npt: broadcastDomainLinks.keySet()) {
            allSwitchPortLinks.put(npt, new HashSet<Link>(broadcastDomainLinks.get(npt)));
        }
        for(NodePortTuple npt: tunnelPorts) {
        	tunnelPorts.add(npt);
            //allSwitchPortLinks.put(npt, new HashSet<Link>(tunnelLinks.get(npt)));
        }
    }
    
    /**
     * Computes the per-cluster spanning trees as well an
     * overall spanning tree for the whole topology.
     */
    public void compute() {
    	// call the parent method to compute the per-cluster spanning trees.
    	super.compute();
    	
//    	// identify OpenFlow clusters
//    	identifyOpenflowClusters();
    	
    	// add links to the general overall cluster.
    	addLinks();
    	
    	// compute spanning general tree for the whole topology.
    	calculateShortestPathTree();
    	
    	// compute the ports included in the spanning tree.
    	calculateBroadcastNodePorts();
    	
//    	// TESTING
//    	System.out.println("TEST STP    : " + topologyBroadcastNodePorts.size() + " : " + topologyBroadcastNodePorts);
    }
    
//    /**
//     * 
//     */
//    public void identifyOpenflowClusters() {
//    	/* Map that maps switch IDs to ClusterDFS. */
//        Map<Long, ClusterDFS> dfsList = new HashMap<Long, ClusterDFS>();
//        /* A set of switches in the current cluster. */
//        Set<Long> currSet = new HashSet<Long>();
//
//        if (switches == null) 
//        	return;
//
//        for (Long sw: switches) {
//            ClusterDFS cdfs = new ClusterDFS();
//            dfsList.put(sw, cdfs);
//        }
//
//        for (Long sw: switches) {
//            ClusterDFS cdfs = dfsList.get(sw);
//            if (cdfs == null) {
//                log.error("No DFS object for switch {} found.", sw);
//            } else if (!cdfs.isVisited()) {
//                mdfsTraverse(0, 1, sw, dfsList, currSet);
//            }
//        }
//    }
    
//    /**
//     * 
//     */
//	private long mdfsTraverse(long parentIndex, long currIndex, long currSw, Map<Long, ClusterDFS> dfsList, Set<Long> currSet) {
//		// Get the DFS object corresponding to the current switch
//		ClusterDFS currDFS = dfsList.get(currSw);
//
//		// Assign the DFS object with right values.
//		currDFS.setVisited(true);
//		currDFS.setDfsIndex(currIndex);
//		currDFS.setParentDFSIndex(parentIndex);
//		currIndex++;
//
//		// Traverse the graph through every outgoing link.
//		if (switchPorts.get(currSw) != null) {
//			for (Short p : switchPorts.get(currSw)) {
//				Set<Link> lset = switchPortLinks.get(new NodePortTuple(currSw, p));
//				if (lset == null)
//					continue; // Here is a problem. switchPortLinks only contains links within an OF island.
//				for (Link l : lset) {
//					long dstSw = l.getDst();
//
////					// ignore incoming links.
////					if (dstSw == currSw)
////						continue;
////
////					// ignore if the destination is already added to another cluster
////					if (switchOfClusterMap.get(dstSw) != null)
////						continue;
////
////					// ignore the link if it is blocked.
////					if (isBlockedLink(l))
////						continue;
////					
////					// ignore this link if it is in broadcast domain
////                    if (isBroadcastDomainLink(l)) 
////                    	continue;
//
//					// Get the DFS object corresponding to the dstSw
//					ClusterDFS dstDFS = dfsList.get(dstSw);
//
//					if (dstDFS.getDfsIndex() < currDFS.getDfsIndex()) {
//						// could be a potential lowpoint
//						if (dstDFS.getDfsIndex() < currDFS.getLowpoint())
//							currDFS.setLowpoint(dstDFS.getDfsIndex());
//
//					} else if (!dstDFS.isVisited()) {
//						// make a DFS visit
//						currIndex = mdfsTraverse(currDFS.getDfsIndex(), currIndex, dstSw, dfsList, currSet);
//
//						if (currIndex < 0)
//							return -1;
//
//						// update lowpoint after the visit
//						if (dstDFS.getLowpoint() < currDFS.getLowpoint())
//							currDFS.setLowpoint(dstDFS.getLowpoint());
//					}
//					// else, it is a node already visited with a higher dfs index, just ignore.
//				}
//			}
//		}
//
//		// Add current node to currSet.
//		currSet.add(currSw);
//
//		// Cluster computation.
//		// If the node's lowpoint is greater than its parent's DFS index,
//		// we need to form a new cluster with all the switches in the
//		// currSet.
//		if (currDFS.getLowpoint() > currDFS.getParentDFSIndex()) {
//			// The cluster thus far forms a strongly connected component.
//			// create a new switch cluster and add the switches in the current
//			// set to the switch cluster.
//			Cluster sc = new Cluster();
//			for (long sw : currSet) {
//				sc.add(sw);
//				switchOfClusterMap.put(sw, sc);
//			}
//			// delete all the nodes in the current set.
//			currSet.clear();
//			// add the newly formed switch clusters to the cluster set.
//			ofClusters.add(sc);
//		}
//
//		return currIndex;
//	}
    
    /**
     * Adds all switches and corresponding links to the 
     * overall cluster.
     */
    protected void addLinks() {
    	// add all switches to cluster.
    	for(long sw: switches) {
    		cluster.add(sw);
    	}
    	
    	// add corresponding links to cluster.
    	for(long sw: switches) {
            if (switchPorts.get(sw) == null)
            	continue;
            for (int p: switchPorts.get(sw)) {
                NodePortTuple npt = new NodePortTuple(sw, p);
                if (allSwitchPortLinks.get(npt) == null) 
                	continue;
                for(Link l: allSwitchPortLinks.get(npt)) {
                    if (isBlockedLink(l)) 
                    	continue;
                    cluster.addLink(l);
                }
            }
        }
    }
    
    /**
     * Sets the link weights and calculates a spanning tree
     * using Dijkstra's algorithm.
     */
    protected void calculateShortestPathTree() {
        topologyDestinationRootedTrees.clear();

        Map<Link, Integer> linkCost = new HashMap<Link, Integer>();
        int tunnel_weight = switchPorts.size() + 1;

        // calculate and set link costs.
        for(NodePortTuple npt: tunnelPorts) {
            if (switchPortLinks.get(npt) == null) 
            	continue;
            for(Link link: switchPortLinks.get(npt)) {
                if (link == null) 
                	continue;
                linkCost.put(link, tunnel_weight);
            }
        }

        // calculate the spanning tree using dijkstra's algorithm.
        for (Long node : cluster.getLinks().keySet()) {
        	BroadcastTree tree = dijkstra(cluster, node, linkCost, true);
        	topologyDestinationRootedTrees.put(node, tree);
        }
    }
    
    /**
     * Calculates the overall broadcast tree by choosing on
     * tree out of all destination rooted trees.
     */
    protected void calculateBroadcastTree() {
    	// cluster.getId() returns the smallest node that's in the cluster
    	broadcastTree = topologyDestinationRootedTrees.get(cluster.getId());
    }
    
    /**
     * Calculates the node ports that are used by the overall
     * spanning tree.
     */
    protected void calculateBroadcastNodePorts() {
        calculateBroadcastTree();

        Set<NodePortTuple> nptSet = new HashSet<NodePortTuple>();
        if (broadcastTree == null)
        	return;
        Map<Long, Link> links = broadcastTree.getLinks();
        if (links == null) 
        	return;
        for(long nodeId: links.keySet()) {
        	Link l = links.get(nodeId);
        	if (l == null) 
        		continue;
        	NodePortTuple npt1 = new NodePortTuple(l.getSrc(), l.getSrcPort());
        	NodePortTuple npt2 = new NodePortTuple(l.getDst(), l.getDstPort());
        	nptSet.add(npt1);
        	nptSet.add(npt2);        	
        }
        topologyBroadcastNodePorts.addAll(nptSet);
    }
    
    /**
     * Getter for broadcastTree.
     * 
     * @return The current broadcast tree.
     */
    public BroadcastTree getBroadcastTree() {
    	return broadcastTree;
    }
    
    /**
     * Getter for topologyBroadcastNodePorts.
     * 
     * @return Map of NodePortTuples.
     */
    public Set<NodePortTuple> getTopologyBroadcastNodePorts() {
    	return topologyBroadcastNodePorts;
    }
    
    /**
     * Returns whether a port is connected to an end host or not
     * 
     * @param switchId The unique switch id
     * @param port The port id
     * @return true if the port is connected to an end host
     */
    public boolean isTopologyAttachmentPointPort(long switchId, int port) {
        NodePortTuple npt = new NodePortTuple(switchId, port);
        if (allSwitchPortLinks.containsKey(npt)) {
        	return false;
        } else {
        	return true;
        }
    }
    
    /**
     * Returns whether a port is connected to another switch in the OpenFlow topology
     * or not
     * 
     * @param switchId The unique switch id
     * @param port The port id
     * @return true if the port is connected to another switch in the OpenFlow topology
     */
    protected boolean isInternalToTopology(long switchId, int port) {
        return !isTopologyAttachmentPointPort(switchId, port);
    }
    
    
    @Override
    protected boolean isIncomingBroadcastAllowedOnSwitchPort(long sw, int portId) {
    	if (isInternalToTopology(sw, portId)) {
    		NodePortTuple npt = new NodePortTuple(sw, portId);
    		if (topologyBroadcastNodePorts.contains(npt))
    			return true;
    		else
    			return false;
    	} else {
    		return true;
    	}
    }
    
    /**
     * Getter for the spanning tree route
     * 
     * @return Route containing the NodePortTuples of the spanning tree.
     */
    @Deprecated
    public Route getSpanningTreeRoute() {
    	List<NodePortTuple> spanningTreeNodePorts = new ArrayList<NodePortTuple>(topologyBroadcastNodePorts);
    	RouteId routeId = new RouteId(0L,0L);
    	return new Route(routeId, spanningTreeNodePorts);
    }
    
    /**
     * Getter for the topology cluster containing all links of the current topology. 
     * 
     * @return Cluster containing tall links of the current topology.
     */
    public OlimpsCluster getCluster() {
    	return cluster;
    }
    
    /**
     * Getter for generalBroadcastNodePorts. Returns all the ports on 
     * the target switch (targetSw) on which a broadcast packet must 
     * be sent from a host whose attachment point is on switch port (src, srcPort).
     * 
     * @param targetSw switch on which a broadcast packet must be send.
     * @param srcSw switch that comprises the corresponding attachment point.
     * @param srcPort port that comprises the attachment point.
     * 
     * @return Set of ports IDs.
     */
    public Set<Integer> getTopologyBroadcastNodePorts(long targetSw, long srcSw, int srcPort) {
        Set<Integer> result = new HashSet<Integer>();
        for(NodePortTuple npt: topologyBroadcastNodePorts) {
            if (npt.getNodeId() == targetSw) {
                result.add(npt.getPortId());
            }
        }
        return result;
    }
    
    @Override
    public String toString() {
    	StringBuffer sb = new StringBuffer("\n");
    	
        sb.append("-----------------------------------------------\n");
        sb.append("Links: " + this.switchPortLinks + "\n");
        sb.append("broadcastDomainPorts: " + broadcastDomainPorts + "\n");
        sb.append("tunnelPorts: " + tunnelPorts + "\n");
        sb.append("clusters: " + clusters + "\n");
        sb.append("OFclusters: " + ofClusters + "\n");
        sb.append("destinationRootedTrees: " + destinationRootedTrees + "\n");
        sb.append("clusterBroadcastNodePorts " + clusterBroadcastNodePorts + "\n");
        sb.append("-----------------------------------------------\n");
        
        return sb.toString();
    }

}
