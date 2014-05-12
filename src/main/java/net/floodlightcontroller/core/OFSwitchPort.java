package net.floodlightcontroller.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import net.floodlightcontroller.util.EnumBitmaps;
import net.floodlightcontroller.util.MACAddress;

import org.jboss.netty.buffer.ChannelBuffer;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.protocol.OFPhysicalPort.PortSpeed;
import org.openflow.util.HexString;

/**
 * 
 * @author Michael Bredel <michael.bredel@caltech.edu>
 */
public class OFSwitchPort implements IOFPort {
	/** The bitmask to retrieve the OFPhysicalPort port number the OFSwitchPort port number by logical &. */
	private static final int PORT_NUMBER_BITMASK = -32768;
	/** The bitmask to retrieve the vlan from the OFSwitchPort port number by logical &. */
	private static final int VLAN_BITMASK = 65535;
	
	/** The port number. The first 16 bit address the physical port number, the last 16 bit address the vlan ID. */
	protected int portNumber;
	/** The physical port this switch port is linked to. */
	protected OFPhysicalPort ofpPort;
	/** Some general attributes. */
	protected ConcurrentMap<String, Object> attributes;
	/** */
	protected boolean portStateLinkDown;
	/** */
    protected OFPortState stpState;
	/** */
	protected EnumSet<OFPortConfig> config;
	/** */
	protected EnumSet<OFPortFeatures> currentFeatures;
	/** */
	protected EnumSet<OFPortFeatures> advertisedFeatures;
	/** */
	protected EnumSet<OFPortFeatures> supportedFeatures;
	/** */
	protected EnumSet<OFPortFeatures> peerFeatures;
	/** The port speed of the (virtual) port in Mbps. */
	protected int portSpeed;
	
	/**
	 * Creates a list of OFSwitchPorts from a list of OFPhysicalPorts.
	 * 
	 * @param ofpPorts A collection of OPhysicalPorts.
	 * @return <b>List of OFSwitchPort</b> A list of OFSwitchPorts.
	 */
	public static List<OFSwitchPort> ofSwitchPortListOf(Collection<OFPhysicalPort> ofpPorts) {
		if (ofpPorts == null) {
            throw new NullPointerException("Port list must not be null");
        }
		
		ArrayList<OFSwitchPort> ofSwitchPorts = new ArrayList<OFSwitchPort>(ofpPorts.size());
		
		for (OFPhysicalPort p: ofpPorts) {
			ofSwitchPorts.add(fromOFPhysicalPort(p));
		}
		
        return ofSwitchPorts;
	}
	
	/**
	 * 
	 * @param switchPorts
	 * @return
	 */
	public static List<OFPhysicalPort> ofPhysicalPortListOf(Collection<OFSwitchPort> switchPorts) {
		if (switchPorts == null) {
            throw new NullPointerException("Port list must not be null");
        }
		
		ArrayList<OFPhysicalPort> ofpPorts = new ArrayList<OFPhysicalPort>();
		
		for (OFSwitchPort p: switchPorts) {
			if (!ofpPorts.contains(p.getOFPhysicalPort()))
				ofpPorts.add(p.getOFPhysicalPort());
		}
		
		return ofpPorts;
	}
	
	/**
	 * Creates an OFSwitchPort from an OFPhysical port with VLAN Id 0.
	 * 
	 * @param ofpPort The OFPhysicalPort that needs to be transfered to a default OFSwitchPort.
	 * @return <b>OFSwitchPort</b> A default OFSwitchPort with VLAN Id 0.
	 */
	public static OFSwitchPort fromOFPhysicalPort(OFPhysicalPort ofpPort) {
        if (ofpPort == null) {
            throw new NullPointerException("OFPhysicalPort must not be null");
        }
        
        OFSwitchPort ofsPort =  new OFSwitchPort(ofpPort, (short) 0, OFPortState.isPortDown(ofpPort.getState()), OFPortState.getStpState(ofpPort.getState()));
        return ofsPort;
    }
	
