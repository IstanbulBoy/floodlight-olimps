package net.floodlightcontroller.cli.commands;

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

import java.util.Collection;

import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.wanswitch.IWANSwitchService;


/**
 * The "show host" command shows information about hosts
 * that are connected to switches controlled by Floodlight.
 * 
 * The "show host" command uses the Floodlight.context service
 * to directly address the corresponding Floodlight module to
 * retrieve the needed information.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ShowWanSwitchCmd implements ICommand {
	/** */
	private IWANSwitchService wanSwitchManager;
	/** The command string. */
	private String commandString = "show wanswitch";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ShowWanSwitchCmd(FloodlightModuleContext context) {
		this.wanSwitchManager = context.getServiceImpl(IWANSwitchService.class);
	}
	
	@Override
	public String getCommandString() {
		return commandString;
	}
	
	@Override
	public String getArguments() {
		return arguments;
	}

	@Override
	public String getHelpText() {
		return help;
	}
	
	@Override
	public Collection<Completer> getCompleter() {
		return null;
	}

	@Override
	public String execute(IConsole console, String arguments) {
		/* The resulting string. */
		StringBuilder result = new StringBuilder();
		
		if (this.wanSwitchManager == null)
			return "No WAN switch configured. Module loaded?";
		
		// Show..
		result.append("  WAN switch DPID: " + HexString.toHexString(this.wanSwitchManager.getDPID()) + "\n");
		if (this.wanSwitchManager.getController() != null) {
			result.append("  Controller     : " + this.wanSwitchManager.getController().getHostAddress() + ":" + this.wanSwitchManager.getControllerPort() + "\n");	
		} else {
			result.append("  Controller     : not configured \n");
		}
		result.append("  isConnected    : " + this.wanSwitchManager.isConnected() + "\n");
		result.append("  isActive       : " + this.wanSwitchManager.isActive() + "\n");
		
		if (this.wanSwitchManager.getPorts().isEmpty()) {
			result.append("  WAN ports      : none\n");
		} else {
			result.append("  WAN ports      : \n");
			for (int portId : this.wanSwitchManager.getPorts()) {
				NodePortTuple npt = this.wanSwitchManager.getPort(portId);
				result.append("    " + portId + ": " + HexString.toHexString(npt.getNodeId()) + " - " + OFSwitchPort.stringOf(npt.getPortId()) + "\n");
			}
		}
		return result.toString();
	}
	
}
