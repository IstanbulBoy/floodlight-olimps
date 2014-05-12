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

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.wanswitch.IWANSwitchService;

import jline.console.completer.Completer;

/**
 * The configure command is used to present the completer of
 * "config", i.e. all commands that start with a "config" string.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ConfigureWANSwitchCmd implements ICommand {
	/** Required Module: */
	private IWANSwitchService wanSwitchManager;
	/** The command string. */
	private String commandString = "config wanswitch";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ConfigureWANSwitchCmd(FloodlightModuleContext context) {
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
				if (argumentElements[0].trim().toLowerCase().equals("no") && argumentElements[1].trim().toLowerCase().equals("shut")) {
					this.wanSwitchManager.shutdown(false);
					return "Activate WAN switch";
				}
				short port = 0;
				try {
					port = Short.parseShort(argumentElements[1]);
				} catch (NumberFormatException e) {
					return "Wrong argument: " + argumentElements[1];
				}
				this.wanSwitchManager.setControllerPort(port);
			case 1:
				String hostname = argumentElements[0];
				if (hostname.trim().toLowerCase().equals("shut")) {
					this.wanSwitchManager.shutdown(true);
					return "Shutdown WAN switch";
				}
				this.wanSwitchManager.setController(hostname.toLowerCase().trim());
				return "New WAN switch controller: " + hostname.toLowerCase().trim() + ":" + this.wanSwitchManager.getControllerPort();
			default:
				return "Usage: configure wanswitch <CONTROLLER_IP> [<CONTROLLER_PORT>]";
		}
	}

}
