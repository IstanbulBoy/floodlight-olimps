package net.floodlightcontroller.core;

import java.util.Set;

import org.jboss.netty.buffer.ChannelBuffer;

import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;

/**
 * 
 * @author Michael Bredel <michael.bredel@caltech.edu>
 * 
 * TODO: Setters are deprecated, since an OpenFlow port should be
 *   immutable. Hence, it should get its features once it is created
 *   and the features should not change.
 */
public interface IOFPort {
	
	/**
	 * OpenFlow port type.
	 */
	public enum Type {
	    OFPP_MAX                ((short)0xff00),
	    OFPP_IN_PORT            ((short)0xfff8),
	    OFPP_TABLE              ((short)0xfff9),
	    OFPP_NORMAL             ((short)0xfffa),
	    OFPP_FLOOD              ((short)0xfffb),
	    OFPP_ALL                ((short)0xfffc),
	    OFPP_CONTROLLER         ((short)0xfffd),
	    OFPP_LOCAL              ((short)0xfffe),
	    OFPP_NONE               ((short)0xffff);

	    protected short value;

	    private Type(short value) {
	        this.value = value;
	    }

	    /**
	     * @return The value
	     */
	    public short getValue() {
	        return value;
	    }
	}
	
	/**
     * @return The name of the port.
     */
	public String getName();
	
	/**
	 * @param name The name of the port.
	 */
	@Deprecated
	public void setName(String name);
	
	/**
	 * @return The port number.
	 */
	public int getPortNumber();
	//public short getPortNumber();
	
	/**
	 * @param portNumber The port number.
	 */
	@Deprecated
	public void setPortNumber(int portNumber);
	
	/**
	 * @return The MAC address.
	 */
	public long getHardwareAddress();
	
	/**
	 * @param hardwareAddress The MAC address.
	 */
	@Deprecated
	public void setHardwareAddress(long hardwareAddress);
	
	/**
     * @return The port configuration.
     */
	@Deprecated
	public Set<OFPortConfig> getConfig();
	
	/**
	 * @param config The port configuration.
	 */
	@Deprecated
	public void setConfig(int config);
	
	/**
     * @return The port state.
     */
	public int getState();
	
	/**
	 * @param state The port state.
	 */
	@Deprecated
	public void setState(int state);
	
	/**
	 * @return The current port features.
	 */
	public Set<OFPortFeatures> getCurrentFeatures();
	
	/** 
	 * @param currentFeatures The current port features.
	 */
	@Deprecated
	public void setCurrentFeatures(int currentFeatures);
	
	/**
	 * @return The advertised port features.
	 */
	public Set<OFPortFeatures> getAdvertisedFeatures();
	
	/**
	 * @param advertisedFeatures The advertised port features.
	 */
	@Deprecated
	public void setAdvertisedFeatures(int advertisedFeatures);
	
	/**
	 * @return The supported port features.
	 */
	public Set<OFPortFeatures> getSupportedFeatures();
	
	/**
	 * @param supportedFeatures The supported port features.
	 */
	@Deprecated
	public void setSupportedFeatures(int supportedFeatures);
	
	/**
	 * @return The peer features.
	 */
	public Set<OFPortFeatures> getPeerFeatures();
	
	/**
	 * @param peerFeatures The peer features.
	 */
	@Deprecated
	public void setPeerFeatures(int peerFeatures);
	
	/**
	 * @return The speed of the port in Mbps if the port is enabled, otherwise return SPEED_NONE.
	 */
	public int getCurrentPortSpeed();
	
	/**
	 * @param portSpeed The port speed of a virtual port in Mbps.
	 */
	public void setCurrentPortSpeed(int portSpeed);
	
	/**
	 * @return The VLAN id associated with this port, 
	 * or 0 if no vlan is assigned.
	 */
	public short getVlanId();
	
	/**
     * Read this message off the wire from the specified ByteBuffer.
     * 
     * @param data The buffer to read the message from.
     */
	public void readFrom(ChannelBuffer data);
	
	/**
     * Write this message's binary format to the specified ByteBuffer.
     * 
     * @param data The buffer to write the data to.
     */
	public void writeTo(ChannelBuffer data);
	
	/**
     * Returns true if the OFPortState indicates the port is down.
     * 
     * @return True if the OFPortState indicates the port is down.
     */
	public boolean isLinkDown();
	
	/**
     * Returns true if the port is up, i.e., it's neither administratively
     * down nor link down. It currently does NOT take STP state into
     * consideration
     * 
     * @return True if the port is up.
     */
	public boolean isEnabled();

}
