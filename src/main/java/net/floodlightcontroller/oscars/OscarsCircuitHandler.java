package net.floodlightcontroller.oscars;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.floodlightcontroller.cli.ICliService;
import net.floodlightcontroller.configuration.IConfigurationListener;
import net.floodlightcontroller.configuration.IConfigurationService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.oscars.cli.ShowOscarsCmd;
import net.floodlightcontroller.oscars.cli.ShowOscarsReservationsCmd;
import net.floodlightcontroller.wanswitch.IWANSwitchService;
import net.es.oscars.client.Client;
import net.es.oscars.oscars.AAAFaultMessage;
import net.es.oscars.wsdlTypes.*;

import org.apache.axis2.AxisFault;

public class OscarsCircuitHandler implements IFloodlightModule, IConfigurationListener, IOscarsService {
	/** Logger to log OscarsCircuitHandler events.*/
	protected static Logger logger = LoggerFactory.getLogger(OscarsCircuitHandler.class);;
	/** */
	protected static final short DEFAULT_LAYER = 2;
	/** */
	protected static final short DEFAULT_VLAN = 0;
	/** */
	protected static final boolean DEFAULT_USE_KEY_STORE = true;
	/** */
	protected static final String DEFAULT_PATH_SETUP_MODE = "timer-automatic";
	/** The unique name of this configuration listener. */
	public static final String CONFIGURATOR_NAME = "OscarsCircuitHandler";
	
	/** Required Module: Floodlight CLI Service. */
	protected ICliService cliHander;
	/** Required Module: The configuration manager. */
    protected IConfigurationService configManager;
    /** Required Module: */
	private IWANSwitchService wanSwitchManager;
	
	/** */
	private Client oscarsClient;
	/** */
	private PathInfo pathInfo;
	/** */
    private Layer2Info layer2Info;
    /** */
    private ResCreateContent request;
    
    /** The URL of the OSCARS IDC. */
    private String url;
    /** The location if the repository (axis2.xml) directory. */
    private String repo;
    /** */
    private short layer;
    /** */
    private short vlan;
    /** */
    private String pathSetupMode;
    /** */
    private boolean useKeyStore;
    /** */
    private Map<String, CreateReply> reservations;
    
