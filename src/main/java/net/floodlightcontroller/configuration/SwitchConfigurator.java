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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.OFPortManager;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reads switch configurations from a JSON file/node provided by the configuration manager updates
 * the switches accordingly. Writes switch configurations from the current switches to the configuration
 * file.
 * 
 * This is a helper module that acts as a kind of "glue" between the ConfigurationManager and 
 * the Controller/FloodlightProvider. Since the Controller is not implemented as a Floodlight model. It
 * is tricky to have the right module dependencies. Maybe this can be done nicer by adding some
 * functionality to the Controller class.
 * 
 * @author Michael Bredel (michael.bredel@caltech.edu)
 */
public class SwitchConfigurator implements IFloodlightModule, IConfigurationListener, IOFSwitchListener {
	/** States whether the switch management ports should be included to configuration or not. */
	public static final boolean MGMT_PORTS_INCLUDED = false;
	/** The unique name of this configuration listener. */
	private static final String CONFIGURATOR_NAME = "switchConfig";
	/** The (field-) name of configuration section that deals with virtual ports. */
	private static final String CONFIG_SECTION_VIRTUAL_PORTS = "virtports";
	/** The (field-) name of configuration section that deals with port speeds. */
	private static final String CONFIG_SECTION_PORT_SPEED = "portspeeds";
	/** The (field-) name of configuration section that deals with the switch alias. */
	private static final String CONFIG_SECTION_ALIAS = "alias";
	
	/** Logger to log switch configuration events.*/
	protected static Logger logger;
	/** Required Module: Floodlight Provider Service. */
	protected IFloodlightProviderService floodlightProvider;
	/** Required Module: Configuration Service. */
	protected IConfigurationService configurationManager;
	
