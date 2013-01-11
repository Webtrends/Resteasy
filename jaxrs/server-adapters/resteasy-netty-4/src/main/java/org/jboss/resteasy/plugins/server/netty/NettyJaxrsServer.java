package org.jboss.resteasy.plugins.server.netty;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.socket.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.jboss.resteasy.core.SynchronousDispatcher;
import org.jboss.resteasy.plugins.server.embedded.EmbeddedJaxrsServer;
import org.jboss.resteasy.plugins.server.embedded.SecurityDomain;
import org.jboss.resteasy.spi.ResteasyDeployment;

import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 *
 * @author Ivan von Nagy
 */
public class NettyJaxrsServer implements EmbeddedJaxrsServer
{
    protected ServerBootstrap bootstrap;
    protected Channel channel;
    protected int port = 8080;
    protected ResteasyDeployment deployment = new ResteasyDeployment();
    protected String root = "";
    protected SecurityDomain domain;
    private int ioWorkerCount = Runtime.getRuntime().availableProcessors() * 2;
    private int executorThreadCount = 16;
    private SSLContext sslContext;
    private int maxRequestSize = 1024 * 1024 * 10;
    private boolean started = false;
   
    public void setSSLContext(SSLContext sslContext) 
    {
        this.sslContext = sslContext;
    }

    /**
    * Specify the worker count to use. For more informations about this please see the javadocs of {@link NioEventLoopGroup}
    * 
    * @param ioWorkerCount
    */
    public void setIoWorkerCount(int ioWorkerCount) 
    {
        this.ioWorkerCount = ioWorkerCount;
    }
   
    /**
    * Set the number of threads to use for the Executor. For more informations please see the javadocs of {@link OrderedMemoryAwareThreadPoolExecutor}. 
    * If you want to disable the use of the {@link ExecutionHandler} specify a value <= 0.  This should only be done if you are 100% sure that you don't have any blocking
    * code in there.
    * 
    * 
    * @param executorThreadCount
    */
    public void setExecutorThreadCount(int executorThreadCount)
    {
        this.executorThreadCount = executorThreadCount;
    }

    /**
    * Set the max. request size in bytes. If this size is exceed we will send a "413 Request Entity Too Large" to the client.
    * 
    * @param maxRequestSize the max request size. This is 10mb by default.
    */
    public void setMaxRequestSize(int maxRequestSize) 
    {
        this.maxRequestSize  = maxRequestSize;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort(int port)
    {
        this.port = port;
    }

    @Override
    public void setDeployment(ResteasyDeployment deployment)
    {
        this.deployment = deployment;
    }

    @Override
    public void setRootResourcePath(String rootResourcePath)
    {
        root = rootResourcePath;
        if (root != null && root.equals("/")) root = "";
    }

    @Override
    public ResteasyDeployment getDeployment()
    {
        return deployment;
    }

    @Override
    public void setSecurityDomain(SecurityDomain sc)
    {
        this.domain = sc;
    }

    @Override
    public synchronized void start()
    {
        if (started)
            return;
        
        deployment.start();
        RequestDispatcher dispatcher = new RequestDispatcher((SynchronousDispatcher)deployment.getDispatcher(), deployment.getProviderFactory(), domain);

        HttpChannelInitializer initializer;
        if (sslContext == null) {
            initializer = new HttpChannelInitializer(dispatcher, root, executorThreadCount, maxRequestSize);
        } else {
            initializer = new HttpsChannelInitializer(dispatcher, root, executorThreadCount, maxRequestSize, sslContext);
        }

        // Configure the server.
        bootstrap = new ServerBootstrap();
        bootstrap.group(new NioEventLoopGroup(), new NioEventLoopGroup(ioWorkerCount))
             .channel(NioServerSocketChannel.class)
             .localAddress(new InetSocketAddress(port))
             .handler(new LoggingHandler(LogLevel.DEBUG))
             .childHandler(initializer);
     
        // Bind and start to accept incoming connections.
        channel = bootstrap.bind().awaitUninterruptibly().channel();
    }

    @Override
    public synchronized void stop()
    {
        bootstrap.shutdown();
        deployment.stop();
        started = false;
    }
}