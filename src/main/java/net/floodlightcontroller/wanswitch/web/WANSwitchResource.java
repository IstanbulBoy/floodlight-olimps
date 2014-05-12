package net.floodlightcontroller.wanswitch.web;

import java.util.HashMap;
import java.util.Map;

import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.wanswitch.IWANSwitchService;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WANSwitchResource extends ServerResource {
	/** The logger. */
	protected static Logger log = LoggerFactory.getLogger(WANSwitchResource.class);
	
	@Get("json")
    public Map<Integer, Object> retrieve() {
		/* */
		HashMap<Integer, Object> result = new HashMap<Integer, Object>();
		/* */
		IWANSwitchService wanSwitchManager = (IWANSwitchService) getContext().getAttributes().get(IWANSwitchService.class.getCanonicalName());
		
		for (int portId : wanSwitchManager.getPorts()) {
			NodePortTuple npt = wanSwitchManager.getPort(portId);
			result.put(portId, npt);
			//result.append("    " + portId + ": " + HexString.toHexString(npt.getNodeId()) + " - " + OFSwitchPort.stringOf(npt.getPortId()) + "\n");
		}
		
		return result;
	}
}
