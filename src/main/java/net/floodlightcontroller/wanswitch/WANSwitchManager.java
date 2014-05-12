package net.floodlightcontroller.wanswitch;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFactory;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.util.HexString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.floodlightcontroller.arp.IARPProxyService;
import net.floodlightcontroller.configuration.IConfigurationListener;
import net.floodlightcontroller.configuration.IConfigurationService;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery.LDUpdate;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.multipath.IMultipathService;
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.multipath.IStatisticsCollectorService;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.BSN;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.LLDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import net.floodlightcontroller.topology.ITopologyListener;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.wanswitch.web.WANSwitchWebRoutable;

/**
 * 
 * @author Michael Bredel (michael.bredel@caltech.edu)
 */
public class WANSwitchManager implements IFloodlightModule, IWANSwitchService, IConfigurationListener, ITopologyListener, IOFMessageListener, IOFSwitchListener {
	/** Our unique vendor tag for a switch DPID.  */
	public final static long DPID_VENDOR_TAG = ( ((long)"Caltech".hashCode()) << 48 );
	/** The unique name of this configuration listener. */
	public static final String CONFIGURATOR_NAME = "WANSwitchManager";
	/** The default port of the parent controller. */
	public static final short DEFAULT_CONTROLLER_PORT = 6633;
	/** The unique wan switch cookie id. */
    public static final int WANSWITCH_APP_ID = 3;
    /** Register the wan switch manager cookie. */
    static {
        AppCookie.registerApp(WANSWITCH_APP_ID, "WANSwitch");
    }
    /** An unique application cookie. */
    public static final long appCookie = AppCookie.makeCookie(WANSWITCH_APP_ID, 0);
    /** The (field-) name of configuration section that deals with virtual ports. */
	private static final String CONFIG_SECTION_WAN_PORTS = "wanports";
	
	/** The logger. */
    protected Logger log = LoggerFactory.getLogger(WANSwitchManager.class);
	/** Required Module: */
    protected IFloodlightProviderService floodlightProvider;
    /** Required Module: */
    protected IThreadPoolService threadPool;
    /** Required Module: */
    protected IConfigurationService configManager;
    /** Required Module: */
    protected ITopologyService topologyManager;
	/** Required Module: Floodlight Device Service. */
	protected IDeviceService deviceManager;
	/** Required Module: Floodlight Link Discovery Service. */
	protected ILinkDiscoveryService linkDiscoveryManager;
	/** The Floodlight module context. */
	protected FloodlightModuleContext context;
	/** Required Module: */
	protected IRestApiService restApi;
	/** Required Module: */
	protected IARPProxyService arpManager;

    /** This WAN switch's DPID. */
    protected long dpid;
    /** The IP address of the controller the WAN switch is connected to. */
    protected InetAddress inetAddress;
    /** The TCP port of the controller the WAN switch is connected to. */
    protected short port;
    /** The local OpenFlow management port, which is needed by every switch. */
    protected OFPhysicalPort localOpenFlowPort;
    /** A map of WAN switch Ports: WANSwitchPortId -> OFPhysicalPort. */
    protected Map<Integer, OFPhysicalPort> wanSwitchPorts;
    /** A map that maps WAN switch ports to switches: WANSwitchPortId -> SwitchId -> OFSwitchPort. */
    protected Map<Integer, NodePortTuple> wanSwitchPortsToSwitch;
    /** A map that maps switches to WAN switch ports: SwitchId -> OFSwitchPortId -> WANSwitchPortId. */
    protected Map<Long, Map<Integer, Integer>> switchToWanSwitchPorts;
    
    /** A map that maps the local flow ID to a OFFlow mod from the parent controller: FlowId -> OFFflowMod. */
    protected Map<Integer, OFFlowMod> flowIdToFlowMod; // TODO: create a flow cache to avoid memory leaks.
    /** A map that maps an OFMatch installed by the parent controller to Flow ids: OFMatch -> FLowId. */
    protected Map<OFMatch, Integer> matchToFlowId; // TODO: create a flow cache to avoid memory leaks.
    /** A Set of used flow ids used to calculate the next minimal flow id. */
	protected Set<Integer> flowIds; // TODO: create a flow cache to avoid memory leaks.
	
	/** Local configuration of virtual switch port as in the JSON file: SwitchId -> PortId. */
	protected Map<Long, Set<Integer>> switchWanPortConfiguration;

    protected ClientBootstrap bootstrap;
    protected volatile Channel channel;
    protected WANSwitchHandler wanSwitchHandler;
    protected WANSwitchChannelHandler wanSwitchChannelHandler;
    protected WANSwitchPipelineFactory wanSwitchPipelineFactory;
    
    /** The connection state of the connection to the parent controller: NONE, PENDING, CONNECTED. */
    protected volatile ConnectionState state;
    /** Task to periodically ensure that connections are active. */
    protected SingletonTask reconnectTask;
    /** Task to periodically send an echo request to the controller. */
    protected SingletonTask echoTask;
    /** Boolean to shut down the wan switch and avoid connection attempts to the parent controller. */
    protected volatile boolean shutDown = true;
    
    /**
     * Periodically ensure that all the node connections are alive.
     */
    protected class ConnectTask implements Runnable {
        @Override
        public void run() {
            try {
                if (!shutDown && inetAddress != null && port != 0)
                	// Try to connect to the controller.
                    if (!connect(inetAddress, port)) {
                    	// If connection fails back of.
                    	try {
            	            Thread.sleep(1000);
            	        } catch (InterruptedException e) {
            	        	// NO-OP.
            	        }
                    }
            } catch (Exception e) {
                log.error("Error in reconnect task", e);
            }
            if (!shutDown) {
                reconnectTask.reschedule(1000, TimeUnit.MILLISECONDS);
            }
        }
    }
    
