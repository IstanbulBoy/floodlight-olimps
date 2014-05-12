package net.floodlightcontroller.cli;

/*
* Copyright (c) 2013, California Institute of Technology
* ALL RIGHTS RESERVED.
* Based on Government Sponsored Research DE-SC0007346
* Autor Michael Bredel <michael.bredel@cern.ch>
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
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.sshd.server.Environment;


import jline.Terminal;
import jline.console.ConsoleReader;
import jline.console.completer.AggregateCompleter;
import jline.console.completer.ArgumentCompleter;
import jline.console.completer.Completer;
import jline.console.completer.NullCompleter;
import jline.console.completer.StringsCompleter;

/**
 * The console abstracts the Jline console reader. It reads
 * and writes from and to the command line, handles the
 * command completer, and executes the commands using the
 * command handler.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class Console implements IConsole {
	/** An SSH terminal representation. */
	private Terminal terminal;
	/** The Jline console reader. */
	private ConsoleReader reader;
	/** The command hander that executes all console commands. */
	private CommandHandler commander;
	/** The input stream as read from the command line prompt. */
    private InputStream in;
    /** The output stream to write to the command line prompt. */
    private PrintStream out;
    /** The error stream from the command line. */
    @SuppressWarnings("unused")
	private PrintStream err;
    /** The prompt string of the command line. */
    private String prompt;
    /** Boolean that states if the console is running. */
    private volatile boolean running;
    /** List of command completer. */
	private List<Completer> completors = new LinkedList<Completer>();
	
	/**
	 * Constructor.
	 */
	public Console(CommandHandler commander, InputStream in, OutputStream out, OutputStream err, String encoding, Environment env) throws Exception {
		this.commander = commander;
		this.in = in;
		this.out = new PrintStream(new LfToCrLfFilterOutputStream(out), true);
		this.err = new PrintStream(new LfToCrLfFilterOutputStream(err), true);
		this.terminal = new SshTerminal(env);
		
		// Create and configure a console reader.
		this.reader = new ConsoleReader("Floodlight", this.in, this.out, this.terminal, encoding);
		this.reader.setBellEnabled(false);
		this.reader.setHistoryEnabled(true);
		
		// Generate completer
		this.generateCompleters();
		this.reader.addCompleter(new AggregateCompleter(completors));
		
		// Add ourself to the CommandHanlder.
		this.commander.addListener(this);
		
		// TODO: Handle command line history in file. 
	}
	
	@Override
	protected void finalize() throws Throwable {
		// Make sure we are removed from the listener
		this.commander.removeListener(this);
	}
	
	@Override
	public void setPrompt(String prompt) {
		this.prompt = prompt;
	}
	
	@Override
	public String getPrompt() {
		if (this.prompt != null)
			return this.prompt;
		
		return "> ";
	}
	
	@Override
	public Collection<Completer>  getCompleters() {
		return this.reader.getCompleters();
	}
	
	@Override
	public void write(String string) throws IOException {
		this.reader.getOutput().write(string + "\n");
	}
	
	/**
	 * Runs the console, reads the command line string, and
	 * executes the commands.
	 */
	public void run() {
		/* Set the running boolean to true. */
		running = true;
		/* The command line string. */
		String line;
		
		// Print some kind of welcome string.
		this.welcome();
		
		while (running) {
            try {
            	// Read command line.
				line = this.readCommandLine();
				
	            // Execute commands.
	            commander.execute(this, line);

	            // Execute special commands
	            if (line.trim().equalsIgnoreCase("quit") || line.trim().equalsIgnoreCase("exit")) {
	            	break;
	            }
				
			} catch (IOException e) {
				running = false;
			}
		}		
	}
	
	/**
     * Stops the execution of this console.
     */
	public synchronized void stop() {
		this.running = false;
	}
	
	/**
	 * Prints a welcome message to the console.
	 */
	private void welcome() {
		try {
			this.write("\n   Welcome to the OLiMPS OpenFlow Controller CLI\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Reads the current command line and generates a list of
	 * possible command completions.
	 * 
	 * @return The current command line string.
	 * @throws IOException
	 */
	private String readCommandLine() throws IOException {
		/* Line read from the CLI by the console reader. */
		String line = this.reader.readLine(getPrompt());
		/* A list of completion candidates. */
		List<CharSequence> candidates = new LinkedList<CharSequence>();
		
		if (line == null)
			return null;

		for (Completer comp : reader.getCompleters()) {
			if (comp.complete(line, line.length(), candidates) == -1) {
				break;
			}
			candidates = new LinkedList<CharSequence>(new HashSet<CharSequence>(candidates));

			// Completers.complete() automatically adds a space at the end
			// of a command. Thus, we have to handle this case specially.
			if (candidates.contains(line + " ")) {
				if (comp.complete(line + " ", line.length() + 1, candidates) == -1) {
					break;
				}
				candidates = new LinkedList<CharSequence>(new HashSet<CharSequence>(candidates));
				candidates.remove(line + " ");
			}
		}
	
		// Return.
		return line;
	}
	
    /**
     * Generates the completer list by getting all available
     * commands from the command Hander, creates completer and
     * adds them to this consoles completer list.
     */
    private void generateCompleters() {
    	for (ICommand cmd : this.commander.getCommands() ){
    		this.addCommand(cmd);
    	}
    }
    
    /**
     * Creates a (Jline) completer and adds a command and its 
     * arguments to it.
     * 
     * @param cmd Command that is offered by the command handler.
     */
    private void addCommand(ICommand cmd) {
    	/* List of StringCompleters for commands and arguments. */
    	List<Completer> argCompletorList = new LinkedList<Completer>();
    	/* Array of command strings. */
    	String[] commands = (cmd.getCommandString()).split(" ");
    	
    	// Decompose command string and add strings to StringsCompleter. 
    	for (String command : commands) {
    		argCompletorList.add(new StringsCompleter(command.trim().toLowerCase()));
    	}
    	
    	// Add argument strings to StringsCompleter.
    	if (cmd.getArguments() != null) {
    		argCompletorList.add(new StringsCompleter(cmd.getArguments().trim().toUpperCase()));
    	}
    	
    	// Add completer that already exist at the command.
    	if (cmd.getCompleter() != null) {
    		argCompletorList.addAll(cmd.getCompleter());
    	}
    	
    	// Add NullCompleter to terminate the completer.
    	argCompletorList.add(new NullCompleter());
    	
    	// Add new ArgumentCompleter to global completer list.
    	this.completors.add(new ArgumentCompleter(argCompletorList));
    }
	
}
