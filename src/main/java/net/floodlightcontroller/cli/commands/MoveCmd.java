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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.openflow.protocol.OFMatch;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.flowcache.IFlowQueryHandler;
import net.floodlightcontroller.multipath.IMultipathService;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.routing.Path;


import jline.console.completer.Completer;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class MoveCmd implements ICommand, IFlowQueryHandler {
	/** Required Module: */
	private IMultipathService multiPathManager;
	/** Required Module: */
	private IPathCacheService pathCache;
	/** Required Module: */
	private IFlowCacheService flowCache;
	/** The command string. */
	private String commandString = "move";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public MoveCmd(FloodlightModuleContext context) {
		this.pathCache = context.getServiceImpl(IPathCacheService.class);
		this.multiPathManager = context.getServiceImpl(IMultipathService.class);
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
		/* */
		String matchString;
		/* */
		int srcPathId = 0;
		/* */
		int dstPathId = 0;
		/* */
		OFMatch match = new OFMatch();
		
		
		// Switch by number of arguments.
		switch (argumentElements.length) {
		case 2:
			matchString = argumentElements[0];
			dstPathId = Integer.parseInt(argumentElements[1]);
			FlowCacheQueryResp fcqr = null;
			
			match.fromString(matchString);
			FlowCacheQuery fcq = new FlowCacheQuery(this, IFlowCacheService.DEFAULT_DB_NAME, "showpathcmd", null, null, match);
			Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
			try {
				fcqr = future.get(5, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
				return "Moving flow failed";
			} catch (TimeoutException e) {
				// Object not found.
				return "Moving flow failed. Could not find flow.";
			}
			
			// Check if source path ID is unique.
			for (FlowCacheObj fco : fcqr.flowCacheObjList) {
				if (srcPathId == 0) {
					srcPathId = fco.getPathId();
				}
				if (srcPathId != fco.getPathId()) {
					return "Moving flow failed. Match is not unique. Please specifiy source path ID.";
				}
			}
			// Move flow.
			return this.moveFlow(match, srcPathId, dstPathId);
		case 3:
			matchString = argumentElements[0];
			srcPathId = Integer.parseInt(argumentElements[1]);
			dstPathId = Integer.parseInt(argumentElements[2]);			
			match.fromString(matchString);
			// Move flow.
			return this.moveFlow(match, srcPathId, dstPathId);
		default:
			return "Usage: move <MATCH> <SRC_PATH>  <DST_PATH>";
		}
	}
	
	/**
	 * 
	 * @param match
	 * @param srcPathId
	 * @param dstPathId
	 * @return
	 */
	private String moveFlow(OFMatch match, int srcPathId, int dstPathId) {
		Path srcPath = this.pathCache.getPath(srcPathId);
		Path dstPath = this.pathCache.getPath(dstPathId);
		long cookie = 0L;
		
		if (this.multiPathManager.moveFlow(match, srcPath, dstPath, cookie)) {
			return "Moved flow " + match + " from path " + srcPath.getId() + " to path " + dstPath.getId(); 
		} else {
			return "Moving flow failed";
		}
	}
	
	@Override
	public void flowQueryRespHandler(FlowCacheQueryResp resp) {
		// NO-OP.
	}

}
