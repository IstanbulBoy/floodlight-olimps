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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.sshd.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.floodlightcontroller.cli.commands.*;
import net.floodlightcontroller.configuration.IConfigurationListener;
import net.floodlightcontroller.configuration.IConfigurationService;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.restserver.IRestApiService;

/**
 * Command Line Interface (CLI) to Floodlight. The CLI module 
 * offers a Cisco-like CLI to FLoodlight. You can log on to the
 * CLI using an SSH client.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class Cli implements IFloodlightModule, ICliService, IConfigurationListener {
	/** The unique name of this configuration listener. */
	private static final String CONFIGURATOR_NAME = "CLI";
	/** Default port of the SSH config console. */
	private static final int DEFAULT_PORT = 55220;
	/** Default user name: root. */
	private static final String DEFAULT_USERNAME = "root";
	/** Default password: password. */
	private static final String DEFAULT_PASSWORD = "password";	
	/** Default SSH host key location. */
	private static final String DEFAULT_HOSTKEY = "ssh_host_dsa_key.pub";
	/** Logger to log ProactiveFlowPusher events. */
	protected static Logger logger = LoggerFactory.getLogger(Cli.class);
	/** Ports used by the SSH server to offer the console login. */
	protected int port;
	/** User name to log in to the console. */
	protected String username;
	/** Password to log in to the console. */
	protected String password;
	/** Host key file where SSHD stores the host key. */
	protected String hostkey;
	/** The command handler that executes CLI commands. */
	protected CommandHandler commander;
	/** Required Module: Floodlight Provider Service. */
	protected IFloodlightProviderService floodlightProvider;
	/** Required Module: Reference to the REST API service. */
	protected IRestApiService restApi;
	/** Required Module: Floodlight Device Manager Service.*/
	protected IDeviceService deviceManager;
    /** Required Module: Floodlight Configuration Manager Service. */
    protected IConfigurationService configManager;
    
	///
	/// IFloodlightModule
	///

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ICliService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(ICliService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IFloodlightProviderService.class);
	    l.add(IRestApiService.class);
	    l.add(IDeviceService.class);
	    l.add(IConfigurationService.class);
	    return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		configManager = context.getServiceImpl(IConfigurationService.class);
		
		// Read our configuration from a properties file or set to default values.
		this.readConfig(context);
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		this.configManager.addListener(this);
		
		// Initialize command handler;
		commander = CommandHandler.getInstance();
		// Add commands to handler.
		commander.addCommand(new ExitCmd());
		commander.addCommand(new DateCmd());
		commander.addCommand(new MoveCmd(context));
		// Add clear commands to handler.
		commander.addCommand(new ClearCmd());
		commander.addCommand(new ClearMemoryCmd(context));
		commander.addCommand(new ClearFlowCmd(context));
		// Add show commands to handler.
		commander.addCommand(new ShowCmd());
		commander.addCommand(new ShowConfigCmd(context));
		commander.addCommand(new ShowControllerCmd(context));
		commander.addCommand(new ShowSwitchCmd(context));
		commander.addCommand(new ShowHostCmd(context));
		commander.addCommand(new ShowLinkCmd(context));
		commander.addCommand(new ShowFlowCmd(context));
		commander.addCommand(new ShowPathCmd(context));
		commander.addCommand(new ShowCalculatorCmd(context));
		commander.addCommand(new ShowSelectorCmd(context));
		commander.addCommand(new ShowAppsCmd(context));
		commander.addCommand(new ShowWanSwitchCmd(context));
		// Add update commands to handler.
		commander.addCommand(new UpdateCmd());
		commander.addCommand(new UpdatePathsCmd(context));
		commander.addCommand(new UpdateFlowsCmd(context));
		// Add configure commands to handler.
		commander.addCommand(new ConfigureCmd());
		commander.addCommand(new ConfigureCalculatorCmd(context));
		commander.addCommand(new ConfigureSelectorCmd(context));
		commander.addCommand(new ConfigureWANSwitchCmd(context));
		commander.addCommand(new ConfigureWANPortCmd(context));
		// Add remove commands to handler.
		commander.addCommand(new RemoveCmd());
		commander.addCommand(new RemoveVirtportCmd());
		commander.addCommand(new RemoveVirtportVlanCmd(context));
		// Add create commands to handler.
		commander.addCommand(new CreateCmd());
		commander.addCommand(new CreateVirtportCmd());
		commander.addCommand(new CreateVirtportVlanCmd(context));
		// Add save command to handler.
		commander.addCommand(new SaveCmd());
		commander.addCommand(new SaveConfigCmd(context));
		// Add restore command to handler.
		commander.addCommand(new RestoreCmd());
		commander.addCommand(new RestoreConfigCmd(context));
		
		// Initialize the SSH server.
		SshServer sshd = SshServer.setUpDefaultServer();
		sshd.setPort(this.port);
		sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(this.hostkey, "DSA"));
		sshd.setPasswordAuthenticator(new SimplePasswordAuthenticator(this.username, this.password));
		sshd.setShellFactory(new FloodlightShellFactory());
		
		// Start the SSH server.
		try {
			sshd.start();
			Cli.logger.info("Starting config console (via SSH) on port {}", this.port);
		} catch (IOException e) {
			Cli.logger.error("Starting config console (via SSH) on port {} failed", this.port);
			e.printStackTrace();
		}
	}
	
	///
	/// ICliService
	///
	
	@Override
	public void registerCommand(ICommand command) {
		if (logger.isDebugEnabled()) {
			logger.debug("Added command '{}' to CLI.", command.getCommandString());
		}
		this.commander.addCommand(command);
	}
	
	@Override
	public void unregisterCommand(ICommand command) {
		if (logger.isDebugEnabled()) {
			logger.debug("REmoved command '{}' from CLI.", command.getCommandString());
		}
		this.commander.removeCommand(command);
	}
	
	///
	/// IConfigurationListener
	///

	@Override
	public String getName() {
		return CONFIGURATOR_NAME;
	}
	
	@Override
	public JsonNode getJsonConfig() {		
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = mapper.createObjectNode();
		try {
			if (this.port != DEFAULT_PORT)
				rootNode.put("port", this.port);
			if (this.username != DEFAULT_USERNAME)
				rootNode.put("username", this.username);
			if (this.password != DEFAULT_PASSWORD)
				rootNode.put("password", this.password);
			if (this.hostkey != DEFAULT_HOSTKEY)
				rootNode.put("hostkey", this.hostkey);
			// Write any other CLI configuration.
			// ... here.
		} catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Creating the CLI configuration for JSON failed. ", e);
			}
		}
		
		// Return root node if it contains some configuration. Otherwise return null.
		return (rootNode.size() > 0) ? (JsonNode) rootNode : null;
	}
	
	@Override
	public void putJsonConfig(JsonNode jsonNode) {
		Iterator<Entry<String, JsonNode>> iter = jsonNode.fields();
		while (iter.hasNext()) {
			Entry<String, JsonNode> entry = iter.next();
			String fieldname = entry.getKey().toLowerCase();
			JsonNode child = entry.getValue();
			switch(fieldname) {
				case "port":
					this.port = child.asInt();
					break;
				case "username":
					this.username = child.asText();
					break;
				case "password":
					this.password = child.asText();
					break;
				case "hostkey":
					this.hostkey = child.asText();
					break;
				default:
					if (logger.isWarnEnabled()) {
						logger.warn("Reading the CLI configuration for {} from JSON failed.", fieldname);
					}
			}
		}
	}
	
	///
	/// Local methods
	///
	
	/**
	 * Reads the configuration for this module from properties file "floodlightdefaults.propertiers".
	 */
	private void readConfig(FloodlightModuleContext context) {
		// Read the configure options.
        Map<String, String> configOptions = context.getConfigParams(this);
        
        this.port = (configOptions.get("port") != null) ? Integer.parseInt(configOptions.get("port")) : DEFAULT_PORT;
        this.username = (configOptions.get("username") != null) ? configOptions.get("username") : DEFAULT_USERNAME;
        this.password = (configOptions.get("password") != null) ? configOptions.get("password") : DEFAULT_PASSWORD;
        this.hostkey = (configOptions.get("hostkey") != null) ? configOptions.get("hostkey") : DEFAULT_HOSTKEY;
	}

}
