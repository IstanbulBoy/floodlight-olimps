package net.floodlightcontroller.wanswitch;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import net.floodlightcontroller.wanswitch.IWANSwitchService.ConnectionState;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.handler.timeout.IdleStateAwareChannelHandler;

import org.openflow.protocol.OFBarrierRequest;
import org.openflow.protocol.OFEchoRequest;
import org.openflow.protocol.OFFeaturesRequest;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFGetConfigRequest;
import org.openflow.protocol.OFHello;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFSetConfig;
import org.openflow.protocol.OFStatisticsRequest;
import org.openflow.protocol.OFType;
import org.openflow.protocol.OFVendor;
import org.openflow.protocol.statistics.OFFlowStatisticsRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Netty channel handler, handles all events from the Netty channel, i.e. the
 * connection to the parent OpenFlow controller, and passes it to the WAN switch
 * handler.  
 * 
 * @author Michael Bredel <michael.bredel@caltech.edu>
 */
public class WANSwitchChannelHandler extends IdleStateAwareChannelHandler {
    /** The maximum queue length for incoming OpenFlow messages from the parent controller. TODO: Right size? How to handle full? */
    private static final int MAX_QUEUE = 255;
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(WANSwitchChannelHandler.class);
    /** A counter for received messages. */
    private final AtomicInteger counter;
    /** The WAN switch handler that sends message to the parent controller. */
    private WANSwitchHandler wanSwitchHandler;
    /** An OpenFlow message queue for the producer-consumer pattern to handle incoming message from the parent controller. */
    private final Queue<OFMessage> ofMessageQueue = new LinkedList<OFMessage>();
    /** The reentrant lock for the OpenFlow message queue. */
    private final Lock lock = new ReentrantLock();
    /** The reentrant lock condition to indicate the OpenFlow message queue is not full (anymore). */
    private final Condition notFull = lock.newCondition();
    /** The reentrant lock condition to indicate the OpenFlow message queue is not empty (anymore). */
    private final Condition notEmpty = lock.newCondition();
    
    /**
     * A worker thread of the producer-consumer pattern to 
     * consume and process OpenFlow messages.
     */
    protected class OFMessageWorker implements Runnable {
        @Override
        public void run() {
        	while(true) {
        		lock.lock();
        		try {
        			while (ofMessageQueue.size() == 0) {
        				notEmpty.await();
        			}
        			OFMessage ofm = ofMessageQueue.poll();
        			if (log.isTraceEnabled()) {
        				log.trace("Process OpenFlow message: " + ofm);
        			}
        			processOFMessage(ofm);
					if (ofMessageQueue.size() < MAX_QUEUE)
						notFull.signalAll();
        		} catch (InterruptedException e) {
        			e.printStackTrace();
        		} finally {
        			lock.unlock();
        		}
        	}
        }
    }
    
