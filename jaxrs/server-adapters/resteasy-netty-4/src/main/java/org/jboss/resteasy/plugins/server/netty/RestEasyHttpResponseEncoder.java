package org.jboss.resteasy.plugins.server.netty;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;

import javax.ws.rs.ext.RuntimeDelegate;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;


/**
 * {@link MessageToMessageEncoder} implementation which encodes {@link org.jboss.resteasy.spi.HttpResponse}'s to
 * {@link io.netty.handler.codec.http.HttpResponse}'s
 * 
 * This implementation is {@link Sharable}
 * 
 * @author Norman Maurer
 * @author Ivan von Nagy
 *
 */
@Sharable
public class RestEasyHttpResponseEncoder extends MessageToMessageEncoder<org.jboss.resteasy.spi.HttpResponse> 
{
    
    private final RequestDispatcher dispatcher;

    public RestEasyHttpResponseEncoder(RequestDispatcher dispatcher) 
    {
        this.dispatcher = dispatcher;
    }
    
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Object encode(ChannelHandlerContext ctx, org.jboss.resteasy.spi.HttpResponse msg) throws Exception 
    {
        NettyHttpResponse nettyResponse = (NettyHttpResponse) msg;
        // Build the response object.
        HttpResponseStatus status = HttpResponseStatus.valueOf(nettyResponse.getStatus());
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, status);

        for (Map.Entry<String, List<Object>> entry : nettyResponse.getOutputHeaders().entrySet())
        {
            String key = entry.getKey();
            for (Object value : entry.getValue())
            {
                RuntimeDelegate.HeaderDelegate delegate = dispatcher.providerFactory.createHeaderDelegate(value.getClass());
                if (delegate != null)
                {
                    response.addHeader(key, delegate.toString(value));
                }
                else
                {
                    response.setHeader(key, value.toString());
                }
            }
        }

        nettyResponse.getOutputStream().flush();
        response.setContent(nettyResponse.getBuffer());

        if (nettyResponse.isKeepAlive()) 
        {
            // Add content length and connection header if needed
            response.setHeader(Names.CONTENT_LENGTH, response.getContent().readableBytes());
            response.setHeader(Names.CONNECTION, Values.KEEP_ALIVE);
        }
        return response;
    }

}
