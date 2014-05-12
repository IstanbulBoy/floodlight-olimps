package net.floodlightcontroller.multipath;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.multipath.IPathCacheService;
import net.floodlightcontroller.multipath.PathCache;
import net.floodlightcontroller.routing.Link;
import net.floodlightcontroller.routing.Path;

import org.junit.Before;
import org.junit.Test;

public class PathCacheTest {
	/** The patch cache to test. */
	PathCache pathCache = new PathCache();
	/** The Floodlight context. */
	FloodlightContext cntx;
	/** A standard switch id. */
	long switchId_1 = 1L;
	long switchId_2 = 2L;
	long switchId_3 = 3L;
	/** Port used by the links. */
	short port_1 = 1;
	short port_2 = 2;
	/** */
	int pathId_1 = 1;
	int pathId_2 = 2;
	/* */
	int capacity = 1;
	/* */
	Link link_1 = new Link(switchId_1, port_1, switchId_2, port_2);
	Link link_2 = new Link(switchId_2, port_1, switchId_3, port_1);
	/* */
	List<Link> links_1 = new LinkedList<Link>(Arrays.asList(link_1));
	List<Link> links_2 = new LinkedList<Link>(Arrays.asList(link_1, link_2));
	/* */
	Path path_1 = new Path(switchId_1, switchId_2, links_1, pathId_1, capacity);
	Path path_2 = new Path(switchId_2, switchId_3, links_2, pathId_2, capacity);
	
	@Before
    public void setUp() throws Exception {
		cntx = new FloodlightContext();
		FloodlightModuleContext fmc = new FloodlightModuleContext();
        fmc.addService(IPathCacheService.class, pathCache);
        
        pathCache.init(fmc);
        pathCache.startUp(fmc);
	}
	
	@Test
	public void SimpleEqualTest() {
		if (!path_1.equals(path_1)) {
			fail("Path_1 does NOT equal path_1: Strange");
		}
	}
	
	@Test
	public void SimpleUnEqualTest() {
		if (path_1.equals(path_2)) {
			fail("Path_1 equals path_2: Strange");
		}
	}

	@Test
	public void SimpleStoreTest() {
		Path storedPath = this.pathCache.addPath(path_1);
		if (!storedPath.equals(path_1)) {
			fail("Adding path_1 failed: Got " + storedPath);
		}
	}
	
	@Test
	public void SimpleIsEmptyTest() {
		if (!this.pathCache.isEmpty()) {
			fail("Path cache is not empty");
		}
		
		Path storedPath = this.pathCache.addPath(path_1);
		if (!storedPath.equals(path_1)) {
			fail("Adding path_1 failed: Got " + storedPath);
		}
		
		if (this.pathCache.isEmpty()) {
			fail("Path cache is empty");
		}
	}
	
	@Test
	public void SimpleContainsTest() {
		Path storedPath = this.pathCache.addPath(path_1);
		if (!storedPath.equals(path_1)) {
			fail("Adding path_1 failed: Got " + storedPath);
		}
		
		if (!this.pathCache.containsPath(path_1.getId())) {
			fail("PathId: Path cache does not contain path_1");
		}
		
		if (!this.pathCache.containsPath(switchId_1, switchId_2)) {
			fail("SwitchId: Path cache does not contain path_1");
		}
	}
	
	@Test
	public void SimpleRemoveByPathIdTest() {
		Path storedPath = this.pathCache.addPath(path_1);
		if (!storedPath.equals(path_1)) {
			fail("Adding path_1 failed: Got " + storedPath);
		}
		
		if (!this.pathCache.containsPath(path_1.getId())) {
			fail("Path cache does not contain path_1");
		}
		
		Path removedPath = this.pathCache.removePath(path_1.getId());
		if (removedPath == null) {
			fail("We did not remove anything.");
		}
	}
	
	@Test
	public void SimpleRemoveBySwitchIdsTest() {
		Path storedPath = this.pathCache.addPath(path_1);
		if (!storedPath.equals(path_1)) {
			fail("Adding path_1 failed: Got " + storedPath);
		}
		
		if (!this.pathCache.containsPath(switchId_1, switchId_2)) {
			fail("Path cache does not contain path_1");
		}

		Set<Path> removedPaths = this.pathCache.removePath(switchId_1, switchId_2);		
		if (removedPaths == null) {
			fail("We did not remove anything.");
		}
		if (removedPaths.size() != 1) {
			fail("Remove failed. Removed path set size differs from 1 and is: " + removedPaths.size());
		}
		
		for (Path path : removedPaths) {
			if (!path.equals(path_1)) {
				fail("We removed the wrong path: " + path);
			}
		}
	}
}
