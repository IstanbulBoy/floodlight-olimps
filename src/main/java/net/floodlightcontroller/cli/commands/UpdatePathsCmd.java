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
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.routing.EndPoints;


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
public class UpdatePathsCmd implements ICommand {
	/** Required Module: */
	private IPathFinderService pathFinder;
	/** Required Module: */
	private IPathCacheService pathCache;
	/** The command string. */
	private String commandString = "update paths";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public UpdatePathsCmd(FloodlightModuleContext context) {
		this.pathFinder = context.getServiceImpl(IPathFinderService.class);
		this.pathCache = context.getServiceImpl(IPathCacheService.class);
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
		
		// update all paths
		for (EndPoints endPoints : pathCache.getAllEndPoints()) {
			pathFinder.calculatePaths(endPoints.getSrc(), endPoints.getDst());
		}
		
		// Show all paths.
		if (!pathCache.isEmpty()) {
			return this.endPointsToTableString(pathCache.getAllEndPoints());
		}
		
		// Return
		return "no path available";
	}
	
	/**
	 * 
	 * @param routeIds
	 * @return
	 */
	private String endPointsToTableString(Set<EndPoints> endPointsSet) {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("Src Switch");
        header.add("Dst Switch");
        header.add("No. of Paths");
        
        // Add header to string table.
        stringTable.setHeader(header);
        
        for (EndPoints endPoints : endPointsSet) {
        	List<String> row = new LinkedList<String>();
        	row.add(HexString.toHexString(endPoints.getSrc()));
        	row.add(HexString.toHexString(endPoints.getDst()));
        	row.add(String.valueOf(pathCache.getAllPaths(endPoints.getSrc(), endPoints.getDst()).size()));
        	stringTable.addRow(row);
        }
        
        // Return string table as a string.
        return stringTable.toString();
	}
	
}
