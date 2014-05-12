package net.floodlightcontroller.appaware;

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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFFlowRemoved;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.appaware.web.AppAwareWebRoutable;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.flowcache.IFlowCacheService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;

/**
 * Implementation of the application aware service.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class AppAware implements IFloodlightModule, IAppAwareService, IOFMessageListener {
	/** The application aware application ID. */
	public static final int APPAWARE_APP_ID = 3;
	/** The default switch for installing default routes for small flows. */
	public static final boolean DEFAULT_ROUTE_FOR_SMALL_FLOWS = true;
	
	/** The default priority of installed FlowMods. */
	protected static final short FLOWMOD_DEFAULT_PRIORITY = 99;
	/** Default idle timeout. 60 seconds. */
	protected static final short FLOWMOD_DEFAULT_IDLE_TIMEOUT = 60;
	/** The hard time out. Should be greater then the IDLE_TIMEOUT. 120 seconds. */
	protected static final short FLOWMOD_DEFAULT_HARD_TIMEOUT = 120;
	/** The default maximum file size that characterizes a small flow. In [MByte]. */
	protected static final int SMALL_FLOW_FILE_SIZE = 0;
	
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(AppAware.class);
    
	/** Required Module: */
    protected IFloodlightProviderService floodlightProvider;
    /** Required Module: Floodlight Device Manager Service. */
	protected IDeviceService deviceManager;
	/** Required Module: */
	protected IRestApiService restApi;
	/** Required Module: */
	protected IFlowCacheService flowCache;
	
	/** srcIp -> dstIp -> port -> appEntry. */
	protected ConcurrentHashMap<Integer, HashMap<Integer, HashMap<Short, AppEntry>>> appCache;
	
	/** Default wildcards that match on dl_type, nw_src, nw_dst, nw_proto, tp_src, and tp_dst. */
	private int wildcards = 3145743;
	/** The maximum file size that characterizes a small flow. In [MByte]. */
	private int smallFlowFileSize = SMALL_FLOW_FILE_SIZE;

	@Override
	public String getName() {
		return "appaware";
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return (type.equals(OFType.FLOW_REMOVED) && name.equals("olimpsforwarding"));
	}
	
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IAppAwareService.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IAppAwareService.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IDeviceService.class);
		l.add(IFlowCacheService.class);
		l.add(IRestApiService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		flowCache = context.getServiceImpl(IFlowCacheService.class);
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		restApi.addRestletRoutable(new AppAwareWebRoutable());
		floodlightProvider.addOFMessageListener(OFType.FLOW_REMOVED, this);
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		appCache = new ConcurrentHashMap<Integer, HashMap<Integer, HashMap<Short, AppEntry>>>();
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		switch (msg.getType()) {
			case FLOW_REMOVED:
				OFFlowRemoved flowRemMsg = (OFFlowRemoved) msg;
				this.processFlowModRemovalMsg(sw, flowRemMsg, cntx);
				break;
			case PACKET_IN:
				OFPacketIn pktInMsg = (OFPacketIn) msg;
				this.processPacketInMsg(sw, pktInMsg, cntx);
				break;
			default:
				if (log.isDebugEnabled()) {
					log.debug("Ignoring mesg type: {}", msg.getType());
				}
				break;
		}
		return Command.CONTINUE;
	}

	@Override
	public synchronized void addApplication(int srcIp, int dstIp, short dstPort, String name, int fileSize) {
		/* dstIp -> port -> appEntry. */
		HashMap<Integer, HashMap<Short, AppEntry>> appCacheSrcIp = null;
		/* port -> appEntry. */
		HashMap<Short, AppEntry> appCacheDstIp = null;
		
		if (!appCache.containsKey(srcIp)) {
			appCache.put(srcIp, new HashMap<Integer, HashMap<Short, AppEntry>>());
		} 
		appCacheSrcIp = appCache.get(srcIp);
		
		if (!appCacheSrcIp.containsKey(dstIp)) {
			appCacheSrcIp.put(dstIp, new HashMap<Short, AppEntry>());
		}
		appCacheDstIp = appCacheSrcIp.get(dstIp);
		
		if(appCacheDstIp != null) {
			appCacheDstIp.put(dstPort, new AppEntry(name, fileSize, new OFMatch().setNetworkSource(srcIp).setNetworkDestination(dstIp).setTransportDestination(dstPort)));
		}
		
		// Add a default flow rule that sends packets to the controller.
		if (DEFAULT_ROUTE_FOR_SMALL_FLOWS && fileSize > this.smallFlowFileSize) {
			// Install the forwarding flow mod.
			this.installFlowMod(srcIp, dstIp, dstPort, false);
			// Install the reverse flow mod.
			this.installFlowMod(dstIp, srcIp, dstPort, true);
		}
	}
	
	@Override
	public synchronized AppEntry removeApplication(OFMatch match) {
		return this.removeApplication(match.getNetworkSource(), match.getNetworkDestination(), match.getTransportDestination());
	}
	
	/**
	 * Convenience method to remove an application.
	 * 
	 * @param srcIp The source IP of the application flows.
	 * @param dstIp The destination IP of the application flows.
	 * @param dstPort The destination port of the application flows.
	 * @return <b>AppEntry</b> The application entry that was removed.
	 */
	protected synchronized AppEntry removeApplication(int srcIp, int dstIp, short dstPort) {
		/* dstIp -> port -> appEntry. */
		HashMap<Integer, HashMap<Short, AppEntry>> appCacheSrcIp = null;
		/* port -> appEntry. */
		HashMap<Short, AppEntry> appCacheDstIp = null;
		/* */
		AppEntry removedEntry;
		
		if (appCache.containsKey(srcIp)) {
			appCacheSrcIp = appCache.get(srcIp);
		} else {
			return null;
		}
		
		if (appCacheSrcIp.containsKey(dstIp)) {
			appCacheDstIp = appCacheSrcIp.get(dstIp);
		} else {
			return null;
		}
		
		if (appCacheDstIp.containsKey(dstPort)) {
			removedEntry = appCacheDstIp.get(dstPort);
		} else {
			return null;
		}
		
		if (appCacheDstIp.size() == 0) {
			appCacheSrcIp.remove(dstIp);
		}
		if (appCacheSrcIp.size() == 0) {
			appCache.remove(srcIp);
		}
		
		return removedEntry;
	}
	
	@Override
	public AppEntry getApplication(OFMatch match) {		
		AppEntry appEntry = getApplication(match.getNetworkSource(), match.getNetworkDestination(), match.getTransportDestination());
		if (appEntry != null) {
			if (log.isDebugEnabled()) {
				log.debug("Application found for forwarding path: " + appEntry);
			}
			return appEntry;
		} else {
			// Go for the reverse path. We probably do not want to do that, since the reverse traffic can be totally different.
			appEntry = getApplication(match.getNetworkDestination(), match.getNetworkSource(), match.getTransportSource());
			if (appEntry != null) {
				if (log.isDebugEnabled()) {
					log.debug("Application found: for reverse path. " + appEntry);
				}
				//return appEntry;
				return null;
			} else {
				if (log.isWarnEnabled()) {
					log.warn(" No application found for match: " + match);
				}
				return null;
			}
		}
	}
	
	/**
	 * Convenience method to get an application.
	 * 
	 * @param srcIp The source IP of the application flows.
	 * @param dstIp The destination IP of the application flows.
	 * @param dstPort The destination port of the application flows.
	 * @return <b>AppEntry</b> he corresponding application entry.
	 */
	protected AppEntry getApplication(int srcIp, int dstIp, short dstPort) {
		/* dstIp -> port -> appEntry. */
		HashMap<Integer, HashMap<Short, AppEntry>> appCacheSrcIp = null;
		/* port -> appEntry. */
		HashMap<Short, AppEntry> appCacheDstIp = null;
		
		if (appCache.containsKey(srcIp)) {
			appCacheSrcIp = appCache.get(srcIp);
		} else {
			return null;
		}
		
		if (appCacheSrcIp.containsKey(dstIp)) {
			appCacheDstIp = appCacheSrcIp.get(dstIp);
		} else {
			return null;
		}
		
		if (appCacheDstIp.containsKey(dstPort)) {
			return appCacheDstIp.get(dstPort);
		} else {
			return null;
		}
	}

	@Override
	public Map<OFMatch, AppEntry> getAllApplication() {
		/* A new hash map that contains all registered applications. */
		Map<OFMatch, AppEntry> allApps = new HashMap<OFMatch, AppEntry>();
		
		if (this.appCache == null || this.appCache.isEmpty()) {
			return null;
		}
		
		for (int srcIp : this.appCache.keySet()) {
			for (int dstIp : this.appCache.get(srcIp).keySet()) {
				for (short port : this.appCache.get(srcIp).get(dstIp).keySet()) {
					if (this.appCache.get(srcIp).get(dstIp).get(port) != null) {
						OFMatch match = new OFMatch()
							.setNetworkSource(srcIp)
							.setNetworkDestination(dstIp)
							.setTransportDestination(port);
						allApps.put(match, this.appCache.get(srcIp).get(dstIp).get(port));
					}
				}
			}
		}
		
		return (allApps.isEmpty()) ? null : allApps;
	}
	
	@Override
	public void setSmallFlowFileSize(int smallFlowFileSize) {
		this.smallFlowFileSize = smallFlowFileSize;
	}
	
	/**
	 * Installs a flow mod that explicitly sends packets to the controller. It 
	 * checks on which switch that flow rule should be installed.
	 * 
	 * @param srcIp The source IP that identifies the flow.
	 * @param dstIp The destination IP that identifies the flow.
	 * @param dstPort The destination port that identifies the flow.
	 * @param reverse States whether or not the reverse flow should be installed as well.
	 */
	private void installFlowMod(int srcIp, int dstIp, short dstPort, boolean reverse) {
		@SuppressWarnings("unchecked")
		Iterator<Device> diterReverse= (Iterator<Device>) deviceManager.queryDevices(null, null, dstIp, null, null);
		// There should be only one device to the given IP address. In any case, we return only the first one found.
		if (diterReverse.hasNext()) {
			Device device = diterReverse.next();
			for (SwitchPort switchPort: device.getAttachmentPoints()) {
				// Install rule on all attachment point switches.
				long switchId = switchPort.getSwitchDPID();
				IOFSwitch iofSwitch = floodlightProvider.getSwitch(switchId);
				
				// Install the forwarding flow mod.
				this.installFlowMod(iofSwitch, dstIp, srcIp, dstPort, reverse);
			}
		} else {
			// Device yet unknown? Install flow rule on every switch.
			for (long switchId : floodlightProvider.getAllSwitchDpids()) {
				IOFSwitch iofSwitch = floodlightProvider.getSwitch(switchId);
				
				this.installFlowMod(iofSwitch, dstIp, srcIp, dstPort, reverse);
			}
		}
	}
	
	/**
	 * Installs a flow mod that explicitly sends packets to the controller.
	 * 
	 * @param iofSwitch The switch where the flow mod should be installed.
	 * @param srcIp The source IP that identifies the flow.
	 * @param dstIp The destination IP that identifies the flow.
	 * @param dstPort The destination port that identifies the flow.
	 * @param reverse States whether or not the reverse flow should be installed as well.
	 */
	private void installFlowMod(IOFSwitch iofSwitch, int srcIp, int dstIp, short port, boolean reverse) {
		//int wildcards = ((Integer) iofSwitch.getAttribute(IOFSwitch.PROP_FASTWILDCARDS)).intValue() & this.wildcards;
		//wildcards = wildcards & ~OFMatch.OFPFW_TP_SRC;
		
		int wildcards = OFMatch.OFPFW_ALL &
				~OFMatch.OFPFW_DL_TYPE &
				~OFMatch.OFPFW_NW_DST_MASK &
				~OFMatch.OFPFW_NW_SRC_MASK &
				~OFMatch.OFPFW_NW_PROTO;
		
		// The now OF match.
		OFMatch match = new OFMatch();
		if (reverse) {
			match.setTransportDestination(port);
			wildcards = wildcards & ~OFMatch.OFPFW_TP_DST;
		} else {
			match.setTransportSource(port);
			wildcards = wildcards & ~OFMatch.OFPFW_TP_SRC;
		}
		match.setWildcards(wildcards);
		match.setDataLayerType(Ethernet.TYPE_IPv4);
		match.setNetworkProtocol(IPv4.PROTOCOL_TCP);
		match.setNetworkSource(srcIp);
		match.setNetworkDestination(dstIp);
		
		
		// The new FlowMod message.
		OFFlowMod flowMod = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		flowMod.setPriority(FLOWMOD_DEFAULT_PRIORITY);
		flowMod.setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT);
		flowMod.setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT);
		flowMod.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		flowMod.setFlags(OFFlowMod.OFPFF_SEND_FLOW_REM);
		flowMod.setCookie(AppCookie.makeCookie(APPAWARE_APP_ID, 0));
		flowMod.setOutPort(OFPort.OFPP_CONTROLLER.getValue());
		flowMod.setMatch(match);
		
		// Actions: send packet to controller.
        short actionsLength = 0;
        List<OFAction> actions = new ArrayList<OFAction>();
        // Set the output action.
        actions.add(new OFActionOutput(OFPort.OFPP_CONTROLLER.getValue()));
        actionsLength += OFActionOutput.MINIMUM_LENGTH;
        flowMod.setActions(actions);
        // Length
        flowMod.setLength((short) (OFFlowMod.MINIMUM_LENGTH + actionsLength));

		try {
			iofSwitch.write(flowMod, null);
        	iofSwitch.flush();
        } catch (IOException e) {
            log.error("Failed to write {} to switch {}", new Object[]{ flowMod, iofSwitch }, e);
        }
	}
	
	/**
	 * Handles a packet in message.
	 * 
	 * @param sw The switch that send the packet in message.
	 * @param pktInMsg The packet in message.
	 * @param cntx The Floodlight context.
	 */
	private void processPacketInMsg(IOFSwitch sw, OFPacketIn pktInMsg, FloodlightContext cntx) {
		// TODO
	}

	/**
	 * Handles a flow removed message.
	 * 
	 * @param sw The switch that send the flow removed message.
	 * @param flowRemMsg The flow removed message.
	 * @param cntx The Floodlight context.
	 */
	private synchronized void processFlowModRemovalMsg(IOFSwitch sw, OFFlowRemoved flowRemMsg, FloodlightContext cntx) {
		// TODO
	}

}
