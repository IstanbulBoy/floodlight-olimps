package net.floodlightcontroller.cli.utils;

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

import java.text.SimpleDateFormat;
import java.util.Date;

import org.openflow.util.HexString;

/**
 * Some utils used by the cli modules.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class Utils {
	
	/**
	 * Parses a date and returns a formated date string in the
	 * form: "yyyy-MM-dd HH:mm:ss z", where z is the time zone.
	 * 
	 * @param timestamp The unix timestamp that needs to be converted into a formated string.
	 * @return <b>String</b> A formated date string.
	 */
	public static String parseDate(long timestamp) {
		Date date = new Date ();
		date.setTime(timestamp);
		return parseDate(date);
	}
	
	/**
	 * Parses a date and returns a formated date string in the
	 * form: "yyyy-MM-dd HH:mm:ss z", where z is the time zone.
	 * 
	 * @param date The date that needs to be converted into a formated string.
	 * @return <b>String</b> A formated date string.
	 */
	public static String parseDate(Date date) {
		SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		return dateformat.format(date);
	}
	
	/**
	 * Parses the capacity to a human readable string.
     * 
     * @param capacity The capacity of the port.
     * @return <b>String</b> The capacity in a human readable string representation.
     */
    public static String parseCapacity(int capacity) {
    	/* States whether the link is full or half duplex. */
    	boolean fullDuplex = true;
    	/* The resulting string. */
    	String result = "";
    		
    	if (capacity < 0) {
    		capacity = capacity * (-1);
    		fullDuplex = false;
    	}
    	
    	switch(capacity) {
    	case 10:
    		result += "10 MB";
    		break;
    	case 100:
    		result += "100 MB";
    		break;
    	case 1000:
    		result += "1 GB";
    		break;
    	case 10000:
    		result += "10 GB";
    		break;
    	case 40000:
    		result += "40 GB";
    		break;
    	case 100000:
    		result += "100 GB";
    		break;
    	default:
    		if (capacity >= 1000) {
    			result += String.valueOf(capacity/1000) + " GB";
    		} else {
    			result += String.valueOf(capacity) + " MB";
    		}
    	}
    	
    	if (!fullDuplex)
    		result += " HD";
    	
    	return result;
    }
    
	/**
	 * Parses the bit rate to a human readable string.
	 * 
	 * @param bitRate The bitrate to parse
	 * @return <b>String</b> The bitrate in a human readable string representation.
	 */
	public static String parseBitRate(long bitRate) {
		if (bitRate >= 1000 * 1000 * 1000) {
			return bitRate / 1000/1000/1000 + " Gb/s";
		}
		if (bitRate >= 1000 * 1000) {
			return bitRate / 1000/1000 + " Mb/s";
		}
		if (bitRate >= 1000) {
			return bitRate / 1000 + " Kb/s";
		} else {
			return bitRate + " b/s";
		}
	}
    
    /**
     * Converts the hex string representation of a switch to a long. 
     * Returns -1 iff the string cannot converted into a long.
	 * 
	 * @param switchIdString The switch id representation as hex string.
	 * @return <b>long</b> Floodlight's internal switch id.
	 */
	public static long parseSwitchId(String switchIdString) {
		/* The switch Id as long. */
		long switchId;
		
		try {
			switchId = HexString.toLong(switchIdString);
		} catch (NumberFormatException e) {
			switchId = -1L;
		}
		
		return switchId;
	}
	
	/**
	 * Private constructor for singleton pattern.
	 */
	private Utils() {
		// NO-OP
	}
}
