package net.floodlightcontroller.wanswitch;

import java.net.InetAddress;
import java.util.Collection;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPhysicalPort;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.topology.NodePortTuple;

public interface IWANSwitchService extends IFloodlightService {
	
    /**
     * Various states for connections
     */
    public enum ConnectionState {
        NONE,
        PENDING,
        CONNECTED
    }
	
	/**
	 * Returns the switch DPID.
	 * 
	 * @return <b>long</b> The switch DPID of the WAN switch.
	 */
	public long getDPID();
	
	/**
	 * Sets the controller host name or its IP address. One the
	 * controller is set, and the WAN switch can connect to it,
	 * the WAN switch becomes active.
	 * 
	 * @param hostname The controllers host name or IP address.
	 */
	public void setController(String hostname);
	
	/**
	 * Returns the IP address of the OpenFlow controller this
	 * switch is connected to.
	 * 
	 * @return <b>InetAddress</b> The IP address representation of the controller IP.
	 */
	public InetAddress getController();
	
	/**
	 * Sets a port to connect to the controller. The default port
	 * is 6633.
	 * 
	 * @param port The controller port to connect to.
	 */
	public void setControllerPort(short port);
	
	/**
	 * Returns the port of the OpenFlow controller this switch
	 * is connected to.
	 * 
	 * @return <b>short</b> The TCP port of the controller.
	 */
	public short getControllerPort();
	
	/**
	 * Returns a NodePortTuple that identifies the "real" switch and
	 * the "real" port of the underlying infrastructure.
	 * 
	 * @param portId The ID of a WAN switch port.
	 * @return <b>NodePortTuple</b> A node port tuple containing the switch ID and the (virtual) port ID of the "real" port.
	 */
	public NodePortTuple getPort(int wanSwitchportId);
	
	/**
	 * Gets a collection of active WAN switch port IDs.
	 * 
	 * @return <b>Collection</b> A collection of the WAN switch port IDs.
	 */
	public Collection<Integer> getPorts();
	
	/**
	 * Get the local OpenFlow management port. This port needs to be
	 * present e.g. in FeatureReply messages.
	 * 
	 * @return <b>OFPhysicalPort</b> The local OpenFlow management port.
	 */
	public OFPhysicalPort getOpenFlowPort();
	
	/**
	 * Shuts down or activates the WAN switch. If shut down, the WAN switch does
	 * not try to connect to a parent controller.
	 * 
	 * @param shutdown Activate or shut down the WAN switch.
	 */
	public void shutdown(boolean shutdown);
	
	/**
	 * Checks whether the WAN switch is active or not. The switch is
	 * active if it has a controller configured.
	 * 
	 * @return <b>boolean</b> True if the WAN switch is activated.
	 */
	public boolean isActive();
	
	/**
	 * Checks whether the WAN switch is connected to a controller or not.
	 * 
	 * @return <b>boolean</b> True if the WAN switch is connected to a controller.
	 */
	public boolean isConnected();
	
	/**
	 * Sends an OpenFlow Message to the controller.
	 * 
	 * @param ofm The OpenFlow message to send.
	 */
	public void sendOFMessage(OFMessage ofm);
	
	/**
	 * Adds a wan switch listener to the wan switch manager.
	 * 
	 * @param listener A listener that listens to wan switch events. NOT USED RIGHT NOW.
	 */
	public void addListener(IWANSwitchListener listener);
	
	/**
	 * Removes a wan switch listener from the wan switch manager.
	 * 
	 * @param listener A listener that listens to wan switch events. NOT USED RIGHT NOW.
	 */
	public void removeListener(IWANSwitchListener listener);
	
	/**
	 * Turns a local switch+port combination to a WAN switch port. 
	 * 
	 * @param switchId The local switch Id of the new WAN switch port.
	 * @param portId The local port Id on the switch for the new WAN switch port.
	 * @return <b>int</b> The new WAN switch port ID, or -1 if the creation failed.
	 */
	public int addWanSwitchPort(long switchId, int portId);
	
	/**
	 * Removes a local switch+port combination from the WAN switch ports
	 * 
	 * @param switchId The local switch Id of the new WAN switch port.
	 * @param portId The local port Id on the switch for the new WAN switch port.
	 * @return <b>int</b> The removed WAN switch port ID, or -1 if the deletion failed.
	 */
	public int removeWanSwitchPort(long switchId, int portId);
	
	// Methods that actually belong to a WAN switch port flow cache.
	// The WAN switch need to store the flows installed by its parent controller,
	// and the mapping to actuall flows on the local switches.
	public void addFlowMod(int flowId, OFFlowMod flowMod);
	public OFFlowMod delFlowMod(int flowId);
	public int getFlowId(OFMatch match);
	public int getNextFlowId();
	
}
