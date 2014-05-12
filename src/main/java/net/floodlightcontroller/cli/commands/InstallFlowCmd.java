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

import org.openflow.protocol.OFMatch;
import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.multipath.IMultipathService;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.routing.Path;


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
public class InstallFlowCmd implements ICommand {
	/** Required Module: Floodlight Provider Service. */
	private IFloodlightProviderService floodlightProvider;
	/** */
	private IMultipathService multiPath;
	/** Required Module: */
	private IPathCacheService pathCache;
	/** The command string. */
	private String commandString = "install flow";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public InstallFlowCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.pathCache = context.getServiceImpl(IPathCacheService.class);
		this.multiPath = context.getServiceImpl(IMultipathService.class);
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
		/* Set an empty cookie. */
		long cookie = 0L;
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
			case 3:
				// Install flow <PATH_ID> <OUT_PORT> <MATCH>
				int pathId = Integer.parseInt(argumentElements[0]);
				int outPortId = Short.parseShort(argumentElements[1]);
				OFMatch match = new OFMatch();
				match.fromString(argumentElements[2]);
				// Check for errors.
				if (!this.pathCache.containsPath(pathId)) {
					return "Path with ID " + pathId + " not found.";
				}
				Path path = this.pathCache.getPath(pathId);
				IOFSwitch dstSwitch = this.floodlightProvider.getSwitch(path.getEndPoints().getDst());
				// Check for errors.
				if (dstSwitch == null || dstSwitch.getPort(outPortId) == null) {
					return "Output port " + outPortId + " on switch " + HexString.toHexString(path.getEndPoints().getDst()) + " not found.";
				}
				
				// Install flow on path.
				if (this.multiPath.installFlow(match, path, outPortId, null, cookie)) {
					return "Flow installed";
				} else {
					return "Error installing flow";
				}
			case 4:
				// Install flow <PATH_ID> <IN_PORT> <OUT_PORT> <MATCH>
				break;
			default:
				return "Usage: install path <PATH_ID> [<IN_PORT>] <OUT_PORT>";
		}
		
		// Return
		return result.toString();
	}
	
}
