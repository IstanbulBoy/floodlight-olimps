package net.floodlightcontroller.cli.completer;

import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import org.openflow.util.HexString;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import jline.console.completer.Completer;
import static jline.internal.Preconditions.checkNotNull;

/**
 * 
 * @author Michael Bredel <michael.bredel@cern.ch>
 */
public class SwitchCompleter implements Completer {
	/** */
	private IFloodlightProviderService floodlightProvider;
	/** */
	public String argument = "";
	
	/**
	 * Constructor.
	 * 
	 * @param context
	 */
	public SwitchCompleter(FloodlightModuleContext context) {
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
	}

	@Override
	public int complete(String buffer, int curser, List<CharSequence> candidates) {
		/* */
		SortedSet<String> strings = new TreeSet<String>();
		
		//
		checkNotNull(candidates);
		
		
		
		/* All switches connected to the Floodlight controller. */
		for (long switdchId : floodlightProvider.getAllSwitchDpids()) {
			strings.add(HexString.toHexString(switdchId));
		}
		
		if (buffer == null) {
			candidates.addAll(strings);
		} else {
			for (String match : strings.tailSet(buffer)) {
				if(!match.startsWith(buffer))
					break;
				if (!match.equalsIgnoreCase(argument))
					candidates.add(match);
			}
		}
		
		if (candidates.size() == 1)
			candidates.set(0, candidates.get(0) + " ");
		
		// Return.
		return candidates.isEmpty() ? -1 : 0;
	}

}
