package net.floodlightcontroller.cli;

/*
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
*/

import java.util.HashMap;
import java.util.Map;

import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

/**
 * A very simple password authenticator.
 * 
 * Passwords are not encrypted! Moreover, they are not stored permanently.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class SimplePasswordAuthenticator implements PasswordAuthenticator {
	/** Map containing user name and corresponding password. */
	private Map<String, String> passwd;
	
	/**
	 * Constructor
	 */
	public SimplePasswordAuthenticator(String username, String password) {
		passwd = new HashMap<String, String>();
		passwd.put(username, password);
	}

	@Override
	public boolean authenticate(String username, String password, ServerSession serverSession) {
		return username != null && passwd.get(username.toLowerCase().trim()).equals(password);
	}
	
	/**
	 * Add a new user.
	 * 
	 * @param username
	 * @param password
	 */
	public void addUser(String username, String password) {
		passwd.put(username, password);
	}

}
