package net.floodlightcontroller.configuration.web;

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

import net.floodlightcontroller.configuration.ConfigurationManager;
import net.floodlightcontroller.configuration.IConfigurationService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RestoreConfigurationResource restores a configuration file.
 * 
 * @author Michael Bredel <michael.bredel@caltech.edu>
 */
public class RestoreConfigurationResource extends ServerResource {
	/** The logger. */
	protected static Logger log = LoggerFactory.getLogger(RestoreConfigurationResource.class);
	
	@Get
	public void StoreConfiguration() {
		/* Get the configuration service. */
		IConfigurationService configService = (IConfigurationService) getContext().getAttributes().get(IConfigurationService.class.getCanonicalName());
		
		String param = (String) getRequestAttributes().get("file");
		if (param != null) {
			try {
				configService.restoreConfiguration(param);
				if (log.isDebugEnabled())
					log.debug("Restore configuration from file {}.", param);
			} catch (IOException e) {
				log.warn("Restore configuration from file {} failed.", param);
			}
		} else {
			try {
				configService.restoreConfiguration(null);
				if (log.isDebugEnabled())
					log.debug("Restore configuration from default file {}.", ConfigurationManager.DEFAULT_FILE_NAME);
			} catch (IOException e) {
				log.warn("Restore configuration from default file {} failed.", ConfigurationManager.DEFAULT_FILE_NAME);
			}
		}
	}
}
