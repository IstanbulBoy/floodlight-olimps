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

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.wanswitch.IWANSwitchService;

import jline.console.completer.Completer;

/**
 * The configure command is used to present the completer of
 * "config", i.e. all commands that start with a "config" string.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ConfigureWANPortCmd implements ICommand {
	/** Required Module: */
	private IWANSwitchService wanSwitchManager;
	/** The command string. */
	private String commandString = "config wanport";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ConfigureWANPortCmd(FloodlightModuleContext context) {
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
	public synchronized String execute(IConsole console, String arguments) {
		/* Argument elements. */
		String[] argumentElements = arguments.trim().split(" ");
		if (argumentElements.length == 1) {
			if (argumentElements[0] == "") {
				argumentElements = new String[0];
			}
		}
		
		// Switch by number of arguments.
		switch (argumentElements.length) {
			case 2:
				long switchId = HexString.toLong(argumentElements[0]);
				int portId = Integer.parseInt(argumentElements[1]);
				int wanPortId = this.wanSwitchManager.addWanSwitchPort(switchId, portId);
				return "New WAN switch port: " + wanPortId + " at " + argumentElements[0] + " - " + OFSwitchPort.stringOf(portId);
			default:
				return "Usage: configure wanport <SWITCH_ID> <PORT>";
		}
	}

}
