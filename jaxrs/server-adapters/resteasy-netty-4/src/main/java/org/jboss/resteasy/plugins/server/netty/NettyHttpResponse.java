package org.jboss.resteasy.plugins.server.netty;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;
import org.jboss.resteasy.spi.HttpResponse;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NewCookie;
import java.io.IOException;
import java.io.OutputStream;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class NettyHttpResponse implements HttpResponse
{
   private int status = 200;
   private ByteBufOutputStream underlyingOutputStream;
   private OutputStream os;
   private MultivaluedMap<String, Object> outputHeaders;
   private final Channel channel;
   private boolean committed;
   private boolean keepAlive;
   
   public NettyHttpResponse(Channel channel, boolean keepAlive)
   {
      outputHeaders = new MultivaluedMapImpl<String, Object>();
      os = underlyingOutputStream = new ByteBufOutputStream(Unpooled.buffer());
      this.channel = channel;
      this.keepAlive = keepAlive;
   }

   @Override
   public void setOutputStream(OutputStream os)
   {
      this.os = os;
   }

   public ByteBuf getBuffer()
   {
      return underlyingOutputStream.buffer();
   }

   public Channel getChannel()
   {
      return channel;
   }
   
   @Override
   public int getStatus()
   {
      return status;
   }
   
   @Override
   public void setStatus(int status)
   {
      this.status = status;
   }

   @Override
   public MultivaluedMap<String, Object> getOutputHeaders()
   {
      return outputHeaders;
   }

   @Override
   public OutputStream getOutputStream() throws IOException
   {
      return os;
   }

   @Override
   public void addNewCookie(NewCookie cookie)
   {
      outputHeaders.add(HttpHeaders.SET_COOKIE, cookie);
   }

   @Override
   public void sendError(int status) throws IOException
   {
      sendError(status, null);
   }

   @Override
   public void sendError(int status, String message) throws IOException
   {
       if (committed) 
       {
           throw new IllegalStateException();
       }
       
       HttpResponseStatus responseStatus = null;
       if (message != null)
       {
           responseStatus = new HttpResponseStatus(status, message);
       }
       else
       {
           responseStatus = HttpResponseStatus.valueOf(status);
       }
       DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, responseStatus);
       if (keepAlive) 
       {
           // Add keep alive and content length if needed
           response.addHeader(Names.CONNECTION, Values.KEEP_ALIVE);
           response.addHeader(Names.CONTENT_LENGTH, 0);
       }
       channel.write(response);
       committed = true;
   }

   @Override
   public boolean isCommitted()
   {
      return committed;
   }

   @Override
   public void reset()
   {
      if (committed) 
      {
          throw new IllegalStateException("Already committed");
      }
      outputHeaders.clear();
      underlyingOutputStream.buffer().clear();
      outputHeaders.clear();
   }
   
   public boolean isKeepAlive() {
       return keepAlive;
   }
}