	/**
	 * Creates an OFSwitchPort from an OFPhysical port with a specific VLAN Id.
	 * 
	 * @param ofpPort The OFPhysicalPort that needs to be transfered to a default OFSwitchPort.
	 * @param vlanId The VLAN Id of the new OFSwitchPort.
	 * @return <b>OFSwitchPort</b> A default OFSwitchPort with a specific VLAN Id.
	 */
	public static OFSwitchPort create(OFPhysicalPort ofpPort, short vlanId) {
		if (ofpPort == null) {
            throw new NullPointerException("OFPhysicalPort must not be null");
        }
        
        return new OFSwitchPort(ofpPort, vlanId, OFPortState.isPortDown(ofpPort.getState()), OFPortState.getStpState(ofpPort.getState()));
	}
	
	/**
	 * Extracts the VLAN Id from a virtual switch port Id. The VLAN Id is encoded
	 * in the last 16 bit of the port number.
	 * 
	 * @param portNumber The virtual switch port Id.
	 * @return <b>short</b> The VLAN Id of the virtual switch port.
	 */
	public static short vlanIdOf(int portNumber) {
		if (portNumber >= -32768 && portNumber < 65536)
			return ((short) 0);
		return (short) (portNumber & VLAN_BITMASK);
	}
	
	/**
	 * Extracts the physical port Id from a virtual switch port Id. The physical port Id is encoded
	 * in the first 16 bit of the port number.
	 * 
	 * @param portNumber The virtual switch port Id.
	 * @return <b>short</b> The physical port Id of the virtual switch port.
	 */
	public static short physicalPortIdOf(int portNumber) {
		if (portNumber >= -32768 && portNumber < 65536)
			return ((short) portNumber);
		return (short) ((portNumber & PORT_NUMBER_BITMASK) >> 16);
	}
	
	/**
	 * Creates a virtual port Id from a physical port id and a VLAN Id.
	 * 
	 * @param phyPortId The port Id of the physical port.
	 * @param vlan The VLAN Id.
	 * @return <b>int</b> The virtual port id.
	 */
	public static int virtualPortIdOf(short phyPortId, short vlan) {
		// Make sure that vlan is at least 0 also for untagged packets.
		vlan = (vlan < 0) ? 0 : vlan;
		
		return (phyPortId << 16) + vlan;
	}
	
	/**
	 * Creates a virtual port Id from a string.
	 * 
	 * @param virtPortId The port Id represented as a String in the form: virtualPortId(VlanId).
	 * @return <b>int</b> The virtual port id.
	 */
	public static int virtualPortIdOf(String virtPortId) throws NumberFormatException {
		String[] idElements = virtPortId.split("\\(");
		
		String phyId  = idElements[0];
		String vlanId = idElements[1].substring(0, idElements[1].length() - 1);
		
		return virtualPortIdOf(Short.valueOf(phyId), Short.valueOf(vlanId));
	}
	
	/**
	 * Creates a human readable string that represents the virtual port Id
	 * as a combination of the physical port Id and the VLAN Id as
	 * "physicalPortId(VlanId)".
	 * 
	 * @param virtPortId The virtual switch port Id.
	 * @return <b>String</b> A String that contains the physical port Id and the VLAN Id of a virtual switch port.
	 */
	public static String stringOf(int virtPortId) {
		return (OFSwitchPort.physicalPortIdOf(virtPortId) & 0xffff) + "(" + OFSwitchPort.vlanIdOf(virtPortId) + ")";  
	}
	
