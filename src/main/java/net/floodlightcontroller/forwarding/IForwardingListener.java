package net.floodlightcontroller.forwarding;

public interface IForwardingListener {
	
	public void flowInstalled(long cookie);
	
	public void flowRemoved(long cookie);
	
	public void flowMoved(long cookie);
}
