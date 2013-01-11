package org.jboss.resteasy.plugins.server.netty;

import io.netty.channel.*;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.resteasy.logging.Logger;
import org.jboss.resteasy.spi.Failure;

import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * {@link ChannelInboundMessageHandlerAdapter} which handles the requests and dispatch them.
 *
 * This class is {@link Sharable}.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author Andy Taylor (andy.taylor@jboss.org)
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author Norman Maurer
 * @author Ivan von Nagy
 * @version $Rev: 2368 $, $Date: 2010-10-18 17:19:03 +0900 (Mon, 18 Oct 2010) $
 */
@Sharable
public class RequestHandler extends ChannelInboundMessageHandlerAdapter<Object>
{
    protected final RequestDispatcher dispatcher;
    private final static Logger logger = Logger.getLogger(RequestHandler.class);

    public RequestHandler(RequestDispatcher dispatcher)
    {
        this.dispatcher = dispatcher;
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        if (msg instanceof NettyHttpRequest) {
            NettyHttpRequest request = (NettyHttpRequest) msg;

            if (request.is100ContinueExpected())
            {
                send100Continue(ctx.channel());
            }

            NettyHttpResponse response = request.getResponse();
            try
            {
                dispatcher.service(ctx, request, response, true);
            }
            catch (Failure e1)
            {
                response.reset();
                response.setStatus(e1.getErrorCode());
                return;
            }
            catch (Exception ex)
            {
                response.reset();
                response.setStatus(500);
                logger.error("Unexpected", ex);
                return;
            }

            if (WebSocketProtocolHandler.getHandshaker(ctx) != null) {
                // We have upgraded to a websocket so don't write the result back
            }
            else {
                // Write the response.
                ChannelFuture future = ctx.channel().write(response);

                // Close the non-keep-alive connection after the write operation is done.
                if (!request.isKeepAlive())
                {
                    future.addListener(ChannelFutureListener.CLOSE);
                }
            }
        }

    }

    private void send100Continue(Channel channel)
    {
        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, CONTINUE);
        channel.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // handle the case of to big requests.
        if (cause instanceof TooLongFrameException)
        {
            DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE);
            ctx.channel().write(response).addListener(ChannelFutureListener.CLOSE);
        }
        else
        {
            cause.printStackTrace();
            ctx.channel().close();
        }

    }
}
