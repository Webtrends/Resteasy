/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.resteasy.plugins.server.netty;

import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.handler.ssl.SslHandler;
import javax.ws.rs.core.Response;
import org.jboss.resteasy.logging.Logger;
import org.jboss.resteasy.spi.HttpRequest;

/**
 *
 * @author vonnagyi
 */
public class WebSocketChannelInitializer {
    
    private final static Logger logger = Logger.getLogger(WebSocketChannelInitializer.class);
    
    public static ChannelFuture handshake(final ChannelHandlerContext ctx, 
            final HttpRequest request, 
            final String websocketPath,
            final ChannelHandler handler) {
        
        final String connHead = request.getHttpHeaders().getHeaderString(HttpHeaders.Names.CONNECTION);
        final String upHead = request.getHttpHeaders().getHeaderString(HttpHeaders.Names.UPGRADE);
        final String sockHead = request.getHttpHeaders().getHeaderString(HttpHeaders.Names.SEC_WEBSOCKET_VERSION);
        final String keyHead = request.getHttpHeaders().getHeaderString(HttpHeaders.Names.SEC_WEBSOCKET_KEY);
        
        try {
            DefaultHttpRequest req = new DefaultHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, request.getUri().getAbsolutePath().toString());
            req.setHeader(HttpHeaders.Names.SEC_WEBSOCKET_VERSION, sockHead);
            req.setHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY, keyHead);

            final Channel channel = ctx.channel();
            
            final String location = getWebSocketLocation(channel.pipeline(), request, websocketPath);
            final WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                    location, null, false);

            final WebSocketServerHandshaker handshaker = wsFactory.newHandshaker(req);

            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(channel);
                return null;

            } else if (!connHead.toLowerCase().contains(HttpHeaders.Values.UPGRADE.toLowerCase())
                    || !upHead.toLowerCase().contains(HttpHeaders.Values.WEBSOCKET.toLowerCase())) {
                // Not a valid socket open request
                logger.info("Invalid request: " + request.getUri());
                return null;

            } else {
                // We need to remove the RESTEasy stuff otherwise the Netty logic to write the handshake to the channel
                // will never make it back to the client
                channel.pipeline().remove("resteasyEncoder");
                channel.pipeline().remove("resteasyDecoder");
                
                final ChannelFuture handshakeFuture = handshaker.handshake(channel, req);
                
                handshakeFuture.addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            ctx.fireExceptionCaught(future.cause());
                        }
                        else {
                            final ChannelPipeline pipeline = future.channel().pipeline();
                            pipeline.replace(pipeline.last(), "customSocketHandler", handler);
                            pipeline.addBefore("customSocketHandler", "socketHandler", new WebSocketProtocolHandler());
                        }
                    }
                });

                WebSocketProtocolHandler.setHandshaker(ctx, handshaker);
                return handshakeFuture;
                
                //channel.pipeline().addBefore("timeout", "WS403Responder",
                //    WebSocketProtocolHandler.forbiddenHttpRequestResponder());
            }
            
        }
        catch (Exception e) {
            logger.error("Error trying to upgrade the channel to a socket", e);
        }
        
        return null;
    }
    
    private static String getWebSocketLocation(ChannelPipeline cp, HttpRequest req, String path) {
        String protocol = "ws";
        if (cp.get(SslHandler.class) != null) {
            // SSL in use so use Secure WebSockets
            protocol = "wss";
        }
        return protocol + "://" + req.getHttpHeaders().getHeaderString(HttpHeaders.Names.HOST) + path;
    }
}
