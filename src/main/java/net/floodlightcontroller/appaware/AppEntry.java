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

import org.openflow.protocol.OFMatch;

/**
 * AppEntry objects contain the information of a registered data transfer application.
 * This information can be used to identify the applications data flows to treat
 * them specially, if needed.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class AppEntry {
	/** The application name. */
	protected String name;
	/** The number of bytes the application wants to transfer. In [MByte]. */
	protected int fileSize;
	/** Start timestamp. */
	protected long startTime;
	/** The OpenFlow match that characterizes the application traffic. */
	protected OFMatch match;
	/** States whether this application is active, i.e. a flow is associated to it, or not. */
	protected boolean isActive;
	/** The cookie that identifies the flow match for this application. */
	protected long cookie;

	/**
	 * Constructor.
	 * 
	 * @param name The name of the application.
	 * @param fileSize The number of bytes the application wants to transfer. In [MBytes].
	 * @param match The OpenFlow match that characterizes the application traffic.
	 */
	public AppEntry(String name, int fileSize, OFMatch match) {
		this.name = name;
		this.fileSize = fileSize;
		this.match = match;
		this.startTime = System.currentTimeMillis();
		this.isActive = false;
	}
	
	/**
	 * Getter for the application name.
	 * 
	 * @return <b>String</b> The application name.
	 */
	public String getName() {
		return this.name;
	}
	
	/**
	 * Getter for the file size.
	 * 
	 * @return <b>int</b> The number of bytes the application wants to transfer. In [MByte].
	 */
	public int getFileSize() {
		return this.fileSize;
	}
	
	/**
	 * Getter for the start time.
	 * 
	 * @return <b>long</b> The start time timestamp.
	 */
	public long getStartTime() {
		return startTime;
	}

	/**
	 * Getter for the OpenFlow match that characterizes the application traffic.
	 * 
	 * @return <b>OFMatch</b> The OpenFlow match that characterizes the application traffic.
	 */
	public OFMatch getMatch() {
		return match;
	}
	
	/**
	 * Setter to set the application entry active.
	 * 
	 * @param isActive States whether the application is active, i.e. a flow is associated to it, or not.
	 */
	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}
	
	/**
	 * Check if the application is active, i.e. a flow is associated to it, or not.
	 * 
	 * @return <b>boolean</b> States whether the application is active, i.e. a flow is associated to it, or not.
	 */
	public boolean isActive() {
		return this.isActive;
	}
	
	/**
	 * Sets the cookie that identifies the flow of that application.
	 * 
	 * @param cookie The cookie that identifies the flow of that application.
	 */
	public void setCookie(long cookie) {
		this.cookie = cookie;
	}
	
	/**
	 * Get the cookie that identifies the flow of that application.
	 * 
	 * @return <b>long</b> The cookie that identifies the flow of that application.
	 */
	public long getCookie() {
		return this.cookie;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
            return false;
        }
		if (!(obj instanceof AppEntry)) {
            return false;
        }
		AppEntry other = (AppEntry) obj;
		if (!this.name.equalsIgnoreCase(other.getName())) {
			return false;
		}
		return true;
	}
	
	@Override
	public int hashCode() {
		final int prime = 131;
		int result = 1;
		result = prime * result + this.name.hashCode();
		return result;
	}
	
	@Override
	public String toString() {
		/* The string builder. */
		StringBuilder sb = new StringBuilder();
		
		sb.append("AppEntry[");
		sb.append("name=" + name + ",");
		sb.append("fileSize=" + fileSize + ",");
		sb.append("startTime=" + startTime + ",");
		sb.append("match=" + match);
		sb.append("]");
		
		return sb.toString();
	}
}