	///
  	/// IFloodlightModule
  	///

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IOscarsService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IOscarsService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(ICliService.class);
		l.add(IConfigurationService.class);
		l.add(IWANSwitchService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		this.configManager = context.getServiceImpl(IConfigurationService.class);
		this.cliHander = context.getServiceImpl(ICliService.class);
		this.wanSwitchManager = context.getServiceImpl(IWANSwitchService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// Set defaults.
		layer = DEFAULT_LAYER;
		vlan = DEFAULT_VLAN;
		pathSetupMode = DEFAULT_PATH_SETUP_MODE;
		useKeyStore = DEFAULT_USE_KEY_STORE;
		
		// Instantiate some default objects.
		oscarsClient = new Client();
		pathInfo = new PathInfo();
		layer2Info = new Layer2Info();
		request = new ResCreateContent();
		reservations = new HashMap<String, CreateReply>();
		
		// Register and read configuration.
		configManager.addListener(this);
		
		// Register our CLI commands.
		this.cliHander.registerCommand(new ShowOscarsCmd(context));
		this.cliHander.registerCommand(new ShowOscarsReservationsCmd(context));

	}
	
	///
	/// IOscarsService
	///
	
	@Override
	public String getURL() {
		return this.url;
	}
	
	@Override
	public void setURL(String url) {
		if (url != null) {
			this.url = url;
			this.setupOscarsClient();
		}
	}
	
	@Override
	public String getRepository() {
		return this.repo;
	}
	
	@Override
	public void setRepository(String repo) {
		if (repo != null) {
			this.repo = repo;
			this.setupOscarsClient();
		}
	}
	
	@Override
	public short getLayer() {
		return this.layer;
	}
	
	@Override
	public void setLayer(short layer) {
		this.layer = layer;
	}
	
	@Override
	public String getPathSetupMode() {
		return this.pathSetupMode;
	}
	
	@Override
	public void setPathSetupMode(String pathSetupMode) {
		if (pathSetupMode != null) {
			this.pathSetupMode = pathSetupMode;
		}
	}
	
	@Override
	public Client getClient() {
		return this.oscarsClient;
	}
	
	@Override
	public GetTopologyResponseContent getNetworkTopology() {
		GetTopologyResponseContent response = null;
		GetTopologyContent request = new GetTopologyContent();
        request.setTopologyType("all");
        try {
			response = this.oscarsClient.getNetworkTopology(request);
		} catch (RemoteException e) {
			logger.error("Requesting network topology failed due to remote error.");
			logger.error(e.getMessage());
		} catch (AAAFaultMessage e) {
			logger.error("Requesting network topology failed due to AAA error.");
			logger.error(e.getMessage());
		} catch (Exception e) {
			logger.error("Requesting network topology failed.");
			logger.error(e.getMessage());
		}
        
        return response;
	}
	
	@Override
	public CreateReply createReservation(int bandwith, long startTime, long endTime, String description) {
		CreateReply response = null;
		ResCreateContent request = new ResCreateContent();
		PathInfo pathInfo = new PathInfo();
		Layer2Info layer2Info = new Layer2Info();
		
		VlanTag srcVlan = new VlanTag();
        VlanTag dstVlan = new VlanTag();

        /* Initialize VLAN tags to default values */
        srcVlan.setTagged(true);
        srcVlan.setString("any");
        dstVlan.setTagged(true);
        dstVlan.setString("any");

        pathInfo.setPathSetupMode(this.pathSetupMode);
        pathInfo.setLayer2Info(layer2Info);
        request.setPathInfo(pathInfo);
        request.setBandwidth(bandwith);
        request.setStartTime(startTime);
        request.setEndTime(endTime);
        request.setDescription(description);
		
		try {
			response = this.oscarsClient.createReservation(request);
			logger.debug("Circuit reservation installed.");
		} catch (RemoteException e) {
			logger.error("Circuit reservation failed due to remote error.");
			logger.error(e.getMessage());
		} catch (AAAFaultMessage e) {
			logger.error("Circuit reservation failed due to AAA error.");
			logger.error(e.getMessage());
		} catch (Exception e) {
			logger.error("Circuit reservation failed.");
			logger.error(e.getMessage());
		}
		
		if (response != null) {
			this.reservations.put(response.getGlobalReservationId(), response);
		}
		
		return response;
	}
	
	@Override
	public String cancelReservation(GlobalReservationId grid) {
		/* The*/
		String response = null;
		
		try {
			response = oscarsClient.cancelReservation(grid);
			logger.debug("Circuit reservation canceled.");
		} catch (RemoteException e) {
			logger.error("Circuit cancelation for GRI '{}' failed due to remote error.", grid.getGri());
			logger.error(e.getMessage());
		} catch (AAAFaultMessage e) {
			logger.error("Circuit cancelation for GRI '{}' failed due to AAA error.", grid.getGri());
			logger.error(e.getMessage());
		} catch (Exception e) {
			logger.error("Circuit cancelation for GRI '{}' failed.", grid.getGri());
			logger.error(e.getMessage());
		}
		
		if (response != null) {
			this.reservations.remove(grid.getGri());
		}
		
		return response;
	}
	
	@Override
	public Set<CreateReply> getReservations() {
		if (this.reservations != null) {
			return new HashSet<CreateReply>(this.reservations.values());
		} else {
			return null;
		}
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
			// Write the OSCARS IDC URL configuration.
			rootNode.put("url", this.getURL());
			// Write the OSCARS repository configuration.
			rootNode.put("repo", this.getRepository());
			// Write the OSCARS circuit layer.
			rootNode.put("layer", this.getLayer());
			// Write the OSCARS path setup mode.
			rootNode.put("pathSetupMode", this.getPathSetupMode());
			// Write any other OscarsCircuitHandler configuration.
			// ... here.
		} catch (Exception e) {
			if (logger.isErrorEnabled()) {
				logger.error("Creating the " + this.getName() + " configuration for JSON failed. ", e);
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
				case "url":
					String url = child.asText().trim().toLowerCase();
					this.setURL(url);
				case "repo":
					String repo = child.asText().trim().toLowerCase();
					this.setRepository(repo);
				case "layer":
					short layer = (short) child.asInt();
					this.setLayer(layer);
				case "pathSetupMode":
					String pathSetupMode = child.asText().trim().toLowerCase();
					this.setPathSetupMode(pathSetupMode);
				default:
					if (logger.isWarnEnabled()) {
						logger.warn("Reading the " + this.getName() + " for {} configuration from JSON failed.", fieldname);
					}
			}
		}
	}
	
	///
	/// Local methods
	///
	
	private void setupOscarsClient() {
		if (this.url != null && this.repo != null) {
			try {
				this.oscarsClient.setUp(this.useKeyStore, this.url, this.repo);
				if (logger.isDebugEnabled()) {
					logger.debug("Setting up OSCARS client with URL {} and Repo {}.", this.url, this.repo);
				}
			} catch (AxisFault e) {
				logger.error("Setting up the OSCARS client failed");
				logger.error(e.getMessage());
			}
		}
	}

}
