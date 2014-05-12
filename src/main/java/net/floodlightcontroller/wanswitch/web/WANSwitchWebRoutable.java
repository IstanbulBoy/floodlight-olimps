package net.floodlightcontroller.wanswitch.web;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class WANSwitchWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/ports/json", WANSwitchResource.class);
		return router;
	}
	
	@Override
	public String basePath() {
		return "/wm/wanswitch";
	}
}
