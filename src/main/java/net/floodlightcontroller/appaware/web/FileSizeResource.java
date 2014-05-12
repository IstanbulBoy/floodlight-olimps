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

import net.floodlightcontroller.appaware.IAppAwareService;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

/**
 * FileSizeResource allows to register file sizes that might be treaded
 * specially by the controller.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class FileSizeResource extends ServerResource {
	
	@Post
	public String setSelector(String psJson) {
		/* The application aware service. */
		IAppAwareService appAware = (IAppAwareService) this.getContext().getAttributes().get(IAppAwareService.class.getCanonicalName());
		/* The initial file size string. */
		String value = null;
		/* The initial file size that characterizes small flow. */
		int smallFlowFileSize = 0;
		
		try {
			value = jsonExtract(psJson);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (value != null) {
			smallFlowFileSize = Integer.parseInt(value);
			appAware.setSmallFlowFileSize(smallFlowFileSize);
			return "{\"status\" : \"New small flow file size set to: " + value + ".\"}";
		} else {
			return "{\"status\" : \"Error! Could not set small flow file size, see log for details.\"}";
		}
	}
	
	/**
	 * Extracts the file size form a given JSON string.
	 * 
	 * @param psJson The JSON string to be extracted.
	 * @return <b>String</b> The file size as a string.
	 * @throws IOException
	 */
	private static String jsonExtract(String psJson) throws IOException {
		MappingJsonFactory f = new MappingJsonFactory();
        JsonParser jp;
        String selectorName = "";

        try {
            jp = f.createJsonParser(psJson);
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
            
            if (fieldName.equalsIgnoreCase("size")) {
            	selectorName = fieldValue;
            }
            
            if (!selectorName.equalsIgnoreCase("")) {
            	return selectorName;
            }
        }
        
        return null;
	}
}
