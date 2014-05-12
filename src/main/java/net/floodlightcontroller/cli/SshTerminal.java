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

import jline.TerminalSupport;

import org.apache.sshd.server.Environment;

/**
 * Provides an SSH terminal for Jline. 
 */
public class SshTerminal extends TerminalSupport {
	/** */
    private Environment environment;

    /**
     * Constructor.
     * 
     * @param environment
     */
    public SshTerminal(Environment environment) {
        super(true);
        setAnsiSupported(true);
        this.environment = environment;
    }

    @Override
    public void init() throws Exception {
    }

    @Override
    public void restore() throws Exception {
    }

    @Override
    public int getWidth() {
        int width = 0;
        try {
            width = Integer.valueOf(this.environment.getEnv().get(Environment.ENV_COLUMNS));
        } catch (Throwable t) {
            // Ignore
        }
        return width > 0 ? width : super.getWidth();
    }

    @Override
    public int getHeight() {
        int height = 0;
        try {
            height = Integer.valueOf(this.environment.getEnv().get(Environment.ENV_LINES));
        } catch (Throwable t) {
            // Ignore
        }
        return height > 0 ? height : super.getHeight();
    }

}
