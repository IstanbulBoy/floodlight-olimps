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
import java.util.List;

import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ARPHandler {
	/** Broadcast MAC address. */
	protected static final long BROADCAST_MAC = 0xffffffffffffL;
	/** Logger to log Handler events.*/
	protected Logger logger;
	/** Required Module: Floodlight Provider Service. */
	protected IFloodlightProviderService floodlightProvider;
	
	/**
	 * 
	 */
	public ARPHandler(Logger logger, IFloodlightProviderService floodlightProvider) {
		this.logger = logger;
		this.floodlightProvider = floodlightProvider;
	}
	
	/**
	 * Creates an ARP reply frame, puts it into a packet out message and 
	 * sends the packet out message to the switch that received the ARP
	 * request message.
	 * 
	 * @param arpReply The ARPMessage object containing information regarding the current ARP process.
	 */
	public void sendARPReply(IOFSwitch sw, short port, ARPMessage arpReply) {
		// Create an ARP reply frame (from target (source) to source (destination)).
		IPacket ethernet = new Ethernet()
    		.setSourceMACAddress(Ethernet.toByteArray(arpReply.getTargetMACAddress()))
        	.setDestinationMACAddress(Ethernet.toByteArray(arpReply.getSourceMACAddress()))
        	.setEtherType(Ethernet.TYPE_ARP)
        	.setPayload(new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setOpCode(ARP.OP_REPLY)
				.setHardwareAddressLength((byte)6)
				.setProtocolAddressLength((byte)4)
				.setSenderHardwareAddress(Ethernet.toByteArray(arpReply.getTargetMACAddress()))
				.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(arpReply.getTargetIPAddress()))
				.setTargetHardwareAddress(Ethernet.toByteArray(arpReply.getSourceMACAddress()))
				.setTargetProtocolAddress(IPv4.toIPv4AddressBytes(arpReply.getSourceIPAddress()))
				.setPayload(new Data(new byte[] {0x01})));
		// Send ARP reply.
		sendPOMessage(ethernet, sw, port);
	}
	
	/**
	 * Creates an ARP request frame, puts it into a packet out message and 
	 * sends the packet out message to all switch ports (attachment point ports)
	 * that are not connected to other OpenFlow switches.
	 * 
	 * @param arpRequest The ARPMessage object containing information regarding the current ARP process.
	 */
	public void sendARPRequest(IOFSwitch sw, short port, ARPMessage arpRequest) {
		// Create an ARP request frame
		IPacket arpReply = new Ethernet()
    		.setSourceMACAddress(Ethernet.toByteArray(arpRequest.getSourceMACAddress()))
        	.setDestinationMACAddress(Ethernet.toByteArray(BROADCAST_MAC))
        	.setEtherType(Ethernet.TYPE_ARP)
        	.setPayload(new ARP()
				.setHardwareType(ARP.HW_TYPE_ETHERNET)
				.setProtocolType(ARP.PROTO_TYPE_IP)
				.setOpCode(ARP.OP_REQUEST)
				.setHardwareAddressLength((byte)6)
				.setProtocolAddressLength((byte)4)
				.setSenderHardwareAddress(Ethernet.toByteArray(arpRequest.getSourceMACAddress()))
				.setSenderProtocolAddress(IPv4.toIPv4AddressBytes(arpRequest.getSourceIPAddress()))
				.setTargetHardwareAddress(Ethernet.toByteArray(arpRequest.getTargetMACAddress()))
				.setTargetProtocolAddress(IPv4.toIPv4AddressBytes(arpRequest.getTargetIPAddress()))
				.setPayload(new Data(new byte[] {0x01})));
		// Send ARP Request.
		sendPOMessage(arpReply, sw, port);
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

}
