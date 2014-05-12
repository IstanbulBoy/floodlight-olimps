package net.floodlightcontroller.configuration;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * The configuration manager reads and stores configuration information from
 * and to a JSON file. It get and puts this information from and to modules.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ConfigurationManager implements IFloodlightModule, IConfigurationService {
	/** The default configuration file name */
	public static final String DEFAULT_FILE_NAME = "floodlight.json";
	/** Should the configuration file automatically be loaded. */
	public static final boolean LOAD_AT_START = true;
	
	/** Logger to log configuration events.*/
	protected static Logger logger;
	/** Required Module: Floodlight Provider Service. */
	protected IFloodlightProviderService floodlightProvider;
	/** The name of the file where the configuration is stored. */
	protected String fileName;
	/** A map of configuration listeners that actually handle the module configurations. */
	protected Map<String, IConfigurationListener> configurationListener;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IConfigurationService.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(IConfigurationService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) {
		logger = LoggerFactory.getLogger(ConfigurationManager.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		configurationListener = new HashMap<String, IConfigurationListener>();
		
		fileName = DEFAULT_FILE_NAME;
	}

	@Override
	public void startUp(FloodlightModuleContext context) {
		// NO-OP.
	}
	
	@Override
	public void addListener(IConfigurationListener listener) {
		String listenerName = listener.getName().toLowerCase();
		if (this.configurationListener.containsKey(listenerName)) {
			if (logger.isErrorEnabled()) {
				logger.error("Configuration Listener " + listener.getName() + " already present.");
			}
			return;
		}
		this.configurationListener.put(listenerName, listener);
		if (logger.isDebugEnabled()) {
			logger.debug("Configuration Listener " + listener.getName() + " added.");
		}
		
		// Load configuration as soon as a configuration listener is ready.
		if (LOAD_AT_START) {
			readJsonFromFile(null);
		}
	}

	@Override
	public void removeListener(IConfigurationListener listener) {
		String listenerName = listener.getName().toLowerCase();
		this.configurationListener.remove(listenerName);
		if (logger.isDebugEnabled()) {
			logger.debug("Configuration Listener " + listener.getName() + " removed.");
		}
	}

	@Override
	public void saveConfiguration(String fileName) throws IOException {
		if (fileName == null) {
			this.writeJsonToFile(DEFAULT_FILE_NAME);
		} else {
			this.writeJsonToFile(fileName);
		}
	}

	@Override
	public void restoreConfiguration(String fileName) {
		if (fileName == null) {
			this.readJsonFromFile(DEFAULT_FILE_NAME);
		} else {
			this.readJsonFromFile(fileName);
		}
	}
	
	@Override
	public String showConfiguration(String fileName) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = createJsonRootNode();
		JsonFactory f = new JsonFactory();
		OutputStream out = new ByteArrayOutputStream();
		JsonGenerator g = null;
		try {
			g = f.createGenerator(out, JsonEncoding.UTF8);
			g.useDefaultPrettyPrinter();
			mapper.writeTree(g, rootNode);
		} catch (IOException e) {
			return "Error: Could not parse the JSON configuration file.";
		}
		return out.toString();
	}
	
	/**
	 * Collects the configuration information from all the configuration listener.
	 * 
	 * @return <b>JsonNode</b> A JSON node that contains all the configuration information.
	 */
	private ObjectNode createJsonRootNode() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = mapper.createObjectNode();
		for (String name : configurationListener.keySet()) {
			if (configurationListener.get(name).getJsonConfig() != null) {
				rootNode.put(name, configurationListener.get(name).getJsonConfig());
			}
		}
		return rootNode;
	}
	
	/**
	 * Writes a configuration file based on information gathered from
	 * the various configuration listeners.
	 * 
	 * @param file An optional configuration file name.
	 */
	private void writeJsonToFile(String fileName) throws IOException {
		String configFile = (fileName != null) ? fileName : this.fileName;
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = createJsonRootNode();
		JsonFactory f = new JsonFactory();
		JsonGenerator g = null;
		
		try {
			g = f.createJsonGenerator(new File(configFile), JsonEncoding.UTF8);
			g.useDefaultPrettyPrinter();
			mapper.writeTree(g, rootNode);
		} catch (IOException e) {
			if (logger.isErrorEnabled()) {
				logger.error("Could not write the JSON configuration file.");
			}
			throw new IOException("Could not write the JSON configuration file");
		}
	}
	
	/**
	 * Reads a configuration file and calls the corresponding configuration 
	 * listeners.
	 * 
	 * @param file An optional configuration file name.
	 */
	private void readJsonFromFile(String fileName) {
		String configFile = (fileName != null) ? fileName : this.fileName;
		ObjectMapper mapper = new ObjectMapper();
		JsonFactory f = new JsonFactory();
		JsonParser jp = null;
		
		try {
			jp = f.createJsonParser(new File(configFile));
			JsonNode root = mapper.readTree(jp);
			// Check if the file is empty.
			if (root == null)
				return;
			Iterator<Entry<String, JsonNode>> iter = root.fields();
			// For every configuration sub-node.
			while (iter.hasNext()) {
				Entry<String, JsonNode> entry = iter.next();
				String fieldName = entry.getKey();
				JsonNode child = entry.getValue();
				if (configurationListener.containsKey(fieldName)) {
					configurationListener.get(fieldName).putJsonConfig(child);
				} else {
					if (logger.isWarnEnabled()) {
						logger.warn("No configuration entry found in " + configFile + " for " + fieldName);
					}
				}
			}
		} catch (FileNotFoundException e){
			if (logger.isWarnEnabled()) {
				logger.warn("Configuration file {} not found.", configFile);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
