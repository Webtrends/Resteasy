package org.jboss.resteasy.plugins.server.netty;

import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpChunkAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;

/**
 * The {@link HttpChannelInitializer} which is used to serve HTTP Traffic.
 * 
 * @author Ivan von Nagy
 */
public class HttpChannelInitializer extends ChannelInitializer<SocketChannel> {
    
    private final ChannelHandler resteasyEncoder;
    private final ChannelHandler resteasyDecoder;
    private final ChannelHandler resteasyRequestHandler;
    private final EventExecutorGroup executorGroup;
    private final int maxRequestSize;

    public HttpChannelInitializer(RequestDispatcher dispatcher, String root, int executorThreadCount, int maxRequestSize)
    {
        this.resteasyDecoder = new RestEasyHttpRequestDecoder(dispatcher.getDispatcher(), root, getProtocol());
        this.resteasyEncoder = new RestEasyHttpResponseEncoder(dispatcher);
        this.resteasyRequestHandler = new RequestHandler(dispatcher);
        if (executorThreadCount > 0) 
        {
            this.executorGroup = new DefaultEventExecutorGroup(executorThreadCount);
        } 
        else 
        {
            this.executorGroup = null;
        }
        this.maxRequestSize = maxRequestSize;

    }

    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        
        // Create a default pipeline implementation.
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast("decoder", new HttpRequestDecoder());
        pipeline.addLast("aggregator", new HttpChunkAggregator(maxRequestSize));
        pipeline.addLast("resteasyDecoder", resteasyDecoder);
        pipeline.addLast("encoder", new HttpResponseEncoder());
        pipeline.addLast("resteasyEncoder", resteasyEncoder);
        
        if (executorGroup != null) {
            pipeline.addLast(executorGroup, "executionHandler", resteasyRequestHandler);
        }
        else {
            pipeline.addLast("handler", resteasyRequestHandler);
        }
    }
    
    protected RestEasyHttpRequestDecoder.Protocol getProtocol() {
        return RestEasyHttpRequestDecoder.Protocol.HTTP;
    }
}
