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

import org.openflow.protocol.statistics.OFAggregateStatisticsReply;
import org.openflow.protocol.statistics.OFFlowStatisticsReply;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class StatisticEntry {
	/** The packet counter from the last (previous) update. */
	private long lastPacketCount;
	/** The packet counter from the current update, e.g. from a statistics reply message. */
	private long currentPacketCount;
	/** The byte counter from the last (previous) update. */
	private long lastByteCount;
	/** The packet counter from the current update, e.g. from a statistics reply message. */
	private long currentByteCount;
	/** The flow counter from the current update, e.g. from a statistics reply message. */
	private int flowCount;
	/** The average packet rate. */
	private long averagePacketRate;
	/** The average packet rate. */
	private long averageByteRate;
	/** The starting timestamp in [ms]. */
	private long startTime;
	/** The duration in [ms] */
	private long duration;
	
	/**
	 * Default constructor.
	 * 
	 * @param packetCount A packet count derived, e.g. from a flow statistics reply message.
	 * @param byteCount A byte count derived, e.g. from a flow statistics reply message.
	 * @param flowCount A flow count derived, e.g. from a flow statistics reply message.
	 */
	public StatisticEntry(long packetCount, long byteCount, int flowCount) {
		this.lastPacketCount = 0;
		this.currentPacketCount = packetCount;
		this.lastByteCount = 0;
		this.currentByteCount = byteCount;
		this.flowCount = flowCount;
		this.averagePacketRate = 0;
		this.averageByteRate = 0;
		this.duration = 0;
		this.startTime = System.currentTimeMillis();
	}
	
	/**
	 * Convenience constructor to generate a statistics entry 
	 * from an OFAggregateStatisticsReply message.
	 * 
	 * @param msg A aggregate statistics reply message.
	 */
	public StatisticEntry(OFAggregateStatisticsReply msg) {
		this(msg.getPacketCount(), msg.getByteCount(), msg.getFlowCount());
	}
	
	/**
	 * Convenience constructor to generate a statistics entry 
	 * from an OFFlowStatisticsReply message.
	 * 
	 * @param msg A flow statistics reply message.
	 */
	public StatisticEntry(OFFlowStatisticsReply msg) {
		this(msg.getPacketCount(), msg.getByteCount(), 1);
	}
	
	/**
	 * Updates the statistics of this given statistics entry. Thus,
	 * it calculates the new packet rate and byte rate using an
	 * exponential weighted moving average window.
	 * 
	 * @param msg A statistics reply message.
	 */
	public void updateStatistics(OFAggregateStatisticsReply msg) {
		this.lastPacketCount = this.currentPacketCount;
		this.lastByteCount = this.currentByteCount;
		this.currentPacketCount = msg.getPacketCount();
		this.currentByteCount = msg.getByteCount();
		this.flowCount = msg.getFlowCount();
		this.duration += (System.currentTimeMillis() - this.startTime);
		this.calculateEWMA();
	}
	
	/**
	 * Updates the statistics of this given statistics entry. Thus,
	 * it calculates the new packet rate and byte rate using an
	 * exponential weighted moving average window.
	 * 
	 * @param msg A statistics reply message.
	 */
	public void updateStatistics(OFFlowStatisticsReply msg) {
		this.lastPacketCount = this.currentPacketCount;
		this.lastByteCount = this.currentByteCount;
		this.currentPacketCount = msg.getPacketCount();
		this.currentByteCount = msg.getByteCount();
		this.duration = (long) msg.getDurationSeconds() * 1000;
		this.calculateEWMA();
	}
    
	/**
	 * Getter for the packet count. Returns the current packet
	 * counter, i.e. the number of packets transfered by flow
	 * (or port) until now.
	 * 
	 * @return <b>long</b> The packets transfered by this flow.
	 */
	public long getPacketCount() {
		return this.currentPacketCount;
	}
    
    /**
     * Getter for the packet rate.
     * 
     * @return <b>long</b> The average packet rate in [packets/s].
     */
    public long getPacketRate() {
    	return this.averagePacketRate;
    }
    
    /**
     * Getter for the byte count. Returns the current byte
	 * counter, i.e. the number of bytes transfered by flow
	 * (or port) until now.
     * 
     * @return <b>long</b> The bytes transfered by this flow in [Byte]. 
     */
    public long getByteCount() {
    	return this.currentByteCount;
    }
    
    /**
     * Getter for the byte rate. Returns the average byte rate
     * calculated using an exponentially weighted moving average
     * filter.
     * 
     * @return <b>long</b> The average byte rate in [Byte/s].
     */
    public long getByteRate() {
    	return this.averageByteRate;
    }
    
    /**
     * Getter for the flow count. Returns the number of flows
     * represented by this statistics entry.
     * 
     * @return <b>int</b> The number of flows.
     */
    public int getFlowCount() {
    	return this.flowCount;
    }
    
    /**
     * Getter for the start time. Returns the start time
     * of this statistics representation.
     * 
     * @return <b>long</b> The start timestamp in [ms];
     */
    public long getStartTime() {
		return startTime;
	}

    /**
     * Getter for the duration. Returns the duration of this
     * statistics representation.
     * 
     * @return <b>long</b> The duration of this flow in [ms];
     */
	public long getDuration() {
		return duration;
	}
    
    @Override
    public String toString() {
    	/* The string builder. */
    	StringBuilder sb = new StringBuilder();
    	
    	sb.append("StatsEntry[");
    	//sb.append("packetCount=" + this.currentPacketCount + ", ");
    	sb.append("packetRate=" + this.averagePacketRate + ",");
    	sb.append("byteRate=" + this.averageByteRate + ",");
    	sb.append("flowCount=" + this.flowCount + ",");
    	sb.append("duration=" + this.duration);
    	sb.append("]");
    	
    	return sb.toString();
    }
    
    /**
     * Calculates an exponentially weighted moving average value
     * for the packet rate and for the byte rate. It takes the
     * scan interval as well as the actual scan period into account.
     */
    private void calculateEWMA() {
    	/* Get the current time. */
    	long currentTimestamp = System.currentTimeMillis();
    	/* Delay between two measurements in [seconds]. */
    	double delay = (currentTimestamp - this.startTime) / 1000;
    	/* Packet rate in packets per second. */
    	double currentPacketRate = (this.currentPacketCount - this.lastPacketCount) / delay;
    	/* Byte rate in bytes(!) per second. */
    	double currentByteRate = (this.currentByteCount - this.lastByteCount) / delay;
    	/* The for the next value. */
    	double weight = 1-Math.exp((this.startTime - currentTimestamp) / StatisticsCollector.DEFAULT_SWITCH_FLOW_TBL_SCAN_INTERVAL_MSEC);
    	
    	if (this.averageByteRate == 0) {
    		this.averageByteRate = (long) currentByteRate;
    	} else {
    		this.averageByteRate = (long) (weight * currentByteRate + (1-weight) * this.averageByteRate);
        	this.averageByteRate = (this.averageByteRate > 0) ? this.averageByteRate : 0;
    	}
    	
    	if (this.averagePacketRate == 0) {
    		this.averagePacketRate = (long) currentPacketRate;
    	} else {
    		this.averagePacketRate = (long) (weight * currentPacketRate + (1-weight) * this.averagePacketRate);
    		this.averagePacketRate = (this.averagePacketRate > 0) ? this.averagePacketRate : 0;
    	}
    	
    	this.startTime = currentTimestamp;
    }
}