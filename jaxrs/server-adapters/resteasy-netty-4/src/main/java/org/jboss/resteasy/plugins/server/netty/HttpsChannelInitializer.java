package org.jboss.resteasy.plugins.server.netty;

import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import org.jboss.resteasy.plugins.server.netty.RestEasyHttpRequestDecoder.Protocol;

/**
 * {@link HttpChannelInitializer} subclass which enable the use of HTTPS
 * 
 * @author Ivan von Nagy
 *
 */
public class HttpsChannelInitializer extends HttpChannelInitializer {
    
    private final SSLContext context;

    public HttpsChannelInitializer(RequestDispatcher dispatcher, String root, int executorThreadCount, int maxRequestSize, SSLContext context) 
    {
        super(dispatcher, root, executorThreadCount, maxRequestSize);
        this.context = context;
    }
    
    @Override
    public void initChannel(SocketChannel ch) throws Exception {
        
        // Call the base first
        super.initChannel(ch);
        ChannelPipeline pipeline = ch.pipeline();

        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        pipeline.addFirst("ssl", new SslHandler(engine));
    }
    
    @Override
    protected Protocol getProtocol() 
    {
        return Protocol.HTTPS;
    }
}
