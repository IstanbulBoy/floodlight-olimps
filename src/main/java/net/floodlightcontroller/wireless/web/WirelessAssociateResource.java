package net.floodlightcontroller.wireless.web;

import java.io.IOException;

import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.wireless.IWirelessHandover;


import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

public class WirelessAssociateResource extends ServerResource {
	/** The logger. */
	protected static Logger log = LoggerFactory.getLogger(WirelessAssociateResource.class);
	
	/**
	 *
	 */
	protected class ResourceEntry {
		/** */
		public int apIp;
		/** */
        public long apMac;
        /** */
        public long clientMac;
        /** */
        public String ssid;
        
        /**
         * Checks if the resource entry is valid, i.e. it contains all
         * relevant information.
         * 
         * @param withSSID Check if the wireless SSID should be part of the check.
         * @return True iff the entry contains all relevant information.
         */
        public boolean isValid() {
        	if (apIp != 0 && apMac != 0 && clientMac != 0 && ssid != null && !ssid.equalsIgnoreCase("")) {
        		return true;
            } else {
            	return false;
            }
        }
	}
	
	/**
     * @param hoJson The handover entry in JSON format.
     * @return A string status message
     */
	@Post
    public String handlePost(String hoJson) {
		/* */
		IWirelessHandover handoverManager = (IWirelessHandover) this.getContext().getAttributes().get(IWirelessHandover.class.getCanonicalName());
		/* */
		ResourceEntry entry = null;
		
		try {
            entry = jsonExtract(hoJson);
        } catch (IOException e) {
            e.printStackTrace();
            return "{\"status\" : \"Error! Could not parse message, see log for details.\"}";
        }
		
		if (entry == null) {
			return "{\"status\" : \"Error! Could not parse message, see log for details.\"}";
		}
		
		// Associate a client.
		handoverManager.associate(entry.apIp, entry.apMac, entry.clientMac, entry.ssid);
		
		// Return a JSON status message.
		return ("{\"status\" : \"client associated\"}");
	}
	
	/**
	 * 
	 * @param fmJson
	 * @return
	 * @throws IOException
	 */
	public ResourceEntry jsonExtract(String fmJson) throws IOException {
		/* */
		MappingJsonFactory f = new MappingJsonFactory();
		/* */
        JsonParser jp;
        /* */
        ResourceEntry entry = new ResourceEntry();

        try {
            jp = f.createJsonParser(fmJson);
        } catch (JsonParseException e) {
            throw new IOException(e);
        }

        jp.nextToken();
        if (jp.getCurrentToken() != JsonToken.START_OBJECT) {
            throw new IOException("Expected START_OBJECT");
        }
        
        while (jp.nextToken() != JsonToken.END_OBJECT) {
            if (jp.getCurrentToken() != JsonToken.FIELD_NAME) {
                throw new IOException("Expected FIELD_NAME");
            }
            
            String fieldName =  jp.getCurrentName();
            jp.nextToken();
            String fieldValue = jp.getText();
            
            if (fieldName.equalsIgnoreCase("ap-ip")) {
            	entry.apIp = IPv4.toIPv4Address(fieldValue);
            	continue;
            }
            if (fieldName.equalsIgnoreCase("ap-mac")) {
            	entry.apMac = MACAddress.valueOf(fieldValue).toLong();
            	continue;
            }
            if (fieldName.equalsIgnoreCase("client-mac")) {
            	entry.clientMac = MACAddress.valueOf(fieldValue).toLong();
            	continue;
            }
            if (fieldName.equalsIgnoreCase("ssid")) {
            	entry.ssid = fieldValue;
            	continue;
            }
        }
        
        if (entry.isValid()) {
        	return entry;
        } else {
        	return null;
        }
       
	}
}
