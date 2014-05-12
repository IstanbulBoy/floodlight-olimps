package net.floodlightcontroller.appaware.web;

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

import java.io.IOException;

import net.floodlightcontroller.appaware.AppEntry;
import net.floodlightcontroller.appaware.IAppAwareService;
import net.floodlightcontroller.packet.IPv4;


import org.openflow.protocol.OFMatch;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

/**
 * AppAwareResouce allows to register data transfer applications that are identified by
 * their source IP, their destination IP, and their destination port, to the controller.
 * The controller can identify the flow of that application and treat them specially, if 
 * needed.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class AppAwareResource extends ServerResource {
	/** The logger. */
	protected static Logger log = LoggerFactory.getLogger(AppAwareResource.class);
	
	/**
     * Takes an application string in JSON format and parses it into
     * our AppEntry data structure, then adds it to the AppAware application.
     * 
     * @param fmJson The application entry in JSON format.
     * @return A string status message
     */
	@Post
    public String handlePost(String appJson) {
		/* The application aware service. */
		IAppAwareService appAware = (IAppAwareService) this.getContext().getAttributes().get(IAppAwareService.class.getCanonicalName());
		/* An initial application entry. */
		AppEntry appEntry = null;
		
		try {
            appEntry = jsonExtract(appJson);
        } catch (IOException e) {
            log.error("Error parsing new subnet mask: " + appJson, e);
            e.printStackTrace();
            return "{\"status\" : \"Error! Could not parse application, see log for details.\"}";
        }
		
		if (appEntry == null) {
			return "{\"status\" : \"Error! Could not parse application, see log for details.\"}";
		}
		
		// Extract application information.
		int appSrcIp     = appEntry.getMatch().getNetworkSource();
		int appDstIp     = appEntry.getMatch().getNetworkDestination();
		short appDstPort = appEntry.getMatch().getTransportDestination();
		String appName   = appEntry.getName();
		int appFileSize  = appEntry.getFileSize();
		
		// Add the new application to the app store.
		appAware.addApplication(appSrcIp, appDstIp, appDstPort, appName, appFileSize);
		
		// Return a JSON status message.
		return ("{\"status\" : \"application information set for " + appEntry.getName() + " : " + appName + " (" + appDstPort +")" + "\"}");
	}
	
	/**
	 * Extracts an application entry object form a given JSON string.
	 * 
	 * @param fmJson The JSON string to be extracted.
	 * @return <b>AppEntry</b> The application entry that is extracted from the JSON string.
	 * @throws IOException
	 */
	public static AppEntry jsonExtract(String fmJson) throws IOException {
		MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        OFMatch match = null;
        String appName = "";
        int fileSize = 0;
        int srcIp = 0;
        int dstIp = 0;
        short dstPort = 0;

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
            
            if (fieldName.equalsIgnoreCase("name")) {
            	appName = fieldValue;
            	continue;
            }
            if (fieldName.equalsIgnoreCase("size")) {
            	fileSize = Integer.parseInt(fieldValue);
            	continue;
            }
            if (fieldName.equalsIgnoreCase("src_ip")) {
            	srcIp = IPv4.toIPv4Address(fieldValue);
            	continue;
            }
            if (fieldName.equalsIgnoreCase("dst_ip")) {
            	dstIp = IPv4.toIPv4Address(fieldValue);
            	continue;
            }
            if (fieldName.equalsIgnoreCase("dst_port")) {
            	dstPort = Short.parseShort(fieldValue);
            	continue;
            }
        }
        
        if (srcIp != 0 && dstIp != 0 && dstPort != 0) {
        	match = new OFMatch()
        		.setNetworkSource(srcIp)
        		.setNetworkDestination(dstIp)
        		.setTransportDestination(dstPort);
        } else {
        	return null;
        }
		
        if (!appName.equalsIgnoreCase("") && fileSize != 0) {
        	return new AppEntry(appName, fileSize, match);
        } else {
        	return null;
        }
	}
}
