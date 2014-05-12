package net.floodlightcontroller.cli;

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
import java.io.InputStream;
import java.io.OutputStream;


import org.apache.sshd.common.Factory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

/**
 * A {@link Factory} of {@link Command} that will create a new shell process
 * and bridge the streams.
 *
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class FloodlightShellFactory implements Factory<Command> {
	
	@Override
    public Command create() {
        return new FloodlightShell();
    }

    /**
     * The Floodlight shell that is created whenever a new connection to
     * the SSH console is established.
     * 
     * @author Michael Bredel <michael.bredel@cern.ch>
     */
    public static class FloodlightShell implements Command, Runnable {
    	/** The (unique) command hander that executes all console commands. */
    	private CommandHandler commander = CommandHandler.getInstance();
    	/** The console of the shell that handles in- and outputs as well as command execution. */
    	private Console console;
    	/** The input stream as read from the shell's command line prompt. */
        private InputStream in;
        /** The output stream to write to the shell's command line prompt. */
        private OutputStream out;
        /** The error stream from the shell's command line. */
        private OutputStream err;
        /** The function that is executed when the shell is terminated. */
		private ExitCallback callback;
		/** Environment to get some user data, like the console encoding, from. */
        private Environment environment;
        /** The thread that runs this Floodlight shell. */
        private Thread thread;

        @Override
        public void setInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public void setOutputStream(OutputStream out) {
            this.out = out;
        }

        @Override
        public void setErrorStream(OutputStream err) {
            this.err = err;
        }

        @Override
        public void setExitCallback(ExitCallback callback) {
            this.callback = callback;
        }

        @Override
        public void start(Environment env) throws IOException {
            environment = env;
            thread = new Thread(this, "FloodlightShell");
            thread.start();
        }

        @Override
        public void destroy() {
        	this.console.stop();
            thread.interrupt();
        }

        @Override
        public void run() {        	
            String encoding = environment.getEnv().get("LC_CTYPE");
            if (encoding != null && encoding.indexOf('.') > 0) {
                encoding = encoding.substring(encoding.indexOf('.') + 1);
            }

        	try {
				this.console = new Console(commander, in, out, err, encoding, environment);
				// Blocking call.
				this.console.run();
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				// End this thread.
				if (this.callback != null)
					this.callback.onExit(0);
			}
        }
    }
}
