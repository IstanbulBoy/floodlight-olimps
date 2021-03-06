package net.floodlightcontroller.multipath;

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

import org.openflow.protocol.OFMatch;

import net.floodlightcontroller.routing.Path;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 * 
 * TODO: This might be better a Floodlight service, i.e. extend IFloodlightService.
 *
 */
public interface IPathSelector {
	
	/**
	 * 
	 * @return
	 */
	public String getName();
	
	/**
	 * 
	 * @param srcSwitchId
	 * @param dstSwitchId
	 * @return <b>Path</b> The best path for the source-destination pair, or null of no path can be found.
	 */
	public Path selectPath(long srcSwitchId, long dstSwitchId);
	
	/**
	 * 
	 * @param srcSwitchId
	 * @param dstSwitchId
	 * @param match
	 * @return <b>Path</b> The best path for the source-destination pair, or null of no path can be found.
	 */
	public Path selectPath(long srcSwitchId, long dstSwitchId, OFMatch match);
	
	/**
	 * Set the optional arguments handled by the path selector.
	 * 
	 * @param args The optional arguments handled by the path selector.
	 */
	public void setArgs(String args);

}
