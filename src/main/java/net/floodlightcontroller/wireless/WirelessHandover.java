package net.floodlightcontroller.wireless;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.wireless.web.WirelessHandoverWebRoutable;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class WirelessHandover implements IFloodlightModule, IWirelessHandover {
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(WirelessHandover.class);
    
	/** Required Module: */
    protected IFloodlightProviderService floodlightProvider;
	/** Required Module: */
	protected IRestApiService restApi;
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IWirelessHandover.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IWirelessHandover.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		restApi.addRestletRoutable(new WirelessHandoverWebRoutable());
	}

	@Override
	public void associate(int apIp, long apMac, long clientMac, String ssid) {
		// TESTING
		System.out.println("WirelessHandover: ASSOCIATE:     " + "Wireless client " + MACAddress.valueOf(clientMac).toString() + " is associated to network " + ssid + " at AP " + MACAddress.valueOf(apMac).toString() + " with IP " + IPv4.fromIPv4Address(apIp));
	}

	@Override
	public void disassociate(int apIp, long apMac, long clientMac) {
		// TESTING
		System.out.println("WirelessHandover: DIS-ASSOCIATE: " + "Wireless client " + MACAddress.valueOf(clientMac).toString() + " is dis-associated from AP " + MACAddress.valueOf(apMac).toString() + " with IP " + IPv4.fromIPv4Address(apIp));
	}
	
}