    /**
     * Periodically sends a echo request message to the controller.
     */
    protected class EchoTask implements Runnable {
    	@Override
        public void run() {
    		try {
    			if (isConnected() && isActive()) {
    				wanSwitchHandler.sendOFMessage(OFType.ECHO_REQUEST);
    			}
    		} catch (Exception e) {
                log.error("Error in hello task", e);
            }
    		if (!shutDown) {
                echoTask.reschedule(15000, TimeUnit.MILLISECONDS);
            }
    	}
    }

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		//return (type.equals(OFType.PACKET_IN) && name.equals("linkdiscovery"));
		//return false;
		return name.equals("linkdiscovery");
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IWANSwitchService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IWANSwitchService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IThreadPoolService.class);
        l.add(IConfigurationService.class);
        l.add(ITopologyService.class);
        l.add(IDeviceService.class);
        l.add(ILinkDiscoveryService.class);
        l.add(IMultipathService.class);
        l.add(IPathFinderService.class);
        l.add(IFlowCacheService.class);
        l.add(IARPProxyService.class);
        l.add(IStatisticsCollectorService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		this.context = context;
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		threadPool = context.getServiceImpl(IThreadPoolService.class);
		configManager = context.getServiceImpl(IConfigurationService.class);
		topologyManager = context.getServiceImpl(ITopologyService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		linkDiscoveryManager = context.getServiceImpl(ILinkDiscoveryService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		arpManager = context.getServiceImpl(IARPProxyService.class);
		
		wanSwitchPorts = new ConcurrentHashMap<Integer, OFPhysicalPort>();
		wanSwitchPortsToSwitch = new ConcurrentHashMap<Integer, NodePortTuple>();
		switchToWanSwitchPorts = new ConcurrentHashMap<Long, Map<Integer, Integer>>();
		
		switchWanPortConfiguration = new ConcurrentHashMap<Long, Set<Integer>>();
		
		flowIdToFlowMod = new ConcurrentHashMap<Integer, OFFlowMod>();
		matchToFlowId = new ConcurrentHashMap<OFMatch, Integer>();
		flowIds = new HashSet<Integer>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		this.configManager.addListener(this);
		//this.topologyManager.addListener(this); // Add for automatic WAN port detection.
		this.floodlightProvider.addOFSwitchListener(this);
		this.floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		this.floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
		this.wanSwitchHandler = new WANSwitchHandler(this, this.context);
		this.wanSwitchChannelHandler = new WANSwitchChannelHandler(this.wanSwitchHandler);
		this.wanSwitchPipelineFactory = new WANSwitchPipelineFactory(this.wanSwitchChannelHandler);
		this.restApi.addRestletRoutable(new WANSwitchWebRoutable());
		
		if (this.dpid == 0)
			this.dpid = this.calculateDPID();
		if (this.port == 0)
			this.port = DEFAULT_CONTROLLER_PORT;
		this.state = ConnectionState.NONE;
		
        // Try to create a local OpenFlow port.
        if (this.dpid != 0)
        	this.initOpenFlowPort();
		
		Executor bossPool = Executors.newCachedThreadPool();
        Executor workerPool = Executors.newCachedThreadPool();
        ChannelFactory factory = new NioClientSocketChannelFactory(bossPool, workerPool);
        this.bootstrap = new ClientBootstrap(factory);
        this.bootstrap.setOption("child.keepAlive", true);
        this.bootstrap.setOption("child.tcpNoDelay", true);
        this.bootstrap.setPipelineFactory(wanSwitchPipelineFactory);

        ScheduledExecutorService ses = threadPool.getScheduledExecutor();
        reconnectTask = new SingletonTask(ses, new ConnectTask());
        reconnectTask.reschedule(0, TimeUnit.SECONDS);
        echoTask = new SingletonTask(ses, new EchoTask());
        echoTask.reschedule(0, TimeUnit.SECONDS);
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
			// Write the DPID.
			if (this.dpid != 0)
				rootNode.put("dpid", HexString.toHexString(this.dpid));
			// Write the controller IP address.
			if (this.inetAddress != null)
				rootNode.put("controller", this.inetAddress.getHostAddress());
			// Write shutdown state.
			if (!this.shutDown)
				rootNode.put("shutdown", this.shutDown);
			// Write the controller port.
			if (this.port != 0 && this.port != DEFAULT_CONTROLLER_PORT)
				rootNode.put("port", this.port);
			// Write the WAN switch ports.
			if (this.wanSwitchPorts != null && !this.wanSwitchPorts.isEmpty()) {
				ObjectNode portNode = mapper.createObjectNode();
				rootNode.put(CONFIG_SECTION_WAN_PORTS, portNode);
				for (long switchId : this.switchToWanSwitchPorts.keySet()) {
					ArrayNode wanIdNode = portNode.putArray(HexString.toHexString(switchId));
					for (int portId : this.switchToWanSwitchPorts.get(switchId).keySet()) {
						wanIdNode.add(OFSwitchPort.stringOf(portId));
					}
				}
			}
			// Write any other WAN switch configuration.
			// ... here.
		} catch (Exception e) {
			if (log.isErrorEnabled()) {
				log.error("Creating the WAN switch configuration for JSON failed. ", e);
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
				case "dpid":
					this.dpid = HexString.toLong(child.asText());
					break;
				case "controller":
					this.inetAddress = parseInetAddress(child.asText());
					break;
				case "port":
					this.port = (short) child.intValue();
					break;
				case "shutdown":
					this.shutDown = child.asBoolean();
					break;
				case CONFIG_SECTION_WAN_PORTS:
					Iterator<Entry<String, JsonNode>> wanPortsIter = child.fields();
					while (wanPortsIter.hasNext()) {
						Entry<String, JsonNode> wanPortsEntry = wanPortsIter.next();	
						long switchId = HexString.toLong(wanPortsEntry.getKey());
						JsonNode wanPortIdArray = wanPortsEntry.getValue();
						for (int i=0; i<wanPortIdArray.size(); i++) {
							if (!this.switchWanPortConfiguration.containsKey(switchId))
								this.switchWanPortConfiguration.put(switchId, new HashSet<Integer>());
							int portId = OFSwitchPort.virtualPortIdOf(wanPortIdArray.get(i).asText());
							this.switchWanPortConfiguration.get(switchId).add(portId);
						}
					}
					break;
				default:
					if (log.isWarnEnabled()) {
						log.warn("Reading the WAN switch configuration for {} from JSON failed.", fieldname);
					}
			}	
		}
		
		// Perform the actual update.
		this.updateConfig();
	}
	
	///
	/// IOFSwitchListener
	///

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
	
	///
	/// IWANSwitchService
	///
	
	@Override
	public void addListener(IWANSwitchListener listener) {
		// TODO Auto-generated method stub
	}
	
	@Override
	public void removeListener(IWANSwitchListener listener) {
		// TODO Auto-generated method stub
	}

	@Override
	public void setController(String hostname) {
		this.inetAddress = this.parseInetAddress(hostname);
		this.initOpenFlowPort();
	}
	
	@Override
	public InetAddress getController() {
		return this.inetAddress;
	}

	@Override
	public void setControllerPort(short port) {
		this.port = (port == 0) ? DEFAULT_CONTROLLER_PORT : (short) port;
	}
	
	@Override
	public short getControllerPort() {
		return this.port;
	}
	
	@Override
	public long getDPID() {
		return this.dpid;
	}
	
	@Override
	public NodePortTuple getPort(int wanSwitchportId) {
		return this.wanSwitchPortsToSwitch.get(wanSwitchportId);
	}
	
	@Override
	public Collection<Integer> getPorts() {
		return this.wanSwitchPortsToSwitch.keySet();
	}
	
	@Override
	public OFPhysicalPort getOpenFlowPort() {
		return this.localOpenFlowPort;
	}
	
	@Override
	public void shutdown(boolean shutdown) {
		this.shutDown = shutdown;
		// Restart the connect task.
		if ( (!shutdown && this.channel == null) || (!shutdown && !this.channel.isConnected()) )
			reconnectTask.reschedule(1000, TimeUnit.MILLISECONDS);
		// Disconnect
		if (shutdown && this.channel != null && this.channel.isConnected()) {
			this.channel.disconnect();
			this.state = ConnectionState.NONE;
		}
	}

	@Override
	public boolean isActive() {
		return (!this.shutDown && this.inetAddress != null);
	}
	
	@Override
	public boolean isConnected() {
		return (this.state == ConnectionState.CONNECTED);
	}
	
	@Override
	public void sendOFMessage(OFMessage ofm) {
		if (this.channel != null) {
			if (log.isDebugEnabled()) {
				log.debug("Sending OFMessage to parent controller: " + ofm);
			}
			this.channel.write(Collections.singletonList(ofm));
		}
	}
	
	@Override
	public void addFlowMod(int flowId, OFFlowMod flowMod) {
		// Clone the flow mod - since flowMod.clone() does not work.
		OFFlowMod flowModClone = new OFFlowMod();
		flowModClone.setBufferId(flowMod.getBufferId());
		flowModClone.setCommand(flowMod.getCommand());
		flowModClone.setCookie(flowMod.getCookie());
		flowModClone.setFlags(flowMod.getFlags());
		flowModClone.setHardTimeout(flowMod.getHardTimeout());
		flowModClone.setIdleTimeout(flowMod.getIdleTimeout());
		flowModClone.setLength(flowMod.getLength());
		flowModClone.setLengthU(flowMod.getLengthU());
		flowModClone.setMatch(flowMod.getMatch().clone());
		flowModClone.setOutPort(flowMod.getOutPort());
		flowModClone.setPriority(flowMod.getPriority());
		flowModClone.setType(flowMod.getType());
		flowModClone.setVersion(flowMod.getVersion());
		flowModClone.setXid(flowMod.getXid());
		List<OFAction> actions = new LinkedList<OFAction>();
		for (OFAction action : flowMod.getActions()) {
			actions.add(action);
		}
		flowModClone.setActions(actions);
		
		this.flowIdToFlowMod.put(flowId, flowModClone);
		this.matchToFlowId.put(FlowCacheObj.wildcardMatch(flowModClone.getMatch()), flowId);
	}
	
	@Override
	public OFFlowMod delFlowMod(int flowId) {
		this.flowIds.remove((Integer) flowId);
		OFFlowMod ofm = this.flowIdToFlowMod.remove(flowId);
		if (ofm != null)
			this.matchToFlowId.remove(FlowCacheObj.wildcardMatch(ofm.getMatch()));
		return ofm;
	}
	
	@Override
	public int getFlowId(OFMatch match) {
		FlowCacheObj.wildcardMatch(match);
		if (this.matchToFlowId.containsKey(match)) {
			return this.matchToFlowId.get(match);
		} else {
			return 0;
		}
	}
	
	@Override
	public synchronized int getNextFlowId() {
		/* The new flow id. */
		int newFlowId = 1;
		
		if (this.flowIds.isEmpty()) {
			this.flowIds.add(newFlowId);
			return newFlowId;
		}

		int maxFlowId = Collections.max(this.flowIds);
		
		if (this.flowIds.size() == maxFlowId) {
			newFlowId = (maxFlowId+1);
			this.flowIds.add(newFlowId);
			return newFlowId;
		}
		
		for (int i = 1; i <= maxFlowId; i++) {
			if (!flowIds.contains(i)) {
				newFlowId = i;
				this.flowIds.add(newFlowId);
				break;
			}
		}
		
		return newFlowId;
	}

	///
	/// ITopologyListener
	///
	
	@Override
	public void topologyChanged(List<LDUpdate> linkUpdates) {
		for (LDUpdate ldu : linkUpdates) {
			long switchId = ldu.getSrc();
			int portId = ldu.getSrcPort();
			short vlanId = OFSwitchPort.vlanIdOf(ldu.getSrcPort());
			
			if (log.isDebugEnabled()) {
				int phyPortId = OFSwitchPort.physicalPortIdOf(ldu.getSrcPort());
				log.debug(ldu.getOperation() + ": " + HexString.toHexString(switchId) + " - " + phyPortId + " (" + vlanId + ")");
			}
			
			if (ldu.getOperation().equals(ILinkDiscovery.UpdateOperation.SWITCH_UPDATED)) {
				// Add new ports of that switch.
			}
			if (ldu.getOperation().equals(ILinkDiscovery.UpdateOperation.SWITCH_REMOVED)) {
				// Remove all ports of that switch.
				IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(switchId);
				if (iofSwitch != null) {
					for (OFSwitchPort ofsPort : iofSwitch.getPorts()) {
						this.removeWanSwitchPort(switchId, ofsPort.getPortNumber());
					}
				}
			}
//			if (ldu.getOperation().equals(ILinkDiscovery.UpdateOperation.PORT_UP)) {
//				// A new (virtual) port is detected.
//				if (this.topologyManager.isAttachmentPointPort(switchId, portId) && portId > 0) {
//					this.addWanSwitchPort(switchId, portId);
//				}
//			}
			if (ldu.getOperation().equals(ILinkDiscovery.UpdateOperation.PORT_DOWN)) {
				// A (virtual) port went down.
				this.removeWanSwitchPort(switchId, portId);
			}
			if (ldu.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_UPDATED)) {
				long dstSwitchId = ldu.getDst();
				int dstPortId = ldu.getDstPort();
				
//				// This is a dirty hack to add WAN switch ports even thought, the controller receives its own BDDP message
//				if (ldu.getType().equals(LinkType.MULTIHOP_LINK)) {
//					this.addWanSwitchPort(switchId, portId);
//					this.addWanSwitchPort(dstSwitchId, dstPortId);
//					
//					continue;
//				}
				
				// A new link is detected. Thus, we may have lost some attachment point ports.
				if (!this.switchWanPortConfiguration.containsKey(switchId)) {
					this.removeWanSwitchPort(switchId, portId);
					this.removeWanSwitchPort(dstSwitchId, dstPortId);
				} else if (!this.switchWanPortConfiguration.get(switchId).contains(portId)) {
					this.removeWanSwitchPort(switchId, portId);
					this.removeWanSwitchPort(dstSwitchId, dstPortId);
				}
				
			}
			if (ldu.getOperation().equals(ILinkDiscovery.UpdateOperation.LINK_REMOVED)) {
				// A link went down. Thus, we may have new attachment point ports.
				long dstSwitchId = ldu.getDst();
				int dstPortId = ldu.getDstPort();
				
				if (this.floodlightProvider.getSwitch(switchId) != null) {
					OFSwitchPort srcPort = this.floodlightProvider.getSwitch(switchId).getPort(portId);
					if (srcPort != null && srcPort.isEnabled() && !srcPort.isLinkDown()) {
						this.addWanSwitchPort(switchId, portId);
					}
				}
				
				if (this.floodlightProvider.getSwitch(dstSwitchId) != null) {
					OFSwitchPort dstPort = this.floodlightProvider.getSwitch(dstSwitchId).getPort(dstPortId);
					if (dstPort != null && dstPort.isEnabled() && !dstPort.isLinkDown()) {
						this.addWanSwitchPort(dstSwitchId, dstPortId);
					}
				}
			}
		}
	}

	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		if (!this.isConnected() || !this.isActive()) {
			return Command.CONTINUE;
		}
		
		switch (msg.getType()) {
			case PACKET_IN: // From a (physical) switch connected to this controller.
				IRoutingDecision decision = null;
                if (cntx != null) {
                    decision = IRoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION);
                }
				return this.processPacketInMessage(sw, (OFPacketIn) msg, decision, cntx);
			case FLOW_REMOVED:
                return this.processFlowRemovedMessage(sw, (OFFlowRemoved) msg, cntx);
			default:
				break;
		}
		return Command.CONTINUE;
	}
	
	///
	/// Local methods
	///
	
	/**
	 * Handles a packet in message received from a local switch.
	 * 
	 * @param sw
	 * @param piMsg
	 * @param decision
	 * @param cntx
	 * @return
	 */
	protected Command processPacketInMessage(IOFSwitch sw, OFPacketIn piMsg, IRoutingDecision decision, FloodlightContext cntx) {
		/* Get the Ethernet frame representation of the PacketIn message. */
		Ethernet ethPacket = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		/* The switch ID of the incoming switch. */
		long switchId = sw.getId();
		/* The port ID where the message was received. */
		short inPortId = piMsg.getInPort();
		/* The (normalized) VLAN ID if any. */
		short vlanId = (ethPacket.getVlanID() > 0) ? ethPacket.getVlanID() : 0;
		/* The port ID of the virtual port the packet was received. */
		int virtPortId = OFSwitchPort.virtualPortIdOf(inPortId, vlanId);
		// Check if the incoming port is a WAN switch port.
		if (this.switchToWanSwitchPorts.containsKey(switchId)) {
			if (!this.switchToWanSwitchPorts.get(switchId).containsKey(virtPortId)) {				
				return Command.CONTINUE;
			}
		} else {
			return Command.CONTINUE;
		}

		// If this is a BSN message ...
		if (ethPacket.getEtherType() == Ethernet.TYPE_BSN) {
			if (ethPacket.getPayload() instanceof BSN) {
				BSN bsn = (BSN) ethPacket.getPayload();
				if (this.linkDiscoveryManager.isOwnBSN(bsn)) {
					// If this is our own BSN, continue.
					return Command.CONTINUE;
				} else {
					if (log.isDebugEnabled()) {
						log.debug("Different BSN message received.");
					}		
					// Send a copy (clone) packet in message to controller.
					int wanPortId = this.switchToWanSwitchPorts.get(switchId).get(virtPortId);
					this.sendPacketInMessage(piMsg, ethPacket, (short) wanPortId, Ethernet.VLAN_UNTAGGED);
					// TODO: Modify the the TLV to myTLV, make a routing decision and continue;
	   				return Command.STOP;
				}
			}
		}
		
		// If this is an LLDP message ...
		if (ethPacket.getEtherType() == Ethernet.TYPE_LLDP) {
			if (ethPacket.getPayload() instanceof LLDP) {
				LLDP lldp = (LLDP) ethPacket.getPayload();
				if (this.linkDiscoveryManager.isOwnLLDP(lldp)) {
					// If this is our own LLDP, continue.
					return Command.CONTINUE;
				} else {
					if (log.isDebugEnabled()) {
						log.debug("Different LLDP message received.");
					}					
					// Send a copy (clone) packet in message to controller.
					int wanPortId = this.switchToWanSwitchPorts.get(switchId).get(virtPortId);
					this.sendPacketInMessage(piMsg, ethPacket, (short) wanPortId, Ethernet.VLAN_UNTAGGED);
					// TODO: Modify the the TLV to myTLV, make a routing decision and continue;
	   				return Command.STOP;
				}
			}
		}
		
		// If this an ARP message ...
		if (ethPacket.getEtherType() == Ethernet.TYPE_ARP) {
			if (ethPacket.getPayload() instanceof ARP) {
				ARP arp = (ARP) ethPacket.getPayload();
				
				if (log.isDebugEnabled()) {
					log.debug("Received ARP " + ARP.OpCode2String(arp.getOpCode()) + " message at local switch: " + HexString.toHexString(switchId) + " - " + OFSwitchPort.stringOf(inPortId) + " from " + IPv4.fromIPv4Address(IPv4.toIPv4Address(arp.getSenderProtocolAddress())) + " for target: " + IPv4.fromIPv4Address(IPv4.toIPv4Address(arp.getTargetProtocolAddress())));
				}

				if (arp.getOpCode() != ARP.OP_REPLY) {
					// Check if the WAN switch controller knows the destination.
					int targetIPAddress = IPv4.toIPv4Address(arp.getTargetProtocolAddress());
					@SuppressWarnings("unchecked")
					Iterator<Device> diter = (Iterator<Device>) deviceManager.queryDevices(null, null, (int) targetIPAddress, null, null);
					//if (diter.hasNext()) {
					if (diter.hasNext() && diter.next().getAttachmentPoints().length > 0) {
						System.out.println("TEST: handle ARP " + arp.getOpCode() + " from " + sw.getStringId() + " - " + OFSwitchPort.stringOf(inPortId) + " locally.");
						// If we know the destination and its attachment point.
						return Command.CONTINUE;
					}
	   			}
				
				// Send a copy (clone) packet in message to controller.
				System.out.println("TEST: send ARP " + arp.getOpCode() + " from " + sw.getStringId() + " - " + OFSwitchPort.stringOf(inPortId) + " to parent. " + arp);
				int wanPortId = this.switchToWanSwitchPorts.get(switchId).get(virtPortId);
				this.sendPacketInMessage(piMsg, ethPacket, (short) wanPortId, Ethernet.VLAN_UNTAGGED);
				// Make a routing decision for the ARP message.
				decision = new RoutingDecision(switchId, virtPortId, IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE), IRoutingDecision.RoutingAction.NONE);
				decision.addToContext(cntx);
				// Allow further processing, e.g. to learn the device.
				return Command.CONTINUE;
			}
		}
		
		// If this is an IP message ...
		if (ethPacket.getEtherType() == Ethernet.TYPE_IPv4 ) {
			if (ethPacket.getPayload() instanceof IPv4) {
				IPv4 ipPacket = (IPv4) ethPacket.getPayload();
				int dstIPAddress = ipPacket.getDestinationAddress();
				
				if (log.isDebugEnabled()) {
					log.debug("Received IP message at local switch: " + HexString.toHexString(switchId) + " - " + OFSwitchPort.stringOf(inPortId) + " from " + IPv4.fromIPv4Address(ipPacket.getSourceAddress()) + " to destination: " + IPv4.fromIPv4Address(ipPacket.getDestinationAddress()));
				}
				
				// Check if the WAN switch controller knows the destination.
				@SuppressWarnings("unchecked")
				Iterator<Device> diter = (Iterator<Device>) deviceManager.queryDevices(null, null, dstIPAddress, null, null);
				if (diter.hasNext() && diter.next().getAttachmentPoints().length > 0) {
					System.out.println("TEST: handle IP packet locally.");
					// If we know the destination and its attachment point continue.
	   				return Command.CONTINUE;
	   			} else {
	   				System.out.println("TEST: send IP packet from " + sw.getStringId() + " - " + OFSwitchPort.stringOf(inPortId) + " to parent. " + ipPacket);
	   				// Send a copy (clone) packet in message to controller.
	   				int wanPortId = this.switchToWanSwitchPorts.get(switchId).get(virtPortId);
	   				this.sendPacketInMessage(piMsg, ethPacket, (short) wanPortId, Ethernet.VLAN_UNTAGGED);
	   				// Make a routing decision for the ARP message.
					decision = new RoutingDecision(switchId, virtPortId, IDeviceService.fcStore.get(cntx, IDeviceService.CONTEXT_SRC_DEVICE), IRoutingDecision.RoutingAction.NONE);
					decision.addToContext(cntx);
					// Allow further processing, e.g. to learn the device.
					return Command.CONTINUE;
	   			}
			}
		}
		
		// If this is broadcast or a multicast message but not an ARP, LLDP, or BSN message.
		if ((ethPacket.isBroadcast() || ethPacket.isMulticast()) && ethPacket.getEtherType() != Ethernet.TYPE_ARP && ethPacket.getEtherType() != Ethernet.TYPE_LLDP && ethPacket.getEtherType() != Ethernet.TYPE_BSN) {
			//System.out.println("TEST: BROADCAST/MULTICAST: " + ethPacket);
			
			return Command.STOP;
			// Send to parent controller.
			// TODO: Broadcasts should be handles by the highest controller.
			// TODO: Multicasts (like LLDPs) should be handled specially.
		}
		
		// Send message to parent controller and consume message. This should not happen.
		try {
			log.error("THIS SHOULD NOT HAPPEN");
			int wanPortId = this.switchToWanSwitchPorts.get(switchId).get(virtPortId);
			piMsg.setInPort((short) wanPortId);
			this.sendOFMessage(piMsg);
		} catch (Exception e) {
			// Before it happens, because we receive packets also on the non-WAN switch ports. e.g. due to intermediate equipment.
		}
		// Consume the packet.
		return Command.STOP;
	}
	
	/**
	 * Sends a OFPacketIn message to the controller. It clones the received OFPacketIn message
	 * and modifies the InPort to the corresponding WAN port and the VLAN tag to the corresponding
	 * WAN VLAN tag.
	 * 
	 * @param piMsg The original packet in message received by the local switch.
	 * @param ethPacket The original Ethernet packet data received by the local switch.
	 * @param wanPortId The WAN port that corresponds to the local switch+port combination.
	 * @param wanVlanId The WAN VLAN Id (if any).
	 */
	protected void sendPacketInMessage(OFPacketIn piMsg, Ethernet ethPacket, short wanPortId, short wanVlanId) {
		/* A clone of the current packet in message. */
		OFPacketIn piMsgClone;
		/* Get the Ethernet frame representation of the PacketIn message. */
		Ethernet ethPacketClone = (Ethernet) ethPacket.clone();
		/* The (normalized) VLAN ID if any. */
		short vlanId = (ethPacket.getVlanID() > 0) ? ethPacket.getVlanID() : 0;
		
		// Clone the original packet in message.
		try {
			piMsgClone = piMsg.clone();
		} catch (CloneNotSupportedException e) {
			e.printStackTrace();
			return;
		}
		
		// Set the WAN port id.
		piMsgClone.setInPort(wanPortId);
		
		// Modify or strip a VLAN if any.
		if (vlanId != 0) {
			if (wanVlanId == 0 || wanVlanId == Ethernet.VLAN_UNTAGGED) {
				ethPacketClone.setVlanID(Ethernet.VLAN_UNTAGGED);
			} else {
				ethPacketClone.setVlanID(wanVlanId);
			}
			piMsgClone.setPacketData(ethPacketClone.serialize());
		}
		
		// Sent the message to the parent controller.
		this.sendOFMessage(piMsgClone);
	}
	
	/**
     * Handles a flow removed message received from a local switch.
     * 
     * @param sw The switch that sent the flow removed message.
     * @param flowRemovedMsg The flow removed message.
     * @param cntx The Floodlight context.
     * @return Whether to continue processing this message or stop.
     */
    protected Command processFlowRemovedMessage(IOFSwitch sw, OFFlowRemoved flowRemovedMsg, FloodlightContext cntx) {
    	/* The cookie in the flow removed message. */
    	long cookie = flowRemovedMsg.getCookie();
    	/* The application id stored in the cookie. */
    	int cookieApp = AppCookie.extractApp(cookie);
    	/* The flow id in the cookie. */
    	int cookieFlowId = AppCookie.extractUser(cookie);
    	
    	if (cookieApp != WANSWITCH_APP_ID) {
    		return Command.CONTINUE;
    	}
    	
    	// Send FlowRemoved message to parent controller.
    	OFFlowMod flowMod = this.delFlowMod(cookieFlowId);
    	if (flowMod != null) {
    		// TODO: Move to WANSwitchHandler?
    		OFFlowRemoved flowRemoved = (OFFlowRemoved) BasicFactory.getInstance().getMessage(OFType.FLOW_REMOVED);
    			flowRemoved.setMatch(flowMod.getMatch());
    			flowRemoved.setCookie(flowMod.getCookie());
    			flowRemoved.setPriority(flowMod.getPriority());
    			flowRemoved.setIdleTimeout(flowMod.getIdleTimeout());
    			flowRemoved.setReason(flowRemovedMsg.getReason());
    			flowRemoved.setDurationSeconds(flowRemovedMsg.getDurationSeconds());
    			flowRemoved.setDurationNanoseconds(flowRemovedMsg.getDurationNanoseconds());
    			flowRemoved.setByteCount(flowRemovedMsg.getByteCount());
    			flowRemoved.setPacketCount(flowRemovedMsg.getPacketCount());
    		// Send flow removed message to parent controller.
    		this.sendOFMessage(flowRemoved);
    	}
    	
    	// Make sure to remove all corresponding local flows.
    	
    	return Command.STOP; // CONTINUE?
    }

	/**
	 * Connects the WAN switch to a parent controller.
	 */
	protected boolean connect(InetAddress inetAddress, short port) {
	    if (channel == null || !channel.isConnected()) {
	        ChannelFuture future = bootstrap.connect(new InetSocketAddress(inetAddress, port));
	        
	        state = ConnectionState.PENDING;
	        
	        future.awaitUninterruptibly();
	        if (!future.isSuccess()) {
	        	if (log.isInfoEnabled()) {
	        		log.info("Could not connect to " + inetAddress + ":" + port);
	        	}
	            return false;
	        }
	        channel = future.getChannel();
	    }
	    while (channel != null && channel.isConnected()) {
	        try {
	            Thread.sleep(10);
	        } catch (InterruptedException e) {
	        	// NO-OP.
	        }
	    }
	    if (channel == null || !channel.isConnected()) {
	    	if (log.isWarnEnabled()) {
	    		log.warn("Timed out connecting to {}:{}", inetAddress, port);
	    	}
	        return false;
	    }
	    if (log.isDebugEnabled()) {
	    	log.debug("Connected to {}:{}", inetAddress, port);
	    }
	    
	    return true;
	}
	
	/**
	 * Calculates a switch DPID based on a given 2-Byte Vendor tag and
	 * the nodes host MAC address. If no MAC address is found, e.g. the
	 * controller runs in a virtual machine, a random MAC address is generated.
	 * Alternatively, we can set up a fixed DPID in the configuration.
     * 
     * @return <b>long</b> A new switch DPID of the WAN switch.
     */
    private long calculateDPID() {
    	/* Start with our vendor tag. */
    	long dpid = DPID_VENDOR_TAG;
    	/* */
    	byte[] mac = null;
    	
    	try {
    		InetAddress ip = InetAddress.getLocalHost();
    		if (NetworkInterface.getByInetAddress(ip) != null)
    			mac = NetworkInterface.getByInetAddress(ip).getHardwareAddress();
     
    		// If MAC is null, e.g. because we are using a virtual machine, try to find something else.
    		if (mac == null) {
    			NetworkInterface network = null;
    			for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e.hasMoreElements();) {
    				network = e.nextElement();
    				mac = network.getHardwareAddress();
    			
    				if (mac != null) {
    					break;
    				}
    			}
    		}
    		// IF MAC is still null, use a random address;
    		if (mac == null) {
    			// Generate a random 48 bit MAC address.
    			dpid += ((long)(Math.random() * 4294967295L) << 16) + (Math.random() * 65535);
    		} else {
    			// Add our MAC address to the DPID.
    			dpid += Ethernet.toLong(mac);
    		}
    	} catch (UnknownHostException e) {
    		//e.printStackTrace();
    	} catch (SocketException e){
    		//e.printStackTrace();
    	}

    	return dpid;
    }

    /**
     * Parses an IP address. Thus i takes a String representation of an
     * IPv4 address, e.g. 192.168.0.1, ore a resolvable host name and 
     * creates a Java InetAddress object.
     * 
     * @param address A String representing an IP Address or host name.
     * @return <b>InetAddress</b> An IP address for the given host name.
     */
    private InetAddress parseInetAddress(String address) {
    	InetAddress inetAddress;
    	try {
    		inetAddress = InetAddress.getByName(address);
		} catch (UnknownHostException e) {
			if (log.isWarnEnabled()) {
				log.warn("Could not parse the controllers IP address.", e);
			}
			return null;
		}
    	return inetAddress;
    }
    
    /**
     * Initializes the local OpenFlow port. The OpenFlow port is used for ...?
     */
    private void initOpenFlowPort() {
    	/* 0000 0000 0000 0000 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 1111 */
    	long mask = 281474976710655L; 
    	/* Use the DPID and the mask to get least 48 bit for the MAC address. */
    	byte[] mac = Ethernet.toByteArray(this.dpid & mask);
    	
		this.localOpenFlowPort = new OFPhysicalPort();
		this.localOpenFlowPort.setHardwareAddress(mac);
		this.localOpenFlowPort.setPortNumber((short) -2);
		this.localOpenFlowPort.setName("wan-mgmt");
		this.localOpenFlowPort.setConfig(0);
		this.localOpenFlowPort.setState(0);
		this.localOpenFlowPort.setSupportedFeatures(703);	// 10Mbps, 100Mbps, 1Gbps, Cooper, AutoNeg
		this.localOpenFlowPort.setAdvertisedFeatures(0);	// None
		this.localOpenFlowPort.setCurrentFeatures(672);		// 1Gbps, Cooper, AutoNeg
		this.localOpenFlowPort.setPeerFeatures(0);			// None
    }

	/**
	 * Adds a WAN switch port. The WAN switch port translates to a local node 
	 * port tuple, i.e. a local (virtual) port on a local switch controlled
	 * by this controller.
	 * 
	 * @param switchId The switch ID of the local switch that hosts the WAN switch port.
	 * @param portId The port ID of the local switch port.
	 * @return <b>int</b> The port number of the added WAN switch port.
	 */
	public synchronized int addWanSwitchPort(long switchId, int portId) {
		// Check if the switch port is already there.
		if (this.switchToWanSwitchPorts.containsKey(switchId))
			if (this.switchToWanSwitchPorts.get(switchId).containsKey(portId))
				return -1;
		
		// Add the switch+port combination to Suppressed LLDP in order to avoid link discovery by the local controller.
		this.linkDiscoveryManager.AddToSuppressLLDPs(switchId, portId);
		// Add the switch+port combination to Suppressed APs in order to avoid host-learning by the local controller.
		this.deviceManager.addSuppressAPs(switchId, portId);
		// Add the switch+port combination to Suppressed ARPs in order to avoid sending APR requests by the local controller.
		this.arpManager.addToSuppressARPs(switchId, portId);
		
		// Generate a new ID for the WAN switch port.
		int wanSwitchPortId = getNextWanSwitchPortId();
		
		if (log.isDebugEnabled()) {
			int phyPortId = OFSwitchPort.physicalPortIdOf(portId);
			short vlanId = OFSwitchPort.vlanIdOf(portId);
			log.debug("Add WAN switch port (" + wanSwitchPortId + ") for: " + HexString.toHexString(switchId) + " - " + phyPortId + " (" + vlanId + ")");
		}
		
		this.wanSwitchPortsToSwitch.put(wanSwitchPortId, new NodePortTuple(switchId, portId));
		if (!this.switchToWanSwitchPorts.containsKey(switchId)) {
			this.switchToWanSwitchPorts.put(switchId, new HashMap<Integer, Integer>());
		}
		this.switchToWanSwitchPorts.get(switchId).put(portId, wanSwitchPortId);
		
		// Send a WAN switch port add message to the controller.
		OFPhysicalPort oldOfpPort = this.floodlightProvider.getSwitch(switchId).getPort(portId).getOFPhysicalPort();
		OFPhysicalPort newOfpPort = new OFPhysicalPort();
			newOfpPort.setHardwareAddress(oldOfpPort.getHardwareAddress());
			newOfpPort.setPortNumber((short) wanSwitchPortId);
			newOfpPort.setName("wan-" + wanSwitchPortId);
			newOfpPort.setConfig(oldOfpPort.getConfig());
			newOfpPort.setState(oldOfpPort.getState());
			newOfpPort.setSupportedFeatures(oldOfpPort.getSupportedFeatures());
			newOfpPort.setAdvertisedFeatures(oldOfpPort.getAdvertisedFeatures());
			newOfpPort.setCurrentFeatures(oldOfpPort.getCurrentFeatures());
			newOfpPort.setPeerFeatures(oldOfpPort.getPeerFeatures());
			
		this.wanSwitchPorts.put(wanSwitchPortId, newOfpPort);
				
		OFPortStatus portStatus = (OFPortStatus) BasicFactory.getInstance().getMessage(OFType.PORT_STATUS);
		portStatus.setDesc(newOfpPort);
		portStatus.setReason(OFPortReason.OFPPR_ADD.getReasonCode());
		this.sendOFMessage(portStatus);
		
		return wanSwitchPortId;
	}
	
	/**
	 * Removes a WAN switch port. To this end, it checks if the given port
	 * is an active WAN switch port. If true, it removes the port from the
	 * local WAN switch collections and send a OFPortStatus message to
	 * the controller. 
	 * 
	 * @param switchId The switch ID of the local switch that hosts the WAN switch port.
	 * @param portId The port ID of the local switch port.
	 * @return <b>int</b> The port number of the removed WAN switch port.
	 */
	public synchronized int removeWanSwitchPort(long switchId, int portId) {
		int wanSwitchPortId = -1;
		OFPhysicalPort ofpPort = null;
		if (this.switchToWanSwitchPorts.containsKey(switchId)) {
			if (this.switchToWanSwitchPorts.get(switchId).containsKey(portId)) {
				
				// Remove the switch+port combination from Suppressed LLDP in order to allow link discovery by the local controller.
				this.linkDiscoveryManager.RemoveFromSuppressLLDPs(switchId, portId);
				// Remove the switch+port combination from Suppressed APs in order to allow host-learning by the local controller.
				this.deviceManager.removeSuppressAPs(switchId, portId);
				// Remove the switch+port combination from Suppressed ARPs in order to allow sending of ARP requests by the local controller.
				this.arpManager.removeFromSuppressARPs(switchId, portId);
				
				wanSwitchPortId = this.switchToWanSwitchPorts.get(switchId).remove(portId);
				this.wanSwitchPortsToSwitch.remove(wanSwitchPortId);
				ofpPort = this.wanSwitchPorts.remove(wanSwitchPortId);
				
				// Send a WAN switch port remove message to the controller.
				OFPortStatus portStatus = (OFPortStatus) BasicFactory.getInstance().getMessage(OFType.PORT_STATUS);
				portStatus.setDesc(ofpPort);
				portStatus.setReason(OFPortReason.OFPPR_DELETE.getReasonCode());
				this.sendOFMessage(portStatus);
			
				if (this.switchToWanSwitchPorts.get(switchId).isEmpty()) {
					this.switchToWanSwitchPorts.remove(switchId);
				}
			}
		}
		
		if (log.isDebugEnabled()) {
			int phyPortId = OFSwitchPort.physicalPortIdOf(portId);
			short vlanId = OFSwitchPort.vlanIdOf(portId);
			if (wanSwitchPortId == -1) {
				log.debug("Failed to remove WAN switch port for: " + HexString.toHexString(switchId) + " - " + phyPortId + " (" + vlanId + ")");
			} else {
				log.debug("Remove WAN switch port (" + wanSwitchPortId + ") for: " + HexString.toHexString(switchId) + " - " + phyPortId + " (" + vlanId + ")");
			}
		}
		
		return wanSwitchPortId;
	}
	
	
	
	/**
	 * Gets the next available minimum WAN switch port ID based between 1 and 
	 * Short.MAX_VALUE (2^15-1 = 32767) based on the WAN switch ports that are already present.
	 * Hence, we currently support 32767 WAN switch ports.
	 * 
	 * @return A new WAN switch port ID between 1 and 32767.
	 */
	private int getNextWanSwitchPortId() {
		List<Integer> wanSwitchPortIds = new ArrayList<Integer>(this.wanSwitchPortsToSwitch.keySet());
		Collections.sort(wanSwitchPortIds);
		for (int i=1; i<=wanSwitchPortIds.size(); i++) {
			if (i < wanSwitchPortIds.get(i-1))
				return i;
		}
		
		// Since OFv1.0 only supports 16 bit port IDs, make sure we do not exceed the range.
		if (wanSwitchPortIds.size() > Short.MAX_VALUE) {
			if (log.isErrorEnabled()) {
				log.error("Port ID exceeds port range.");
			}
		}
		
		return wanSwitchPortIds.size() + 1;
	}
	
	/**
	 * Reads the current configuration from locale configuration
	 * cache and updates the wan switch.
	 */
	private void updateConfig() {
		// Update the wan port configuration.
		if (switchWanPortConfiguration == null || switchWanPortConfiguration.isEmpty()) {
			if (log.isDebugEnabled()) {
				log.debug("We don't have a wan port configuration.");
			}
		} else {
			for (long switchId : switchWanPortConfiguration.keySet()) {
				IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(switchId);
				if (iofSwitch == null)
					continue;
				for (int portId : switchWanPortConfiguration.get(switchId)) {
					if (iofSwitch.getPort(portId) != null)
						this.addWanSwitchPort(switchId, portId);
				}
			}
		}
	}

}
