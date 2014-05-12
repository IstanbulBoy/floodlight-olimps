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

import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TimeZone;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.Utils;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;


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
public class ShowControllerCmd implements ICommand {
	/** */
	private FloodlightModuleContext context;
	/** Required Module: */
	private IFloodlightProviderService floodlightProvider;
	/** The command string. */
	private String commandString = "show controller";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ShowControllerCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.context = context;
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
		
		// Get the information regarding memory.
		Map<String, Long> memory = this.floodlightProvider.getMemory();
		long total = memory.get("total");
		long free  = memory.get("free");
		long used  = total - free;
		result.append("  Memory usage: " + this.parseMemory(used) + " of " + this.parseMemory(total) + "\n");
		
		// Get the information regarding uptime.
		long uptime = this.floodlightProvider.getUptime();
		result.append("  Uptime: " + this.parseUptime(uptime) + "\n");
		
		// Get the information regarding the (loaded) modules.
		Map<String, Object> model = new HashMap<String, Object>();
		Collection<IFloodlightModule> modules = this.context.getAllModules();
		for (IFloodlightModule module : modules) {
			// Module information map.
			Map<String,Object> moduleInfo = new HashMap<String, Object>();
			// Get the module name.
			String moduleName = module.getClass().getCanonicalName();
			
			// Get the services a module provides.    		
			Collection<Class<? extends IFloodlightService>> provides = module.getModuleServices();
			if (provides == null)
            	provides = new HashSet<Class<? extends IFloodlightService>>();
			Map<String,Object> providesMap = new HashMap<String,Object>();
			for (Class<? extends IFloodlightService> service : provides) {
        		providesMap.put(service.getCanonicalName(), module.getServiceImpls().get(service).getClass().getCanonicalName());
        	}
			moduleInfo.put("provides", providesMap);
			
			// Add the module and the module information to the model map.
			model.put(moduleName, moduleInfo);
		}
		
		result.append("  Loaded Modules:" + "\n");
		for (String moduleName : model.keySet()) {
			result.append("    " + moduleName + "\n");
		}
		
		// Save the configuration.
		return result.toString();
	}

	/**
	 * 
	 * @param memory
	 * @return
	 */
	private String parseMemory(long memory) {
		if (memory < 1000)
			return memory + " Byte";
		memory = memory / 1000;
		if (memory < 1000)
			return memory + " KByte";
		memory = memory / 1000;
		if (memory < 1000)
			return memory + " MByte";
		memory = memory / 1000;
		if (memory < 1000)
			return memory + " GByte";
		memory = memory / 1000;
		return memory + " TByte";
	}
	
	/**
	 * 
	 * @param uptime
	 * @return
	 */
	private String parseUptime(long uptime) {
		String upSince = Utils.parseDate(this.floodlightProvider.getSystemStartTime());
		
		Date date = new Date ();
		date.setTime(uptime - TimeZone.getDefault().getRawOffset());
		
		SimpleDateFormat dateformat = new SimpleDateFormat("HH:mm:ss");
		return dateformat.format(date) + " (h:m:s) since " + upSince;
	}
	
}
