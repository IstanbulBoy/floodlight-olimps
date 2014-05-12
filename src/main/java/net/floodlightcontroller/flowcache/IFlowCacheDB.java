package net.floodlightcontroller.flowcache;

import java.util.Map;
import java.util.Set;

/**
 * The flow cache database interface specifies a minimum set of operations
 * the database should provide. By implementing this interface, every application
 * can have its own database, optimized for inserts and queries as they occur for 
 * that specific application. The application has to register its database at the
 * flow cache service. 
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface IFlowCacheDB {
	
	/**
	 * Stores a specific flow cache object in the database.
	 * 
	 * @param switchId The switchID that is associated with this flow cache object.
	 * @param fco The flow cache object to be stored.
	 * @param overrideEntries States whether new entries should be installed even if such an entry is already present.
	 * @return <b>boolean</b> True iff the object is successfully stored.
	 */
	public boolean storeEntry(long switchId, FlowCacheObj fco, boolean overrideEntries);
	
	/**
	 * Get a specific flow cache object from the database.
	 * 
	 * @param switchId The switchID that is associated with this flow cache object.
	 * @param fco The flow cache object to be taken.
	 * @return <b>FlowCacheObj</b> The flow cache object that was removed - or null.
	 */
	public FlowCacheObj getEntry(long switchId, FlowCacheObj fco);
	
	/**
	 * Removes a specific flow cache object from the database.
	 * 
	 * @param switchId The switchID that is associated with this flow cache object.
	 * @param fco The flow cache object to be removed.
	 * @return <b>FlowCacheObj</b> The flow cache object that was removed - or null.
	 */
	public FlowCacheObj removeEntry(long switchId, FlowCacheObj fco);
	
	/**
	 * Checks if the database has a specific flow cache object.
	 * 
	 * @param switchId The switchID that is associated with this flow cache object.
	 * @param fco The flow cache object to be checked.
	 * @return <b>boolean</b> True iff the object is in the flow cache database.
	 */
	public boolean hasEntry(long switchId, FlowCacheObj fco);
	
	
	/**
	 * Checks whether the database is empty or not.
	 * 
	 * @return <b>boolean</b> True iff no object is stored in this flow cache database.
	 */
	public boolean isEmpty();
	
	/**
	 * Gets all the flow cache objects stored in the database.
	 * 
	 * @return A Map (SwitchId -> SetOf FlowCacheObj) with all the flow cache objects in that database.
	 */
	public Map<Long, Set<FlowCacheObj>> getAllEntries();
	
	/**
	 * Queries the database to find one ore more flow cache objects that meet what we want to find.
	 * 
	 * @param query The flow cache query with what we want to find.
	 * @return A Map (SwitchId-> SetOf FlowCacheObj) with all the flow cache objects that match the query - or null.
	 */
	public Map<Long, Set<FlowCacheObj>> queryDB(long switchId, FlowCacheQuery query);
	
	/**
	 * Removes all entries in the database.
	 */
	public void clear();
}
