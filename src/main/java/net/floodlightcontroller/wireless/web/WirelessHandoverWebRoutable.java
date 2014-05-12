package net.floodlightcontroller.wireless.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class WirelessHandoverWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/associate/json", WirelessAssociateResource.class);
		router.attach("/disassociate/json", WirelessDisassociateResource.class);
		return router;
	}

	@Override
	public String basePath() {
		return "/wm/wirelesshandover";
	}

}