	/**
	 * 
	 * @param ofpPort
	 * @param vlanId
	 * @param portStateLinkDown
	 * @param portStateStp
	 */
	private OFSwitchPort(OFPhysicalPort ofpPort, short vlanId, boolean portStateLinkDown, OFPortState portStateStp) {
		
		if (ofpPort == null)
            throw new NullPointerException("OFPhysicalPort must not be null");

		this.ofpPort = ofpPort;
        this.portNumber = (ofpPort.getPortNumber() << 16) + vlanId;
        this.portStateLinkDown = portStateLinkDown;
        this.stpState = portStateStp;
        
        this.config = EnumBitmaps.toEnumSet(OFPortConfig.class, ofpPort.getConfig());
        this.currentFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getCurrentFeatures());
        this.advertisedFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getAdvertisedFeatures());
        this.supportedFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getSupportedFeatures());
        this.peerFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getPeerFeatures());
        
        this.portSpeed = getPhysicalPortSpeed();
	}

	@Override
	public String getName() {
		return ofpPort.getName() + "-" + this.getVlanId();
	}

	@Override
	public void setName(String name) {
		this.ofpPort.setName(name);
	}

	@Override
	public int getPortNumber() {
	//public short getPortNumber() {
		return this.portNumber;
	}

	@Override
	public void setPortNumber(int portNumber) {
		this.portNumber = portNumber;
	}

	@Override
	public long getHardwareAddress() {
		return MACAddress.valueOf(this.ofpPort.getHardwareAddress()).toLong();
	}

	@Override
	public void setHardwareAddress(long hardwareAddress) {
		this.ofpPort.setHardwareAddress(MACAddress.valueOf(hardwareAddress).toBytes());
	}

	@Override
	public Set<OFPortConfig> getConfig() {
		return Collections.unmodifiableSet(config);
	}

	@Override
	public void setConfig(int config) {
		this.ofpPort.setConfig(config);
	}

	@Override
	public int getState() {
		return this.ofpPort.getState();
	}

	@Override
	public void setState(int state) {
		this.ofpPort.setState(state);
	}

	@Override
	public Set<OFPortFeatures> getCurrentFeatures() {
		return Collections.unmodifiableSet(currentFeatures);
	}

	@Override
	public void setCurrentFeatures(int currentFeatures) {
		this.ofpPort.setCurrentFeatures(currentFeatures);
		this.currentFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getCurrentFeatures());
	}

	@Override
	public Set<OFPortFeatures> getAdvertisedFeatures() {
		return Collections.unmodifiableSet(advertisedFeatures);
	}

	@Override
	public void setAdvertisedFeatures(int advertisedFeatures) {
		this.ofpPort.setAdvertisedFeatures(advertisedFeatures);
		this.advertisedFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getAdvertisedFeatures());
	}

	@Override
	public Set<OFPortFeatures> getSupportedFeatures() {
		return Collections.unmodifiableSet(supportedFeatures);
	}

	@Override
	public void setSupportedFeatures(int supportedFeatures) {
		this.ofpPort.setSupportedFeatures(supportedFeatures);
		this.supportedFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getSupportedFeatures());
	}

	@Override
	public Set<OFPortFeatures> getPeerFeatures() {
		return Collections.unmodifiableSet(peerFeatures);
	}

	@Override
	public void setPeerFeatures(int peerFeatures) {
		this.ofpPort.setPeerFeatures(peerFeatures);
		this.peerFeatures = EnumBitmaps.toEnumSet(OFPortFeatures.class, ofpPort.getPeerFeatures());
	}

	@Override
	public int getCurrentPortSpeed() {
		if (!isEnabled()) {
            return 0;
		}
        return this.portSpeed;
	}
	
	@Override
	public void setCurrentPortSpeed(int portSpeed) {
		this.portSpeed = portSpeed;
	}

	@Override
	public void readFrom(ChannelBuffer data) {
		this.ofpPort.readFrom(data);
	}

	@Override
	public void writeTo(ChannelBuffer data) {
		this.ofpPort.writeTo(data);
	}

	@Override
	public boolean isLinkDown() {
		return portStateLinkDown;
	}

	@Override
	public boolean isEnabled() {
		return (!config.contains(OFPortConfig.OFPPC_PORT_DOWN));
	}
	
	@Override
	public short getVlanId() {
		return (short) (portNumber & VLAN_BITMASK);
	}
	
	/**
	 * 
	 * @return
	 */
	public OFPhysicalPort getOFPhysicalPort() {
		return this.ofpPort;
	}
	
	@Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((advertisedFeatures == null) ? 0 : advertisedFeatures.hashCode());
        result = prime * result + ((config == null) ? 0 : config.hashCode());
        result = prime * result + ((currentFeatures == null) ? 0 : currentFeatures.hashCode());
        result = prime * result + Arrays.hashCode(this.ofpPort.getHardwareAddress());
        result = prime * result + ((this.getName() == null) ? 0 : this.getName().hashCode());
        result = prime * result + ((peerFeatures == null) ? 0 : peerFeatures.hashCode());
        result = prime * result + portNumber;
        result = prime * result + (portStateLinkDown ? 1231 : 1237);
        result = prime * result + ((stpState == null) ? 0 : stpState.hashCode());
        result = prime * result + ((supportedFeatures == null) ? 0 : supportedFeatures.hashCode());
        return result;
    }
	
	@Override
    public boolean equals(Object obj) {
        if (this == obj) 
        	return true;
        if (obj == null) 
        	return false;
        if (getClass() != obj.getClass()) 
        	return false;
        OFSwitchPort other = (OFSwitchPort) obj;
        if (portNumber != other.portNumber) 
        	return false;
        if (this.getName() == null) {
            if (other.getName() != null) 
            	return false;
        } else {
        	if (!this.getName().equalsIgnoreCase(other.getName())) 
        		return false;
        }
        if (advertisedFeatures == null) {
            if (other.advertisedFeatures != null) 
            	return false;
        } else {
        	if (!advertisedFeatures.equals(other.advertisedFeatures))
        		return false;
        }
        if (config == null) {
            if (other.config != null) 
            	return false;
        } else {
        	if (!config.equals(other.config)) 
        		return false;
        }
        if (currentFeatures == null) {
            if (other.currentFeatures != null) 
            	return false;
        } else {
        	if (!currentFeatures.equals(other.currentFeatures))
        		return false;
        }
        if (this.getHardwareAddress() != other.getHardwareAddress())
            return false;
        if (peerFeatures == null) {
            if (other.peerFeatures != null) 
            	return false;
        } else { 
        	if (!peerFeatures.equals(other.peerFeatures)) 
        		return false;
        }
        if (portStateLinkDown != other.portStateLinkDown) 
        	return false;
        if (stpState != other.stpState) 
        	return false;
        if (supportedFeatures == null) {
            if (other.supportedFeatures != null) 
            	return false;
        } else { 
        	if (!supportedFeatures.equals(other.supportedFeatures))
        		return false;
        }
        return true;
    }
	
	@Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String linkState = (this.isEnabled()) ? "DOWN" : "UP";
        sb.append("Port [")
                .append(this.getName())
                .append("(").append(portNumber).append(")")
                .append(", hardwareAddress=")
                .append(HexString.toHexString(this.getHardwareAddress()))
                .append(", config=").append(config)
                .append(", link=").append(linkState)
                .append(", stpState=").append(stpState)
                .append(", currentFeatures=").append(currentFeatures)
                .append(", advertisedFeatures=").append(advertisedFeatures)
                .append(", supportedFeatures=").append(supportedFeatures)
                .append(", peerFeatures=").append(peerFeatures).append("]");
        return sb.toString();
    }
	
	/**
	 * Get the port speed of the physical port from its features.
	 * 
	 * @return The port speed of the physical port in Mpbs.
	 */
	private int getPhysicalPortSpeed() {
		PortSpeed maxSpeed = PortSpeed.SPEED_NONE;
        for (OFPortFeatures f: this.getCurrentFeatures()) {
            maxSpeed = PortSpeed.max(maxSpeed, f.getSpeed());
        }
        return (int) (maxSpeed.getSpeedBps() / 1000 / 1000);
	}

}
