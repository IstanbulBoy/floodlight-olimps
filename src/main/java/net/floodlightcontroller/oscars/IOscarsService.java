package net.floodlightcontroller.oscars;

import java.util.Set;

import net.es.oscars.client.Client;
import net.es.oscars.wsdlTypes.CreateReply;
import net.es.oscars.wsdlTypes.GetTopologyResponseContent;
import net.es.oscars.wsdlTypes.GlobalReservationId;
import net.floodlightcontroller.core.module.IFloodlightService;

public interface IOscarsService extends IFloodlightService {
	
	/**
	 * 
	 * @return
	 */
	public String getURL();
	
	/**
	 * 
	 * @param url
	 */
	public void setURL(String url);
	
	/**
	 * 
	 * @return
	 */
	public String getRepository();
	
	/**
	 * 
	 * @param repo
	 */
	public void setRepository(String repo);
	
	/**
	 * 
	 * @return
	 */
	public short getLayer();
	
	/**
	 * 
	 * @param layer
	 * @return
	 */
	public void setLayer(short layer);
	
	/**
	 * 
	 * @return
	 */
	public String getPathSetupMode();
	
	/**
	 * 
	 * @param pathSetupMode
	 */
	public void setPathSetupMode(String pathSetupMode);
	
	/**
	 * 
	 * @return
	 */
	public Client getClient();
	
	/**
	 * 
	 * @return
	 */
	public GetTopologyResponseContent getNetworkTopology();
	
	/**
	 * 
	 * @param bandwith
	 * @param startTime
	 * @param endTime
	 * @param description
	 * @return
	 */
	public CreateReply createReservation(int bandwith, long startTime, long endTime, String description);
	
	/**
	 * 
	 * @param grid
	 * @return
	 */
	public String cancelReservation(GlobalReservationId grid);
	
	public Set<CreateReply> getReservations();
}
