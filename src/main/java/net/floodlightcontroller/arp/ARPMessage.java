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

import org.openflow.util.HexString;

import net.floodlightcontroller.util.MACAddress;

/**
 * Encapsulates information regarding the ARP request and its
 * state. 
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ARPMessage {
	/** The MAC address of the source host */
	private long sourceMACAddress;
	/** The IP address of the source host. */
	private int sourceIPAddress;
	/** The MAC address of the target (destination) host. */
	private long targetMACAddress;
	/** The IP address of the target (destination) host. */
	private int targetIPAddress;
	/** The switch ID of the switch where the ARP request is received. */
	private long switchId;
	/** The port ID of the port where the ARP request is received.*/
	private int inPort;
	/** The time the ARP request started. */
	private long startTime;
	
	/** 
	 * Setter for the IP address of the source host that initialized the ARP request.
	 * 
	 * @param sourceIPAddress The IP address of the source host.
	 * @return <b>ARPMessage</b> The current ARPMessage object.
	 */
	public ARPMessage setSourceMACAddress(long sourceMACAddress) {
		this.sourceMACAddress = sourceMACAddress;
		return this;
	}
	
	/**
	 * Setter for the IP address of the source host that initialized the ARP request.
	 * 
	 * @param sourceIPAddress The IP address of the source host.
	 * @return <b>ARPMessage</b> The current ARPMessage object.
	 */
	public ARPMessage setSourceIPAddress(int sourceIPAddress) {
		this.sourceIPAddress = sourceIPAddress;
		return this;
	}
	
	/**
	 * Setter for the MAC address of the target (destination) host.
	 * 
	 * @param targetMACAddress The MAC address of the target (destination) host.
	 * @return <b>ARPMessage</b> The current ARPMessage object.
	 */
	public ARPMessage setTargetMACAddress(long targetMACAddress) {
		this.targetMACAddress = targetMACAddress;
		return this;
	}
	
	/**
	 * Setter for the IP address of the target (destination) host.
	 * 
	 * @param targetIPAddress The IP address of the target (destination) host.
	 * @return <b>ARPMessage</b> The current ARPMessage object.
	 */
	public ARPMessage setTargetIPAddress(int targetIPAddress) {
		this.targetIPAddress = targetIPAddress;
		return this;
	}
	
	/**
	 * Setter for the SwitchId where the ARP request is received.
	 * 
	 * @param switchId Where the ARP request is received.
	 * @return <b>ARPMessage</b> The current ARPMessage object.
	 */
	public ARPMessage setSwitchId(long switchId) {
		this.switchId = switchId;
		return this;
	}
	
	/**
	 * Setter for the (virtual) PortId where the original ARP request is received.
	 * 
	 * @param portId Where the ARP request is received.
	 * @return <b>ARPRequest</b> The current ARPRequest object.
	 */
	public ARPMessage setInPort(int portId) {
		this.inPort = portId;
		return this;
	}
	
	/**
	 * Setter for the start time when the ARP request is received.
	 * 
	 * @param startTime The time when the ARP request is received.
	 * @return <b>ARPMessage</b> The current ARPMessage object.
	 */
	public ARPMessage setStartTime(long startTime) {
		this.startTime = startTime;
		return this;
	}
	
	/**
	 * Getter for the source MAC address, i.e from the node that initialized the ARP request.
	 * 
	 * @return <b>long</b> The MAC address of the source of the ARP request. 
	 */
	public long getSourceMACAddress() {
		return this.sourceMACAddress;
	}
	
	/**
	 * Getter for the source IP address, i.e. from the node that initialized the ARP request.
	 * 
	 * @return <b>int</b> The IP address of the source of the ARP request. 
	 */
	public int getSourceIPAddress() {
		return this.sourceIPAddress;
	}
	
	/**
	 * Getter for the target (destination) MAC address.
	 * 
	 * @return <b>long</b> The MAC address of the target (destination) of the ARP request. 
	 */
	public long getTargetMACAddress() {
		return this.targetMACAddress;
	}
	
	/**
	 * Getter for the target (destination) IP address.
	 * 
	 * @return <b>int</b> The IP address of the target (destination) of the ARP request. 
	 */
	public int getTargetIPAddress() {
		return this.targetIPAddress;
	}
	
	/**
	 * Getter for the switch ID of the ARP incoming switch.
	 * 
	 * @return <b>long</b> The switch ID of the switch where the ARP request is received.
	 */
	public long getSwitchId() {
		return this.switchId;
	}
	
	/**
	 * Getter for the (virtual) port ID where the original ARP request was received
	 * 
	 * @return <b>int</b> The port ID of the port where the ARP request was received.
	 */
	public int getInPort() {
		return this.inPort;
	}
	
	/**
	 * Getter for the start time of the ARP request.
	 * 
	 * @return <b>long</b> The start time when the ARP request is received.
	 */
	public long getStartTime() {
		return this.startTime;
	}
	
	@Override
	public String toString() {
		/* The string builder. */
		StringBuilder sb = new StringBuilder();
		
		sb.append("start_time=" + this.startTime + ",");
		sb.append("switch=" + HexString.toHexString(this.switchId) + ",");
		sb.append("in_port=" + this.inPort + ",");
		sb.append("src_mac=" + MACAddress.valueOf(this.sourceMACAddress).toString() + ",");
		sb.append("target_mac=" + MACAddress.valueOf(this.targetMACAddress).toString() + ",");
		sb.append("src_ip=" + this.sourceIPAddress + ",");
		sb.append("target_ip=" + this.targetIPAddress);
		
		return sb.toString();
		
	}
}
