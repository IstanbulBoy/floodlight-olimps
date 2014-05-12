package net.floodlightcontroller.multipath.web;

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
import java.util.ArrayList;
import java.util.List;

import net.floodlightcontroller.multipath.IPathFinderService;
import net.floodlightcontroller.multipath.IPathSelector;
import net.floodlightcontroller.routing.Path;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class PathFinderResource extends ServerResource {
	@Get("json")
    public Object retrieve() {
		/* */
		IPathFinderService pathFinderService = (IPathFinderService) getContext().getAttributes().get(IPathFinderService.class.getCanonicalName());
		/* */
		String op = (String) getRequestAttributes().get("op");
		
		if (op.equalsIgnoreCase("paths")) {
			List<Path> l = new ArrayList<Path>(pathFinderService.getPaths());
			return l;
		}
		
		if (op.equalsIgnoreCase("selectors")) {
			return pathFinderService.getAllPathSelector();
		}
		
		if (op.equalsIgnoreCase("selector")) {
			return pathFinderService.getPathSelector();
		}
		
		// no known options found
        return "{\"paths\" : \"selectors\" : \"invalid operation\"}";
    }
	
	
	@Post
	public String setSelector(String psJson) {
		/* */
		IPathFinderService pathFinderService = (IPathFinderService) getContext().getAttributes().get(IPathFinderService.class.getCanonicalName());
		/* */
		String name = null;
		/* */
		String args = null;
		
		try {
			String value = jsonExtract(psJson);
			String[] valueElements = value.trim().toLowerCase().split(" ");
			name = valueElements[0];
			args = "";
			for (int i = 1; i < valueElements.length; i ++) {
				args = args + valueElements[i] + " "; 
			}
			args = args.trim();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if (name == null) {
			return "{\"status\" : \"Error! Could not parse selector, see log for details.\"}";
		} else {
			IPathSelector selector = pathFinderService.setPathSelector(name, args);
			if (selector == null) {
				return "{\"status\" : \"Error! Could not set selector, see log for details.\"}";
			} else {
				return "{\"status\" : \"New selector set to: " + selector.getName() + ".\"}";
			}
		}
	}
	
	/**
	 * 
	 * @param psJson
	 * @return
	 * @throws IOException
	 */
	private static String jsonExtract(String psJson) throws IOException {
		/* */
		MappingJsonFactory f = new MappingJsonFactory();
		/* */
        JsonParser jp;
        /* */
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
            
            if (fieldName.equalsIgnoreCase("name")) {
            	selectorName = fieldValue;
            }
            
            if (!selectorName.equalsIgnoreCase("")) {
            	return selectorName;
            }
        }
        
        return null;
	}
}
