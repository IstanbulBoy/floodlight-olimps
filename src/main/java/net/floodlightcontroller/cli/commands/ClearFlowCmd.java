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

import jline.console.completer.Completer;
import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.Utils;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ClearFlowCmd implements ICommand {
	/** Required Module: */
	private IFloodlightProviderService floodlightProvider;
	/** The command string to execute this command. */
	private String commandString = "clear flow";
	/** The Arguments used by this command. */
	private String arguments = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ClearFlowCmd(FloodlightModuleContext context) {
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
		return "";
	}
	
	@Override
	public Collection<Completer> getCompleter() {
		return null;
	}

	@Override
	public synchronized String execute(IConsole console, String arguments) {
		/* The resulting string. */
		StringBuilder result = new StringBuilder();
		/* Argument elements. */
		String[] argumentElements = arguments.trim().split(" ");
		if (argumentElements.length == 1) {
			if (argumentElements[0] == "") {
				argumentElements = new String[0];
			}
		}
		
		// Switch by number of arguments.
		switch (argumentElements.length) {
			case 1:
				if (argumentElements[0].equalsIgnoreCase("all")) {
					for (IOFSwitch iofSwitch : floodlightProvider.getAllSwitchMap().values()) {
						iofSwitch.clearAllFlowMods();
					}
					result.append("All flows cleared");
				} else {
					long argument = Utils.parseSwitchId(argumentElements[0]);
					if (floodlightProvider.getAllSwitchDpids().contains(argument)) {
						floodlightProvider.getSwitch(argument).clearAllFlowMods();
					}
					result.append("Flows from switch " + argumentElements[0] + " cleared");
				}
				break;
			default:
				result.append("Usage: clear flow <SWITCH | ALL>");
		}
		
		return result.toString();
	}

}
