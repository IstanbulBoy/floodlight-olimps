package net.floodlightcontroller.cli;

import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * Add and remove commands to and from the command line interface.
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public interface ICliService extends IFloodlightService {
	
	/**
	 * Adds a CLI command to the CLI.
	 * 
	 * @param command The command to be added.
	 */
	public void registerCommand(ICommand command);
	
	/**
	 * removes a CLI command from the CLI. 
	 * 
	 * @param command The command to be removed.
	 */
	public void unregisterCommand(ICommand command);

}
