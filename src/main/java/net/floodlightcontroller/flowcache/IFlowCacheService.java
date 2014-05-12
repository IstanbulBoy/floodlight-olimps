package net.floodlightcontroller.flowcache;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import net.floodlightcontroller.core.module.IFloodlightService;

import org.openflow.protocol.OFMatch;
import org.openflow.protocol.action.OFAction;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface IFlowCacheService extends IFloodlightService {
    /** Delay Scan flow tables of 5 minutes. */
    public static final int SWITCH_FLOW_TBL_SCAN_INITIAL_DELAY_MSEC = 5 * 60 * 1000;
    /** Scan flow tables of each switch every 15 minutes in a staggered way. */
    public static final int SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC  = 15 * 60 * 1000;
	/** The default timeout for callable tasks. */
    public static final long DEFAULT_TIMEOUT = 60;
    /** THe default timeout unit for callable tasks. */
    public static final TimeUnit DEFAULT_TIMEOUT_UNIT = TimeUnit.SECONDS;
    /** The name of the default flow cache database. */
    public static final String DEFAULT_DB_NAME = "default";
	
	/**
	 * Register a flow cache database for an application at the flow
	 * cache service. The application can use its database to store
	 * flow-mods.
	 * 
	 * @param appName The application name that uses this database.
	 * @param flowCacheDB The application specific database.
	 */
	public void registerFlowCacheDB(String appName, IFlowCacheDB flowCacheDB);
	
	/**
	 * Remove the database that belongs to the given application.
	 * 
	 * @param appName THe name of the application which database should be removed.
	 * @return <b>IFlowCacheDB</b> The database that is removed.
	 */
	public IFlowCacheDB unRegisterFlowCacheDB(String appName);
	
	/**
	 * Adds a new flow to the flow cache and returns the corresponding
	 * flow cache object or null if no flow was stored.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param match The match of the flow.
	 * @param cookie The cookie that is installed with the flow mod.
	 * @param priority The priority of the flow.
	 * @param actions The actions of the flow mod.
	 * @return <b>FlowCacheObj</b> The flow cache object that was installed - or null no flow was stored in the cache.
	 */
	public FlowCacheObj addFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions);
	
	/**
	 * Removes a flow from the flow cache and returns the corresponding
	 * flow cache object or null if no flow was removed.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param match The match of the flow.
	 * @param cookie The cookie that is installed with the flow mod.
	 * @param priority The priority of the flow.
	 * @param actions The actions of the flow mod.
	 * @return <b>FlowCacheObj</b> The flow cache object that was removed from the flow cache - or null if no flow was removed.
	 */
	public FlowCacheObj removeFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions);
	
	/**
	 * Gets a flow from the flow cache and returns the corresponding
	 * flow cache object or null if no flow was found.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param match The match of the flow.
	 * @param cookie The cookie that is installed with the flow mod.
	 * @param priority The priority of the flow.
	 * @param actions The actions of the flow mod.
	 * @return <b>FlowCacheObj</b> The flow cache object from the flow cache - or null if no flow was found.
	 */
	public FlowCacheObj getFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions);
	
	/**
	 * Getter for all flows of a application.
	 * 
	 * @param appName The application name that uses this database.
	 * @return A map (switchId -> SetOf FlowCacheObj) that contains all flow cache objects of the applications database.
	 */
	public Map<Long, Set<FlowCacheObj>> getAllFlows(String appName);
	
	/**
	 * Getter for all flows of the flow cache.
	 * 
	 * @return A map (switchId -> SetOf FlowCacheObj) that contains all flow cache objects of database.
	 */
	public Map<Long, Set<FlowCacheObj>> getAllFlows();
	
	/**
	 * Checks whether a flow is present in the flow cache or not. Returns true
	 * if the flow is present.
	 * 
	 * @param appName The application name that uses this database.
	 * @param switchId The unique switch ID where this flow is installed.
	 * @param match The match of the flow.
	 * @param cookie The cookie that is installed with the flow mod.
	 * @param priority The priority of the flow.
	 * @param actions The actions of the flow mod.
	 * @return <b>boolean</b> True if the flow is present in the flow cache.
	 */
	public boolean hasFlow(String appName, long switchId, Long cookie, short priority, OFMatch match, List<OFAction> actions);

	/**
	 * Removes all flows installed on given switch.
	 * 
	 * @param switchId The unique switch ID where this flow is installed.
	 */
	public void deleteFlowCacheBySwitch(long switchId);
	
	/**
	 * Submits a query to the flow cache looking for flow cache objects. The query
	 * is handled asynchronously. The query response message is pushed to the
	 * IFlowCacheQueryResponseHandler.
	 * 
	 * @param flowCachequery The flow cache query comprising all the information we are looking for.
	 */
	@Deprecated
	public void submitFlowCacheQuery(FlowCacheQuery flowCachequery);
	
	/**
	 * Submits a query to the flow cache looking for flow cache objects. The query
	 * is handled asynchronously.
	 * 
	 * @param flowCacheQuery The flow cache query comprising all the information we are looking for.
	 * @return <b>Future</b> A future object that contains the information we are looking for - or null if nothing was found.
	 */
	public Future<FlowCacheQueryResp> queryDB(FlowCacheQuery flowCacheQuery);
	
	/**
	 * Submits a list of queries to the flow cache looking for flow cache objects. The queries
	 * are handled asynchronously.
	 * 
	 * @param flowCacheQueryList The list of flow cache queries comprising all the information we are looking for.
	 * @return A list of future objects that contain the information we are looking for - or null if nothing was found.
	 */
	public List<Future<FlowCacheQueryResp>> queryDB(List<FlowCacheQuery> flowCacheQueryList);
	
	/**
	 * Query a switch directly by sending flow stats request messages.
	 * 
	 * @param switchId The unique switch ID where this flow is installed.
	 */
	@Deprecated
	public void querySwitchFlowTable(long switchId);

}
