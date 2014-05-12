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
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.multipath.IPathSelector;

import jline.console.completer.Completer;

/**
 * The configure command is used to present the completer of
 * "config", i.e. all commands that start with a "config" string.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ConfigureSelectorCmd implements ICommand {
	/** Required Module: */
	private IPathFinderService pathFinder;
	/** The command string. */
	private String commandString = "config selector";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ConfigureSelectorCmd(FloodlightModuleContext context) {
		this.pathFinder = context.getServiceImpl(IPathFinderService.class);
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
		String name = "";
		String args = "";
		IPathSelector selector;
		
		/* Argument elements. */
		String[] argumentElements = arguments.trim().split(" ");
		if (argumentElements.length == 1) {
			name = argumentElements[0];
		} else {
			name = argumentElements[0];
			for (int i = 1; i < argumentElements.length; i++) {
				args = args + argumentElements[i] + " ";
			}
			args = args.trim();
		}
		
		// Switch by number of arguments.
		switch (argumentElements.length) {
			case 0:
				return "Usage: configure selector <NAME> <ARGS>";
			default:
				selector = this.pathFinder.setPathSelector(name.toLowerCase().trim(), args.toLowerCase().trim());
				
				if (selector != null) {
					return "New path selector: " + selector.getName();
				} else {
					return "Error! Could not set selector " + name + ". Wrong selector name? See logs for details. Using old selector: " + this.pathFinder.getPathSelector().getName(); 
				}
		}
	}

}
