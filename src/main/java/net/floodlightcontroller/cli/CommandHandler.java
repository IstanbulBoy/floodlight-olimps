package net.floodlightcontroller.cli;

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
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The command handler that handles and executes commands.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class CommandHandler {
	/** The unique command hander that executes all console commands. */
	private static CommandHandler commander;
	/** Map of all commands handled by the command handler. */
	private Map<String, ICommand> commands = new HashMap<String, ICommand>();
	/** Map of all consoles connected to the command handler. */
	private Map<Integer, IConsole> consoles = new HashMap<Integer, IConsole>();
	
	/**
	 * Provides access to the singleton instance of the command handler.
	 * 
	 * @return instance of the command handler.
	 */
	public static synchronized CommandHandler getInstance() {
		if (commander == null)
			commander = new CommandHandler();
		return commander;
	}
	
	/**
	 * Adds a new command to the command handler.
	 * 
	 * @param command The new command that is added.
	 */
	public synchronized void addCommand(ICommand command) {
		this.commands.put(command.getCommandString().trim().toLowerCase(), command);
	}
	
	/**
	 * Removes a command from the command handler.
	 * 
	 * @param command The command that should be removed.
	 */
	public synchronized void removeCommand(ICommand command) {
		this.commands.remove(command.getCommandString().trim().toLowerCase());
	}
	
	/**
	 * Returns the commands registered and handles by the command handler.
	 * 
	 * @return a collection of commands.
	 */
	public Collection<ICommand> getCommands() {
		return this.commands.values();
	}
	
	/**
	 * Adds a console that shows the result of executed commands.
	 * 
	 * @param console IConsole that provides and reacts to commands.
	 */
	public void addListener(IConsole console) {
		this.consoles.put(console.hashCode(), console);
	}
	
	/**
	 * Removes a console.
	 * 
	 * @param console IConsole to be removed.
	 */
	public void removeListener(IConsole console) {
		this.consoles.remove(console.hashCode());
	}
	
	/**
	 * Executes a command as given in the command string. Allows for
	 * results to be written back to the console.
	 * 
	 * @param console IConsole that allows the command to access the console directly.
	 * @param commandString the command string as read from the command line.
	 */
	public synchronized void execute(IConsole console, String commandString) {
		/* Return string (if any) displayed at the output of the console. */
		String string;
		/* Arguments string that is interpreted by the command. */
		String arguments;
		/* Command that is executed. */
		ICommand command;
		
		// Parse commandString to get the actual commandString and arguments.
		Map.Entry<String, String> commandEntry = this.parseCommand(commandString);
		
		// get command and arguments
		command = this.commands.get(commandEntry.getKey().trim().toLowerCase());
		arguments = commandEntry.getValue();
		
		if ((command) == null) {
			if (arguments.length() > 0)
				this.write("Command not found: " + arguments, console.hashCode());
			return;
		}
		
		// Execute command.
		string = command.execute(console, arguments);
		
		// Write result to console.
		if (string != null) {
			this.write(string, console.hashCode());
		}
	}
	
	/**
	 * Writes a string to a given console.
	 * 
	 * @param string String to be written to the console.
	 * @param consoleHash Integer that identifies the console.
	 */
	@Deprecated
	private void write(String string, int consoleHash) {
		try {			
			this.consoles.get(consoleHash).write(string);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Parses a command string tries to find a corresponding command. To
	 * this end, it decomposes the command and it arguments.
	 * 
	 * @param commandString String that was read from the command line.
	 * @return Map.Entry with the command (key) and its arguments (value). 
	 */
	private Map.Entry<String, String> parseCommand(String commandString) {
		/* All (sub) string elements in a string command. */
		String[] commandElements = commandString.split(" ");
		/* New command string (without arguments). */
		String command = "";
		/* New argument string. */
		String arguments = "";
		
		for (int i=0; i<commandElements.length; i++) {
			if (this.commands.get( (command + " " + commandElements[i].trim()).trim() ) != null) {
				command = (command + " " + commandElements[i].trim()).trim();
			} else {
				arguments = (arguments + " " + commandElements[i].trim()).trim();
			}
			
		}
		
		// Return.
		return new AbstractMap.SimpleEntry<String, String>(command, arguments);
	}
	
	/**
	 * Singleton. Private constructor to avoid instantiation.
	 */
	private CommandHandler() {
		// do nothing;
	}
}
