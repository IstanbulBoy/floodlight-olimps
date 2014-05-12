package net.floodlightcontroller.wanswitch;

import java.util.concurrent.ThreadPoolExecutor;

import net.floodlightcontroller.core.internal.OFMessageDecoder;
import net.floodlightcontroller.core.internal.OFMessageEncoder;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.Channels;
import org.jboss.netty.handler.timeout.IdleStateHandler;
import org.jboss.netty.handler.timeout.ReadTimeoutHandler;
import org.jboss.netty.util.ExternalResourceReleasable;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WANSwitchPipelineFactory implements ChannelPipelineFactory, ExternalResourceReleasable {
	/** The logger. */
    protected static Logger log = LoggerFactory.getLogger(WANSwitchPipelineFactory.class);
    
    protected ThreadPoolExecutor pipelineExecutor;
    protected Timer timer;
    protected IdleStateHandler idleHandler;
    protected ReadTimeoutHandler readTimeoutHandler;
    protected WANSwitchChannelHandler channelHandler;
    
    /**
     * Constructor.
     * 
     * @param wanSwitchManager
     */
    public WANSwitchPipelineFactory(WANSwitchChannelHandler channelHandler) {
        super();
        this.timer = new HashedWheelTimer();
        this.idleHandler = new IdleStateHandler(timer, 20, 25, 0);
        this.readTimeoutHandler = new ReadTimeoutHandler(timer, 30);
        this.channelHandler = channelHandler;
    }
 
    @Override
    public ChannelPipeline getPipeline() throws Exception {        
        ChannelPipeline pipeline = Channels.pipeline();
        pipeline.addLast("ofmessagedecoder", new OFMessageDecoder());
        pipeline.addLast("ofmessageencoder", new OFMessageEncoder());
        pipeline.addLast("idle", idleHandler);
        pipeline.addLast("timeout", readTimeoutHandler);
        // For now, we only use one thread for the pipeline execution.
        //if (pipelineExecutor != null)
        //	pipeline.addLast("pipelineExecutor", new ExecutionHandler(pipelineExecutor));
        pipeline.addLast("handler", channelHandler);
        return pipeline;
    }

    @Override
    public void releaseExternalResources() {
        timer.stop();        
    }
}
