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

import java.util.Collection;

import jline.console.completer.Completer;

/**
 * The command interface that needs to be implemented by
 * every command to be executed by the command handler.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface ICommand {
	
	/**
	 * Gets the command (id) string.
	 * 
	 * @return The command as a string.
	 */
	public String getCommandString();

	/**
	 * Gets the command argument string.
	 * 
	 * @return The command's argument as a string.
	 */
	public String getArguments();
	
	/**
	 * Gets the command help string
	 * 
	 * @return The command's help text as a string.
	 */
	public String getHelpText();
	
	/**
	 * 
	 * @return
	 */
	public Collection<Completer> getCompleter();
	
	/**
	 * Executes the command and performs its actual action.
	 * 
	 * @param console The console where the command was initialized.
	 * @param arguments The command's argument.
	 * @return A string that might be returned by the command execution.
	 */
	public String execute(IConsole console, String arguments);
}