    /**
     * Constructor.
     * 
     * @param listener
     */
    public WANSwitchChannelHandler(WANSwitchHandler wanSwitchHandler) {
    	this.wanSwitchHandler = wanSwitchHandler;
        this.counter = new AtomicInteger();
        
        Thread ofMessageWorkerThread = new Thread(new OFMessageWorker(), "OFMessageWorker");
        ofMessageWorkerThread.start();
    }
 
    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e) throws Exception {
    	// Do nothing. Do we need this method?
        super.handleUpstream(ctx, e);
    }
 
    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        this.counter.incrementAndGet();
        
        lock.lock();
        try {
        	// Wait for not-full queue.
        	while ( ofMessageQueue.size() >= MAX_QUEUE ) {
                notFull.await();
        	}
        	if (e.getMessage() instanceof List) {
                @SuppressWarnings("unchecked")
                List<OFMessage> msglist = (List<OFMessage>) e.getMessage();
                
                // Add all OpenFlow messages to the producer-consumer queue.
                for (OFMessage ofm : msglist) {
                    ofMessageQueue.add(ofm);
                }
                notEmpty.signalAll();
            } else {
            	System.out.println("ERROR: messageReceived: " + e.getMessage());
            }
        } finally {
        	lock.unlock();
        }
        
    }
    
    @Override
    public void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
    	if (log.isDebugEnabled()) {
    		log.debug("Channel connected " + e);
    	}
    }
 
    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        super.channelClosed(ctx, e);
        if (log.isDebugEnabled()) {
    		log.debug("Channel closed " + e);
    	}
    }
    
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
    	// TODO: Debug output.
    }
    
    /**
     * Process the OpenFlow message that has been received from the parent OpenFlow
     * controller. Decide on the message type what to do.
     * 
     * @param ofm An OpenFlow message received from the parent OpenFlow controller.
     */
    protected void processOFMessage(OFMessage ofm) {
    	switch(ofm.getType()) {
    		case HELLO:
    			processOFHello((OFHello) ofm);
    			break;
    		case FEATURES_REQUEST:
    			processOFFeaturesRequest((OFFeaturesRequest) ofm);
    			break;
            case PACKET_OUT:	// LLDP etc.
            	processOFPacketOut((OFPacketOut) ofm);
            	break;
            case FLOW_MOD:		// New flows.
            	this.processOFFlowMod((OFFlowMod) ofm);
            	break;
            case STATS_REQUEST:	// Statistics.
            	processOFStatsRequest((OFStatisticsRequest) ofm);
            	break;
            case PORT_MOD:
            	System.out.println("WANSwitchChannelHandler.processOFMEssage: " + ofm.getType());
            	break;
    		case ECHO_REQUEST:
    			processOFEcho((OFEchoRequest) ofm);
    			break;
    		case SET_CONFIG:
    			processOFSetConfig((OFSetConfig) ofm); 
    			break;
    		case GET_CONFIG_REQUEST:
    			processOFConfigRequest((OFGetConfigRequest) ofm);
    			break;
            case QUEUE_GET_CONFIG_REQUEST:
            	System.out.println("WANSwitchChannelHandler.processOFMEssage: " + ofm.getType());
            	break;
            case BARRIER_REQUEST:
            	processOFBarrierRequest((OFBarrierRequest) ofm);
            	break;
    		case VENDOR:
    			processOFVendor((OFVendor) ofm);
    			break;
    		case ECHO_REPLY:
    			// Do nothing.
    			break;
            // The following messages are sent to the controller. The WAN switch
            // should never receive them
            case BARRIER_REPLY:
    		case ERROR:
    		case FEATURES_REPLY:
    		case QUEUE_GET_CONFIG_REPLY:
    		case STATS_REPLY:
    		case FLOW_REMOVED:
    		case GET_CONFIG_REPLY:
    		case PACKET_IN:
    		case PORT_STATUS:
    			illegalMessageReceived(ofm);
    			break;
    		default:
    			break;
    	}
    }
    
    /**
     * Log that an illegal OpenFlow message has been received from the
     * parent OpenFlow controller. Actually, that should never happen.
     * 
     * @param ofm
     */
    protected void illegalMessageReceived(OFMessage ofm) {
        if (log.isErrorEnabled()) {
        	log.error("Illegal message received. The Controller should never send this message: " + ofm);
        }
    }
    
    /**
     * Process an OpenFlow Hello message received from the parent
     * OpenFlow controller. Thus, set the connection state of the
     * WAN switch to "connected" and send a Hello reply message
     * back to the parent controller.
     * 
     * @param ofm An OFHello message received from the parent OpenFlow controller.
     */
    protected void processOFHello(OFHello ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFHello message received from parent OpenFlow controller: " + ofm);
        }
    	this.wanSwitchHandler.setConnectionState(ConnectionState.CONNECTED);
    	this.wanSwitchHandler.sendOFMessage(OFType.HELLO);
    }
    
    /**
     * Process an OpenFlow Echo Request message received from the
     * parent OpenFlow controller. Thus, send an Echo Reply message
     * back to the parent controller.
	 * 
	 * @param ofm An OFEchoRequest message received from the parent OpenFlow controller.
	 */
	protected void processOFEcho(OFEchoRequest ofm) {
		if (log.isDebugEnabled()) {
        	log.debug("OFEchoRequest message received from parent OpenFlow controller: " + ofm);
        }
		this.wanSwitchHandler.sendOFMessage(OFType.ECHO_REPLY);
	}

	/**
	 * Process an OpenFlow Features Request message received from the
     * parent OpenFlow controller. Thus, send a Features Reply message
     * back to the parent controller.
     * 
     * @param ofm An OFFeaturesRequest message received from the parent OpenFlow controller.
     */
    protected void processOFFeaturesRequest(OFFeaturesRequest ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFFeaturesRequest message received from parent OpenFlow controller: " + ofm);
        }
    	this.wanSwitchHandler.sendOFFeatureReply(ofm.getXid());
    }
    
    /**
	 * Process an OpenFlow Config Request message received from the
     * parent OpenFlow controller. Thus, send a Config Reply message
     * back to the parent controller.
     * 
     * @param ofm An OFGetConfigRequest message received from the parent OpenFlow controller.
     */
    protected void processOFConfigRequest(OFGetConfigRequest ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFGetConfigRequest message received from parent OpenFlow controller: " + ofm);
        }
    	this.wanSwitchHandler.sendOFConfigReply(ofm.getXid());
    }
    
    /**
	 * Process an OpenFlow Statistics Request message received from the
     * parent OpenFlow controller. Thus, send a Statistics Reply message
     * back to the parent controller. Decide based on the statistics type
     * which message to send back.
     * 
     * @param ofm An OFStatisticsRequest message received from the parent OpenFlow controller.
     */
    protected void processOFStatsRequest(OFStatisticsRequest ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFStatisticsRequest message received from parent OpenFlow controller: " + ofm);
        }
    	switch (ofm.getStatisticType()) {
    		case DESC:
    			this.wanSwitchHandler.sendOFStatsReplyDesc(ofm.getXid());
    			break;
    		case FLOW:
    			this.wanSwitchHandler.sendOFStatsReplyFlow(ofm.getXid(), (OFFlowStatisticsRequest) ofm.getFirstStatistics());
    			break;
    		case AGGREGATE:
    			this.wanSwitchHandler.sendOFStatsReplyAggregate(ofm.getXid());
    			break;
    		case TABLE:
    			System.out.println("WANSwitchChannelHandler.processOFStatsRequest: TABLE - Yet Unhandled");
    			break;
    		case PORT:
    			this.wanSwitchHandler.sendOFStatsReplyPort(ofm.getXid());
    			break;
    		case QUEUE:
    			System.out.println("WANSwitchChannelHandler.processOFStatsRequest: QUEUE - Yet Unhandled");
    			break;
    		case VENDOR:
    			System.out.println("WANSwitchChannelHandler.processOFStatsRequest: VENDOR - Yet Unhandled");
    			break;
    		default:
    			illegalMessageReceived(ofm);
    	}
    }
    
    /**
	 * Process an OpenFlow Barrier Request message received from the
     * parent OpenFlow controller. Thus, send a Barrier Reply message
     * back to the parent controller.
     * 
     * @param ofm An OFBarrierRequest message received from the parent OpenFlow controller.
     */
    protected void processOFBarrierRequest(OFBarrierRequest ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFBarrierRequest message received from parent OpenFlow controller: " + ofm);
        }
    	this.wanSwitchHandler.sendOFBarrierReply(ofm.getXid());
    }
    
    /**
	 * Process an OpenFlow Set Config message received from the
     * parent OpenFlow controller.
     * 
     * @param ofm An OFSetConfig message received from the parent OpenFlow controller.
     * 
     * TODO
     */
    protected void processOFSetConfig(OFSetConfig ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFSetConfig message received from parent OpenFlow controller: " + ofm);
        }
		ofm.getMissSendLength();
	}

    /**
	 * Process an OpenFlow Vendor message received from the
     * parent OpenFlow controller. Thus, send a Vendor reply message
     * back to the parent controller.
     * 
     * @param ofm An OFVendor message received from the parent OpenFlow controller.
     */
    protected void processOFVendor(OFVendor ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFVendor message received from parent OpenFlow controller: " + ofm);
        }
    	this.wanSwitchHandler.sendOFVendor(ofm.getXid());
    }
    
    /**
	 * Process an OpenFlow Packet Out message received from the
     * parent OpenFlow controller. Thus, send a packet out to the
     * corresponding WAN switch ports.
     * 
     * @param ofm An OFPacketOut message received from the parent OpenFlow controller.
     */
    protected void processOFPacketOut(OFPacketOut ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFPacketOut message received from parent OpenFlow controller: " + ofm);
        }
    	this.wanSwitchHandler.handleOFPacketOut(ofm);
    }
    
    /**
	 * Process an OpenFlow Flow Mod message received from the
     * parent OpenFlow controller. Decide based on the command
     * how to process further.
     * 
     * @param ofm An OFFlowMod message received from the parent OpenFlow controller.
     */
    protected void processOFFlowMod(OFFlowMod ofm) {
    	if (log.isDebugEnabled()) {
        	log.debug("OFFlowMod message received from parent OpenFlow controller: " + ofm);
        }
    	switch (ofm.getCommand()) {
    		case OFFlowMod.OFPFC_ADD:
    			this.wanSwitchHandler.handleOFModAdd(ofm);
    			break;
    		case OFFlowMod.OFPFC_DELETE_STRICT:
    			this.wanSwitchHandler.handleOFModDelete(ofm);
    			break;
    		case OFFlowMod.OFPFC_DELETE:
    			this.wanSwitchHandler.handleOFModDelete(ofm);
    			break;
    		case OFFlowMod.OFPFC_MODIFY_STRICT:
    			System.out.println("WANSwitchChannelHandler.processOFMEssage: OFPFC_MODIFY_STRICT: " + ofm);
    			break;
    		case OFFlowMod.OFPFC_MODIFY:
    			System.out.println("WANSwitchChannelHandler.processOFMEssage: OFPFC_MODIFY: " + ofm);
    			break;
    		default:
    			System.out.println("WANSwitchChannelHandler.processOFMEssage: DEFAULT: " + ofm);
    	}
    }

}