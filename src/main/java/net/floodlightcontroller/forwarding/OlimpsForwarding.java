package net.floodlightcontroller.forwarding;

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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import net.floodlightcontroller.arp.ARPMessage;
import net.floodlightcontroller.arp.IARPProxyListener;
import net.floodlightcontroller.arp.IARPProxyService;
import net.floodlightcontroller.configuration.IConfigurationListener;
import net.floodlightcontroller.configuration.IConfigurationService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.annotations.LogMessageCategory;
import net.floodlightcontroller.core.annotations.LogMessageDoc;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.flowcache.FlowCacheObj.Status;
import net.floodlightcontroller.multipath.IMultipathService;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.topology.IOlimpsTopologyService;
import net.floodlightcontroller.topology.ITopologyService;

import org.openflow.protocol.OFBarrierReply;
import org.openflow.protocol.OFBarrierRequest;
import org.openflow.protocol.OFError;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.Wildcards;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.action.OFActionStripVirtualLan;
import org.openflow.protocol.action.OFActionVirtualLanIdentifier;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * TODO: Make sure arpCache does not have a memory leak. Thus, remove old entries continuously.
 * 
 * @author Michael Bredel <michael.bredel@caltech.edu>
 */
@LogMessageCategory("Flow Programming")
public class OlimpsForwarding extends ForwardingBase implements IMultipathService, IARPProxyListener, IConfigurationListener, IFloodlightModule {
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(OlimpsForwarding.class);
	
	/** The default priority of installed FlowMods. */
	protected static final short FLOWMOD_DEFAULT_PRIORITY = 100;
	/** The default priority of installed FlowMods. */
	protected static final short FLOWMOD_DROP_RULE_PRIORITY = 1;
	/** Override FowardingBase default idle timeout. Should be greater then the BARRIER_MESSAGE_TIMEOUT. 10 seconds. */
	protected static final short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 10;
	/** The hard time out for the drop rule. Should be greater then the IDLE_TIMEOUT. 15 seconds. */
	protected static final short FLOWMOD_DROP_RULE_HARD_TIMEOUT = 15;
	/** Request a barrier message whenever a flow mod is installed. */
	protected static final boolean REQUEST_BARRIER_MESSAGE = true;
	/** The timeout we wait for barrier replies. 5 seconds. */
	protected static final short BARRIER_MESSAGE_TIMEOUT = 5;
	/** Usually, do not setup reverse flow. */
	protected static final boolean INSTALL_REVERSE_FLOW = false;
	/** MAC address that identifies RSTP messages. */
	protected static final long RSTP_MAC = 0x0180c200000eL;
	/** MAC address that identifies PRSTP messages. */
	protected static final long PVSTP_MAC = 0x01000ccccccdL;
	
	/** Pathfinder might be used instead of the IRoutingService (That is also implemented by PathFinder.class). However, IPathFinderService is not yet used! */
	protected IPathFinderService pathfinder;
    /** Required Module: */
	protected IARPProxyService arpManager;
	/** Required Module: */
	protected IFlowCacheService flowCache;
	/** Required Module: */
	protected IPathCacheService pathCache;
	/** Required Module: */
    protected IConfigurationService configManager;
    
