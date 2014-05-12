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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.cli.utils.Utils;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.packet.IPv4;

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
public class SearchHostCmd implements ICommand  {
	/** The Floodlight device manager to access Floodlight's device information. */
	private IDeviceService deviceManager;
	/** */
	private IFloodlightProviderService floodlightProvider;
	/** The command string. */
	private String commandString = "search host";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public SearchHostCmd(FloodlightModuleContext context) {
		this.deviceManager = context.getServiceImpl(IDeviceService.class);
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
		/* All devices connected to Floodlight-controlled switches. */
		Collection<? extends IDevice> devices = deviceManager.getAllDevices();
		/* The IP address we are looking for. */
		int ipaddress;
		/* Argument elements. */
		String[] argumentElements = arguments.trim().split(" ");
		if (argumentElements.length != 1 || argumentElements[0] == "") {
			return "";
		}
		
		// Parse the argument
		try {
			ipaddress = IPv4.toIPv4Address(argumentElements[0]);
		} catch (IllegalArgumentException e) {
			return e.toString();
		}
		
		// See if we know the host already
		for (IDevice device : devices) {
			if (Arrays.asList(device.getIPv4Addresses()).contains(ipaddress)) {
				return deviceToString(device, argumentElements[0]);
			}
		}
		
		// Send ARP to find host
		
		return "host with IP: " + argumentElements[0] + " not found.";
	}
	
	/**
	 * 
	 * @param device
	 * @param ipaddress
	 * @return
	 */
	private String deviceToString(IDevice device, String ipaddress) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("Device found for IP " + ipaddress + " at Switch: " );
		sb.append(HexString.toHexString(device.getAttachmentPoints()[0].getSwitchDPID()));
		sb.append(" port: ");
		sb.append(device.getAttachmentPoints()[0].getPort());
		
		return sb.toString();
	}

}
