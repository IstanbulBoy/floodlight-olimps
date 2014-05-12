package net.floodlightcontroller.wireless;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IWirelessHandover extends IFloodlightService {

	/**
	 * 
	 * @param apIp IP address of the wireless access point.
	 * @param apMac MAC address of the wireless access point.
	 * @param clientMac MAC address of the wireless client associated with the access point. 
	 * @param ssid The wireless network ID the client is associated with.
	 */
	public void associate(int apIp, long apMac, long clientMac, String ssid);
	
	/**
	 * 
	 * @param apIp IP address of the wireless access point.
	 * @param apMac MAC address of the wireless access point.
	 * @param clientMac MAC address of the wireless client associated with the access point. 
	 */
	public void disassociate(int apIp, long apMac, long clientMac);
}
