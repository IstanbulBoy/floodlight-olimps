package net.floodlightcontroller.oscars.cli;

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

import jline.console.completer.Completer;
import net.es.oscars.wsdlTypes.CreateReply;
import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.oscars.IOscarsService;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ShowOscarsReservationsCmd implements ICommand {
	/** */
	private IOscarsService oscarsHandler;
	/** The command string. */
	private String commandString = "show oscars reservations";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ShowOscarsReservationsCmd(FloodlightModuleContext context) {
		this.oscarsHandler = context.getServiceImpl(IOscarsService.class);
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
		
		// Print the configuration.
		result.append(this.client2String(oscarsHandler) + "\n");
		result.append(this.reservationToTableString(oscarsHandler.getReservations()) + "\n");
		
		return result.toString();
	}
	
	/**
	 * 
	 * @param oscarsHandler
	 * @return
	 */
	private String client2String(IOscarsService oscarsHandler) {
		StringBuilder sb = new StringBuilder();
		
		sb.append("OSCARS IDC reservations:\n");
		
		return sb.toString();
	}
	
	private String reservationToTableString(Set<CreateReply> createReplies) {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        stringTable.setOffset(3);
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        header.add("GRI");
        header.add("Status");
        header.add("Path Type");
        header.add("Path Setup Mode");
        header.add("SRC-Endpoint");
        header.add("SRC-VLAN");
        header.add("DST-Endpoint");
        header.add("DST-VLAN");
        
        // Add header to string table.
        stringTable.setHeader(header);
        
        if (createReplies == null)
        	return stringTable.toString();
        
        for (CreateReply reply : createReplies) {
        	// Create StringTable
			List<String> row = new LinkedList<String>();
			row.add(reply.getGlobalReservationId());
			row.add(reply.getStatus());
			row.add(reply.getPathInfo().getPathType());
			row.add(reply.getPathInfo().getPathSetupMode());
			if (reply.getPathInfo().getLayer2Info() != null) {
				row.add(reply.getPathInfo().getLayer2Info().getSrcEndpoint());
				row.add(reply.getPathInfo().getLayer2Info().getSrcVtag().getString());
				row.add(reply.getPathInfo().getLayer2Info().getDestEndpoint());
				row.add(reply.getPathInfo().getLayer2Info().getDestVtag().getString());
			} else {
				row.add("unknown");
				row.add("unknown");
				row.add("unknown");
				row.add("unknown");
			}
			
			// Add row to string table.
			stringTable.addRow(row);
        }
        
        // Return string table as a string.
        return stringTable.toString();
	}
	
}
