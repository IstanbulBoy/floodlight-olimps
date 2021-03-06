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

import java.util.Set;

import org.openflow.protocol.OFMatch;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.Path;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface IPathFinderService extends IFloodlightService {
	
	/**
	 * 
	 * @param srcNode
	 * @param dstNode
	 * @return
	 */
	public boolean hasPath(long srcNode, long dstNode);
	
	/**
	 * 
	 * @param srcNode
	 * @param dstNode
	 * @return
	 */
	public Path getPath(long srcNode, long dstNode, OFMatch match);
	
	/**
	 * Getter for all (multiple) paths between two nodes.
	 * 
	 * @param srcNode The source node of the paths.
	 * @param dstNode The destination node of the paths.
	 * @return <b>Set&lt;Path&gt;</b> Set of all (multiple) paths between source and destination node. 
	 */
	public Set<Path> getPaths(long srcNode, long dstNode);
	
	/**
	 * Getter for all (multiple) paths.
	 * 
	 * @return <b>Set&lt;Path&gt;</b> Set of all (multiple) paths between source and destination node. 
	 */
	public Set<Path> getPaths();
	
	/**
	 * Calculates paths between a source and destination node and puts them into a path cache.
	 * 
	 * @param srcNode The source node of the paths.
	 * @param dstNode The destination node of the paths.
	 */
	public void calculatePaths(long srcNode, long dstNode);
	
	/**
	 * 
	 * @return <b>Set of IPathSelecto</b> All available path sSelector.
	 */
	public Set<IPathSelector> getAllPathSelector();
	
	/**
	 * 
	 * @return <b>IPathSelector</b> The current path selector.
	 */
	public IPathSelector getPathSelector();
	
	/**
	 * 
	 * @param name The name of the path selector.
	 * @param args The optional arguments of the path selector.
	 * @return <b>IPathSelector</b> The current path selector.
	 */
	public IPathSelector setPathSelector(String name, String args);
	
	/**
	 * 
	 * @return <b>Set of IPathCalculator</b> All available path calculator.
	 */
	public Set<IPathCalculator> getAllPathCalculator();
	
	/**
	 * 
	 * @return <b>IPathCalculator</b> The current path calculator.
	 */
	public IPathCalculator getPathCalculator();
	
	/**
	 * 
	 * @param name The name of the path calculator.
	 * @return <b>IPathCalculator</b> The current path calculator.
	 */
	public IPathCalculator setPathCalculator(String name);
}
