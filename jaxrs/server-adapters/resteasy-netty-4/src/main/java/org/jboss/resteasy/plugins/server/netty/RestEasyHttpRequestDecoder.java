package org.jboss.resteasy.plugins.server.netty;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpRequest;
import javax.ws.rs.core.HttpHeaders;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.logging.Logger;
import org.jboss.resteasy.spi.ResteasyUriInfo;

/**
 * This {@link MessageToMessageDecoder} is responsible for decode {@link io.netty.handler.codec.http.HttpRequest}
 * to {@link NettyHttpRequest}'s
 * 
 * This implementation is {@link Sharable}
 * 
 * @author Norman Maurer
 * @author Ivan von Nagy
 *
 */
@Sharable
public class RestEasyHttpRequestDecoder extends MessageToMessageDecoder<HttpRequest>
{
    private final static Logger logger = Logger.getLogger(RestEasyHttpRequestDecoder.class);

    private final SynchronousDispatcher dispatcher;
    private final String servletMappingPrefix;
    private final String proto;
    
    enum Protocol 
    {
        HTTPS,
        HTTP
    }
    
    public RestEasyHttpRequestDecoder(SynchronousDispatcher dispatcher, String servletMappingPrefix, Protocol protocol) 
    {
        this.dispatcher = dispatcher;
        this.servletMappingPrefix = servletMappingPrefix;
        if (protocol == Protocol.HTTP) 
        {
            proto = "http";
        } 
        else 
        {
            proto = "https";
        }
    }
    
    @Override
    public Object decode(ChannelHandlerContext ctx, HttpRequest request) throws Exception {
        boolean keepAlive = io.netty.handler.codec.http.HttpHeaders.isKeepAlive(request);
        NettyHttpResponse response = new NettyHttpResponse(ctx.channel(), keepAlive);

        HttpHeaders headers = null;
        ResteasyUriInfo uriInfo = null;
        try
        {
           headers = NettyUtil.extractHttpHeaders(request);

           uriInfo = NettyUtil.extractUriInfo(request, servletMappingPrefix, proto);
           org.jboss.resteasy.spi.HttpRequest nettyRequest = new NettyHttpRequest(headers, uriInfo, request.getMethod().getName(), dispatcher, response, io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected(request) );
           ByteBufInputStream is = new ByteBufInputStream(request.getContent());
           nettyRequest.setInputStream(is);
           return nettyRequest;
        }
        catch (Exception e)
        {
           response.sendError(400);
           // made it warn so that people can filter this.
           logger.warn("Failed to parse request.", e);
           
           return null;
        }

    }
}
