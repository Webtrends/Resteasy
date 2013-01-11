/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jboss.resteasy.plugins.server.netty;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.AttributeKey;
import static io.netty.handler.codec.http.HttpVersion.*;
import org.jboss.resteasy.logging.Logger;

/**
 *
 * @author vonnagyi
 */
@Sharable
public class WebSocketProtocolHandler extends ChannelInboundMessageHandlerAdapter<WebSocketFrame> {

    private final static Logger logger = Logger.getLogger(WebSocketProtocolHandler.class);
    
    private static final AttributeKey<WebSocketServerHandshaker> HANDSHAKER_ATTR_KEY =
            new AttributeKey<WebSocketServerHandshaker>(WebSocketServerHandshaker.class.getName());

    private final String subprotocols;
    private final boolean allowExtensions;

    public WebSocketProtocolHandler() {
        this(null, false);
    }

    public WebSocketProtocolHandler(String subprotocols) {
        this(subprotocols, false);
    }

    public WebSocketProtocolHandler(String subprotocols, boolean allowExtensions) {
        this.subprotocols = subprotocols;
        this.allowExtensions = allowExtensions;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof CloseWebSocketFrame) {
            WebSocketServerHandshaker handshaker = getHandshaker(ctx);
            
            try {
                if (handshaker != null) {
                    handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame);
                }
                else {
                    final ChannelPromise promise = ctx.channel().newPromise();
                    promise.addListener(ChannelFutureListener.CLOSE);
                    ctx.channel().write(frame, promise);
                }

            } catch (Exception e) {
                logger.warn("Closing socket caused an error when closing the channel", e);
            }
            
            return;
        }
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.getBinaryData()));
            return;
        }

        ctx.nextInboundMessageBuffer().add(frame);
        ctx.fireInboundBufferUpdated();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof WebSocketHandshakeException) {
            DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST);
            response.setContent(Unpooled.wrappedBuffer(cause.getMessage().getBytes()));
            ctx.channel().write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            ctx.close();
        }
    }

    public static WebSocketServerHandshaker getHandshaker(ChannelHandlerContext ctx) {
        return ctx.attr(HANDSHAKER_ATTR_KEY).get();
    }

    static void setHandshaker(ChannelHandlerContext ctx, WebSocketServerHandshaker handshaker) {
        ctx.attr(HANDSHAKER_ATTR_KEY).set(handshaker);
    }
    
    static ChannelHandler forbiddenHttpRequestResponder() {
        return new ChannelInboundMessageHandlerAdapter<Object>() {
            @Override
            public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (!(msg instanceof WebSocketFrame)) {
                    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FORBIDDEN);
                    ctx.channel().write(response);
                } else {
                    ctx.nextInboundMessageBuffer().add(msg);
                    ctx.fireInboundBufferUpdated();
                }
            }
        };
    }

}