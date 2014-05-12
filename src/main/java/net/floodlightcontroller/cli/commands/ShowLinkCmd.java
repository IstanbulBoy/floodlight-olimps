package net.floodlightcontroller.cli.commands;

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
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.openflow.protocol.OFPhysicalPort;
import org.openflow.protocol.OFPhysicalPort.OFPortConfig;
import org.openflow.protocol.OFPhysicalPort.OFPortState;
import org.openflow.util.HexString;

import jline.console.completer.Completer;

import net.floodlightcontroller.cli.ICommand;
import net.floodlightcontroller.cli.IConsole;
import net.floodlightcontroller.cli.utils.StringTable;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.OFSwitchPort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.LinkInfo;
import net.floodlightcontroller.routing.Link;


/**
 * The "show host" command shows information about hosts
 * that are connected to switches controlled by Floodlight.
 * 
 * The "show host" command uses the Floodlight.context service
 * to directly address the corresponding Floodlight module to
 * retrieve the needed information.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class ShowLinkCmd implements ICommand  {
	/** */
	private IFloodlightProviderService floodlightProvider;
	/** */
	private ILinkDiscoveryService linkDiscoveryManager;
	/** The command string. */
	private String commandString = "show link";
	/** The command's arguments. */
	private String arguments = null;
	/** The command's help text. */
	private String help = null;
	
	/**
	 * Constructor.
	 * 
	 * @param context The Floodlight context service.
	 */
	public ShowLinkCmd(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.linkDiscoveryManager = context.getServiceImpl(ILinkDiscoveryService.class);
	}
	
	@Override
	public String getCommandString() {
		return commandString;
	}
	
	@Override
	public String getArguments() {
		return arguments;
	}

	@Override
	public String getHelpText() {
		return help;
	}
	
	@Override
	public Collection<Completer> getCompleter() {
		return null;
	}

	@Override
	public String execute(IConsole console, String arguments) {
		/* The resulting string. */
		String result = "";
		/* All links of the current topology. */
		Map<Link, LinkInfo> links = this.linkDiscoveryManager.getLinks();
		/* Parse possible argument as switch id. */
		long switchId = parseSwitchId(arguments.trim());
		
		// Filter the links, in case we have an argument
		if (switchId > 0) {
			Iterator<Map.Entry<Link, LinkInfo>> linksIterator = links.entrySet().iterator();
			while (linksIterator.hasNext()) {
				Map.Entry<Link, LinkInfo> link = linksIterator.next();
				if (link.getKey().getSrc() == switchId || link.getKey().getDst() == switchId) {
					// Do nothing
				} else {
					linksIterator.remove();
				}
			}
		}
		
		try {
			result = this.linksToTableString(links);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return result;
	}
	
	/**
	 * Creates a string table and returns a formated string that
	 * shows the device information as a table.
	 * 
	 * @param devices A collection of devices.
	 * @return A formated string that shows the device information as a table.
	 * @throws IOException
	 */
	private String linksToTableString(Map<Link, LinkInfo> links) throws IOException {
		/* The string table that contains all the device information as strings. */
        StringTable stringTable = new StringTable();
        /* */
        int linkCounter = 0;
        
        // Generate header data.
        List<String> header = new LinkedList<String>();
        //header.add("Link ID");
        header.add("#");
        header.add("Source Switch");
        header.add("Source Port");
        header.add("Destination Switch");
        header.add("Destination Port");
        header.add("Capacity");
        header.add("Utilisation");
        header.add("Active Since");
        header.add("Link Type");
        header.add("Status");
        
        // Add header to string table.
        stringTable.setHeader(header);
        
        // Sort links
        Map<Link, LinkInfo> sortedLinks = this.sortByKeys(links);
        
        for (Entry<Link, LinkInfo> entry : sortedLinks.entrySet()) {
        	linkCounter++;
			Link link = entry.getKey();
			LinkInfo linkInfo = entry.getValue();
			// Get attributes
			IOFSwitch srcSwitch = floodlightProvider.getSwitch(link.getSrc());
			IOFSwitch dstSwitch = floodlightProvider.getSwitch(link.getDst());
			OFSwitchPort srcPort = srcSwitch.getPort(link.getSrcPort());
			OFSwitchPort dstPort = dstSwitch.getPort(link.getDstPort());
			
			// When creating virtual ports, it might happen that the link information is out dated for a while.
			if (srcPort == null) {
				continue;
			}
			if (dstPort == null) {
				continue;
			}

			// Create StringTable
			List<String> row = new LinkedList<String>();
			//row.add("Link ID");
			row.add(String.valueOf(linkCounter));
			row.add(srcSwitch.getStringId());
			row.add(srcPort.getName() + " (" + String.valueOf(link.getSrcPort()) + ")");
			row.add(dstSwitch.getStringId());
			row.add(dstPort.getName() + " (" + String.valueOf(link.getDstPort()) + ")");
			row.add(this.parsePortCapacity(Math.max(srcPort.getCurrentPortSpeed(), dstPort.getCurrentPortSpeed())));
			row.add("unknown");
			row.add(parseDate(new Date(linkInfo.getFirstSeenTime())));
			row.add(linkInfo.getLinkType().toString());
			row.add((portEnabled(srcPort.getOFPhysicalPort()) && portEnabled(dstPort.getOFPhysicalPort())) ? "up" : "down");

			stringTable.addRow(row);
		}
        
		// Return string table as a string.
        return stringTable.toString();
	}
	
	/**
	 * 
	 * @param port
	 * @return
	 */
    private boolean portEnabled(OFPhysicalPort port) {
        if (port == null) 
        	return false;
        if ((OFPortConfig.OFPPC_PORT_DOWN.getValue() & port.getConfig()) > 0)
        	return false;
        if ((OFPortState.OFPPS_LINK_DOWN.getValue() & port.getState()) > 0)
        	return false;
        // Port STP state doesn't work with multiple VLANs, so ignore it for now
        // if ((port.getState() & OFPortState.OFPPS_STP_MASK.getValue()) == OFPortState.OFPPS_STP_BLOCK.getValue())
        // 	return false;
        
        return true;
    }
    
    /**
	 * 
	 * @param switchIdString
	 * @return
	 */
	private long parseSwitchId(String switchIdString) {
		/* */
		long switchId;
		
		try {
			switchId = HexString.toLong(switchIdString);
		} catch (NumberFormatException e) {
			switchId = -1L;
		}
		
		return switchId;
	}
    
    /**
     * 
     * @param capacity The port capacity in Mpbs.
     * @return
     */
    private String parsePortCapacity(int capacity) {
    	/* States whether the link is full or half duplex. */
    	boolean fullDuplex = true;
    	/* The resulting string. */
    	StringBuilder sb = new StringBuilder();
    		
    	if (capacity < 0) {
    		capacity = capacity * (-1);
    		fullDuplex = false;
    	}
    	
    	if (capacity < 1000)
			sb.append(capacity + " MByte");
		if (capacity >= 1000 && capacity < 1000000)
			sb.append(capacity / 1000 + " GByte");
		if (capacity >= 1000000)
			sb.append(capacity / 1000 / 1000 + " TByte");
    	
    	if (!fullDuplex)
    		sb.append(" HD");
    	
    	return sb.toString();
    }
    
	/**
	 * Parses a date and returns a formated date string in the
	 * form: "yyyy-MM-dd HH:mm:ss z", where z is the time zone.
	 * 
	 * @param date The date that needs to be converted into a formated string.
	 * @return A formated date string.
	 */
	private String parseDate(Date date) {
		SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
		return dateformat.format(date);
	}
	
	/**
	 * Compares links by source and destination switch IDs and port numbers.
	 * 
	 * @param links The links that need to be sorted.
	 * @return <b>Map of Links</b> A sorted map of links.
	 */
	private <K extends Comparable<Link>, V extends Comparable<LinkInfo>> Map<Link, LinkInfo> sortByKeys(Map<Link, LinkInfo> links){
	    List<Link> keys = new LinkedList<Link>(links.keySet());
	    Collections.sort(keys, (Comparator<? super Link>) new Comparator<Link>() {
	        @Override
	        public int compare(Link arg0, Link arg1) {
				// Compare the source switches.
				if (arg0.getSrc() > arg1.getSrc())
					return 1;
				if (arg0.getSrc() < arg1.getSrc())
					return -1;
				
				// If source switches are equal, compare the destination switches.
				if (arg0.getDst() > arg1.getDst())
					return 1;
				if (arg0.getDst() < arg1.getDst())
					return -1;
				
				// Compare the source ports.
				if (arg0.getSrcPort() > arg1.getSrcPort())
					return 1;
				if (arg0.getSrcPort() < arg1.getSrcPort())
					return -1;
				
				// Compare the destination ports.
				if (arg0.getDstPort() > arg1.getDstPort())
					return 1;
				if (arg0.getDstPort() < arg1.getDstPort())
					return -1;
				
				// Everything is equal? That should not happen.
				return 0;
			}
	    });

	    LinkedHashMap<Link, LinkInfo> sortedMap = new LinkedHashMap<Link, LinkInfo>();
	    for(Link key: keys){
	        sortedMap.put(key, links.get(key));
	    }

	    return sortedMap;
	}
	
}
