package net.floodlightcontroller.util;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortFeatures;

public class Utils {
	
	/**
	 * Retrieves the port capacity from the port features. 
	 * 
	 * @param port
	 * @return <b>int</b> the capacity of the port in [Mbit/s].
	 * 
     * TODO: Move to OFPhysicalPort.
     */
    public static int getPortCapacity(OFPhysicalPort port) {
    	if (port == null) 
        	return 0;
    	if((OFPortFeatures.OFPPF_10MB_FD.getValue() & port.getCurrentFeatures()) > 0)
    		return 10;
    	if((OFPortFeatures.OFPPF_100MB_HD.getValue() & port.getCurrentFeatures()) > 0)
    		return 10;
    	if((OFPortFeatures.OFPPF_100MB_FD.getValue() & port.getCurrentFeatures()) > 0)
    		return 100;
    	if((OFPortFeatures.OFPPF_100MB_HD.getValue() & port.getCurrentFeatures()) > 0)
    		return 100;
    	if((OFPortFeatures.OFPPF_1GB_FD.getValue() & port.getCurrentFeatures()) > 0)
    		return 1000;
    	if((OFPortFeatures.OFPPF_1GB_HD.getValue() & port.getCurrentFeatures()) > 0)
    		return 1000;
    	if((OFPortFeatures.OFPPF_10GB_FD.getValue() & port.getCurrentFeatures()) > 0)
    		return 10000;
    	
    	return 0;
    }

	/**
	 * Private constructor to avoid instantiation.
	 */
	private Utils() {
		// NO-OP
	}

}
