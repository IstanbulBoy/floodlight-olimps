package net.floodlightcontroller.arp;

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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.topology.IOlimpsTopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.MACAddress;

/**
 * The ARPProxy uses the topology information gathered by Floodlight and,
 * therefore, is aware of the location of the traffic's destination. It 
 * offers the MAC address directly to a requesting host. To this end, ARP
 * messages are not forwarded by the data plane, but handled by the
 * control plane. Thus, ARP messages are sort of tunneled to the OpenFlow
 * network. Moreover, the ARPProxy takes care that ARP requests are 
 * deleted after a certain timeout.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ARPProxy extends TimerTask implements IOFMessageListener, IFloodlightModule, IARPProxyService {
	/** Broadcast MAC address. */
	protected static final long BROADCAST_MAC = 0xffffffffffffL;
	/** APR timeout in milliseconds. Default = 1 second. */
	protected static final long ARP_TIMEOUT = 1000L;
	
	/** Logger to log ProxyARP events.*/
	protected static Logger logger;
	/** Required Module: Floodlight Provider Service. */
	protected IFloodlightProviderService floodlightProvider;
	/** Required Module: Floodlight Device Manager Service. */
	protected IDeviceService deviceManager;
	/** Required Module: Topology Manager module. We listen to the topologyManager for changes of the topology. */
	protected IOlimpsTopologyService topologyManager;
	/** A list of APR proxy listener. */
	protected List<IARPProxyListener> proxyListener;
	/** A map that maps: TargetIPAddress -> Set of ARPRequest. */
	protected Map<Integer, Set<ARPMessage>> arpRequests;
	/** A timer object to schedule ARP timeouts. */
	protected Timer timer;
	/**List of ports through which ARPs are not sent. */
    protected Set<NodePortTuple> suppressARP;

	@Override
	public String getName() {
		return "arpproxy";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return (type.equals(OFType.PACKET_IN) && name.equals("forwarding"));
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IARPProxyService.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(IARPProxyService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOlimpsTopologyService.class);
		l.add(IDeviceService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		topologyManager = context.getServiceImpl(IOlimpsTopologyService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		logger = LoggerFactory.getLogger(ARPProxy.class);	
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		arpRequests = new ConcurrentHashMap<Integer, Set<ARPMessage>>();
		proxyListener = new ArrayList<IARPProxyListener>();
		timer = new Timer();
		timer.schedule(this, ARP_TIMEOUT, ARP_TIMEOUT);
		suppressARP = Collections.synchronizedSet(new HashSet<NodePortTuple>());
	}
	

	@Override
	public void initiateARPRequest(long switchId, int portId, long srcMACAddress, int srcIPAddress, int dstIPAddress) {
		// Check if there is an ongoing ARP process for this packet.
		if (arpRequests.containsKey(srcIPAddress)) {
			// Update start time of current ARPRequest objects
			long startTime = System.currentTimeMillis();
			Set<ARPMessage> arpRequestSet = arpRequests.get(srcIPAddress);

			for (Iterator<ARPMessage> iter = arpRequestSet.iterator(); iter.hasNext();) {
				iter.next().setStartTime(startTime);
			}
		} else {
			ARPMessage arpRequest = new ARPMessage()
				.setSourceMACAddress(srcMACAddress)
				.setSourceIPAddress(srcIPAddress)
				.setTargetIPAddress(dstIPAddress)
				.setSwitchId(switchId)
				.setInPort(portId)
				.setStartTime(System.currentTimeMillis());
			// Put new ARPRequest object to current ARPRequests list.
			this.putArpRequest(dstIPAddress, arpRequest);
			// Send ARP request
			this.sendARPRequest(arpRequest);
		}
	}
	
	@Override
	public void addListener(IARPProxyListener listener) {
		this.proxyListener.add(listener);
	}
	
	@Override
	public void removeListener(IARPProxyListener listener) {
		this.proxyListener.remove(listener);
	}
	
	@Override
	public void addToSuppressARPs(long switchId, int portId) {
		NodePortTuple npt = new NodePortTuple(switchId, portId);
		this.suppressARP.add(npt);
	}

	@Override
	public void removeFromSuppressARPs(long switchId, int portId) {
		NodePortTuple npt = new NodePortTuple(switchId, portId);
		this.suppressARP.remove(npt);
	}

	@Override
	public Command receive(	IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
        	case PACKET_IN:
        		IRoutingDecision decision = null;
                if (cntx != null) {
                    decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
                }
                return this.processPacketInMessage(sw, (OFPacketIn) msg, decision, cntx);
        	default:
        		break;
			}
		return Command.CONTINUE;
	}
	
	/**
	 * Handles packetIn messages and decides what to do with it.
	 * 
	 * @param sw The switch the packet is received.
	 * @param piMsg The OpenFlow PacketIN message from the switch containing all relevant information.
	 * @param decision A forwarding decision made by a previous module. 
	 * @param cntx The Floodlight context.
	 * @return <b>Command</b> The command whether another listener should proceed or not.
	 */
	protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn piMsg, IRoutingDecision decision, FloodlightContext cntx) {
		/* Get the Ethernet frame representation of the PacketIn message. */
		Ethernet ethPacket = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		// If this is not an ARP message, continue.
		if (ethPacket.getEtherType() != Ethernet.TYPE_ARP )
			return Command.CONTINUE;
		
		
		/* A new empty ARP packet. */
		ARP arp = new ARP();
		/* The VLAN ID. */
		short vlanId = ethPacket.getVlanID();
		/* The port ID of the virtual incoming port. */
		int virtPort = OFSwitchPort.virtualPortIdOf(piMsg.getInPort(), vlanId);

		// Get the ARP packet or continue.
		if (ethPacket.getPayload() instanceof ARP) {
            arp = (ARP) ethPacket.getPayload();
		} else {
			return Command.CONTINUE;
		}
		
		// If a decision has been made already we obey it.
		if (decision != null) {
			if (logger.isTraceEnabled()) {
                logger.trace("Forwaring decision={} was made for PacketIn={}", decision.getRoutingAction().toString(), piMsg);
            }
					
			switch(decision.getRoutingAction()) {
            	case NONE:
            		// Don't handle the APR message.
            		return Command.CONTINUE;
            	case DROP:
            		// Don't handle the APR message.
            		return Command.CONTINUE;
            	case FORWARD_OR_FLOOD:
            		// Handle the ARP message by the ARP proxy.
            		break;
            	case FORWARD:
            		// Handle the ARP message by the ARP proxy.
            		break;
            	case MULTICAST:
            		// Handle the ARP message by the ARP proxy.
            		break;
            	default:
            		logger.error("Unexpected decision made for this packet-in={}", piMsg, decision.getRoutingAction());
            		return Command.CONTINUE;
			}
		}
		
		// Handle ARP request.
		if (arp.getOpCode() == ARP.OP_REQUEST) {
			return this.handleARPRequest(arp, sw.getId(), virtPort, cntx);
		}
		
		// Handle ARP reply.
		if (arp.getOpCode() == ARP.OP_REPLY) {
			return this.handleARPReply(arp, sw.getId(), virtPort, cntx);
		}
		
		// Handle RARP request. TODO
		
		// Handle RARP reply. TODO
		
		// Make a routing decision and forward the ARP message. (Actually, this should never happen).
		decision = new RoutingDecision(sw.getId(), piMsg.getInPort(), IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE), IRoutingDecision.RoutingAction.NONE);
        decision.addToContext(cntx);
		
		return Command.CONTINUE;
	}
	
	/**
	 * Handles incoming ARP requests. Reads the relevant information, creates an ARPRequest
	 * object, sends out the ARP request message or, if the information is already known by
	 * the system, sends back an ARP reply message.
	 * 
	 * @param arp The ARP (request) packet received.
	 * @param switchId The ID of the incoming switch where the ARP message is received. 
	 * @param portId The Port ID where the ARP message is received.
	 * @param cntx The Floodlight context.
	 * @return <b>Command</b> The command whether another listener should proceed or not.
	 */
	protected Command handleARPRequest(ARP arp, long switchId, int portId, FloodlightContext cntx) {
		/* The known IP address of the ARP source. */
		int sourceIPAddress = IPv4.toIPv4Address(arp.getSenderProtocolAddress());
		/* The known MAC address of the ARP source. */
		long sourceMACAddress = Ethernet.toLong(arp.getSenderHardwareAddress());
		/* The IP address of the (yet unknown) ARP target. */
		int targetIPAddress = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
		/* The MAC address of the (yet unknown) ARP target. */
		long targetMACAddress = 0;
		
		if (logger.isDebugEnabled()) {
			logger.debug("Received ARP request message at " + HexString.toHexString(switchId) + " - " + OFSwitchPort.stringOf(portId) + " from " + IPv4.fromIPv4Address(IPv4.toIPv4Address(arp.getSenderProtocolAddress())) + " for target: " + IPv4.fromIPv4Address(IPv4.toIPv4Address(arp.getTargetProtocolAddress())));
		}
		
		// Check if there is an ongoing ARP process for this packet.
		if (arpRequests.containsKey(targetIPAddress)) {
			// Update start time of current ARPRequest objects
			long startTime = System.currentTimeMillis();
			Set<ARPMessage> arpRequestSet = arpRequests.get(targetIPAddress);
			
			for (Iterator<ARPMessage> iter = arpRequestSet.iterator(); iter.hasNext();) {
				iter.next().setStartTime(startTime);
			}
			return Command.STOP;
		}
		
		@SuppressWarnings("unchecked")
		Iterator<Device> diter = (Iterator<Device>) deviceManager.queryDevices(null, null, targetIPAddress, null, null);	

		// There should be only one MAC address to the given IP address. In any case, 
		// we return only the first MAC address found.
		if (diter.hasNext()) {
			// If we know the destination device and an attachment point, get the corresponding MAC address and send an ARP reply.
			Device device = diter.next();
			targetMACAddress = device.getMACAddress();
			//long age = System.currentTimeMillis() - device.getLastSeen().getTime();
			//if (targetMACAddress > 0 && age < ARP_TIMEOUT) {
			//if (device.getAttachmentPoints() != null && device.getAttachmentPoints().length > 0 && targetMACAddress > 0 && age < ARP_TIMEOUT) {
			if (device.getAttachmentPoints() != null && device.getAttachmentPoints().length > 0 && targetMACAddress > 0) {
				ARPMessage arpMessage = new ARPMessage()
					.setSourceMACAddress(sourceMACAddress)
					.setSourceIPAddress(sourceIPAddress)
					.setTargetMACAddress(targetMACAddress)
					.setTargetIPAddress(targetIPAddress)
					.setSwitchId(switchId)
					.setInPort(portId);
				// Send ARP reply.
				this.sendARPReply(arpMessage);
			} else {
				ARPMessage arpMessage = new ARPMessage()
					.setSourceMACAddress(sourceMACAddress)
					.setSourceIPAddress(sourceIPAddress)
					.setTargetIPAddress(targetIPAddress)
					.setSwitchId(switchId)
					.setInPort(portId)
					.setStartTime(System.currentTimeMillis());
				// Put new ARPRequest object to current ARPRequests list.
				this.putArpRequest(targetIPAddress, arpMessage);
				// Send ARP request.
				this.sendARPRequest(arpMessage);
			}
		} else {
			ARPMessage arpMessage = new ARPMessage()
				.setSourceMACAddress(sourceMACAddress)
				.setSourceIPAddress(sourceIPAddress)
				.setTargetIPAddress(targetIPAddress)
				.setSwitchId(switchId)
				.setInPort(portId)
				.setStartTime(System.currentTimeMillis());
			// Put new ARPRequest object to current ARPRequests list.		
			this.putArpRequest(targetIPAddress, arpMessage);
			// Send ARP request
			this.sendARPRequest(arpMessage);
		}
		
		// Make a routing decision and forward the ARP message
		IRoutingDecision decision = new RoutingDecision(switchId, portId, IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE), IRoutingDecision.RoutingAction.NONE);
		decision.addToContext(cntx);
		
		return Command.CONTINUE;
	}
	
	/**
	 * Handles incoming ARP replies. Reads the relevant information, get the corresponding 
	 * ARPRequest object, and sends back and ARP reply message.
	 * 
	 * @param arp The ARP (reply) packet received.
	 * @param switchId The ID of the incoming switch where the ARP message is received. 
	 * @param portId The Port ID where the ARP message is received.
	 * @param cntx The Floodlight context.
	 * @return <p>Command</p> The command whether another listener should proceed or not.
	 */
	protected Command handleARPReply(ARP arp, long switchId, int portId, FloodlightContext cntx) {
		/* The IP address of the ARP target. */
		int targetIPAddress = IPv4.toIPv4Address(arp.getSenderProtocolAddress());
		/* The set of APRRequest objects related to the target IP address.*/
		Set<ARPMessage> arpRequestSet = arpRequests.remove(targetIPAddress);
		/* The ARPRequenst object related to the ARP reply message. */
		ARPMessage arpMessage;
		
		if (logger.isDebugEnabled()) {
			logger.debug("Received ARP reply message at " + HexString.toHexString(switchId) + " - " + OFSwitchPort.stringOf(portId) + " from " + IPv4.fromIPv4Address(IPv4.toIPv4Address(arp.getSenderProtocolAddress())));
		}
		
		// If the ARP request has already timed out, consume the message.
		// The sending host should send a new request.
		if (arpRequestSet == null)
			return Command.STOP;
		
		for (Iterator<ARPMessage> iter = arpRequestSet.iterator(); iter.hasNext();) {
			arpMessage = iter.next();
			iter.remove();
			arpMessage.setTargetMACAddress(MACAddress.valueOf(arp.getSenderHardwareAddress()).toLong());
			sendARPReply(arpMessage);
			
			// Inform listeners about the ARP reply.
			for (IARPProxyListener listener : this.proxyListener) {
				listener.arpReplyReceived(arpMessage);
			}
		}
		
		// Make a routing decision and forward the ARP message
		IRoutingDecision decision = new RoutingDecision(switchId, portId, IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE), IRoutingDecision.RoutingAction.NONE);
		decision.addToContext(cntx);
				
		return Command.CONTINUE;
	}
	
	/**
	 * Creates an ARP request frame, puts it into a packet out message and 
	 * sends the packet out message to all switch ports (attachment point ports)
	 * that are not connected to other OpenFlow switches.
	 * 
	 * @param arpMessage The ARPMessage object containing information regarding the current ARP process.
	 */
	protected void sendARPRequest(ARPMessage arpMessage) {
		// Create an ARP request frame
		IPacket arpReply = new Ethernet()
    		.setSourceMACAddress(Ethernet.toByteArray(arpMessage.getSourceMACAddress()))
        	.setDestinationMACAddress(Ethernet.toByteArray(BROADCAST_MAC))
        	.setEtherType(Ethernet.TYPE_ARP)
        	.setPayload(new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setOpCode(ARP.OP_REQUEST)
				.setHardwareAddressLength((byte) 6)
				.setProtocolAddressLength((byte) 4)
				.setSenderHardwareAddress(Ethernet.toByteArray(arpMessage.getSourceMACAddress()))
				.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(arpMessage.getSourceIPAddress()))
				.setTargetHardwareAddress(Ethernet.toByteArray(arpMessage.getTargetMACAddress()))
				.setTargetProtocolAddress(IPv4.toIPv4AddressBytes(arpMessage.getTargetIPAddress()))
				.setPayload(new Data(new byte[] {0x01})));
		
		// Send ARP request to all attachment point ports, except the one that received it.
		for (long switchId : floodlightProvider.getAllSwitchDpids()) {
			IOFSwitch sw = floodlightProvider.getSwitch(switchId);
			for (OFSwitchPort port : sw.getPorts()) {
				int virtPortId = port.getPortNumber();
				// Don't send ARP request to the requester.
				if (switchId == arpMessage.getSwitchId() && virtPortId == arpMessage.getInPort()) {
					continue;
				}
				// Don't send ARP if the ARP is suppressed.
				if (this.isARPSuppressed(switchId, virtPortId)) {
					continue;
				}
				short phyPortId = port.getOFPhysicalPort().getPortNumber(); 
				short vlan = port.getVlanId();
				if (vlan > 0) {
					((Ethernet) arpReply).setVlanID(vlan);
				}
				if (topologyManager.isAttachmentPointPort(switchId, virtPortId)) {
					this.sendPOMessage(arpReply, sw, phyPortId);
					if (logger.isDebugEnabled()) {
						logger.debug("Send ARP request from " + HexString.toHexString(switchId) + " - " + port.getName() + " " + OFSwitchPort.stringOf(virtPortId) + " for target " + IPv4.fromIPv4Address(arpMessage.getTargetIPAddress()));
					}
				}
				// Reset the vlan id.
				((Ethernet) arpReply).setVlanID((short) -1);
			}
		}
	}
	
	/**
	 * Creates an ARP reply frame, puts it into a packet out message and 
	 * sends the packet out message to the switch that received the ARP
	 * request message.
	 * 
	 * @param arpMessage The ARPMessage object containing information regarding the current ARP process.
	 * @param switchId The ID of the switch where the ARP message is should be send to. 
	 * @param portId The virtual Port ID where the ARP message should be send to.
	 */
	protected void sendARPReply(ARPMessage arpMessage) {
		long switchId = arpMessage.getSwitchId();
		int virtPortId = arpMessage.getInPort();
		short phyPortId = OFSwitchPort.physicalPortIdOf(virtPortId);
		short vlanId = OFSwitchPort.vlanIdOf(virtPortId);
		// Create an ARP reply frame (from target (source) to source (destination)).
		IPacket ethernet = new Ethernet()
    		.setSourceMACAddress(Ethernet.toByteArray(arpMessage.getTargetMACAddress()))
        	.setDestinationMACAddress(Ethernet.toByteArray(arpMessage.getSourceMACAddress()))
        	.setEtherType(Ethernet.TYPE_ARP)
        	.setPayload(new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setOpCode(ARP.OP_REPLY)
				.setHardwareAddressLength((byte)6)
				.setProtocolAddressLength((byte)4)
				.setSenderHardwareAddress(Ethernet.toByteArray(arpMessage.getTargetMACAddress()))
				.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(arpMessage.getTargetIPAddress()))
				.setTargetHardwareAddress(Ethernet.toByteArray(arpMessage.getSourceMACAddress()))
				.setTargetProtocolAddress(IPv4.toIPv4AddressBytes(arpMessage.getSourceIPAddress()))
				.setPayload(new Data(new byte[] {0x01})));
		// Set VLAN if needed.
		if (vlanId > 0) {
			((Ethernet) ethernet).setVlanID(vlanId);
		}
		// Send ARP reply.
		sendPOMessage(ethernet, floodlightProvider.getSwitch(switchId), phyPortId);
		if (logger.isDebugEnabled()) {
			logger.debug("Send ARP reply from " + HexString.toHexString(switchId) + " - " + OFSwitchPort.stringOf(virtPortId) + " to " + IPv4.fromIPv4Address(arpMessage.getSourceIPAddress()) + " for target " + IPv4.fromIPv4Address(arpMessage.getTargetIPAddress()));
		}
	}
	
	/**
	 * Creates and sends an OpenFlow PacketOut message containing the packet 
	 * information to the switch. The packet included on the PacketOut message 
	 * is sent out at the given port. 
	 * 
	 * @param packet The packet that is sent out.
	 * @param sw The switch the packet is sent out.
	 * @param port The port the packet is sent out.
	 */
	protected void sendPOMessage(IPacket packet, IOFSwitch sw, short port) {		
		// Serialize and wrap in a packet out
        byte[] data = packet.serialize();
        OFPacketOut po = (OFPacketOut) floodlightProvider.getOFMessageFactory().getMessage(OFType.PACKET_OUT);
        po.setBufferId(OFPacketOut.BUFFER_ID_NONE);
        po.setInPort(OFPort.OFPP_NONE);

        // Set actions
        List<OFAction> actions = new ArrayList<OFAction>();
        actions.add(new OFActionOutput(port, (short) 0));
        po.setActions(actions);
        po.setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);

        // Set data
        po.setLengthU(OFPacketOut.MINIMUM_LENGTH + po.getActionsLength() + data.length);
        po.setPacketData(data);
        
        // Send message
        try {
        	sw.write(po, null);
        	sw.flush();
        } catch (IOException e) {
        	logger.error("Failure sending ARP out port {} on switch {}", new Object[] { port, sw.getStringId() }, e);
        }
	}
	
	/**
	 * Puts the current ARP request to a list of concurrent ARP requests.
	 * 
	 * @param targetIPAddress The IP address of the target (destination) hosts.
	 * @param arpRequest The ARP request initialized by the source hosts.
	 */
	private synchronized void putArpRequest(int targetIPAddress, ARPMessage arpRequest) {
		if (arpRequests.containsKey(targetIPAddress)) {
			arpRequests.get(targetIPAddress).add(arpRequest);
		} else {
			arpRequests.put(targetIPAddress, new HashSet<ARPMessage>());
			arpRequests.get(targetIPAddress).add(arpRequest);
		}
	}
	
	/**
	 * Check for old ARP request. Remove ARP requests 
	 * older than ARP_TIMEOUT from the arpRequests data 
	 * structure.
	 */
	private synchronized void removeOldArpRequests() {
		/* The current time stamp. */
		long currentTime = System.currentTimeMillis();
		
		for (int targetIPAddress : arpRequests.keySet()) {
			Set<ARPMessage> arpRequestSet = arpRequests.get(targetIPAddress);
			for (Iterator<ARPMessage> iter = arpRequestSet.iterator(); iter.hasNext();) {
				if ((currentTime - iter.next().getStartTime()) > ARP_TIMEOUT)
					iter.remove();
				if (arpRequestSet.isEmpty()) 
					arpRequests.remove(targetIPAddress);
			}
		}
	}
	
	/**
	 * Checks whether the ARP is suppressed on the given port.
	 * 
	 * @param switchId The switch ID of the switch port to check.
	 * @param portId The port of the switch port to check.
	 * @return <b>boolean</b> True if the ARP is suppress on the given switch port.
	 */
	protected boolean isARPSuppressed(long switchId, int portId) {
        return this.suppressARP.contains(new NodePortTuple(switchId, portId));
    }

	@Override
	public void run() {
		if (!arpRequests.isEmpty()) {
			this.removeOldArpRequests();
		}
	}

}
