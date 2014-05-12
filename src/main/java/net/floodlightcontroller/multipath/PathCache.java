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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.routing.EndPoints;
import net.floodlightcontroller.routing.Path;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 * 
 * TODO: Use Google'S Guava Cache.
 */
public class PathCache implements IFloodlightModule, IPathCacheService {
	/** Logger to log ProactiveFlowPusher events. */
	protected static Logger log = LoggerFactory.getLogger(PathCache.class);
	/** Required Module: Floodlight Provider Service. */
	protected IFloodlightProviderService floodlightProvider;
	/** Stores all paths established in the topology: [EndPoints -> PathSet]. */
	protected ConcurrentHashMap<EndPoints, Set<Path>> endPointsToPathMap;
	/** A Set of already used path numbers: [pathId -> Path]. */
	protected ConcurrentHashMap<Integer, Path> pathIdToPathMap;
//	/** Convenience structure to improve search performance. PathHash -> SetOf FlowCachObj. */
//	protected ConcurrentHashMap<Integer, Set<Integer>> pathToFcoMap;
//	/** Convenience structure to improve search performance. FcoHash -> SetOf Path. */
//	protected ConcurrentHashMap<Integer, Set<Path>> fcoToPathMap;
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(IPathCacheService.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(IPathCacheService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		endPointsToPathMap = new ConcurrentHashMap<EndPoints, Set<Path>>();
		pathIdToPathMap = new ConcurrentHashMap<Integer, Path>();
//		pathToFcoMap = new ConcurrentHashMap<Integer, Set<Integer>>();
//		fcoToPathMap = new ConcurrentHashMap<Integer, Set<Path>>();
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		// NO-OP
	}
	
	@Override
	public synchronized Path addPath(Path path) {
		//  Put path into endPointsToPathMap.
		EndPoints endPoints = path.getEndPoints();
		if(!this.endPointsToPathMap.containsKey(endPoints)) {
			this.endPointsToPathMap.put(endPoints, new HashSet<Path>());
		}
		this.endPointsToPathMap.get(endPoints).add(path);
		
		// Update the paths ID.
		path.setId(this.getNextPathId());
		
		// Put path into pathIdToPathMap.
		this.pathIdToPathMap.put(path.getId(), path);
		
		// TODO: Check that path was stored correctly in all maps.
		
		// Return successfully install path.
		return path;
	}
	
	@Override
	public synchronized Set<Path> addPaths(Set<Path> pathSet) {
		for (Path path : pathSet) {
			this.addPath(path);
		}
		
		// Return successfully installed set of paths.
		return pathSet;
	}
	
	@Override
	public synchronized Path removePath(int pathId) {
		// Get and remove path object from pathIdToPathMap.
		Path path = this.pathIdToPathMap.remove(pathId);
		
		// Get and remove path object from endPointsToPathMap.
		if (path != null) {
			EndPoints endPoints = path.getEndPoints();
			this.endPointsToPathMap.get(endPoints).remove(path);

			if (this.endPointsToPathMap.get(endPoints).isEmpty()) {
				this.endPointsToPathMap.remove(endPoints);
			}
			
		}
		
		// Return removed path object.
		return path;
	}
	
	@Override
	public synchronized Set<Path> removePath(long srcSwitchId, long dstSwitchId) {
		/* A set of paths recently removed form the path cache. */
		Set<Path> paths = new HashSet<Path>();
		
		// Get and remove path object from endPointsToPathMap.
		EndPoints endPoints = new EndPoints(srcSwitchId, dstSwitchId);
		for (Iterator<Map.Entry<EndPoints, Set<Path>>> iter = endPointsToPathMap.entrySet().iterator(); iter.hasNext();) {
			Map.Entry<EndPoints, Set<Path>> entry = iter.next();
			if (entry.getKey().equals(endPoints)) {
				paths.addAll(entry.getValue());
				iter.remove();
			}
		}
		if (this.endPointsToPathMap.get(endPoints) != null && this.endPointsToPathMap.get(endPoints).isEmpty()) {
			this.endPointsToPathMap.remove(endPoints);
		}
		
		// Get and remove path object from pathIdToPathMap.
		for (Path path : paths) {
			this.pathIdToPathMap.remove(path.getId());
		}
		
		return (paths.isEmpty()) ? null : paths;
	}
	
	@Override
	public Path getPath(int pathId) {
		return this.pathIdToPathMap.get(pathId);
	}

	@Override
	public Set<Path> getAllPaths(long srcSwitchId, long dstSwitchId) {
		EndPoints endPoints = new EndPoints(srcSwitchId, dstSwitchId);
		return this.endPointsToPathMap.get(endPoints);
	}

	@Override
	public Set<Path> getAllPaths() {
		Set<Path> paths = new HashSet<Path>();
		paths.addAll(this.pathIdToPathMap.values());
		return (paths.isEmpty()) ? null : paths;
	}
	
	@Override
	public Set<EndPoints> getAllEndPoints() {
		return this.endPointsToPathMap.keySet();
	}
	
	@Override
	public boolean containsPath(Path path) {
		return this.pathIdToPathMap.containsKey(path.getId());
	}
	
	@Override
	public boolean containsPath(int pathId) {
		return this.pathIdToPathMap.containsKey(pathId);
	}
	
	@Override
	public boolean containsPath(long srcSwitchId, long dstSwitchId) {
		EndPoints endPoints = new EndPoints(srcSwitchId, dstSwitchId);
		return this.endPointsToPathMap.containsKey(endPoints);
	}

	@Override
	public int size() {
		return this.pathIdToPathMap.size();
	}

	@Override
	public boolean isEmpty() {
		return this.pathIdToPathMap.isEmpty();
	}
	
	@Override
	public void clear() {
		this.pathIdToPathMap.clear();
		this.endPointsToPathMap.clear();
//		pathToFcoMap.clear();
//		fcoToPathMap.clear();
	}
	
	@Override
	public synchronized int getNextPathId() {
		/* The new path id. */
		int newPathId = 1;
		
		if (pathIdToPathMap.isEmpty()) {
			return newPathId;
		}

		int maxPathId = Collections.max(pathIdToPathMap.keySet());
		
		if (pathIdToPathMap.size() == maxPathId) {
			return ++maxPathId;
		}
		
		for (int i = 1; i <= maxPathId; i++) {
			if (!pathIdToPathMap.containsKey(i)) {
				newPathId = i;
				break;
			}
		}
		
		return newPathId;
	}
}
