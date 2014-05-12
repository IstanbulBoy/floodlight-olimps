package net.floodlightcontroller.configuration;

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

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * The configuration server that allows for storing configuration
 * information in JSON file.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface IConfigurationService extends IFloodlightService {

	/**
	 * Saves the current configuration of all modules implementing the
	 * IConfigurationListener interface to a configuration file. 
	 * 
	 * @param file An optional configuration file name.
	 */
	public void saveConfiguration(String fileName) throws IOException;
	
	/**
	 * Loads the configuration of all modules implementing the
	 * IConfigurationListener interface from a configuration file. 
	 * 
	 * @param file An optional configuration file name.
	 */
	public void restoreConfiguration(String fileName) throws IOException;
	
	/**
	 * Returns a string representing the current configuration of all 
	 * modules implementing the IConfigurationListener interface.
	 * 
	 * @param file An optional configuration file name.
	 * @return <b>String</b> A JSON string representing the current configuration
	 */
	public String showConfiguration(String fileName);
	
	/**
	 * Adds a configuration listener to the configuration manager.
	 */
	public void addListener(IConfigurationListener listener);
	
	/**
	 * Removes a configuration listener from the configuration manager.
	 */
	public void removeListener(IConfigurationListener listener);
}
