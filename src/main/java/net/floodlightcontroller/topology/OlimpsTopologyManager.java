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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;
//import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.topology.IOlimpsTopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
//import net.floodlightcontroller.topology.TopologyInstance;
//import net.floodlightcontroller.topology.TopologyManager;

/**
 * Topology manager is responsible for maintaining the controller's notion
 * of the network graph, as well as implementing tools for finding routes 
 * through the topology.
 * 
 * The extended TopologyManager forms clusters (as opposed to OpenFlow islands)
 * that comprise (directly and indirectly) connected OpenFlow switches. Moreover,
 * it calculates a spanning tree for all clusters. This can be used to install
 * OpenFlow rules e.g. for multicast and broadcast messages.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class OlimpsTopologyManager extends TopologyManager implements IOlimpsTopologyService {
	/** The current multipath topology instance. */
    protected OlimpsTopologyInstance currentInstance;
    /** The current multipath topology instance without tunnels. */
    protected OlimpsTopologyInstance currentInstanceWithoutTunnels;
    
    /**
     * Constructor, only calls the constructor of the parent class.
     */
    public OlimpsTopologyManager() {
    	super();
    }
    
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IOlimpsTopologyService.class);
        l.add(ITopologyService.class);
        //l.add(IRoutingService.class);
        return l;
    }
    
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>,
        IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        // We are the class that implements the service
        m.put(IOlimpsTopologyService.class, this);
        m.put(ITopologyService.class, this);
        //m.put(IRoutingService.class, this);
        return m;
    }
    
    @Override
    public boolean createNewInstance() {
        return createNewInstance("internal");
    }
    
    @Override
    public boolean createNewInstance(String reason) {
        Set<NodePortTuple> blockedPorts = new HashSet<NodePortTuple>();
        
        if (!linksUpdated)
        	return false;

        Map<NodePortTuple, Set<Link>> openflowLinks;
        openflowLinks = new HashMap<NodePortTuple, Set<Link>>(switchPortLinks);

        // Remove all tunnel links.
        for(NodePortTuple npt: tunnelPorts) {
            if (openflowLinks.get(npt) != null)
                openflowLinks.remove(npt);
        }

        // Remove all broadcast domain links.
        for(NodePortTuple npt: portBroadcastDomainLinks.keySet()) {
            if (openflowLinks.get(npt) != null)
                openflowLinks.remove(npt);
        }

        OlimpsTopologyInstance nt = new OlimpsTopologyInstance(switchPorts, 
                blockedPorts,
                openflowLinks, 
                portBroadcastDomainLinks, 
                tunnelPorts);
        
        nt.compute();
        // We set the instances with and without tunnels to be identical.
        // If needed, we may compute them differently.
        currentInstance = nt;
        currentInstanceWithoutTunnels = nt;
        return true;
    }
    
    @Override
    public OlimpsTopologyInstance getCurrentInstance() {
        return this.getCurrentInstance(true);
    }
    
    @Override
    public OlimpsTopologyInstance getCurrentInstance(boolean tunnelEnabled) {
        if (tunnelEnabled)
            return currentInstance;
        else 
        	return currentInstanceWithoutTunnels;
    }
    
    /**
     * Returns whether a network port is connected to an end host or not.
     * 
     * @param switchid The unique switch id
     * @param port The port id
     * @return true if the port is connected to an end host
     */
    @Deprecated
    public boolean isTopologyAttachmentPointPort(long switchid, short port) {
        return isTopologyAttachmentPointPort(switchid, port, true);
    }
    
    /**
     * Returns whether a network port is connected to an end host or not.
     * 
     * @param switchid The unique switch id
     * @param port The port id
     * @param tunnelEnabled 
     * @return true if the port is connected to an end host
     */
    @Deprecated
	public boolean isTopologyAttachmentPointPort(long switchid, short port, boolean tunnelEnabled) {
		OlimpsTopologyInstance ti = getCurrentInstance(tunnelEnabled);

		// if the port is not attachment point port according to topology instance, then return false
		if (ti.isTopologyAttachmentPointPort(switchid, port) == false)
			return false;

		// Check whether the port is a physical port. We should not learn attachment points on "special" ports.
		if ((port & 0xff00) == 0xff00 && port != (short) 0xfffe)
			return false;

		// Make sure that the port is enabled.
		IOFSwitch sw = floodlightProvider.getSwitch(switchid);
		if (sw == null)
			return false;
		return (sw.portEnabled(port));
	}
    
    @Override
    public boolean isAttachmentPointPort(long switchid, int port) {
        return isAttachmentPointPort(switchid, port, true);
    }
    
    @Override
	public boolean isAttachmentPointPort(long switchid, int port, boolean tunnelEnabled) {
		OlimpsTopologyInstance ti = getCurrentInstance(tunnelEnabled);

		// if the port is not attachment point port according to topology instance, then return false
		if (ti.isTopologyAttachmentPointPort(switchid, port) == false)
			return false;

//		// Check whether the port is a physical port. We should not learn attachment points on "special" ports.
//		if ((port & 0xff00) == 0xff00 && port != (short) 0xfffe)
//			return false;

		// Make sure that the port is enabled.
		IOFSwitch sw = floodlightProvider.getSwitch(switchid);
		if (sw == null)
			return false;
		
		return (sw.portEnabled(port));
	}
    
    @Override
    public boolean isIncomingBroadcastAllowed(long sw, int portId) {
        return isIncomingBroadcastAllowed(sw, portId, true);
    }
    
    @Override
    public boolean isIncomingBroadcastAllowed(long sw, int portId, boolean tunnelEnabled) {
        OlimpsTopologyInstance ti = this.getCurrentInstance(tunnelEnabled);
        return ti.isIncomingBroadcastAllowedOnSwitchPort(sw, portId);
    }

	@Override
	public OlimpsCluster getTopologyCluster() {
		final OlimpsTopologyInstance ti = this.getCurrentInstance(true);
        return ti.getCluster();
	}
    
}