	/** targetIPAddress -> OFMatch. */
	protected Map<Integer, OFMatch> arpCache;
	/** A cache that stores information regarding barrier requests. switchId -> barrierTransactionId -> Cookie. */
	protected Map<Long, Map<Integer, Long>> barrierCacheCookie = new HashMap<Long, Map<Integer, Long>>();
	/** A Set of already used flow IDs. */
	protected Set<Integer> flowIds;
	/** A list of forwarding listener. */
	protected List<IForwardingListener> forwardingListener;
	/** A temporary set of pending flows, i.e. cookies, we are expecting barrier replies for. cookie -> Set of switchId. */
	protected Map<Long, Set<Long>> pendingFlows;
	/** */
	protected long appCookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0) ;
	
	/** Reentrant lock for the flow id calculation. */
	private final ReentrantLock flowIdLock = new ReentrantLock();
	/** */
	private ExecutorService executor = Executors.newCachedThreadPool();
	/** Default wildcards that match on dl_type, nw_src, nw_dst, nw_proto, tp_src, and tp_dst. */
	private int wildcards = 3145743;
	
	private short idleTimeout;
	private short hardTimeout;
	private short dropTimeout;
	private short barrierMessageTimeout;
	private boolean flowMode;
	
	/**
	 * The path operation.
	 */
	protected enum Operation {
		INSTALL,
		REMOVE,
		MOVE
	}
	
	/**
	 * Installs flow rules on a path.
	 */
	protected class FlowInstallerCallable implements Callable<Boolean> {
		/** */
		boolean installed = false;
		/** */
		IOFSwitch sw;
		/** */
		Path path;
		/** */
		OFMatch match;
		/** */
		int outPort;
		/** */
		List<OFAction> additionalActions;
		/** */
		long cookie;
		/** */
		OFPacketIn packetInMsg;
		/** */
		FloodlightContext cntx;
		
		/**
		 * Default constructor.
		 */
		public FlowInstallerCallable(IOFSwitch sw, OFMatch match, Path path, int outPort, List<OFAction> additionalActions, long cookie, OFPacketIn packetInMsg, FloodlightContext cntx) {
			this.sw = sw;
			this.match = match;
			this.path = path;
			this.outPort = outPort;
			this.additionalActions = additionalActions;
			this.cookie = cookie;
			this.packetInMsg = packetInMsg;
			this.cntx = cntx;
		}

		@Override
		public Boolean call() throws Exception {
			// Install flow.
			installed = installFlow(match, path, outPort, null, cookie);
			// Modify the outPort for the packet out to the first hop.
			outPort = path.getSrcPort();
			// Push the packet. Output port is the output port of the first link in the path or the dstDap port.
			if (installed) {
				// Remove the drop rule.
				clearFlowMod(sw, match, OFPort.OFPP_NONE.getValue(), FLOWMOD_DROP_RULE_PRIORITY, 0, null);
				// Push the packet.
				pushPacket(sw, packetInMsg, false, outPort, cntx);
			}
			return installed;
		}
		
	}
	
	@Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return (type.equals(OFType.PACKET_IN) && name.equals("arpproxy") && name.equals("flowcache"));
    }
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IMultipathService.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(IMultipathService.class, this);
	    return m;
	}
	
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
    	l.add(IFloodlightProviderService.class);
        l.add(IDeviceService.class);
        l.add(ITopologyService.class);
        l.add(ICounterStoreService.class);
        l.add(IPathFinderService.class);
        l.add(IARPProxyService.class);
        l.add(IFlowCacheService.class);
        l.add(IPathCacheService.class);
        l.add(IConfigurationService.class);
        return l;
    }
    
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
    	super.init();
    	floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        deviceManager = context.getServiceImpl(IDeviceService.class);
        topology = context.getServiceImpl(IOlimpsTopologyService.class);
        counterStore = context.getServiceImpl(ICounterStoreService.class);
        pathfinder = context.getServiceImpl(IPathFinderService.class);
        arpManager = context.getServiceImpl(IARPProxyService.class);
        flowCache = context.getServiceImpl(IFlowCacheService.class);
        pathCache = context.getServiceImpl(IPathCacheService.class);
        configManager = context.getServiceImpl(IConfigurationService.class);
    }
    
    @Override
    public void startUp(FloodlightModuleContext context) {
        super.startUp();
        // Register to listen to some more messages.
        floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
        floodlightProvider.addOFMessageListener(OFType.ERROR, this);
        floodlightProvider.addOFMessageListener(OFType.BARRIER_REPLY, this);
        // Register to the ARP proxy.
        arpManager.addListener(this);
        // Register to configuration manager.
        configManager.addListener(this);
        // Initiate local variables.
        arpCache = new HashMap<Integer, OFMatch>();
        flowIds = new HashSet<Integer>();
        pendingFlows = new HashMap<Long, Set<Long>>();
        forwardingListener = new ArrayList<IForwardingListener>();
        // Initiate default timeouts.
        barrierMessageTimeout = OlimpsForwarding.BARRIER_MESSAGE_TIMEOUT;
        idleTimeout = OlimpsForwarding.FLOWMOD_DEFAULT_IDLE_TIMEOUT;
        hardTimeout = OlimpsForwarding.FLOWMOD_DEFAULT_HARD_TIMEOUT;
        dropTimeout = OlimpsForwarding.FLOWMOD_DROP_RULE_HARD_TIMEOUT;
        // Initiate default variables.
        flowMode = OlimpsForwarding.REQUEST_BARRIER_MESSAGE;
    }
    
    @Override
	public void arpReplyReceived(ARPMessage arpMessage) {
		/* The target IP address. */
		int targetIPAddress = arpMessage.getTargetIPAddress();
		
		if (this.arpCache.containsKey(targetIPAddress)) {
			OFMatch match = this.arpCache.get(targetIPAddress);
			IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(arpMessage.getSwitchId());
			// Remove the drop rule.
			this.clearFlowMod(iofSwitch, match, OFPort.OFPP_NONE.getValue(), FLOWMOD_DROP_RULE_PRIORITY, 0, null);
			
			if (log.isWarnEnabled()) {
	    		log.warn("Removing a drop rule on switch " + iofSwitch.getStringId() + " for match " + match);
	    	}
		}
	}

	@Override
    public String getName() {
        return "olimpsforwarding";
    }
    
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        switch (msg.getType()) {
            case PACKET_IN:
            	IRoutingDecision decision = null;
                if (cntx != null)
                	decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
            	return this.processPacketInMessage(sw, (OFPacketIn) msg, decision, cntx);
            case FLOW_REMOVED:
                return this.processFlowRemovedMessage(sw, (OFFlowRemoved) msg, cntx);
            case BARRIER_REPLY:
            	return this.processBarrierReplyMessage(sw, (OFBarrierReply) msg, cntx);
            case ERROR:
                log.info("received an error {} from switch {}", (OFError) msg, sw);
                return Command.CONTINUE;
            default:
            	log.info("received an unknown message {} from switch {}", msg, sw);
            	break;
        }
        
        return Command.CONTINUE;
    }
    
    @Override
    public Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
    	/* Ethernet frame extracted from the Packet_In messages. */
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        
        // If a decision has been made we obey it, otherwise we just forward
        if (decision != null) {
            if (log.isTraceEnabled())
                log.trace("Forwaring decision={} was made for PacketIn={}", decision.getRoutingAction().toString(), pi);
            
            switch(decision.getRoutingAction()) {
                case NONE:
                    // don't do anything
                    return Command.CONTINUE;
                case FORWARD_OR_FLOOD:
                case FORWARD:
                    doForwardFlow(sw, pi, cntx, false);
                    return Command.CONTINUE;
                case MULTICAST:
                    // treat as broadcast
                    //doFlood(sw, pi, cntx);
                    return Command.CONTINUE;
                case DROP:
                	OFMatch match = new OFMatch();
                    match.loadFromPacket(pi.getPacketData(), pi.getInPort());
                    if (decision.getWildcards() != null) {
                        match.setWildcards(decision.getWildcards());
                    }
                    doDropFlow(sw, match, cntx);
                    return Command.CONTINUE;
                default:
                    log.error("Unexpected decision made for this packet-in={}", pi, decision.getRoutingAction());
                    return Command.CONTINUE;
            }
        } else {
            // Drop CFM messages
            if (eth.getEtherType() == (short) 0x8902) {
            	return Command.STOP;
            }
            // Drop BPDU messages.
            if (eth.getDestinationMAC().toLong() == PVSTP_MAC || eth.getDestinationMAC().toLong() == RSTP_MAC) {
            	return Command.STOP;
            }
            // Drop all broadcast and multicast messages. 
        	if (eth.isBroadcast() || eth.isMulticast()) {
        		// For now we don't support multicast as broadcast.
        		return Command.STOP;
        	}

   			doForwardFlow(sw, pi, cntx, true);
        }
        
        return Command.CONTINUE;
    }
	
	@Override
	protected OFMatch wildcard(OFMatch match, IOFSwitch sw, Integer hints) {
//	    int wildcards = ((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue() &
//	    	//~OFMatch.OFPFW_IN_PORT &
//	    	//~OFMatch.OFPFW_DL_SRC &
//	    	//~OFMatch.OFPFW_DL_DST &
//	    	//~OFMatch.OFPFW_DL_VLAN &
//	    	// use IPv4 addresses to distinguish between flows.
//	    	~OFMatch.OFPFW_DL_TYPE &
//	        ~OFMatch.OFPFW_NW_SRC_MASK & 
//	        ~OFMatch.OFPFW_NW_DST_MASK &
//	    	// use ports to distinguish between flows.
//	    	~OFMatch.OFPFW_NW_PROTO &
//	    	~OFMatch.OFPFW_TP_DST &
//	    	~OFMatch.OFPFW_TP_SRC;
	    int wildcards = ((Integer)sw.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue() & this.wildcards;
	    return match.clone().setWildcards(wildcards);
	}

	/**
	 * Forwards a flow, i.e. it calculates a path and installs all flow mods on switches along the path.
	 * 
	 * @param sw
	 * @param packetInMsg
	 * @param cntx
	 * @param requestFlowRemovedNotifn
	 */
	protected synchronized void doForwardFlow(IOFSwitch sw, OFPacketIn packetInMsg, FloodlightContext cntx, boolean requestFlowRemovedNotifn) {
		/* The match filter. */
		OFMatch match = new OFMatch();
        /* Wildcard hints used in net.floodlightcontroller.forwarding.Forwarding.wildcard(...). (Unused). */
        int wildcardHints = 0;

		// Initialize the match filter from the packetIn message.
		match.loadFromPacket(packetInMsg.getPacketData(), packetInMsg.getInPort());
		match = wildcard(match, sw, wildcardHints);

		// Avoid a new path calculation and mapping, if the flow is already present.
		// TODO: Check for the whole path.
		if (this.flowCache.hasFlow(IFlowCacheService.DEFAULT_DB_NAME, sw.getId(), appCookie, FLOWMOD_DEFAULT_PRIORITY, match, null)) {
			// Initiate a flow statistic request to check if forwarding entry is still/also on the switch.
			this.flowCache.querySwitchFlowTable(sw.getId());
			
			// Query the flow cache to find an corresponding match to the packet in. 
			FlowCacheQuery fcq = new FlowCacheQuery(IFlowCacheService.DEFAULT_DB_NAME, this.getName(), null, sw.getId(), match);
			Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
			FlowCacheQueryResp fcqr = null;
			try {
				fcqr = future.get(1, TimeUnit.SECONDS);
			} catch (TimeoutException e) {
				if (log.isDebugEnabled()) {
					log.debug("We did not find any flow cache object in time for query {}", fcq);
				}
				// Just do nothing and continue.
			} catch (InterruptedException | ExecutionException e) {
				if (log.isDebugEnabled()) {
					log.debug(e.toString());
				}
				// Just do nothing and continue.
			}
			
			// Once we find the flow cache object. If its active, send the packet and return.
			if (fcqr != null) {
				for (FlowCacheObj fco : fcqr.flowCacheObjList) {
					if ( (fco.isActive() || fco.isPending()) && fco.getMatch().equals(match) && fco.getOutPorts() != null) {
						for (int outPort : fco.getOutPorts()) {
							this.pushPacket(sw, packetInMsg, false, outPort, cntx);
						}
					}
				}
				return;
			}
			
//			// Once we find the flow cache object. If its active, send the packet and return.
//			if (fcqr != null) {
//				FlowCacheObj activeFco = null;
//				for (FlowCacheObj fco : fcqr.flowCacheObjList) {
//					if (fco.isActive()) {
//						if (activeFco != null) {
//							activeFco = fco;
//						} else if (fco.isActive()) {
//							if (log.isErrorEnabled()) {
//								log.error("We found more then one flow cache object for this match {}", fcqr.flowCacheObjList);
//							}
//							return;
//						}
//					}
//				}
//				
//				if (activeFco != null && activeFco.getMatch().equals(match) && activeFco.getOutPorts() != null) {
//					for (int outPort : activeFco.getOutPorts()) {
//						this.pushPacket(sw, packetInMsg, true, OFSwitchPort.physicalPortIdOf(outPort), cntx);
//					}
//					return;
//				} else {
//					return;
//				}
//			}

//			// Once we find the flow cache object. If its active, send the packet and return.
//			if (fcqr != null && fcqr.flowCacheObjList.size() == 1) {
//				FlowCacheObj fco = fcqr.flowCacheObjList.get(0);
//				if (fco.isActive() && fco.getMatch().equals(match) && fco.getOutPorts() != null) {
//					for (int outPort : fco.getOutPorts()) {
//						this.pushPacket(sw, packetInMsg, true, OFSwitchPort.physicalPortIdOf(outPort), cntx);
//						return;
//					}
//				}
//			} else {
//				if (log.isDebugEnabled()) {
//					if (fcqr != null) {
//						log.debug("We found more then one flow cache object for this match {}", fcqr.flowCacheObjList);
//					}
//				}
//				return;
//				// Just do nothing and continue.
//			}
		} else {
//			// Add a new flow (without any action) to the flow cache and set its status to PENDING
//			this.flowCache.addFlow(IFlowCacheService.DEFAULT_DB_NAME, sw.getId(), appCookie, FLOWMOD_DEFAULT_PRIORITY, match, null)
//				.setStatus(FlowCacheObj.Status.PENDING);
		}
		
		// Check if we have the location of the destination host (dstDevice)
		IDevice dstDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_DST_DEVICE);

		if (dstDevice == null) {
			if (log.isWarnEnabled()) {
				log.warn("No device entry found for destination device: {}. Thus, initiate ARP request and install temporary drop rule.", dstDevice);
			}
			// Install a drop rule on the switch with hard timeout (5s) to prevent flooding the controller.
			doDropFlow(sw, match, cntx);
	    	// Put the target IP address to the ARP cache.
	    	this.arpCache.put(match.getNetworkDestination(), match);
			// Initialize an ARP request, just in case we missed something, e.g. the sending host uses static ARP.
			doARP(sw.getId(), packetInMsg.getInPort(), cntx);
			// Return.
			return;
		} else {
			// Check the source host (srcDevice)
			IDevice srcDevice = IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE);
			if (srcDevice == null) {
				log.error("No device entry found for source device");
				return;
			}

			// Validate that the source and destination are not on the same switch port
			for (SwitchPort dstDap : dstDevice.getAttachmentPoints()) {
				if ((sw.getId() == dstDap.getSwitchDPID()) && (packetInMsg.getInPort() == dstDap.getPort())) {
					if (log.isWarnEnabled()) {
						log.warn("Both source and destination are on the same switch/port {}/{}, Action = NOP", sw.toString(), packetInMsg.getInPort());
					}
					return;
				}
			}

			// Install all the routes where both src and dst have attachment
			// points. Since the lists are stored in sorted order we can
			// traverse the attachment points in O(m+n) time
			SwitchPort[] srcDaps = srcDevice.getAttachmentPoints();
			Arrays.sort(srcDaps, clusterIdComparator);
			SwitchPort[] dstDaps = dstDevice.getAttachmentPoints();
			Arrays.sort(dstDaps, clusterIdComparator);
			
			// Validate that we have the attachment points
			if (srcDaps == null || srcDaps.length == 0 || dstDaps == null || dstDaps.length == 0) {
				if (log.isWarnEnabled()) {
					log.warn("Attachment points missing. Thus, install temporary drop rule and initiate ARP request.");
				}
				// Initialize an ARP request, just in case we missed something, e.g. the sending host uses static ARP.
				doARP(sw.getId(), packetInMsg.getInPort(), cntx);
		    	// Put the target IP address to the ARP cache.
		    	this.arpCache.put(match.getNetworkDestination(), match);
				// Install a drop rule on the switch with hard timeout (5s) to prevent flooding the controller.
				doDropFlow(sw, match, cntx);
				// Return.
				return;
			}
			
			// Validate that we have only one attachment point. TODO: Fix duplicated attachment points! -> See TopologyManager.isAttachmentPoint().
			if (srcDaps.length != 1 || dstDaps.length != 1) {
				if (log.isErrorEnabled()) {
					log.error("We have multiple attachment points: srcDaps={}, dstDaps={}.", Arrays.toString(srcDaps), Arrays.toString(dstDaps));
				}
				// Return. 
				return;
			}
			
			SwitchPort srcDap = srcDaps[0];
			SwitchPort dstDap = dstDaps[0];
			
			if (!srcDap.equals(dstDap)) {
				// Get a path from the source to the destination switch
				Path path = this.pathfinder.getPath(srcDap.getSwitchDPID(), dstDap.getSwitchDPID(), match);
				// Get the last output port for the first packet.
				int outPort = dstDap.getPort();
				// Install the path on the switches.
				if (path != null) {
					int flowId  = this.getNextFlowId();
					long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, flowId);
					this.executor.submit(new FlowInstallerCallable(sw, match, path, outPort, null, cookie, packetInMsg, cntx));
				}
				
//				// Install the path on the switches.
//				if (path != null) {
//					int flowId  = this.getNextFlowId();
//					long cookie = AppCookie.makeCookie(FORWARDING_APP_ID, flowId);
//					this.installFlow(match, path, outPort, null, cookie);
//					// Modify the outPort for the packet out to the first hop.
//					outPort = path.getSrcPort();
//				} else {
//					// This should not happen any more, since we have a path also for only one switch!
//					this.installLastHop(dstDap.getSwitchDPID(), match, this.appCookie, outPort, 0, cntx);
//				}
//				
//				// Push the packet. Output port is the output port of the first link in the path or the dstDap port.
//				this.pushPacket(sw, packetInMsg, false, outPort, cntx);
			} else {
				if (log.isWarnEnabled()) {
					log.warn("source attachment point equals destination attachment Point. Packet is discarded. " + srcDap);
				}
			}
		} 
	}
	
    /**
     * Installs an implicit drop flow rule, i.e. a flow rule without an action,
     * for a given flow on a switch.
     * 
     * The alternative would be to install a rule that forwards to the virtual 
     * port OFPort.OFPP_NONE. Should we use that?
	 * 
	 * @param sw
	 * @param match
	 * @param cntx
	 */
    @LogMessageDoc(level="ERROR",
            message="Failure writing drop flow mod",
            explanation="An I/O error occured while trying to write a drop flow mod to a switch",
            recommendation=LogMessageDoc.CHECK_SWITCH)
	protected void doDropFlow(IOFSwitch sw, OFMatch match, FloodlightContext cntx) {
    	/* The FlowMod message. */
		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		
    	if (log.isDebugEnabled()) {
    		log.debug("Writing a drop rule on switch " + sw.getStringId() + " for match " + match);
    	}
    	
		// Application cookie.
	    flowMod.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0));
	    // Idle timeout.
	    flowMod.setIdleTimeout(this.idleTimeout);
	    // Hard timeout of 5 seconds.
	    flowMod.setHardTimeout(this.dropTimeout);
	    // Buffered packet to apply to (or -1).
	    flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
	    // Add a new flow rule.
	    flowMod.setCommand(OFFlowMod.OFPFC_ADD);
	    // priority: very low.
	    flowMod.setPriority(OlimpsForwarding.FLOWMOD_DROP_RULE_PRIORITY);
	    // Flags: send flow remove message.
	    flowMod.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
	    // Fields to match: the packet in match.
	    flowMod.setMatch(match);
	    // Actions: no actions = drop
	    flowMod.setActions(new ArrayList<OFAction>());
	    // Length
	    flowMod.setLength((short) OFFlowMod.MINIMUM_LENGTH);
	    
	    // Write flowMod.
	    writeFlowMod(sw, flowMod, cntx);
	}

	/**
	 * 
	 * @param switchId
	 * @param match
	 * @param cookie
	 * @param outPort
	 * @param pathId
	 * @param cntx
	 */
	protected void installLastHop(long switchId, OFMatch match, long cookie, int outPort, int pathId, FloodlightContext cntx) {
		// Get the last switch, i.e. the destination device attachment point.
		IOFSwitch sw = floodlightProvider.getSwitch(switchId);
		// Wildcard the flow according to the configuration in this.wildcard(...).
		match = this.wildcard(match, sw, 0);
		// Add flow mod.
		FlowCacheObj fco = this.addFlowMod(sw, match, outPort, cookie, cntx);
		// Write path Id to flow cache object.
		fco.setPathId(pathId);
		// Flush switch.
		sw.flush();
		// Request a barrier message
        if (flowMode) {
    		int xid = this.sendBarrierRequest(sw, cntx);
    		// Add the pending barrier message.
    		this.addBarrierXid(switchId, xid, cookie);
    		// Add pending flow.
    		this.addPendingFlow(cookie, switchId);
    	}
		
		if (log.isTraceEnabled()) {
			log.trace("Installed flow on: " + HexString.toHexString(switchId) + " : " + match + " : " + OFSwitchPort.stringOf(outPort));
		}
	}
	
	/**
	 * 
	 * @param switchId
	 * @param match
	 * @param outport
	 * @param cookie
	 * @param cntx
	 */
	protected void removeLastHop(long switchId, OFMatch match, int outPort, long cookie, FloodlightContext cntx) {
		// Get the last switch, i.e. the destination device attachment point.
		IOFSwitch iofSwitch = floodlightProvider.getSwitch(switchId);
		// Strip the physical port number from the virtual output port number.
		short phyOutportId = OFSwitchPort.physicalPortIdOf(outPort);
		// Check for a VLAN id.
		if (OFSwitchPort.vlanIdOf(outPort) != 0) {
			match.setDataLayerVirtualLan(OFSwitchPort.vlanIdOf(outPort));
		}
		// Clear flow mod.
		this.clearFlowMod(iofSwitch, match, phyOutportId, FLOWMOD_DEFAULT_PRIORITY, cookie, cntx);
	}

	/**
	 * Pushes the links of a path to the corresponding switches.
	 * 
	 * @param path The path to push to the switches.
	 * @param match The flow match.
	 * @param cookie The unique cookie.
	 * @param cntx The Floodlight context.
	 * @return <b>boolean</b> true of the path setup in successfully initiated.
	 */
	protected boolean installPath(Path path, OFMatch match, long cookie, FloodlightContext cntx) {
		if (path == null)
			return false;
		
		// Install the path
		return this.updatePath(path, match, cookie, Operation.INSTALL, cntx);
	}
	
	/**
	 * 
	 * @param path The path to be removed from the switches.
	 * @param match The flow match.
	 * @param cntx The Floodlight context.
	 */
	protected void removePath(Path path, OFMatch match, long cookie, FloodlightContext cntx) {
		if (path == null)
			return;
	
		// TODO: Need to find the correct device attachment point (the out port).
		this.removeLastHop(path.getDst(), match, OFSwitchPort.virtualPortIdOf(OFPort.OFPP_ALL.getValue(), (short) 0), cookie, cntx);
		
		// Remove the path.
		this.updatePath(path, match, cookie, Operation.REMOVE, cntx);
		
		// Remove the flow id form the flowIds set.
		this.removeFlowId(AppCookie.extractUser(cookie));
	}
	
	/**
	 * Adds or removes flow mods to or from a path.
	 * 
	 * @param path The path to push to the switches.
	 * @param match The flow match.
	 * @param operation The path operation, i.e INSTALL, REMOVE, MOVE
	 * @param cntx The Floodlight context.
	 * @return
	 */
	protected boolean updatePath(Path path, OFMatch match, long cookie, Operation operation, FloodlightContext cntx) {
		/* We need to update the match.inputPort for the first hop. */
		short firstPhyInputPort = match.getInputPort();
        /* The links of the path. */
        List<Link> links = path.getLinks();
        
        boolean moveOn = true;
        long switchId;
        int outPort;
        IOFSwitch iofSwitch;
        
        // Install links in reverse order, i.e. begin at the last (destination) switch.
        ListIterator<Link> iter = links.listIterator(links.size());
    	// Get the last link.
    	Link link = iter.previous();
        do {
        	// Get the current switchId in the path.
        	switchId = link.getSrc();
        	outPort = link.getSrcPort();
            //Get the current switch in the path.
            iofSwitch = floodlightProvider.getSwitch(switchId);
            // Check if we found the switch.
            if (iofSwitch == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to update path, switch {} not available", HexString.toHexString(switchId));
                }
                // Remove the path again if necessary.
                if (operation == Operation.INSTALL || operation == Operation.MOVE) {
                	match.setInputPort(firstPhyInputPort);
                	this.updatePath(path, match, cookie, Operation.REMOVE, cntx);
                	return false;
                }
            }
            
            if (iter.hasPrevious()) {
                // Get the next link.
            	link = iter.previous();
            	// Update the match.inputPort.
        		match.setInputPort(OFSwitchPort.physicalPortIdOf(link.getDstPort()));
        	} else {
        		outPort = link.getSrcPort();
        		match.setInputPort(firstPhyInputPort);
        		moveOn = false;
        		// Wait for all barrier replies.
        		long startTime = System.currentTimeMillis();
        		if (flowMode && operation == Operation.INSTALL) {
        			while( this.pendingFlows.containsKey(cookie) && (System.currentTimeMillis() - startTime) < this.barrierMessageTimeout * 1000) {
        				// Busy waiting.
        				try {
							Thread.sleep(100);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
        			}
        			// Check if flow was installed successfully. 
        			if (this.pendingFlows.containsKey(cookie)) {
        				// Error output.
        				for (Long unhandledSwitchId : this.pendingFlows.get(cookie)) {
        					if (log.isErrorEnabled())
        						log.error("Did not receive a barrier message for 0x" + Long.toHexString(cookie) + " from " + HexString.toHexString(unhandledSwitchId) + " in time.");
        				}
        				
        				// Remove the path again if necessary.
                        if (operation == Operation.INSTALL || operation == Operation.MOVE) {
                        	match.setInputPort(firstPhyInputPort);
                        	this.updatePath(path, match, cookie, Operation.REMOVE, cntx);
                        	return false;
                        }
        			}
        		}
        	}
            
            // Write flow-mod.
            switch (operation) {
            	case INSTALL:
            		// Wildcard the flow according to the configuration in this.wildcard(...).
            		match = this.wildcard(match, iofSwitch, 0);
            		// Add flow.
            		FlowCacheObj fco = this.addFlowMod(iofSwitch, match, outPort, cookie, cntx);
                    // Write path Id to flow cache object.
            		if (fco != null) {
            			fco.setPathId(path.getId());
            		}
            		if (log.isTraceEnabled()) {
            			log.trace("Installed flow on: " + HexString.toHexString(switchId) + " : " + match + " : " + OFSwitchPort.stringOf(outPort));
            		}
            		break;
            	case REMOVE:
                    this.clearFlowMod(iofSwitch, match, OFSwitchPort.physicalPortIdOf(outPort), FLOWMOD_DEFAULT_PRIORITY, cookie, cntx);
                    this.removePendingFlow(cookie, switchId);
            		break;
            	case MOVE:
            		break;
            }
            
            // Flush switch.
            iofSwitch.flush();
            // Query barrier request.
    		if (flowMode && operation == Operation.INSTALL) {
        		int xid = this.sendBarrierRequest(iofSwitch, cntx);
        		// Add the pending barrier message.
        		this.addBarrierXid(switchId, xid, cookie);
        		// Add pending flow.
        		this.addPendingFlow(cookie, switchId);
        	}
        } while (moveOn);
        
        // Return.
		return true;
	}
    
    /**
     * Pushes a packet-out to a switch. The assumption here is that
     * the packet-in was also generated from the same switch. Thus, if the input
     * port of the packet-in and the output port are the same, the function will not 
     * push the packet-out.
     * 
     * @param sw Switch that generated the packet-in, and from which packet-out is sent.
     * @param pi Packet-in message.
     * @param useBufferId If true, use the bufferId from the packet in and do not add the packetIn's payload. If false set bufferId to BUFFER_ID_NONE and use the packetIn's payload 
     * @param outport The Output port.
     * @param cntx The context of the packet.
     */
    protected void pushPacket(IOFSwitch sw, OFPacketIn pi, boolean useBufferId, int outPort, FloodlightContext cntx) {
        if (pi == null) {
            return;
        }

        // The assumption here is that (sw) is the switch that generated the 
        // packet-in. If the input port is the same as output port, then
        // the packet-out should be ignored.
        if (pi.getInPort() == outPort) {
            if (log.isDebugEnabled()) {
                log.debug("Attempting to do packet-out to the same interface as packet-in. Dropping packet. SrcSwitch={}, pi={}", new Object[]{sw, pi});
                return;
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Push packet out to " + sw.getStringId() + " - " + OFSwitchPort.stringOf(outPort) + "\n");
        }
        
        // Strip the physical output port id.
        short phyOutPortId = OFSwitchPort.physicalPortIdOf(outPort);
        // Strip the vlan id.
        short vlanId = OFSwitchPort.vlanIdOf(outPort);

        // Create new packet out message
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        // Set actions
        short actionsLength = 0;
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(phyOutPortId, (short) 0xffff));
        actionsLength += OFActionOutput.MINIMUM_LENGTH;
        
        po.setActions(actions) 
        	.setActionsLength(actionsLength);
        short poLength = (short) (po.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);

        if (useBufferId) {
            po.setBufferId(pi.getBufferId());
        } else {
            po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
            /* Get the Ethernet frame representation of the PacketIn message. */
    		Ethernet ethPacket = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
    		if (vlanId != 0)
    			ethPacket.setVlanID(vlanId);
            byte[] packetData = ethPacket.serialize();
            poLength += packetData.length;
            po.setPacketData(packetData);
        }

        po.setInPort(pi.getInPort());
        po.setLength(poLength);

        try {
            counterStore.updatePktOutFMCounterStoreLocal(sw, po);
            messageDamper.write(sw, po, cntx);
            sw.flush();
        } catch (IOException e) {
            log.error("Failure writing packet out", e);
        }
    }
    
    /**
     * Processes a flow removed message.
     * 
     * @param sw The switch that sent the flow removed message.
     * @param flowRemovedMsg The flow removed message.
     * @param cntx The Floodlight context.
     * @return Whether to continue processing this message or stop.
     */
    private Command processFlowRemovedMessage(IOFSwitch sw, OFFlowRemoved flowRemovedMsg, FloodlightContext cntx) {
    	/* The switch ID. */
    	long switchId = sw.getId();
    	
        if (AppCookie.extractApp(flowRemovedMsg.getCookie()) != FORWARDING_APP_ID) {
            return Command.CONTINUE;
        }

        if (log.isTraceEnabled()) {
            log.trace("{} flow entry removed {}", sw, flowRemovedMsg);
        }
        
        // Don't remove other drop rules.
        if (AppCookie.extractUser(flowRemovedMsg.getCookie()) == 0)
        	return Command.STOP;
        
        // Match
        OFMatch match = flowRemovedMsg.getMatch();
        // Remove flow(s) from the flow cache.
        FlowCacheObj fco = this.updateFlowCache(switchId, OFFlowMod.OFPFC_DELETE_STRICT, flowRemovedMsg.getPriority(), (short) 0, (short) 0, flowRemovedMsg.getCookie(), match, null, cntx);
        // Find and remove the whole path.
        if (fco != null) {
        	this.removePath(pathCache.getPath(fco.getPathId()), match, flowRemovedMsg.getCookie(), null);
        } else {
        	// Make sure the flow id is removed from flowIds.
        	this.removeFlowId(AppCookie.extractUser(flowRemovedMsg.getCookie()));
        }
        
        // When a flow entry expires, it means the device with the matching source
        // MAC address and VLAN either stopped sending packets or moved to a different
        // port. If the device moved, we can't know where it went until it sends
        // another packet, allowing us to re-learn its port. Meanwhile we somehow
        // need to remove the complete route from all the related switches.
        
        // Also, if packets keep coming from another device (e.g. from ping), the
        // corresponding reverse flow entry will never expire on its own and will
        // send the packets to the wrong port (the matching input port of the
        // expired flow entry), so we must delete the reverse entry explicitly.
//		OFMatch forwardMatch = match.clone()
//                .setNetworkSource(match.getNetworkDestination())
//                .setNetworkDestination(match.getNetworkSource())
//                .setTransportSource(match.getTransportDestination())
//                .setTransportDestination(match.getTransportSource());
//		// Remove the forwarding flow (if any).
//		this.clearFlowMod(sw, forwardMatch, match.getInputPort(), flowRemovedMsg.getPriority(), flowRemovedMsg.getCookie(), cntx);
		
        return Command.STOP;
    }
    
    /**
     * TODO: since we query the flow cache twice, there is room for a performance optimization.
     * 
     * @param iofSwitch The switch that sent the flow removed message.
     * @param flowRemovedMsg The flow removed message.
     * @param cntx The Floodlight context.
     * @return <b>Command</b> Return Command.CONTINUE to continue the message processing by other modules.
     */
    private Command processBarrierReplyMessage(IOFSwitch iofSwitch, OFBarrierReply barrierReplyMsg, FloodlightContext cntx) {
    	/* A set of flow cache objects queried from the flow cache. */
		List<FlowCacheObj> flowCacheObjects = null;
		
    	if (log.isDebugEnabled()) {
			log.debug("Received barrier reply message from {} with Xid {}", iofSwitch.getStringId(), barrierReplyMsg.getXid());
		}
    	
    	int xid = barrierReplyMsg.getXid();
    	long cookie = this.removeBarrierXid(iofSwitch.getId(), xid);
    	if (cookie != 0) {
    		// Remove the cookie+switch combination from the pending flows.
			this.removePendingFlow(cookie, iofSwitch.getId());
    		// Query flow cache for the flow from this switch with the given cookie.
    		FlowCacheQuery fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, this.getName(), null, iofSwitch.getId());
			fcq.setCookie(cookie);
			flowCacheObjects = this.queryFlowCache(fcq);
			
			if (flowCacheObjects.size() == 1) {
				FlowCacheObj fco = flowCacheObjects.get(0);
				fco.setStatus(Status.ACTIVE);
			} else {
				return Command.STOP;
			}
    		
			// Query flow cache for all flows with the given cookie.
    		fcq = new FlowCacheQuery(null, IFlowCacheService.DEFAULT_DB_NAME, this.getName(), null, 0L);
			fcq.setCookie(cookie);
			flowCacheObjects = this.queryFlowCache(fcq);
			
			for (FlowCacheObj fco : flowCacheObjects) {
				if (!fco.isActive())
					return Command.STOP;
			}
				
			// Notify that all flows of a path are active. 
			for (IForwardingListener listener : this.forwardingListener) {
				listener.flowInstalled(cookie);
			}
			
			return Command.STOP;
    	}
    	
		return Command.CONTINUE;
	}

	/**
	 * Sends a barrier request. Barrier requests are flushed immediately.
	 * 
	 * @param iofSwitch
	 * @param cntx
	 * @param <b>int</b> The transaction id of the barrier request.
	 */
	private int sendBarrierRequest(IOFSwitch iofSwitch, FloodlightContext cntx){
		/* The barrier request message. */
		OFBarrierRequest barrierRequest = (OFBarrierRequest) floodlightProvider.getOFMessageFactory().getMessage(OFType.BARRIER_REQUEST);
		/* The next transaction ID of the switch. */
		int xid = iofSwitch.getNextTransactionId();

		if (log.isDebugEnabled()) {
			log.debug("Sending barrier request message to {} with Xid {}", iofSwitch.getStringId(), xid);
		}
		
		try {
			barrierRequest.setXid(xid);
			messageDamper.write(iofSwitch, barrierRequest, cntx, true);
			iofSwitch.flush();
		} catch (IOException e) {
			e.printStackTrace();
			return 0;
		}
		
		return xid;
	}

	/**
     * Initiates an ARP request to find a host that is yet unknown, e.g. because
     * the sending host uses static ARP.
     * 
     * @param switchId
     * @param port
     * @param cntx
     */
    private void doARP(long switchId, int port, FloodlightContext cntx) {
    	/* Ethernet frame extracted from the Packet_In messages. */
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        
        if (eth.getPayload() instanceof IPv4) {
        	IPv4 ipPacket = (IPv4) eth.getPayload();
        	arpManager.initiateARPRequest(switchId, port, eth.getSourceMAC().toLong(), ipPacket.getSourceAddress(), ipPacket.getDestinationAddress());
        }
    }
    
    /**
     * Adds a flow mod to a switch.
     * 
     * @param iofSwitch
     * @param match
     * @param outPort
     * @param cookie
     * @param cntx
     * @return <b>FlowCacheObj</b> A flow cache object representing this flow.
     */
    private FlowCacheObj addFlowMod(IOFSwitch iofSwitch, OFMatch match, int outPort, long cookie, FloodlightContext cntx) {
    	return this.writeFlowMod(iofSwitch, OFFlowMod.OFPFC_ADD, OFPacketOut.BUFFER_ID_NONE, match, outPort, FLOWMOD_DEFAULT_PRIORITY, cookie, cntx);
    }
    
	/**
	 * Removes a flow mod from a switch.
	 * 
	 * @param iofSwitch
	 * @param match
	 * @param outPort
	 * @param priority
	 * @param cookie
	 * @param cntx
	 */
	private void clearFlowMod(IOFSwitch iofSwitch, OFMatch match, short outPort, short priority, long cookie, FloodlightContext cntx) {
		if (priority == 0)
			priority = FLOWMOD_DEFAULT_PRIORITY;
		if (cookie == 0)
			cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);
		this.writeFlowMod(iofSwitch, OFFlowMod.OFPFC_DELETE_STRICT, OFPacketOut.BUFFER_ID_NONE, match, outPort, priority, cookie, cntx);
	}
	
	/**
     * Convenient method to write a OFFlowMod to a switch.
     * 
     * @param iofSwitch The switch to write the flowMod to.
     * @param command The FlowMod actions (add, delete, etc).
     * @param bufferId The buffer ID if the switch has buffered the packet.
     * @param match The OFMatch structure to write.
     * @param outPort The switch port to output it to.
     * @param priority The priority of the flow rule.
     * @param cookie
     * @param cntx The Floodlight context.
     * @return <b>FlowCacheObj</b> A flow cache object representing this flow.
	 */
	private FlowCacheObj writeFlowMod(IOFSwitch iofSwitch, short command, int bufferId, OFMatch match, int outPort, short priority, long cookie, FloodlightContext cntx) {
		/* The new FlowMod message. */
		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		// Get the input port from the match (before canonicalizing the match).
		short phyInPortId = match.getInputPort();
		// Canonicalize match object.
		this.wildcard(match, iofSwitch, 0);
		FlowCacheObj.wildcardMatch(match);
		// Strip the physical output port id.
		short phyOutPortId = OFSwitchPort.physicalPortIdOf(outPort);
		// Strip the vlan id.
		short vlanId = OFSwitchPort.vlanIdOf(outPort);			
		// Flags: If it is not a delete command, request flow removed notification
        short flags = ((command == OFFlowMod.OFPFC_DELETE_STRICT) ? 0 : OFFlowMod.OFPFF_SEND_FLOW_REM);
        // Use this applications cookie by default, i.e. if no cookie is given.
        cookie = ((cookie == 0) ? AppCookie.makeCookie(FORWARDING_APP_ID, 0) : cookie);
		
		// If the input port equals the output port, set the output port to OFPP_IN_PORT explicitly.
		if (command != OFFlowMod.OFPFC_DELETE_STRICT && phyInPortId != 0 && phyInPortId == phyOutPortId) {
			// TODO: Dirty hack for Dell Force 10 Z9000 switches that do not support OFPP_IN_PORT.
			if (iofSwitch.getDescriptionStatistics().getManufacturerDescription().equals("Dell Force 10")) {
				// Do nothing.
			}  else {
				phyOutPortId = OFPort.OFPP_IN_PORT.getValue();
			}
		}
		
		// Set idle timeout.
	    flowMod.setIdleTimeout(this.idleTimeout);
	    // The cookie
	    flowMod.setCookie(cookie);
        // Buffered packet to apply to (or -1). Not meaningful for OFPFC_DELETE.
        flowMod.setBufferId(bufferId);
        // One of OFPFC_*.
        flowMod.setCommand(command);
        // Fields to match.
        flowMod.setMatch(match);
        // priority
        flowMod.setPriority(priority);
        // Flags
        flowMod.setFlags(flags);
        // Output port
        flowMod.setOutPort((command == OFFlowMod.OFPFC_DELETE_STRICT) ? phyOutPortId : OFPort.OFPP_NONE.getValue());
        
        // Actions
        short actionsLength = 0;
        List<OFAction> actions = new ArrayList<OFAction>();
    	// Add set_vlan if necessary.
        if (command != OFFlowMod.OFPFC_DELETE_STRICT) {
        	if (vlanId != 0) {
        		// Set or modify a VLAN.
        		actions.add(new OFActionVirtualLanIdentifier(vlanId));
        		actionsLength += OFActionVirtualLanIdentifier.MINIMUM_LENGTH;
        	} else {
        		// Strip the VLAN tag.
        		actions.add(new OFActionStripVirtualLan());
        		actionsLength += OFActionStripVirtualLan.MINIMUM_LENGTH;
        	}
        	// Set the output action.
            actions.add(new OFActionOutput(phyOutPortId, (short) 0xffff));
            actionsLength += OFActionOutput.MINIMUM_LENGTH;
            flowMod.setActions(actions);
            // Length
            flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + actionsLength));
        }
        
        // Make sure a drop rule has a hard timeout.
        if (flowMod.getActions() != null && flowMod.getActions().size() == 0) {        	
     		flowMod.setHardTimeout(this.dropTimeout);
        } else {
    	    flowMod.setHardTimeout(this.hardTimeout);
        }
        
        // Write the flow mod.
        return this.writeFlowMod(iofSwitch, flowMod, cntx);
	}
	
	/**
	 * 
	 * @param iofSwitch
	 * @param flowMod
	 * @param cntx
	 * @return <b>FlowCacheObj</b> A flow cache object representing this flow.
	 */
	private FlowCacheObj writeFlowMod(IOFSwitch iofSwitch, OFFlowMod flowMod, FloodlightContext cntx) {
		/* */
		short command = flowMod.getCommand();
		
		if (log.isTraceEnabled())
            log.trace("{} {} flow mod {}", new Object[]{ iofSwitch, (command == OFFlowMod.OFPFC_DELETE_STRICT) ? "deleting" : "adding", flowMod });
        
        try {
        	messageDamper.write(iofSwitch, flowMod, cntx);
        	iofSwitch.flush();
        } catch (IOException e) {
            log.error("Failed to write {} to switch {}", new Object[]{ flowMod, iofSwitch }, e);
            return null;
        }
        
        // Update the flow cache and return.
        return this.updateFlowCache(iofSwitch.getId(), flowMod.getCommand(), flowMod.getPriority(), flowMod.getIdleTimeout(), flowMod.getHardTimeout(), flowMod.getCookie(), flowMod.getMatch(), flowMod.getActions(), cntx);
	}
	
	/**
	 * Adds a flow to the flow cache.
	 * 
	 * @param switchId
	 * @param command
	 * @param priority
	 * @param idleTimeout
	 * @param hardTimeout
	 * @param cookie
	 * @param match
	 * @param actions
	 * @param cntx
	 * @return
	 */
	private FlowCacheObj updateFlowCache(long switchId, short command, short priority, short idleTimeout, short hardTimeout, long cookie, OFMatch match, List<OFAction> actions, FloodlightContext cntx) {
		if (log.isTraceEnabled()) {
			log.trace("Cache update for flow OFMatchWithSwDpid [{} : {}] ", HexString.toHexString(switchId), match);
		}
		
		// Try to get a flow cache object.
		FlowCacheObj fco = flowCache.getFlow(IFlowCacheService.DEFAULT_DB_NAME, switchId, cookie, priority, match, actions);
		
		switch (command) {
			case OFFlowMod.OFPFC_ADD:
				if (fco == null) {
					fco = flowCache.addFlow(IFlowCacheService.DEFAULT_DB_NAME, switchId, cookie, priority, match, actions);
					fco.setIdleTimeout(idleTimeout);
					fco.setHardTimeout(hardTimeout);
					if (flowMode) {
						fco.setStatus(Status.PENDING);
					} else {
						fco.setStatus(Status.ACTIVE);
					}
				}
				break;
			case OFFlowMod.OFPFC_MODIFY:
			case OFFlowMod.OFPFC_MODIFY_STRICT:
				break;
			case OFFlowMod.OFPFC_DELETE:
			case OFFlowMod.OFPFC_DELETE_STRICT:
				if (fco != null) {
					flowCache.removeFlow(IFlowCacheService.DEFAULT_DB_NAME, switchId, cookie, priority, match, actions);
					fco.setStatus(Status.INACTIVE);
					//fco.setPathId(-1);
				}
				break;
			default:
				break;
		}
		
		return fco;
	}
	
    /**
     * Convenience method that queries the flow cache.
 	 *
     * @param flowCacheQueryList A list of flow cache queries.
     * @return <b>Map of Long->List->FlowCacheObj</b> 
     */
    private Map<Long, List<FlowCacheObj>> queryFlowCache(List<FlowCacheQuery> flowCacheQueryList) {
    	/* A Map of flow cache objects (switchId -> ListOf FlowCacheObj) queried from the flow cache. */
	    Map<Long, List<FlowCacheObj>> flowCacheObjects = new HashMap<Long, List<FlowCacheObj>>();
	    
	    // Query the flow cache.
	 	List<Future<FlowCacheQueryResp>> futureList = this.flowCache.queryDB(flowCacheQueryList);
	 	if (futureList == null) {
	 		log.error("Could not get futures from flow cache.");
	 		return null;
	 	}
		while (!futureList.isEmpty()) {
			Iterator<Future<FlowCacheQueryResp>> iter = futureList.iterator();
			while (iter.hasNext()) {
				Future<FlowCacheQueryResp> future = iter.next();
				try {
					FlowCacheQueryResp fcqr = future.get(1, TimeUnit.SECONDS);
					if (fcqr != null && fcqr.flowCacheObjList != null) {
						flowCacheObjects.put(fcqr.switchId, fcqr.flowCacheObjList);
					}
					iter.remove();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				} catch (TimeoutException e) {
					iter.remove();
				}
			}
		}
	    
	    return flowCacheObjects;
    }
    
    /**
     * Convenience method that queries the flow cache.
 	 *
     * @param flowCacheQuery
     * @return
     */
    private List<FlowCacheObj> queryFlowCache(FlowCacheQuery flowCacheQuery) {
    	/* A Map of flow cache objects queried from the flow cache. */
	    List<FlowCacheObj> flowCacheObjects = new ArrayList<FlowCacheObj>();
	    
	    // Query the flow cache.
	 	Future<FlowCacheQueryResp> future = this.flowCache.queryDB(flowCacheQuery);
	 	if (future == null) {
	 		log.error("Could not get future from flow cache.");
	 		return null;
	 	}

		try {
			FlowCacheQueryResp fcqr = future.get(1, TimeUnit.SECONDS);
			if (fcqr != null && fcqr.flowCacheObjList != null) {
				flowCacheObjects.addAll(fcqr.flowCacheObjList);
			}
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		} catch (TimeoutException e) {
			e.printStackTrace();
		}

	    return flowCacheObjects;
    }
    
	///
	/// IMultipathService
	///
    
    @Override
    public void addListener(IForwardingListener listener) {
    	this.forwardingListener.add(listener);
    }
    
    @Override
    public void removeListener(IForwardingListener listener) {
    	this.forwardingListener.remove(listener);
    }

	@Override
	public boolean installFlow(OFMatch match, Path path, int outPortId, List<OFAction> additionalActions, long cookie) {
		/* States whether the flow was successfully installed or not. */
		boolean installed = false;
		/* */
		short firstPhyInputPort = match.getInputPort();
		/* */
		long startTime = 0;
		
		if (log.isTraceEnabled()) {
			log.trace("***** START OlimpsForwarding.installFlow *****************************************");
			startTime = System.currentTimeMillis();
		}
		
		// Set a default cookie.
		if (cookie == 0)
			cookie = this.appCookie;
		        
        // Install a drop rule at the first switch
        if (flowMode) {
        	long switchId = path.getSrc();
        	//Get the current switch in the path.
        	IOFSwitch iofSwitch = floodlightProvider.getSwitch(switchId);
        	// Install a drop rule.
        	this.doDropFlow(iofSwitch, match, null);
        }
		
		// Set the correct input port for the last hop.
		if (path.getLinks() != null && path.getDstPort() != 0)
			match.setInputPort(OFSwitchPort.physicalPortIdOf(path.getDstPort()));
		
		// Install the last hop, i.e. the port to the receiving end host, i.e. dstDap.getPort().
		this.installLastHop(path.getEndPoints().getDst(), match, cookie, outPortId, path.getId(), null);
		
		// Install the flow on the path.
		if (path.getLinks() != null) {
			match.setInputPort(firstPhyInputPort);
			installed = this.installPath(path, match, cookie, null);
		}
		
		// Remove the drop rule.
		if (flowMode) {
			// NO-OP. The drop rule is overwritten by the last forwarding rule. 
		}
		
		// Handle additional actions.
		// TODO
		
		if (log.isTraceEnabled()) {
			log.trace("Flow installation time: " + (System.currentTimeMillis() - startTime) + " ms.");
			log.trace("***** END OlimpsForwarding.installFlow *****************************************");
		}
			
		return installed;
	}

	@Override
	public boolean removeFlow(OFMatch match, Path path, int outPortId, long cookie) {
		if (path == null)
			return false;
		
		// Remove the path.
		if (path.getLinks() != null) {
			return this.updatePath(path, match, cookie, Operation.REMOVE, null);
		} else {
			this.removeLastHop(path.getSrc(), match, outPortId, cookie, null);
			return true;
		}
	}

	@Override
	public boolean moveFlow(OFMatch match, Path srcPath, Path dstPath, long cookie) {
		/* A set of flow cache objects queried from the flow cache. */
		Map<Long, List<FlowCacheObj>> flowCacheObjects;
		/* A list of flow cache queries. */
		List<FlowCacheQuery> flowCacheQueryList = new ArrayList<FlowCacheQuery>();
		
		// Check that srcPath and dstPath are identical.
		if (srcPath.equals(dstPath)) {
			if (log.isWarnEnabled()) {
				log.warn("Did not move flow from source path do destionation path. They are the same.");
			}
			return false;
		}
		
		// Check that srcPath and dstPath end points are identical.
		if (!srcPath.getEndPoints().equals(dstPath.getEndPoints())) {
			if (log.isWarnEnabled()) {
				log.warn("Can not move flow from source path do destionation path. End points differ. " + srcPath.getEndPoints() + " : " + dstPath.getEndPoints());
			}
			return false;
		}
		
		// Check that match is on srcPath.
		for (Link link : srcPath.getLinks()) {
			// Create a flow cache query from the match.
			FlowCacheQuery fcq = new FlowCacheQuery(IFlowCacheService.DEFAULT_DB_NAME, this.getName(), null, link.getSrc(), match);
			if (cookie != 0) {
				fcq.setCookie(cookie);
			}
			// Add the source path id.
			fcq.setPathId(srcPath.getId());
			// Add flow cache query to list.
			flowCacheQueryList.add(fcq);
		}
		
		flowCacheObjects = this.queryFlowCache(flowCacheQueryList);		
		for (Link link : srcPath.getLinks()) {
			if (!flowCacheObjects.containsKey(link.getSrc())) {
				if (log.isWarnEnabled()) {
					log.warn("Can not move flow from source path do destionation path. Flow not found on source path");
				}
				return false;
			}
		}
		
		// Set a cookie, if not given.
		if (cookie == 0) {
			cookie = this.appCookie;
			for (List<FlowCacheObj> flowCacheObjectList : flowCacheObjects.values()) {
				for (FlowCacheObj fco : flowCacheObjectList) {
					if (fco.getCookie() != 0) {
						cookie = fco.getCookie();
						break;
					}
				}
			}
		}
		
		if (srcPath.getLinks() != null && srcPath.getLinks().size() > 1) {
			IOFSwitch iofSwitch = floodlightProvider.getSwitch(srcPath.getEndPoints().getSrc());
			// Install drop rule on the egress switch of the flow.
			this.doDropFlow(iofSwitch, match, null);
			// Remove old path.
			if (!this.installPath(dstPath, match, cookie, null)) {
				return false;
			}
			// Remove drop rule. (If it is not remove automatically).
		} else {
			// If only the source switch need to be updated.
			if (!this.installPath(dstPath, match, cookie, null)) {
				return false;
			}
		}
		
		// Remove flow mods from all switches that are part of srcPath, but not dstPath.
		Set<Long> removedSwitches = getRemovedSwitches(srcPath, dstPath);
		for (long switchId : removedSwitches) {
			IOFSwitch iofSwitch = floodlightProvider.getSwitch(switchId);
			// Clear flow mod.
            this.clearFlowMod(iofSwitch, match, OFPort.OFPP_NONE.getValue(), FLOWMOD_DEFAULT_PRIORITY, cookie, null);
		}
		
		// Update path id of last-hop flow cache object.
		FlowCacheQuery fcq = new FlowCacheQuery(IFlowCacheService.DEFAULT_DB_NAME, this.getName(), null, srcPath.getEndPoints().getDst(), match);
		
		Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
		try {
			FlowCacheQueryResp fcqr = future.get(5, TimeUnit.SECONDS);
			if (fcqr != null && fcqr.flowCacheObjList.size() == 1) {
				fcqr.flowCacheObjList.get(0).setPathId(dstPath.getId());
			} else {
				if (log.isWarnEnabled()) {
					log.warn("Did not update the path id of the last hop." + fcqr.flowCacheObjList);
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			if (log.isWarnEnabled()) {
				log.warn("Could not get future from flow cache.");
			}
			e.printStackTrace();
		} catch (TimeoutException e) {
			if (log.isWarnEnabled()) {
				log.warn("Could not get future from flow cache. Object not found.");
			}
			return false;
		}
		
		return true;
	}

	@Override
	public boolean moveFlow(OFMatch match, Path dstPath, long cookie) {
		return false;
	}
	
	///
	/// IConfigurationListener
	///
	
	@Override
	public JsonNode getJsonConfig() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = mapper.createObjectNode();
		try {
			// Write wildcards.
			ObjectNode wildcardsNode = mapper.createObjectNode();
			wildcardsNode.put("in_port", (this.wildcards & OFMatch.OFPFW_IN_PORT) == OFMatch.OFPFW_IN_PORT);
			wildcardsNode.put("dl_src", (this.wildcards & OFMatch.OFPFW_DL_SRC) == OFMatch.OFPFW_DL_SRC);
			wildcardsNode.put("dl_dst", (this.wildcards & OFMatch.OFPFW_DL_DST) == OFMatch.OFPFW_DL_DST);
			wildcardsNode.put("dl_vlan", (this.wildcards & OFMatch.OFPFW_DL_VLAN) == OFMatch.OFPFW_DL_VLAN);
			wildcardsNode.put("dl_vlan_pcp", (this.wildcards & OFMatch.OFPFW_DL_VLAN_PCP) == OFMatch.OFPFW_DL_VLAN_PCP);
			wildcardsNode.put("dl_type", (this.wildcards & OFMatch.OFPFW_DL_TYPE) == OFMatch.OFPFW_DL_TYPE);
			wildcardsNode.put("nw_src_mask", (this.wildcards & OFMatch.OFPFW_NW_SRC_MASK) == OFMatch.OFPFW_NW_SRC_MASK);
			wildcardsNode.put("nw_dst_mask", (this.wildcards & OFMatch.OFPFW_NW_DST_MASK) == OFMatch.OFPFW_NW_DST_MASK);
			wildcardsNode.put("nw_proto", (this.wildcards & OFMatch.OFPFW_NW_PROTO) == OFMatch.OFPFW_NW_PROTO);
			wildcardsNode.put("tp_src", (this.wildcards & OFMatch.OFPFW_TP_SRC) == OFMatch.OFPFW_TP_SRC);
			wildcardsNode.put("tp_dst", (this.wildcards & OFMatch.OFPFW_TP_DST) == OFMatch.OFPFW_TP_DST);
			rootNode.put("wildcards", wildcardsNode);
			// Write timeouts.
			ObjectNode timeoutsNode = mapper.createObjectNode();
			if (barrierMessageTimeout != OlimpsForwarding.BARRIER_MESSAGE_TIMEOUT)
				timeoutsNode.put("barrier_message_timeout", barrierMessageTimeout);
			if (idleTimeout != OlimpsForwarding.FLOWMOD_DEFAULT_IDLE_TIMEOUT)
				timeoutsNode.put("idle_timeout", idleTimeout);
			if (hardTimeout != OlimpsForwarding.FLOWMOD_DEFAULT_HARD_TIMEOUT)
				timeoutsNode.put("hard_timeout", hardTimeout);
			if (dropTimeout != OlimpsForwarding.FLOWMOD_DROP_RULE_HARD_TIMEOUT)
				timeoutsNode.put("drop_timeout", dropTimeout);
			if (timeoutsNode.size() != 0)
				rootNode.put("timeouts", timeoutsNode);
			// Write flow installation mode.
			rootNode.put("flowinstallmode", flowMode);
			// Write any other Forwarding configuration.
			// ... here.
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Creating the Forwarding configuration for JSON failed. ", e);
			}
		}
		
		// Return root node if it contains some configuration. Otherwise return null.
		return (rootNode.size() > 0) ? (JsonNode) rootNode : null;
	}
	
	@Override
	public void putJsonConfig(JsonNode jsonNode) {
		Iterator<Entry<String, JsonNode>> iter = jsonNode.fields();
		while (iter.hasNext()) {
			Entry<String, JsonNode> entry = iter.next();
			String fieldname = entry.getKey().toLowerCase();
			JsonNode child = entry.getValue();
			switch(fieldname) {
				case "wildcards":
					this.wildcards = Wildcards.FULL.getInt();
					Iterator<Entry<String, JsonNode>> wildcardsIter = child.fields();
					while (wildcardsIter.hasNext()) {
						Entry<String, JsonNode> wildcardsEntry = wildcardsIter.next();	
						String wildcardsFieldname = wildcardsEntry.getKey().toLowerCase();
						JsonNode wildcardsChild = wildcardsEntry.getValue();
						switch(wildcardsFieldname) {
							case "in_port":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_IN_PORT;
								}
								break;
							case "dl_src":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_DL_SRC;
								}
								break;
							case "dl_dst":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_DL_DST;
								}
								break;
							case "dl_vlan":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_DL_VLAN;
								}
								break;
							case "dl_vlan_pcp":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_DL_VLAN_PCP;
								}
								break;
							case "dl_type":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_DL_TYPE;
								}
								break;
							case "nw_src_mask":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_NW_SRC_MASK;
								}
								break;
							case "nw_dst_mask":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_NW_DST_MASK;
								}
								break;
							case "nw_proto":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_NW_PROTO;
								}
								break;
							case "tp_src":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_TP_SRC;
								}
								break;
							case "tp_dst":
								if (!wildcardsChild.asBoolean()) {
									this.wildcards = this.wildcards & ~OFMatch.OFPFW_TP_DST;
								}
								break;
							default:
								if (log.isWarnEnabled()) {
									log.warn("Reading the Forwarding configuration for {} from JSON failed.", wildcardsFieldname);
								}
						}
					}
					break;
				case "timeouts":
					Iterator<Entry<String, JsonNode>> timeoutsIter = child.fields();
					while (timeoutsIter.hasNext()) {
						Entry<String, JsonNode> timeoutsEntry = timeoutsIter.next();	
						String timeoutsFieldname = timeoutsEntry.getKey().toLowerCase();
						JsonNode timeoutsChild = timeoutsEntry.getValue();
						switch(timeoutsFieldname) {
							case "barrier_message_timeout":
								barrierMessageTimeout = (short) timeoutsChild.asInt();
								break;
							case "idle_timeout":
								idleTimeout = (short) timeoutsChild.asInt();
								break;
							case "hard_timeout":
								hardTimeout = (short) timeoutsChild.asInt();
								break;
							case "drop_timeout":
								dropTimeout = (short) timeoutsChild.asInt();
								break;
							default:
								if (log.isWarnEnabled()) {
									log.warn("Reading the Forwarding configuration for {} from JSON failed.", timeoutsFieldname);
								}
						}
					}
					break;
				case "flowinstallmode":
					flowMode = child.asBoolean();
					break;
				default:
					if (log.isWarnEnabled()) {
						log.warn("Reading the Forwarding configuration for {} from JSON failed.", fieldname);
					}
			}
		}
	}
	
	///
	/// Local methods
	///
	
	/**
	 * 
	 * @param srcPath
	 * @param dstPath
	 * @return
	 */
	private Set<Long> getRemovedSwitches(Path srcPath, Path dstPath) {
		/* A set of switch IDs that are in srcPath, but not dstPath.*/
		Set<Long> removedSwitches = new HashSet<Long>();
		
		for (Link link : srcPath.getLinks()) {
			removedSwitches.add(link.getSrc());
		}
		
		for (Link link : dstPath.getLinks()) {
			if (removedSwitches.contains(link.getSrc())) {
				removedSwitches.remove(link.getSrc());
			}
		}
		
		return removedSwitches;
	}
	
	/**
	 * Generates a new unique flow id.
	 * 
	 * @return <b>int</b> A new unique flow id.
	 */
	private int getNextFlowId() {
		/* The new flow id. */
		int newFlowId = 1;
		
		this.flowIdLock.lock();
		try {
			if (this.flowIds.isEmpty()) {
				this.flowIds.add(newFlowId);
				return newFlowId;
			}

			int maxFlowId = Collections.max(this.flowIds);
		
			if (this.flowIds.size() == maxFlowId) {
				newFlowId = (maxFlowId+1);
				this.flowIds.add(newFlowId);
				return newFlowId;
			}
		
			for (int i = 1; i <= maxFlowId; i++) {
				if (!flowIds.contains(i)) {
					newFlowId = i;
					this.flowIds.add(newFlowId);
					break;
				}
			}
		} finally {
			this.flowIdLock.unlock();
		}
		
		return newFlowId;
	}
	
	/**
	 * Frees a flow id form the flowIds so that it can be
	 * re-used for the nextFlowId calculation.
	 * 
	 * @param flowId The flow id to be freed.
	 */
	private void removeFlowId(int flowId) {
		this.flowIdLock.lock();
		try {
			this.flowIds.remove(flowId);
		} finally {
			this.flowIdLock.unlock();
		}
	}
	
	/**
	 * Convenience method to add a flow cookie to the barrier cache.
	 * 
	 * @param switchId
	 * @param xid
	 * @param cookie
	 */
	private void addBarrierXid(long switchId, int xid, long cookie) {
		if (!this.barrierCacheCookie.containsKey(switchId))
			this.barrierCacheCookie.put(switchId, new HashMap<Integer, Long>());
		this.barrierCacheCookie.get(switchId).put(xid, cookie);
	}
	
	/**
	 * Convenience method to remove a flow from the barrier cache.
	 * 
	 * @param switchId
	 * @param xid
	 * @param <b>long</b> The cookie that was stored for the switch and the barrier transaction id.
	 */
	private long removeBarrierXid(long switchId, int xid) {
		/* The cookie that will be returned. */
		long cookie = 0;
		
		if (!this.barrierCacheCookie.containsKey(switchId)) {
			if (log.isDebugEnabled()) {
				log.debug("Can not remove barrier Xid from barrier cache. Switch {} not found.", HexString.toHexString(switchId));
			}
			return cookie;
		}
		
		if (!this.barrierCacheCookie.get(switchId).containsKey(xid)) {
			if (log.isDebugEnabled()) {
				log.debug("Can not remove barrier Xid from barrier cache. Xid {} not found.", xid);
			}
			return cookie;
		}
		
		cookie = this.barrierCacheCookie.get(switchId).remove(xid);
		
		if (this.barrierCacheCookie.get(switchId).isEmpty()) {
			this.barrierCacheCookie.remove(switchId);
		}
		
		return cookie;
	}
	
	/**
	 * Convenience method to add a pending flow to the cache.
	 * 
	 * @param cookie
	 * @param switchId
	 */
	private void addPendingFlow(long cookie, long switchId) {
		if (!this.pendingFlows.containsKey(cookie))
			this.pendingFlows.put(cookie, new HashSet<Long>());
		this.pendingFlows.get(cookie).add(switchId);
	}
	
	/**
	 * Convenience method to remove a pending flow from the cache.
	 * 
	 * @param cookie
	 * @param switchId
	 */
	private void removePendingFlow(long cookie, long switchId) {
		
		if (!this.pendingFlows.containsKey(cookie)) {
			if (log.isDebugEnabled()) {
				log.debug("Can not remove switch from pending flows. Cookie 0x{} not found.", Long.toHexString(cookie));
			}
			return;
		}
		
		if (!this.pendingFlows.get(cookie).contains(switchId)) {
			if (log.isDebugEnabled()) {
				log.debug("Can not remove switch from pending flows. switch {} not found.", HexString.toHexString(switchId));
			}
			return;
		}
		
		this.pendingFlows.get(cookie).remove(switchId);
		
		if (this.pendingFlows.get(cookie).isEmpty()) {
			this.pendingFlows.remove(cookie);
		}
	}

}
