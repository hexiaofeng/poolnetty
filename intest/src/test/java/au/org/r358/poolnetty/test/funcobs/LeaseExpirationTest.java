package au.org.r358.poolnetty.test.funcobs;

import au.org.r358.poolnetty.common.BootstrapProvider;
import au.org.r358.poolnetty.common.ConnectionInfo;
import au.org.r358.poolnetty.common.ConnectionInfoProvider;
import au.org.r358.poolnetty.common.LeaseExpiredHandler;
import au.org.r358.poolnetty.common.LeasedChannel;
import au.org.r358.poolnetty.common.LeasedContext;
import au.org.r358.poolnetty.common.Leasee;
import au.org.r358.poolnetty.common.PoolExceptionHandler;
import au.org.r358.poolnetty.common.PoolProvider;
import au.org.r358.poolnetty.common.concurrent.ValueEvent;
import au.org.r358.poolnetty.common.exceptions.PoolProviderException;
import au.org.r358.poolnetty.pool.NettyConnectionPool;
import au.org.r358.poolnetty.pool.NettyConnectionPoolBuilder;
import au.org.r358.poolnetty.pool.reaper.FullPassSimpleReaperLease;
import au.org.r358.poolnetty.test.simpleserver.SimpleInboundHandler;
import au.org.r358.poolnetty.test.simpleserver.SimpleOutboundHandler;
import au.org.r358.poolnetty.test.simpleserver.SimpleServer;
import au.org.r358.poolnetty.test.simpleserver.SimpleServerListener;
import au.org.r358.poolnetty.test.simpleserver.util.TestPoolProviderListener;
import au.org.r358.poolnetty.test.simpleserver.util.TestUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test lease expiration with no introduced errors.
 */
@RunWith(JUnit4.class)
public class LeaseExpirationTest
{
    @Test
    public void testBasicLeaseExpiration()
        throws Exception
    {


        TestPoolProviderListener ppl = new TestPoolProviderListener();
        final ArrayList<Object> serverReceivedMessages = new ArrayList<>();
        String testMessage = "The cat sat on the mat.";


        //
        // The simple server side for testing.
        //

        SimpleServer simpleServer = new SimpleServer("127.0.0.1", 1887, 10, new SimpleServerListener()
        {

            @Override
            public void newConnection(ChannelHandlerContext ctx)
            {

            }

            @Override
            public void newValue(ChannelHandlerContext ctx, String val)
            {
                serverReceivedMessages.add(val);
                ctx.writeAndFlush(val);
            }
        });

        simpleServer.start();


        final CountDownLatch leaseExpiredHandlerCalled = new CountDownLatch(1);

        //
        // Build the pool.
        //

        NettyConnectionPoolBuilder ncb = new NettyConnectionPoolBuilder();
        ncb.withImmortalCount(1);
        ncb.withReaperIntervalMillis(1000); // Set short for testing..
        ncb.withLeaseExpiryHarvester(new FullPassSimpleReaperLease());
        ncb.withLeaseExpiredHandler(new LeaseExpiredHandler()
        {
            @Override
            public boolean closeExpiredLease(LeasedContext context, PoolProvider provider)
            {
                leaseExpiredHandlerCalled.countDown();
                return true; // Cause lease to be expired.
            }
        });


        final EventLoopGroup elg = new NioEventLoopGroup();


        //
        // Create the boot strap.
        //
        ncb.withBootstrapProvider(new BootstrapProvider()
        {
            @Override
            public Bootstrap createBootstrap(PoolProvider poolProvider)
            {
                Bootstrap bs = new Bootstrap();
                bs.group(elg);
                bs.channel(NioSocketChannel.class);
                bs.option(ChannelOption.SO_KEEPALIVE, true);
                bs.option(ChannelOption.AUTO_READ, true);
                return bs;
            }
        });


        //
        // Sets up the connection info and the channel initializer.
        //
        ncb.withConnectionInfoProvider(new ConnectionInfoProvider()
        {
            @Override
            public ConnectionInfo connectionInfo(PoolProvider poolProvider)
            {

                return new ConnectionInfo(new InetSocketAddress("127.0.0.1", 1887), null, new ChannelInitializer()
                {
                    @Override
                    protected void initChannel(Channel ch)
                        throws Exception
                    {
                        ch.pipeline().addLast("decode", new SimpleInboundHandler(10));
                        ch.pipeline().addLast("encode", new SimpleOutboundHandler(10));
                    }
                });


            }
        });


        //
        // Make the pool add listener and start.
        //
        NettyConnectionPool ncp = ncb.build();
        ncp.addListener(ppl);

        ncp.start();

        LeasedChannel lchan = ncp.lease(3, TimeUnit.SECONDS, "aardvarks");

        final CountDownLatch lcanValueCallbackOccured = new CountDownLatch(1);


        lchan.onLeaseExpire(new ValueEvent<Leasee>()
        {
            @Override
            public void on(Leasee value)
            {
                lcanValueCallbackOccured.countDown();
            }
        });


        lchan.writeAndFlush(testMessage);


        TestCase.assertTrue("Lease expired event on listener", ppl.getLeaseExpired().await(4, TimeUnit.SECONDS));
        TestCase.assertTrue("Callback on Leased channel", lcanValueCallbackOccured.await(4, TimeUnit.SECONDS));
        TestCase.assertTrue("Lease handler called", leaseExpiredHandlerCalled.await(4, TimeUnit.SECONDS));


        //
        // Wait for the channel to close.
        //
        TestCase.assertTrue("Channel Closed", ppl.getConnectionClosed().await(4, TimeUnit.SECONDS));


        //
        // Check we can't write.
        //
        lchan.writeAndFlush("aardvark").addListener(new ChannelFutureListener()
        {
            @Override
            public void operationComplete(ChannelFuture future)
                throws Exception
            {
                TestCase.assertFalse("Should not work.", future.isSuccess());
            }
        });


        //
        // Yielding should cause an issue.
        //


        final CountDownLatch gotExceptionWhenTryingToYield = new CountDownLatch(1);
        final AtomicReference<PoolProviderException> yieldException = new AtomicReference<>();


        //
        // We need to intercept the exception when the expired lease is yielded.
        //
        TestUtil.setField(ncp, "poolExceptionHandler", new PoolExceptionHandler()
        {
            @Override
            public void handleException(Throwable th)
            {
                gotExceptionWhenTryingToYield.countDown();
                yieldException.set((PoolProviderException)th);
            }
        });


        //
        // This yield should fail because the channel has been expired and forced close.
        //
        lchan.yield();

        //
        // Test we got the exception and test we got the correct message.
        //
        TestCase.assertTrue(gotExceptionWhenTryingToYield.await(4, TimeUnit.SECONDS));
        TestCase.assertEquals("Unknown channel, has the lease expired?", yieldException.get().getMessage());


    }

}
