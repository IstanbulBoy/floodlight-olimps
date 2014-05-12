package net.floodlightcontroller.wanswitch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.flowcache.FlowCacheObj;
import net.floodlightcontroller.flowcache.FlowCacheQuery;
import net.floodlightcontroller.flowcache.FlowCacheQueryResp;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.flowcache.FlowCacheObj.Status;
import net.floodlightcontroller.multipath.IMultipathService;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.multipath.IStatisticsCollectorService;
import net.floodlightcontroller.multipath.StatisticEntry;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.wanswitch.IWANSwitchService.ConnectionState;

import org.openflow.protocol.OFBarrierReply;
import org.openflow.protocol.OFFeaturesReply;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFGetConfigReply;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFPortStatus;
import org.openflow.protocol.OFPortStatus.OFPortReason;
import org.openflow.protocol.OFStatisticsReply;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.protocol.factory.BasicFactory;
import org.openflow.protocol.statistics.OFAggregateStatisticsReply;
import org.openflow.protocol.statistics.OFDescriptionStatistics;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;
import org.openflow.protocol.statistics.OFPortStatisticsReply;
import org.openflow.protocol.statistics.OFStatistics;
import org.openflow.protocol.statistics.OFStatisticsType;
import org.openflow.protocol.vendor.OFVendorData;
import org.openflow.util.HexString;
import org.openflow.vendor.nicira.OFNiciraVendorData;
import org.openflow.vendor.nicira.OFRoleReplyVendorData;
import org.openflow.vendor.nicira.OFRoleVendorData;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles all events related to the WAN switch. Thus, it takes actions
 * on incoming messages from the controller (passed by the WAN switch channel
 * handler) and events that occur at the physical switch level of this WAN
 * switch.
 * 
 * @author Michael Bredel <michael.bredel@caltech.edu>
 */
public class WANSwitchHandler {
	/** */
	protected static int OFMESSAGE_DAMPER_CAPACITY = 10000;
	/** Timeout in [ms]. 250 ms by default. */
    protected static int OFMESSAGE_DAMPER_TIMEOUT = 250;
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(WANSwitchHandler.class);
	
	protected WANSwitchManager wanSwitchManager;
    protected IFloodlightProviderService floodlightProvider;
    protected IMultipathService multipathManager;
    protected IPathFinderService pathfinder;
    protected IPathCacheService pathCache;
    protected IFlowCacheService flowCache;
    protected IStatisticsCollectorService statisticsCollector;
    protected int handshakeTransactionIds = -1;
    protected OFMessageDamper messageDamper;
	
