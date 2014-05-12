package net.floodlightcontroller.multipath;

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

import java.util.List;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.forwarding.IForwardingListener;
import net.floodlightcontroller.routing.Path;

/**
 * 
 * @author Michael Bredel <michael.bredel@caltech.edu>
 */
public interface IMultipathService extends IFloodlightService {
	
	/**
	 * Adds a listener to listen for IMultipathService notifications.
	 * 
	 * @param listener The listener that wants the notifications.
	 */
	public void addListener(IForwardingListener listener);
	
	/**
	 * Removes a listener thats listens for IMultipathService notifications.
	 * 
	 * @param listener The listener that should be removed.
	 */
	public void removeListener(IForwardingListener listener);
	
	/**
	 * Installs a flow that matches a certain OFMatch on a path, i.e.
	 * list of switches. In addition, it can create additional actions
	 * on the ingress switch.
	 * 
	 * @param match The OFMatch that characterizes the flow to be installed.
	 * @param path The path, i.e. a list of switches where the flow should be installed.
	 * @param outPortId The ID of the output port at the egress switch.
	 * @param additionalActions Some optional actions, e.g. SET_VLAN, STRIP_VLAN, that should be performed at the ingress switch.
	 * @param cookie The (optional) cookie that identifies the application and the flow. 
	 * @return <b>boolean</b> True if the path has been installed successfully.
	 */
	public boolean installFlow(OFMatch match, Path path, int outPortId, List<OFAction> additionalActions, long cookie);
	
	/**
	 * Removes a flow that matches a certain OFMatch from a path.
	 * 
	 * @param match The OFMatch that characterizes the flow to be installed.
	 * @param path The path, i.e. a list of switches where the flow should be installed.
	 * @param outPortId The ID of the output port at the egress switch.
	 * @param cookie The (optional) cookie that identifies the application and the flow.
	 * @return <b>boolean</b> True if the path has been removed successfully.
	 */
	public boolean removeFlow(OFMatch match, Path path, int outPortId, long cookie);

	/**
	 * Moves a flow from the source path to a new destination path.
	 * 
	 * @param match The OFMatch that characterizes the flow to be installed.
	 * @param srcPath The source path, i.e. a list of switches where the flow is installed currently.
	 * @param dstPath The path, i.e. a list of switches where the flow should be installed.
	 * @param cookie The (optional) cookie that identifies the application and the flow.
	 * @return <b>boolean</b> True if the path has been successfully moved.
	 */
	public boolean moveFlow(OFMatch match, Path srcPath, Path dstPath, long cookie);
	
	/**
	 * Convenience method. Moves a flow to a new destination path. To this end,
	 * the match has to identify the flow and the path clearly.
	 * 
	 * @param match The OFMatch that characterizes the flow to be installed.
	 * @param dstPath The path, i.e. a list of switches where the flow should be installed.
	 * @param cookie The (optional) cookie that identifies the application and the flow.
	 * @return <b>boolean</b> True if the path has been successfully moved.
	 */
	public boolean moveFlow(OFMatch match, Path dstPath, long cookie);

}