	/** Local configuration of virtual switch port as in the JSON file: SwitchId -> PhyPortId -> VLAN. */
	protected Map<Long, Map<Short, Set<Short>>> switchVirtualPortConfiguration;
	/** Local configuration of virtual switch port as in the JSON file: SwitchPortId -> Speed. */
	protected Map<Integer, Integer> portSpeedConfiguration;
	/** Local configuration of switch aliases as in the JSON file: SwitchId -> alias. */
	protected Map<Long, String> aliasConfiguration;

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IConfigurationService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		logger = LoggerFactory.getLogger(SwitchConfigurator.class);
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		configurationManager = context.getServiceImpl(IConfigurationService.class);
		switchVirtualPortConfiguration = new HashMap<Long, Map<Short, Set<Short>>>();
		portSpeedConfiguration = new HashMap<Integer, Integer>();
		aliasConfiguration = new HashMap<Long, String>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		configurationManager.addListener(this);
		floodlightProvider.addOFSwitchListener(this);
	}

	@Override
	public String getName() {
		return CONFIGURATOR_NAME;
	}

	@Override
	public JsonNode getJsonConfig() {
		return switchConfigurationToJson();
	}

	@Override
	public void putJsonConfig(JsonNode jsonNode) {
		this.JsonToSwitchConfiguration(jsonNode);
		this.updateConfig();
	}

	@Override
	public void switchAdded(long switchId) {
		this.updateConfig();
	}

	@Override
	public void switchActivated(long switchId) {
		this.updateConfig();
	}

	@Override
	public void switchPortChanged(long switchId, OFSwitchPort port, PortChangeType type) {
		this.updateConfig();
	}

	@Override
	public void switchChanged(long switchId) {
		// NO-OP
	}

	@Override
	public void switchRemoved(long switchId) {
		// NO-OP
	}
	
	/**
	 * Reads the current configuration from locale configuration
	 * cache and updates the switch.
	 */
	private void updateConfig() {
		// Update the virtual port configuration.
		if (switchVirtualPortConfiguration == null || switchVirtualPortConfiguration.isEmpty()) {
			if (logger.isDebugEnabled()) {
				logger.debug("We don't have a virtual port configuration.");
			}
		} else {
			for (long switchId : switchVirtualPortConfiguration.keySet()) {
				IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(switchId);
				if (iofSwitch == null)
					continue;
				for (short phyPortId : switchVirtualPortConfiguration.get(switchId).keySet()) {
					OFPhysicalPort ofpPort = iofSwitch.getPhysicalPort(phyPortId);
					if (ofpPort == null)
						continue;
					for (short vlanId : switchVirtualPortConfiguration.get(switchId).get(phyPortId)) {
						int virtPortId = OFSwitchPort.virtualPortIdOf(phyPortId, vlanId);
						if (iofSwitch.getPort(virtPortId) != null)
							continue;
						OFSwitchPort ofsPort = OFSwitchPort.create(ofpPort, vlanId);
						// Set the port speed if available.
						if (portSpeedConfiguration.containsKey(ofsPort.getPortNumber()))
							ofsPort.setCurrentPortSpeed(portSpeedConfiguration.get(ofsPort.getPortNumber()));
						iofSwitch.addVirtualPort(ofsPort);
					}
				}
			}
		}
		// Update the alias configuration.
		if (aliasConfiguration != null) {
			for (long switchId : aliasConfiguration.keySet()) {
				IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(switchId);
				if (iofSwitch == null)
					continue;
				iofSwitch.setAttribute("alias", this.aliasConfiguration.get(switchId));
			}
		}
	}
	
	/**
	 * Reads the current switch configuration from the switch 
	 * objects and creates a custom JSON node containing the 
	 * relevant information.
	 * 
	 * The custom JSON node is written to a JSON file.
	 */
	private JsonNode switchConfigurationToJson() {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode rootNode = mapper.createObjectNode();
		try {
			// Write configuration for every switch in the system.
			for (long switchId : this.floodlightProvider.getAllSwitchDpids()) {
				IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(switchId);
				ObjectNode switchNode = mapper.createObjectNode();
				rootNode.put(HexString.toHexString(switchId), switchNode);
				// Write switch alias.
				if (iofSwitch.getAttribute("alias") != null) {
					switchNode.put(CONFIG_SECTION_ALIAS, (String) iofSwitch.getAttribute("alias"));
				}
				// Write virtual ports.
				ObjectNode portNode = mapper.createObjectNode();
				switchNode.put(CONFIG_SECTION_VIRTUAL_PORTS, portNode);
				OFPortManager portManager = iofSwitch.getPortManager();
				for (OFPhysicalPort ofpPort : iofSwitch.getPhysicalPorts()) {
					if (!MGMT_PORTS_INCLUDED && (ofpPort.getPortNumber() < 0))
						continue;
					if (portManager.getPorts(ofpPort.getPortNumber()) != null) {
						ArrayNode vlansNode = portNode.putArray(String.valueOf(ofpPort.getPortNumber()));
						for (OFSwitchPort ofsPort : portManager.getPorts(ofpPort.getPortNumber())) {
							vlansNode.add(ofsPort.getVlanId());
						}
					}
				}
				// Write port speeds.
				ObjectNode speedNode = mapper.createObjectNode();
				switchNode.put(CONFIG_SECTION_PORT_SPEED, speedNode);
				for (OFSwitchPort ofsPort : iofSwitch.getPorts()) {
					if (!MGMT_PORTS_INCLUDED && (ofsPort.getPortNumber() < 0))
						continue;
					if (ofsPort.getCurrentPortSpeed() == 0)
						continue;
					ArrayNode portSpeedNode = speedNode.putArray(OFSwitchPort.stringOf(ofsPort.getPortNumber()));
					portSpeedNode.add(ofsPort.getCurrentPortSpeed());
				}
				// Write any other switch configuration.
				// ObjectNode otherNode = mapper.createObjectNode();
				// switchNode.put("other", otherNode);
			}
		} catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Read the WAN switch configuration from JSON failed. ", e);
			}
		}
		
		return (JsonNode) rootNode;
	}
	
	/**
	 * Reads the switch configuration from a JSON node provided by a JSON file.
	 * 
	 * TODO: Instead of storing the configuration data locally in this object,
	 * we should use Floodlight's storage API.
	 * 
	 * @param switchConfig
	 */
	private void JsonToSwitchConfiguration(JsonNode switchConfig) {
		// Remove previous configurations
		this.switchVirtualPortConfiguration.clear();
		
		// Add new configurations from the JSON file.
		Iterator<Entry<String, JsonNode>> iter = switchConfig.fields();
		while (iter.hasNext()) {
			Entry<String, JsonNode> entry = iter.next();
			String fieldname = entry.getKey(); // The switch ID as string.
			JsonNode child = entry.getValue(); // All child nodes of a single switch.
			long switchId = HexString.toLong(fieldname);
			
			Iterator<Entry<String, JsonNode>> switchIter = child.fields();
			while (switchIter.hasNext()) {
				Entry<String, JsonNode> switchEntry = switchIter.next();
				String switchFieldName = switchEntry.getKey(); // One specific child node.
				JsonNode switchChild = switchEntry.getValue(); // All members of that child node.
				if (switchFieldName.equalsIgnoreCase(CONFIG_SECTION_ALIAS)) {
					this.aliasConfiguration.put(switchId, switchChild.asText());
				}
				// Handle virtual ports configuration. Safes the configuration in the configuration map.
				if (switchFieldName.equalsIgnoreCase(CONFIG_SECTION_VIRTUAL_PORTS)) {
					if (!switchVirtualPortConfiguration.containsKey(switchId)) {
						this.switchVirtualPortConfiguration.put(switchId, new HashMap<Short, Set<Short>>());
					}
					Iterator<Entry<String, JsonNode>> virtportsIter = switchChild.fields();
					while (virtportsIter.hasNext()) {
						Entry<String, JsonNode> virtportsEntry = virtportsIter.next();	
						short phyPortId = Short.parseShort(virtportsEntry.getKey());
						if (!switchVirtualPortConfiguration.get(switchId).containsKey(phyPortId)) {
							this.switchVirtualPortConfiguration.get(switchId).put(phyPortId, new HashSet<Short>());
						}
						JsonNode virtPortIdArray = virtportsEntry.getValue();
						for (int i=0; i<virtPortIdArray.size(); i++) {
							short vlanId = (short)(virtPortIdArray.get(i)).asInt();
							this.switchVirtualPortConfiguration.get(switchId).get(phyPortId).add(vlanId);
						}
					}
				}
				// Handle port speed configuration. Safes the configuration in the configuration map.
				if (switchFieldName.equalsIgnoreCase(CONFIG_SECTION_PORT_SPEED)) {
					Iterator<Entry<String, JsonNode>> portSpeedIter = switchChild.fields();
					while (portSpeedIter.hasNext()) {
						Entry<String, JsonNode> portSpeedEntry = portSpeedIter.next();
						int virtPortId = OFSwitchPort.virtualPortIdOf(portSpeedEntry.getKey());
						JsonNode portSpeedArray = portSpeedEntry.getValue();
						int portSpeed = portSpeedArray.get(0).asInt();
						this.portSpeedConfiguration.put(virtPortId, portSpeed);
					}
				}
				// Handle any other configuration.
				// if (switchFieldName.equalsIgnoreCase("other")) {
				//	do something.
				// }
			}
		}
	}

}
