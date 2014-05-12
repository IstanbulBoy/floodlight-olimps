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
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;


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
public class RemoveVirtportVlanCmd implements ICommand {
	/** Required Module: Floodlight Provider Service. */
	private IFloodlightProviderService floodlightProvider;
	/** The command string. */
	private String commandString = "remove virtport vlan";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/** */
	private long switchId;
	/** */
	private short phyPortId;
	/** */
	private short vlanId;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public RemoveVirtportVlanCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
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
		/* Argument elements. */
		String[] argumentElements = arguments.trim().split(" ");
		if (argumentElements.length == 1) {
			if (argumentElements[0] == "") {
				argumentElements = new String[0];
			}
		}
		
		if (!parseArguments(arguments))
			return "Could not parse arguments. Use: remove virtport vlan [SWITCH_ID] [PORT_ID] [VLAN]";
		
		// Get the switch.
		IOFSwitch sw = floodlightProvider.getSwitch(switchId);
		if (sw == null) {
			return "No port removed. Switch not found.";
		}
		// Get the virtual port ID.
		int virtPortId = OFSwitchPort.virtualPortIdOf(phyPortId, vlanId);
		// Get the virtual port.
		OFSwitchPort ofsPort = sw.getPort(virtPortId);
		if (ofsPort == null) {
			return "No port removed. Port not found for " + phyPortId + " (" + vlanId + ")";
		}
		// Add virtual port to switch.
		sw.removeVirtualPort(ofsPort);
		
		if (ofsPort != null) {
			result.append("Removed port: " + ofsPort.getName());
		}
		
		// Return
		return result.toString();
	}
	
	/**
	 * 
	 * @param argumentString
	 * @return
	 */
	private boolean parseArguments(String argumentString) {
		String[] argumentElements = argumentString.trim().split(" ");
		if (argumentElements.length != 3)
			return false;
		
		switch (argumentElements.length) {
		case 3:
			vlanId = this.parseVlanId(argumentElements[2]);
			if ((vlanId == 0)) {
				return false;
			}
		case 2:
			phyPortId = parsePortId(argumentElements[1]);
			if (phyPortId == 0) {
				return false;
			}
		case 1:
			switchId = this.parseSwitchId(argumentElements[0]);
			if (switchId == 0) {
				return false;
			}
			break;
		default:
			return false;
		}
		
		return true;
	}
	
	/**
	 * 
	 * @param switchIdString
	 * @return
	 */
	private long parseSwitchId(String switchIdString) {
		/* */
		long switchId;
		
		try {
			switchId = HexString.toLong(switchIdString);
		} catch (NumberFormatException e) {
			switchId = 0;
		}
		
		return switchId;
	}
	
	/**
	 * 
	 * @param portIdString
	 * @return
	 */
	private short parsePortId(String portIdString) {
		/* */
		short portId;
		
		try {
			portId = Short.parseShort(portIdString);
		} catch (Exception e) {
			portId = 0;
		}
		
		return portId;
	}
	
	/**
	 * 
	 * @param vlanIdString
	 * @return
	 */
	private short parseVlanId(String vlanIdString) {
		/* VLAN ID as integer. */
		short vlanId;
		
		try {
			vlanId = Short.parseShort(vlanIdString);
		} catch (Exception e) {
			vlanId = 0;
		}
		
		return (vlanId > 4000) ? -1 : vlanId;
	}
	
}
