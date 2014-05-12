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

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface IStatisticsCollectorService extends IFloodlightService {
	/** An alias to query all tables of a switch. */
	public static final byte TABLE_ALL = (byte) 0xff;
    /** Delay Scan flow tables of 5 seconds. In [ms]. */
    public static final int DEFAULT_SWITCH_FLOW_TBL_SCAN_INITIAL_DELAY_MSEC = 5 * 1000;
    /** Scan flow tables of each switch every 15 seconds in a staggered way. In [ms]. */
    public static final int DEFAULT_SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC  = 15 * 1000;
	/** This applications default flow cache query timeout in [sec]. */
	public static final int DEFAULT_FLOW_CACHE_QUERY_TIMEOUT_SEC = 2;
	/** This applications default switch query timeout in [sec]. */
	public static final int DEFAULT_SWITCH_QUERY_TIMEOUT_SEC = 10;
    
    /**
     * Getter for the packet rate. Returns the average packet rate.
     * 
     * @param switchId The switch to query.
     * @param port The port to query.
     * @return <b>long</b> The average packet rate in [packets/s].
     */
    public long getPacketRate(long switchId, int port);
    
    /**
     * Getter for the bit rate. Returns the average bit rate.
     * 
     * @param switchId The switch to query.
     * @param port The port to query.
     * @return <b>long</b> The average bit rate in [bit/s].
     */
    public long getBitRate(long switchId, int port);
    
    /**
     * Getter for the flow count. Returns the number of flows.
     * 
     * @param switchId The switch to query.
     * @param port The port to query.
     * @return <b>int</b> The number of flows on a given switch-port tuple.
     */
    public int getFlowCount(long switchId, int port);
    
    /**
     * Getter for a StatisticEntry message.
     * 
     * @param switchId The switch to query.
     * @param port The port to query.
     * @return <b>OFStatisticsReply</b> The statistics entry for a given switch port combination.
     */
    public StatisticEntry getStatisticEntry(long switchId, int port);
}
