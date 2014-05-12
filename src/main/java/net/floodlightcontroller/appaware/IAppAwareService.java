package net.floodlightcontroller.appaware;

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

import java.util.Map;

import org.openflow.protocol.OFMatch;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * The AppAware service allows for the registration of data transfer applications
 * at the controller. The controller can identify the data transfer applications
 * data flows and treats them specially if needed.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface IAppAwareService extends IFloodlightService {

	/**
	 * Registers a new application at the controller. An application (flow) is
	 * identified by its source IP, the destination IP, and the destination port. 
	 * Moreover, an application need to provide some information regarding the
	 * file size it is about to transmit.
	 * 
	 * @param srcIp The source IP address of the application.
	 * @param dstIp The destination IP, where the application wants to send data to.
	 * @param dstPort The destination port, where the application wants to send data to.
	 * @param name The name of the application.
	 * @param fileSize The amount of data the application wants to transfer.
	 */
	public void addApplication(int srcIp, int dstIp, short dstPort, String name, int fileSize);
	
	/**
	 * Removes a already registered application from the controller.
	 * 
	 * @param match The flow match that identifies the flows of an application.
	 * @return <b>AppEntry</b> The application entry that was removed.
	 */
	public AppEntry removeApplication(OFMatch match);
	
	/**
	 * Gets a registered application.
	 * 
	 * @param match The flow match that identifies the flows of an application.
	 * @return <b>AppEntry</b> The corresponding application entry.
	 */
	public AppEntry getApplication(OFMatch match);
	
	/**
	 * Gets all registered applications.
	 * 
	 * @return <b>Map of AppEntry</b> A map of flow matches to application entries.
	 */
	public Map<OFMatch, AppEntry> getAllApplication();
	
	/**
	 * Sets a threshold to identify small flows. Small flow might be treaded specially.
	 * 
	 * @param smallFlowFileSize The upper bound threshold of a small flow.
	 */
	public void setSmallFlowFileSize(int smallFlowFileSize);
}