    /**
     * Constructor.
     * 
     * @param wanSwitchManager
     * @param floodlightProvider
     */
	public WANSwitchHandler(WANSwitchManager wanSwitchManager, FloodlightModuleContext context) {
		this.wanSwitchManager = wanSwitchManager;
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.multipathManager = context.getServiceImpl(IMultipathService.class);
		this.pathfinder = context.getServiceImpl(IPathFinderService.class);
		this.pathCache = context.getServiceImpl(IPathCacheService.class);
		this.flowCache = context.getServiceImpl(IFlowCacheService.class);
		this.statisticsCollector = context.getServiceImpl(IStatisticsCollectorService.class);
		this.messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY, EnumSet.of(OFType.FLOW_MOD), OFMESSAGE_DAMPER_TIMEOUT);
	}
	
	/**
	 * Setter for the connection state. Sets the connection state
	 * of the switch, i.e. CONNECTED, NONE, and PENDING.
	 * 
	 * @param state The connection state of the WAN switch.
	 */
	public void setConnectionState(ConnectionState state) {
		this.wanSwitchManager.state = state;
	}
	
	/**
	 * Getter for the connection state. Gets the connection state
	 * of the switch, i.e. CONNECTED, NONE, and PENDING.
	 * 
	 * @return <b>ConnectionState</b> The connection state of the WAN switch.
	 */
	public ConnectionState getConnectionState() {
		return this.wanSwitchManager.state;
	}
	
	/**
	 * Sends a OpenFlow Feature Reply message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Feature Request.
	 */
	protected void sendOFFeatureReply(int xId) {
		OFFeaturesReply featureReply = (OFFeaturesReply) BasicFactory.getInstance().getMessage(OFType.FEATURES_REPLY);
		// Set the transaction ID.
		xId = (xId != 0) ? xId : xId;
		featureReply.setXid(xId);
    	// Add the WAN switch ID.
    	featureReply.setDatapathId(this.wanSwitchManager.getDPID());
    	// Add all WAN switch ports.
    	featureReply.setPorts(this.getWANSwitchPorts());	
    	// Add capabilities.
    	featureReply.setCapabilities(this.getWANSwitchCapacilities());
    	// Add tables.
    	featureReply.setTables(this.getWANSwitchTables());
    	// Add buffers.
    	featureReply.setBuffers(this.getWANSwitchBuffers());
    	// Add actions.
    	featureReply.setActions(this.getWANSwitchActions());
    	// Set length.
    	featureReply.setLength((short) (OFFeaturesReply.MINIMUM_LENGTH + (featureReply.getPorts().size() * OFPhysicalPort.MINIMUM_LENGTH)));
    	
    	if (log.isDebugEnabled()) {
        	log.debug("Send OFFeaturesReply to the parent OpenFlow controller: " + featureReply);
        }
    	
    	this.wanSwitchManager.sendOFMessage(featureReply);
	}
	
	/**
	 * Sends a OpenFlow Config Reply message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Config Request.
	 */
	protected void sendOFConfigReply(int xId) {
		OFGetConfigReply getConfigReply = (OFGetConfigReply) BasicFactory.getInstance().getMessage(OFType.GET_CONFIG_REPLY);
		// Set the transaction ID.
		xId = (xId != 0) ? xId : xId;
		getConfigReply.setXid(xId);
		// Set the flags that specify IP fragmentation handling.
		getConfigReply.setFlags(getWANSwitchFlags());
		// Set the max bytes of new flow to send to the controller.
		getConfigReply.setMissSendLength((short) 65535);
		
		if (log.isDebugEnabled()) {
        	log.debug("Send OFFeaturesReply to the parent OpenFlow controller: " + getConfigReply);
        }
		
		this.wanSwitchManager.sendOFMessage(getConfigReply);
	}
	
	/**
	 * Sends a OpenFlow Statistics Reply Description message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Statistics Description Request.
	 */
	protected void sendOFStatsReplyDesc(int xId)  {
		// Create the description.
		OFDescriptionStatistics descStats = new OFDescriptionStatistics();
		descStats.setDatapathDescription("None");
		descStats.setHardwareDescription("OLiMPS WAN Switch");
		descStats.setManufacturerDescription("Caltech");
		descStats.setSerialNumber("0.1");
		descStats.setSoftwareDescription("None");
		
		sendOFStatsReply(xId, Collections.singletonList(descStats));
	}
	
	/**
	 * Sends a OpenFlow Statistics Reply Port message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Statistics Port Request.
	 */
	protected void sendOFStatsReplyPort(int xId)  {
		/* A list of OFPortStatisticReply messages that are send to the parent controller. */
		List<OFPortStatisticsReply> stats = new ArrayList<OFPortStatisticsReply>();
		/* A local switch Id.*/
		long localSwitchId;
		/* A local port Id. */
		int localPortId;
		/* A statistics entry from the statistics collector. */
		StatisticEntry statsEntry;
		
		for (int wanPortId : this.wanSwitchManager.getPorts()) {
			NodePortTuple npt = this.wanSwitchManager.getPort(wanPortId);
			localSwitchId = npt.getNodeId();
			localPortId = npt.getPortId();
			statsEntry = this.statisticsCollector.getStatisticEntry(localSwitchId, localPortId);
			
			long byteCount = 0;
			long packetCount = 0;
			if (statsEntry != null) {
				byteCount = statsEntry.getByteCount();
				packetCount = statsEntry.getPacketCount();
			}
	    	
			// Create the flow statistics reply.
			OFPortStatisticsReply portStatsReply = new OFPortStatisticsReply();
				portStatsReply.setPortNumber((short) wanPortId);
				portStatsReply.setCollisions(0);
				portStatsReply.setReceiveBytes(byteCount);	
				portStatsReply.setReceiveCRCErrors(0);
				portStatsReply.setReceiveDropped(0);
				portStatsReply.setreceiveErrors(0);
				portStatsReply.setReceiveFrameErrors(0);
				portStatsReply.setReceiveOverrunErrors(0);
				portStatsReply.setreceivePackets(packetCount);
				portStatsReply.setTransmitBytes(0);
				portStatsReply.setTransmitDropped(0);
				portStatsReply.setTransmitErrors(0);
				portStatsReply.setTransmitPackets(0);
			
			stats.add(portStatsReply);
		}
		
		sendOFStatsReply(xId, stats);
	}
	
	/**
	 * Sends a OpenFlow Statistics Reply Flow message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Statistics Flow Request.
	 */
	protected void sendOFStatsReplyFlow(int xId, OFFlowStatisticsRequest flowStatsRequest)  {
		/* A list of OFFlowStatisticsReply messages that are send to the parent controller. */
		List<OFFlowStatisticsReply> stats = new ArrayList<OFFlowStatisticsReply>();
		/* A Map of flow cache objects (switchId -> ListOf FlowCacheObj) queried from the flow cache. */
	    Map<Long, List<FlowCacheObj>> flowCacheObjects = new HashMap<Long, List<FlowCacheObj>>();
		/* A local switch Id.*/
		long localSwitchId;
		/* A local port Id. */
		int localPortId;
		/* A statistics entry from the statistics collector. */
		StatisticEntry statsEntry;
		/* */
		OFMatch reqMatch = null;
		/* All WAN switch port to query. */
		Collection<Integer> ports = new HashSet<Integer>();
		
		// Filter the ports to query.
		if (flowStatsRequest != null) {
			reqMatch = flowStatsRequest.getMatch();
			int reqOutPort = flowStatsRequest.getOutPort();
			if (reqOutPort != 0 && this.wanSwitchManager.getPort(reqOutPort) != null) {
				ports.add(this.wanSwitchManager.getPort(reqOutPort).getPortId());
			} else {
				ports.addAll(this.wanSwitchManager.getPorts());
			}
		}
		
		for (int wanPortId : ports) {
			NodePortTuple npt = this.wanSwitchManager.getPort(wanPortId);
			localSwitchId = npt.getNodeId();
			localPortId = npt.getPortId();
			
			// Query the flow cache to get all flow statistics.
			List<FlowCacheQuery> queryList = new ArrayList<FlowCacheQuery>();
	    	FlowCacheQuery fcqOutPort = new FlowCacheQuery(IFlowCacheService.DEFAULT_DB_NAME, "WANSwitchHandler", null, localSwitchId, reqMatch)
	    								.setOutPort(localPortId);
	    	FlowCacheQuery fcqInPort = new FlowCacheQuery(IFlowCacheService.DEFAULT_DB_NAME, "WANSwitchHandler", null, localSwitchId, reqMatch)
										.setInPort(localPortId);
	    	queryList.add(fcqOutPort);
	    	queryList.add(fcqInPort);
	    	List<Future<FlowCacheQueryResp>> futures = this.flowCache.queryDB(queryList);
	    	
	    	if (futures == null || futures.isEmpty()) {
		 		log.error("Could not get futures from flow cache.");
		 		return;
		 	}
	    	try {
	    		for (Future<FlowCacheQueryResp> future : futures) {
	    			FlowCacheQueryResp fcqr = future.get(10, TimeUnit.SECONDS);
	    			if (fcqr != null && fcqr.flowCacheObjList != null) {
	    				flowCacheObjects.put(fcqr.switchId, fcqr.flowCacheObjList);
	    			}
				}
			} catch (InterruptedException | ExecutionException | TimeoutException e) {
				e.printStackTrace();
			}
			
			// Create the flow statistics reply.
	    	if (flowCacheObjects.containsKey(localSwitchId)) {
	    		for (FlowCacheObj fco : flowCacheObjects.get(localSwitchId)) {
	    			statsEntry = (StatisticEntry) fco.getAttribute(FlowCacheObj.Attribute.STATISTIC);
	    			OFFlowStatisticsReply flowStatsReply = new OFFlowStatisticsReply();
	    				flowStatsReply.setActions(fco.getActions());
	    				flowStatsReply.setByteCount(statsEntry.getByteCount());
	    				flowStatsReply.setCookie(fco.getCookie());
	    				flowStatsReply.setDurationNanoseconds((int) statsEntry.getDuration() * 1000);
	    				flowStatsReply.setDurationSeconds( (int) statsEntry.getDuration() / 1000);
	    				flowStatsReply.setHardTimeout((short) 0);
	    				flowStatsReply.setIdleTimeout((short) 0);
	    				flowStatsReply.setMatch(fco.getMatch());
	    				flowStatsReply.setPacketCount(statsEntry.getPacketCount());
	    				flowStatsReply.setPriority((short) fco.getPriority());
	    				flowStatsReply.setTableId((byte) 0);
			
	    			stats.add(flowStatsReply);
	    		}
	    	}
		}
				
		sendOFStatsReply(xId, stats);
	}
	

	/**
	 * Sends a OpenFlow Statistics Reply Aggregate message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Statistics Aggregate Request.
	 */
	protected void sendOFStatsReplyAggregate(int xId)  {
		/* A list of OFAggregateStatisticsReply messages that are send to the parent controller. */
		List<OFAggregateStatisticsReply> stats = new ArrayList<OFAggregateStatisticsReply>();
		/* A local switch Id.*/
		long localSwitchId;
		/* A local port Id. */
		int localPortId;
		/* A statistics entry from the statistics collector. */
		StatisticEntry statsEntry;
		
		for (int wanPortId : this.wanSwitchManager.getPorts()) {
			NodePortTuple npt = this.wanSwitchManager.getPort(wanPortId);
			localSwitchId = npt.getNodeId();
			localPortId = npt.getPortId();
			statsEntry = this.statisticsCollector.getStatisticEntry(localSwitchId, localPortId);
			
			// Create the aggregate statistics reply.
			if (statsEntry != null) {
				OFAggregateStatisticsReply aggStatsReply = new OFAggregateStatisticsReply();
					aggStatsReply.setByteCount(statsEntry.getByteCount());
					aggStatsReply.setFlowCount(statsEntry.getFlowCount());
					aggStatsReply.setPacketCount(statsEntry.getPacketCount());
			
				stats.add(aggStatsReply);
			}
		}
		
		sendOFStatsReply(xId, stats);
	}
	
	/**
	 * Sends a OpenFlow Statistics Reply message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Statistics Request.
	 * @param stats A list of OpenFlow statistics messages.
	 */
	protected void sendOFStatsReply(int xId, List<? extends OFStatistics> stats) {
		// Create the statistics reply.
		OFStatisticsReply statsReply = (OFStatisticsReply) BasicFactory.getInstance().getMessage(OFType.STATS_REPLY);
		// Set the transaction ID.
		xId = (xId != 0) ? xId : xId;
		statsReply.setXid(xId);
		// Set the statistics type.
		statsReply.setStatisticType(OFStatisticsType.DESC);
		// Set the flags.
		statsReply.setFlags((short) 0);
		// Add the description.
		statsReply.setStatistics(stats);
		// Set length.
		int length = 0;
		for (OFStatistics stat : stats) {
			length += stat.getLength();
		}
		statsReply.setLengthU(OFStatisticsReply.MINIMUM_LENGTH + length);
		
		if (log.isDebugEnabled()) {
        	log.debug("Send OFStatisticsReply to the parent OpenFlow controller: " + statsReply);
        }
				
		this.wanSwitchManager.sendOFMessage(statsReply);
	}
	
	/**
	 * Sends a OpenFlow Vendor message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Vendor message.
	 */
	protected void sendOFVendor(int xId) {
		// Create the vendor data.
		OFVendorData vendorData = new OFRoleReplyVendorData(OFRoleVendorData.NX_ROLE_MASTER);
		// Create the vendor reply message.
		OFVendor vendor = (OFVendor) BasicFactory.getInstance().getMessage(OFType.VENDOR);
		// Set the transaction ID.
		xId = (xId != 0) ? xId : xId;
		vendor.setXid(xId);
		// Set the vendor.
		vendor.setVendor(OFNiciraVendorData.NX_VENDOR_ID);
		// Add the vendor data.
		vendor.setVendorData(vendorData);
		// Set length.
		vendor.setLengthU(OFVendor.MINIMUM_LENGTH + vendorData.getLength());
		
		if (log.isDebugEnabled()) {
        	log.debug("Send OFVendor to the parent OpenFlow controller: " + vendor);
        }
		
		this.wanSwitchManager.sendOFMessage(vendor);
	}
	
	/**
	 * Sends a OpenFlow Barrier Reply message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param xId The transaction ID of the message. Should be identical to the xId of the corresponding Barrier request message.
	 */
	protected void sendOFBarrierReply(int xId) {
		OFBarrierReply barrierReply = (OFBarrierReply) BasicFactory.getInstance().getMessage(OFType.BARRIER_REPLY);
		// Set the transaction ID.
		xId = (xId != 0) ? xId : xId;
		barrierReply.setXid(xId);
		// Set length.
		barrierReply.setLengthU(OFBarrierReply.MINIMUM_LENGTH);
		
		if (log.isDebugEnabled()) {
        	log.debug("Send OFBarrierReply to the parent OpenFlow controller: " + barrierReply);
        }
		
		this.wanSwitchManager.sendOFMessage(barrierReply);
	}
	
	/**
	 * Sends a OpenFlow Port Status message back to the parent
	 * OpenFlow controller.
	 * 
	 * @param ofpPort The physical OpenFlow port that should be reported to the parent OpenFlow controller.
	 */
	protected void sendOFPortStatus(OFPhysicalPort ofpPort) {
		OFPortStatus portStatus = (OFPortStatus) BasicFactory.getInstance().getMessage(OFType.PORT_STATUS);
		portStatus.setDesc(ofpPort);
		portStatus.setReason(OFPortReason.OFPPR_ADD.getReasonCode());
		
		if (log.isDebugEnabled()) {
        	log.debug("Send OFPortStatus to the parent OpenFlow controller: " + portStatus);
        }
		
		this.wanSwitchManager.sendOFMessage(portStatus);
	}
	
	/**
	 * Generates an OpenFlow message of a given Type and sends it
	 * to the parent OpenFlow controller.
     * 
     * @param type Type of the OpenFlow message to be created and sent to the parent controller.
     */
    protected void sendOFMessage(OFType type) {
    	OFMessage ofm = BasicFactory.getInstance().getMessage(type);
    	this.sendOFMessage(ofm);
    }
    
    /**
     * Sends an OpenFlow message to the parent OpenFlow controller.
     * 
     * @param ofm A general OpenFlow message that is send to the parent controller.
     */
    protected void sendOFMessage(OFMessage ofm) {
        ofm.setXid(handshakeTransactionIds--);
        
        if (log.isDebugEnabled()) {
        	log.debug("Send OFMessage to the parent OpenFlow controller: " + ofm);
        }
        
        this.wanSwitchManager.sendOFMessage(ofm);
    }
    
    /**
     * Handles an OpenFlow Packet Out message received from the parent 
     * OpenFlow controller. To this end, it extracts the WAN switch port 
     * from the packet out message, gets the corresponding local switch 
     * and the corresponding local switch port. It then generates a packet
     * out message for that local switch+port combination.
     * 
     * @param ofm The OFPacketOut message received from the parent OpenFlwo controller.
     */
    protected void handleOFPacketOut(OFPacketOut ofm) {
    	// Debug logging.
		if (log.isDebugEnabled()) {
			log.debug("Handle OFPacketOut: " + ofm);
		}
    	OFPacketOut ofPacketOut;
    	List<OFAction> ofActions;
    	for (OFAction action : ofm.getActions()) {
    		switch (action.getType()) {
    			case OUTPUT:
    				ofPacketOut = (OFPacketOut) BasicFactory.getInstance().getMessage(OFType.PACKET_OUT);
    					ofPacketOut.setPacketData(ofm.getPacketData());
    					ofPacketOut.setInPort(ofm.getInPort());
    					ofPacketOut.setBufferId(ofm.getBufferId());
    					ofPacketOut.setLengthU(ofPacketOut.getLengthU() + ofm.getPacketData().length);
    				ofActions = new ArrayList<OFAction>();
    			
    				OFActionOutput actionOutput = (OFActionOutput) action;
    				short wanPortId = actionOutput.getPort();
    				NodePortTuple npt = this.wanSwitchManager.getPort(wanPortId);
    				// TODO: Check for special ports, like -1, -2, ... !
    				if (npt == null) {
    					break;
    				}
    				IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(npt.getNodeId());
    				short phyPortId = OFSwitchPort.physicalPortIdOf(npt.getPortId());
    				short vlanId = OFSwitchPort.vlanIdOf(npt.getPortId());
    				actionOutput.setPort(phyPortId);
    				
    				if (vlanId != 0) {
//    					OFActionVirtualLanIdentifier vlanAction = new OFActionVirtualLanIdentifier();
//    					vlanAction.setVirtualLanIdentifier(vlanId);
//    					ofActions.add(vlanAction);
//    					ofPacketOut.setLengthU(ofPacketOut.getLengthU() + OFActionVirtualLanIdentifier.MINIMUM_LENGTH);
//    					ofPacketOut.setActionsLength((short) (ofPacketOut.getActionsLength() + OFActionVirtualLanIdentifier.MINIMUM_LENGTH));
    					
    					// It seems that the VLAN is not modified by the switch before packet out.
    					// Hack to make sure the packet has the right VLAN tag.
    					if (ofm.getPacketData() != null && ofm.getPacketData().length != 0) {
    						Ethernet ethPacket = new Ethernet();
    						ethPacket.deserialize(ofm.getPacketData(), 0, ofm.getPacketData().length);
    						ethPacket.setVlanID(vlanId);
    						ofPacketOut.setLengthU(ofPacketOut.getLengthU() - ofPacketOut.getPacketData().length);
    						ofPacketOut.setPacketData(ethPacket.serialize());
    						ofPacketOut.setLengthU(ofPacketOut.getLengthU() + ofPacketOut.getPacketData().length);
    					} else {
    						break;
    					}
    				}
    				
    				ofActions.add(actionOutput);
    				ofPacketOut.setActions(ofActions);
    				ofPacketOut.setLengthU(ofPacketOut.getLengthU() + OFActionOutput.MINIMUM_LENGTH);
    				ofPacketOut.setActionsLength((short) (ofPacketOut.getActionsLength() + OFActionOutput.MINIMUM_LENGTH));
    			
    				try {
    					iofSwitch.write(ofPacketOut, null);
    					iofSwitch.flush();
    					// Debug logging.
    					if (log.isDebugEnabled()) {
    						log.debug("Send OFPacketOut to a locale switch+port combination: " + iofSwitch.getStringId() + " - " + phyPortId + "(" + vlanId + ")" + " \t " + ofPacketOut);
    					}
    				} catch (IOException e) {
    					if (log.isErrorEnabled()) {
    						log.error("Could not send OFPacketOut to a locale switch+port combination: " + iofSwitch.getStringId() + " - " + phyPortId + "(" + vlanId + ")" + " \t " + ofPacketOut);
    					}
    					e.printStackTrace();
    				}
    				break;
    			default:
    				if (log.isErrorEnabled()) {
    					log.error("Could not handle OFPacketOut action. " + action);
    				}
    				break;
    		}
    	}
    }
    
    /**
     * Handles an OpenFlow Flow Mod message received from the parent 
     * OpenFlow controller.
     * 
     * @param ofm The OFFlowMod message received from the parent OpenFlwo controller.
     */
    protected void handleOFModAdd(OFFlowMod flowMod) {
    	// Debug logging.
    	if (log.isDebugEnabled()) {
    		log.debug("Handle OFModAdd: " + flowMod);
    	}
    	/* The switch ID of the incoming switch (if any) from the OpenFlow match. */
    	long inSwitchId = 0;
    	/* The (virtual) port ID (if any) of the incoming port from the OpenFlow match. */
    	int inPortId = 0;
    	/* The physical input port ID (if any) from the OpenFlow match. */
    	short phyInPortId = 0;
    	/* The VLAN (if any) from the OpenFlow match. */
    	short vlanInId = 0;
    	/* Get all the OpenFlow actions. */
    	List<OFAction> actions = flowMod.getActions();
    	/* Get the input port, if any. */
    	short wanSwitchInPortID = flowMod.getMatch().getInputPort();
    	/* Get the flow match. */
    	OFMatch match = flowMod.getMatch();
    	
    	// Store the flow mod.
    	int flowId = this.wanSwitchManager.getNextFlowId();
    	this.wanSwitchManager.addFlowMod(flowId, flowMod);
    	
    	// Populate local input port information if available (in the match).
    	NodePortTuple npt = this.wanSwitchManager.getPort(wanSwitchInPortID);
    	if (wanSwitchInPortID != 0 && npt != null) {
    		inSwitchId = npt.getNodeId();
    		inPortId = npt.getPortId();
    		phyInPortId = OFSwitchPort.physicalPortIdOf(inPortId);
    		vlanInId = 	OFSwitchPort.vlanIdOf(inPortId);
    		// Set the match input port to the local switch physical input port.
    		match.setInputPort(phyInPortId);
    	}
    	
    	// Handle implicit DROP, i.e. actions is empty.
    	if (actions == null || actions.isEmpty()) {
    		// Handle VLANs.
    		if (vlanInId != 0) {
				match.setDataLayerVirtualLan(vlanInId);
				flowMod.setMatch(match);
			}
    		long cookie = AppCookie.makeCookie(WANSwitchManager.WANSWITCH_APP_ID, flowId);
    		// Install drop flow.
    		if (inSwitchId != 0 && phyInPortId != 0) {
    			match.setInputPort(phyInPortId);
    			flowMod.setMatch(match);
    			flowMod.setCookie(cookie);
    			
    			this.writeFlowMod(inSwitchId, flowMod, null);
    			// Debug logging.
				if (log.isDebugEnabled()) {
					log.debug("No Action. Thus, write an implicit drop rule.");
				}
    			// That is it. Return.
    			return;
    		} else {
    			// Install drop flow on every physical switch. // TODO: This can be dangerous!
    			for (long switchId : this.floodlightProvider.getAllSwitchDpids()) {
    				flowMod.setCookie(cookie);    				
    				this.writeFlowMod(switchId, flowMod, null);
    				// Debug logging.
    				if (log.isDebugEnabled()) {
    					log.debug("No Action. Thus, write an implicit drop rule");
    				}
    			}
    			// That is is. Return.
    			return;
    		}
    	}
    	
    	// Handle Actions.
    	for (OFAction action : actions) {
    		// To support more than just the output action, we need to create the custom actions before installing the flow on the path.
    		switch(action.getType()) {
    			case OUTPUT:
    				OFActionOutput actionOutput = (OFActionOutput) action;
    				// Debug logging.
    				if (log.isDebugEnabled()) {
    					log.debug("Process OFActionOutput: " + actionOutput);
    				}
    				
    				long outSwitchId = 0;
    				int outPortId = 0;
    				short phyOutPortId = 0;
		    		short vlanOutId = 0;
		    		long cookie = AppCookie.makeCookie(WANSwitchManager.WANSWITCH_APP_ID, flowId);
    				int wanSwitchOutPortId = actionOutput.getPort();
    				npt = this.wanSwitchManager.getPort(wanSwitchOutPortId);
    				if (wanSwitchOutPortId != 0 && npt != null) {
    		    		outSwitchId = npt.getNodeId();
    		    		outPortId = npt.getPortId();
    		    		phyOutPortId = OFSwitchPort.physicalPortIdOf(outPortId);
    		    		vlanOutId = OFSwitchPort.vlanIdOf(outPortId);
    		    	}
    				
    				// Install flow.
    	    		if (inSwitchId != 0 && phyInPortId != 0) {
    	    			Path path = this.pathfinder.getPath(inSwitchId, outSwitchId, match);
    	    			if (path != null) {
    	    				// Install the flow on local switches.
    	    				this.multipathManager.installFlow(match, path, outPortId, null, cookie);
    	    				if (vlanInId != 0 || vlanOutId != 0) {
    	    					// Input and output VLANs should be handled by multipathManager.installFlow(...)
    	    				}
    	    			}
    	    		} else {
    	    			// Install flow on every physical switch. // TODO: This is very inefficient. Happens if no input port is given in the match!
    	    			for (long switchId : this.floodlightProvider.getAllSwitchDpids()) {
    	    				Path path = this.pathfinder.getPath(switchId, outSwitchId, match);
    	    				if (path != null) {
    	    					// Install the flow on local switches.
        	    				this.multipathManager.installFlow(match, path, phyOutPortId, null, cookie);
        	    				if (vlanInId != 0 || vlanOutId != 0) {
        	    					// Input and output VLANs should be handled by multipathManager.installFlow(...)
        	    				}
    	    				}
    	    			}
    	    		}
    	    		break;
    			case SET_VLAN_ID:
    			case SET_VLAN_PCP:
    			case STRIP_VLAN:
    			default:
    				if (log.isWarnEnabled()) {
    					log.warn("Unsupported action received: " + action.getType());
    				}
    				break;
    		}
    	}
    }
    
    /**
     * 
     * @param flowMod
     */
    protected void handleOFModDelete(OFFlowMod flowMod) {
    	// Debug logging.
    	if (log.isDebugEnabled()) {
    		log.debug("Handle OFModDelete: " + flowMod);
    	}
    	
    	/* Get the input port, if any. */
    	short wanSwitchInPortID = flowMod.getMatch().getInputPort();

    	/* Get the flow match. */
    	OFMatch match = FlowCacheObj.wildcardMatch(flowMod.getMatch());
    	match.setInputPort((short) 0);

    	/* A Map of flow cache objects (switchId -> ListOf FlowCacheObj) queried from the flow cache. */
	    Map<Long, List<FlowCacheObj>> flowCacheObjects = new HashMap<Long, List<FlowCacheObj>>();
    	FlowCacheQuery fcq = new FlowCacheQuery(IFlowCacheService.DEFAULT_DB_NAME, "WANSwitchHandler", null, null, match);
    	// Get the flow id.
    	int flowId = this.wanSwitchManager.getFlowId(match);
    	if (flowId != 0) {
    		fcq.setCookie(AppCookie.makeCookie(WANSwitchManager.WANSWITCH_APP_ID, flowId));
    	}						
    	Future<FlowCacheQueryResp> future = this.flowCache.queryDB(fcq);
    	
    	if (future == null) {
	 		log.error("Could not get future from flow cache.");
	 		return;
	 	}
    	try {
			FlowCacheQueryResp fcqr = future.get(2, TimeUnit.SECONDS);
			if (fcqr != null && fcqr.flowCacheObjList != null) {
				flowCacheObjects.put(fcqr.switchId, fcqr.flowCacheObjList);
			}
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			e.printStackTrace();
		}
    	
    	// Populate local input port information if available (in the match).
    	NodePortTuple npt = this.wanSwitchManager.getPort(wanSwitchInPortID);
    	if (wanSwitchInPortID != 0 && npt != null) {
    		int inPortId = npt.getPortId();
    		short phyInPortId = OFSwitchPort.physicalPortIdOf(inPortId);
    		// Set the match input port to the local switch physical input port.
    		match.setInputPort(phyInPortId);
    	}
    	
    	// Remove the flows derived from the flow cache objects.
    	for (long switchId : flowCacheObjects.keySet()) {
    		for (FlowCacheObj fco : flowCacheObjects.get(switchId)) {
    			// Create a temporary path object.
    			Path path = new Path(switchId, switchId, null, fco.getPathId(), 0);
    			// Remove the flow.
    			this.multipathManager.removeFlow(match, path, OFPort.OFPP_NONE.getValue(), fco.getCookie());
    		}
    	}
    }
    
    /**
     * 
     * @return
     */
    private List<OFPhysicalPort> getWANSwitchPorts() {
    	List<OFPhysicalPort> ports = new ArrayList<OFPhysicalPort>();
    	// Add the local OpenFlow management port.
    	ports.add(this.wanSwitchManager.getOpenFlowPort());
    	// Add all WAN switch ports.
    	for (int portId : this.wanSwitchManager.getPorts()) {
    		NodePortTuple npt = this.wanSwitchManager.getPort(portId);
    		IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(npt.getNodeId());
    		OFPhysicalPort ofpPort = iofSwitch.getPort(npt.getPortId()).getOFPhysicalPort();
    		
    		OFPhysicalPort wanOfpPort = new OFPhysicalPort();
    			wanOfpPort.setPortNumber((short) portId);
    			wanOfpPort.setName("wan-" + portId);
    			wanOfpPort.setConfig(ofpPort.getConfig());
    			wanOfpPort.setState(ofpPort.getState());
    			wanOfpPort.setHardwareAddress(ofpPort.getHardwareAddress());
    			wanOfpPort.setSupportedFeatures(ofpPort.getSupportedFeatures());
    			wanOfpPort.setAdvertisedFeatures(ofpPort.getAdvertisedFeatures());
    			wanOfpPort.setCurrentFeatures(ofpPort.getCurrentFeatures());
    			wanOfpPort.setPeerFeatures(ofpPort.getPeerFeatures());
    		
    		ports.add(wanOfpPort);
    	}
    	
    	return ports;
    }
    
    /**
     * 
     * @return
     */
    private byte getWANSwitchTables() {
    	byte tables = (byte) 255;
    	for (long switchId : this.floodlightProvider.getAllSwitchDpids()) {
    		tables = (byte) Math.min(tables, this.floodlightProvider.getSwitch(switchId).getTables());
    	}
    	return tables;
    }
    
    /**
     * 
     * @return
     */
    private int getWANSwitchBuffers() {
    	int buffers = 256;
    	for (long switchId : this.floodlightProvider.getAllSwitchDpids()) {
    		buffers = (byte) Math.min(buffers, this.floodlightProvider.getSwitch(switchId).getBuffers());
    	}
    	return buffers;
    }
    
    /**
     * 
     * @return
     */
    private int getWANSwitchCapacilities() {
    	// 1 0 0 0 0 1 0 1 := 133 : FlowStats, PortStats, MatchIpInArp, 255 would enable all capabilities.
    	int capabilities = 133;
    	for (long switchId : this.floodlightProvider.getAllSwitchDpids()) {
    		capabilities = capabilities & this.floodlightProvider.getSwitch(switchId).getCapabilities();
    	}
    	return capabilities;
    }
    
    /**
     * 
     * @return
     */
    private int getWANSwitchActions() {
    	// 1 0 0 0 0 0 1 1 0 1 1 1 := 2103 : OutPort, VlanId, VlanPrio, SrcMac, DstMac, Queue, 4095 would enable all actions.
    	int actions = 2103; 
    	for (long switchId : this.floodlightProvider.getAllSwitchDpids()) {
    		actions = actions & this.floodlightProvider.getSwitch(switchId).getCapabilities();
    	}
    	return actions;
    }
    
    /**
     * 
     * @return
     */
    private short getWANSwitchFlags() {
    	short flags = 0; // Normal IP fragmentation handling by default.
    	return flags;
    }
    
    /**
     * 
     * @param switchId
     * @param flowMod
     * @param cntx
     */
    private void writeFlowMod(long switchId, OFFlowMod flowMod, FloodlightContext cntx) {
    	IOFSwitch iofSwitch = this.floodlightProvider.getSwitch(switchId);
		this.writeFlowMod(iofSwitch, flowMod, cntx);
    }
    
    /**
     * 
     * @param iofSwitch
     * @param flowMod
     * @param cntx
     */
    private void writeFlowMod(IOFSwitch iofSwitch, OFFlowMod flowMod, FloodlightContext cntx) {
    	// Debug logging.
    	if (log.isDebugEnabled()) {
    		log.debug("Send OFFlowMod to a locale switch+port combination: " + flowMod);
    	}
    	
    	try {
        	messageDamper.write(iofSwitch, flowMod, cntx);
        	iofSwitch.flush();
        } catch (IOException e) {
            log.error("Failed to write {} to switch {}", new Object[]{ flowMod, iofSwitch }, e);
        }
    	
    	// Update the flow cache and return.
        this.updateFlowCache(iofSwitch.getId(), flowMod.getCommand(), flowMod.getPriority(), flowMod.getCookie(), flowMod.getMatch(), flowMod.getActions(), cntx);
    }
    
    /**
	 * Adds a flow to the flow cache.
	 * 
	 * @param switchId
	 * @param command
	 * @param priority
	 * @param match
	 * @param actions
	 * @param cntx
	 * @return
	 */
	private FlowCacheObj updateFlowCache(long switchId, short command, short priority, long appCookie, OFMatch match, List<OFAction> actions, FloodlightContext cntx) {
		if (log.isTraceEnabled()) {
			log.trace("Cache update for flow OFMatchWithSwDpid [{} : {}] ", HexString.toHexString(switchId), match);
		}
		
		// Try to get a flow cache object.
		FlowCacheObj fco = flowCache.getFlow(IFlowCacheService.DEFAULT_DB_NAME, switchId, appCookie, priority, match, actions);
		
		switch (command) {
			case OFFlowMod.OFPFC_ADD:
				if (fco == null) {
					fco = flowCache.addFlow(IFlowCacheService.DEFAULT_DB_NAME, switchId, appCookie, priority, match, actions);
					fco.setStatus(Status.ACTIVE);
				}
				break;
			case OFFlowMod.OFPFC_MODIFY:
			case OFFlowMod.OFPFC_MODIFY_STRICT:
				break;
			case OFFlowMod.OFPFC_DELETE:
			case OFFlowMod.OFPFC_DELETE_STRICT:
				if (fco != null) {
					flowCache.removeFlow(IFlowCacheService.DEFAULT_DB_NAME, switchId, appCookie, priority, match, actions);
					fco.setStatus(Status.INACTIVE);
					//fco.setPathId(-1);
				}
				break;
			default:
				break;
		}
		
		return fco;
	}
	
	
}
