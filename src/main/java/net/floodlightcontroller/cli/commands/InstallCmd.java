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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;

import jline.console.completer.Completer;

/**
 *
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class InstallCmd implements ICommand {
	/** The command string. */
	private String commandString = "install";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;

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
		/* The String builder that hold the resulting string. */
		StringBuilder result = new StringBuilder();
		/* A list of command completion candidates. */
		List<CharSequence> candidates = new ArrayList<CharSequence>();
		/* Since Completer.complete needs a blank at the end of the command, we need to add one. */
		String line = this.commandString + " ";
		
		// Find possible command completions.
		for (Completer comp : console.getCompleters()) {
			if (comp.complete(line, line.length(), candidates) == -1) {
				break;
			}
		}
		
		// Make sure the list is unique.
		candidates = new ArrayList<CharSequence>(new HashSet<CharSequence>(candidates)); 
		
		// Create the result string.
		result.append("Command not found. Use:");
		for (CharSequence candidate : candidates) {
			result.append("\n  install " + candidate);
		}
	
		// Return.
		return result.toString();
	}

}
