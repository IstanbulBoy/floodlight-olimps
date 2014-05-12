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

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface IARPProxyService extends IFloodlightService {

	/**
	 * Allows the controller to initiate and send ARP requests to
	 * learn connected hosts.
	 * 
	 * @param switchId The switch that received the original ARP request.
	 * @param portId The input port ID of the original ARP request.
	 * @param srcMACAddress The source MAC address.
	 * @param srcIPAddress The source IP address.
	 * @param dstIPAddress The destination IP address.
	 */
	public void initiateARPRequest(long switchId, int portId, long srcMACAddress, int srcIPAddress, int dstIPAddress);
	
	/**
	 * Adds a listener to listen for IARPProxyServices notifications.
	 * 
	 * @param listener The listener that wants the notifications.
	 */
	public void addListener(IARPProxyListener listener);
	
	/**
	 * Removes a listener thats listens for IARPProxyServices notifications.
	 * 
	 * @param listener The listener that should be removed.
	 */
	public void removeListener(IARPProxyListener listener);
	
	/**
     * Add a switch port to the suppressed ARP list. 
     * Remove any known hosts on the switch port.
     *
	 * @param switchId The switch ID of the switch port to check.
	 * @param portId The port of the switch port to check.
	 */
	public void addToSuppressARPs(long switchId, int portId);
	
	/**
     * Remove a switch port from the suppressed ARP list. 
	 * 
	 * @param switchId The switch ID of the switch port to check.
	 * @param portId The port of the switch port to check.
	 */
	public void removeFromSuppressARPs(long switchId, int portId);
}
