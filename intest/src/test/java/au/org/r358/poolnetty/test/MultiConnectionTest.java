/*
 * Copyright (c) 2014 R358 https://github.com/R358
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package au.org.r358.poolnetty.test;

import au.org.r358.poolnetty.common.*;
import au.org.r358.poolnetty.pool.NettyConnectionPool;
import au.org.r358.poolnetty.pool.NettyConnectionPoolBuilder;
import au.org.r358.poolnetty.pool.reaper.FullPassSimpleLeaseReaper;
import au.org.r358.poolnetty.test.simpleserver.SimpleInboundHandler;
import au.org.r358.poolnetty.test.simpleserver.SimpleOutboundHandler;
import au.org.r358.poolnetty.test.simpleserver.SimpleServer;
import au.org.r358.poolnetty.test.simpleserver.SimpleServerListener;
import au.org.r358.poolnetty.test.simpleserver.util.TestPoolProviderListener;
import au.org.r358.poolnetty.test.simpleserver.util.TestUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@RunWith(JUnit4.class)
public class MultiConnectionTest
{

    /**
     * Test the creation and leasing of connections and harvesting of expired leases.
     *
     * @throws Exception
     */
    @Test
    public void testHarvestingWithMultiples()
        throws Exception
    {

        final int maxImmortal = 5;
        final int maxEphemeral = 10;
        final int maxAll = maxEphemeral + maxImmortal;

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
        ncb.withImmortalCount(maxImmortal);
        ncb.withMaxEphemeralCount(maxEphemeral);
        ncb.withEphemeralLifespanMillis(5000);
        ncb.withReaperIntervalMillis(1000); // Set short for testing..
        ncb.withLeaseExpiryHarvester(new FullPassSimpleLeaseReaper());
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

        final CountDownLatch leaseAll = new CountDownLatch(maxAll);

        final CountDownLatch harvestedLeases = new CountDownLatch(maxAll);

        final CountDownLatch closedConnections = new CountDownLatch(maxEphemeral);

        final CountDownLatch openedConnections = new CountDownLatch(maxAll + maxImmortal);

        ncp.addListener(new PoolProviderListenerAdapter()
        {

            @Override
            public void connectionCreated(PoolProvider provider, Channel channel, boolean immortal)
            {
                openedConnections.countDown();
            }

            @Override
            public void leaseGranted(PoolProvider provider, Channel channel, Object userObject)
            {
                leaseAll.countDown();
            }

            @Override
            public void leaseExpired(PoolProvider provider, Channel channel, Object userObject)
            {
                harvestedLeases.countDown();
            }

            @Override
            public void connectionClosed(PoolProvider provider, Channel channel)
            {
                closedConnections.countDown();
            }
        });


        TestCase.assertTrue(ncp.start(10, TimeUnit.SECONDS));

        TestCase.assertEquals(5, ((List)TestUtil.getField(ncp, "immortalContexts")).size());
        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "ephemeralContexts")).size());

        //
        // Lease all 15 connections which is 5 immortal and 10 ephemeral.
        //

        List<LeasedChannel> leasedChannels = new ArrayList<>();


        for (int t = 0; t < 15; t++)
        {
            leasedChannels.add(ncp.lease(2, TimeUnit.SECONDS, t));
        }

        TestCase.assertTrue(leaseAll.await(5, TimeUnit.SECONDS));


        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "immortalContexts")).size());
        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "ephemeralContexts")).size());

        TestCase.assertEquals(maxAll, ((List)TestUtil.getField(ncp, "leasedContexts")).size());
        TestCase.assertEquals(maxAll, ((Set)TestUtil.getField(ncp, "leasedContextSet")).size());


        //
        // Over this period the leases should all expire and drop back to the pool.
        //

        TestCase.assertTrue(harvestedLeases.await(10, TimeUnit.SECONDS));

        //
        // Wait for ephemeral connections to be closed.
        //

        TestCase.assertTrue(closedConnections.await(10, TimeUnit.SECONDS));

        //
        // At this point we wait until the replace immortal connections are created.
        //

        // Total opened connections should be maxAll+maxImmortal.
        TestCase.assertTrue(openedConnections.await(10, TimeUnit.SECONDS));


        TestCase.assertEquals(5, ((List)TestUtil.getField(ncp, "immortalContexts")).size());

        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "ephemeralContexts")).size());

        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "leasedContexts")).size());
        TestCase.assertEquals(0, ((Set)TestUtil.getField(ncp, "leasedContextSet")).size());


        simpleServer.stop();

    }

    @Test
    public void leaseAndYield()
        throws Exception
    {
        final int maxImmortal = 5;
        final int maxEphemeral = 10;
        final int maxAll = maxEphemeral + maxImmortal;

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
        ncb.withImmortalCount(maxImmortal);
        ncb.withMaxEphemeralCount(maxEphemeral);
        ncb.withEphemeralLifespanMillis(5000);
        ncb.withReaperIntervalMillis(1000); // Set short for testing..
        ncb.withLeaseExpiryHarvester(new FullPassSimpleLeaseReaper());
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

        final CountDownLatch leaseAll = new CountDownLatch(maxAll);

        final CountDownLatch closedConnections = new CountDownLatch(maxEphemeral);

        final CountDownLatch openedConnections = new CountDownLatch(maxAll);

        final CountDownLatch yieldedConnections = new CountDownLatch(maxAll);

        ncp.addListener(new PoolProviderListenerAdapter()
        {

            @Override
            public void connectionCreated(PoolProvider provider, Channel channel, boolean immortal)
            {
                openedConnections.countDown();
            }

            @Override
            public void leaseGranted(PoolProvider provider, Channel channel, Object userObject)
            {
                leaseAll.countDown();
            }

            @Override
            public void leaseYield(PoolProvider provider, Channel channel, Object userObject)
            {
                yieldedConnections.countDown();
            }

            @Override
            public void connectionClosed(PoolProvider provider, Channel channel)
            {
                closedConnections.countDown();
            }
        });


        TestCase.assertTrue(ncp.start(10, TimeUnit.SECONDS));

        TestCase.assertEquals(5, ((List)TestUtil.getField(ncp, "immortalContexts")).size());
        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "ephemeralContexts")).size());

        //
        // Lease all 15 connections which is 5 immortal and 10 ephemeral.
        //

        List<LeasedChannel> leasedChannels = new ArrayList<>();


        for (int t = 0; t < maxAll; t++)
        {
            leasedChannels.add(ncp.lease(2, TimeUnit.SECONDS, t));
        }

        TestCase.assertTrue(leaseAll.await(5, TimeUnit.SECONDS));


        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "immortalContexts")).size());
        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "ephemeralContexts")).size());

        TestCase.assertEquals(maxAll, ((List)TestUtil.getField(ncp, "leasedContexts")).size());
        TestCase.assertEquals(maxAll, ((Set)TestUtil.getField(ncp, "leasedContextSet")).size());


        while (!leasedChannels.isEmpty())
        {
            leasedChannels.remove(0).yield();
        }

        TestCase.assertTrue(yieldedConnections.await(10, TimeUnit.SECONDS));

        //
        // At this point we have yielded the leases so we need to wait for
        // the ephemeral connections to be closed.
        //

        //
        // Wait for ephemeral connections to be closed.
        //

        TestCase.assertTrue(closedConnections.await(20, TimeUnit.SECONDS));

        //
        // At this point we wait until the replace immortal connections are created.
        //

        // Total opened connections should be maxAll.
        TestCase.assertTrue(openedConnections.await(10, TimeUnit.SECONDS));


        TestCase.assertEquals(5, ((List)TestUtil.getField(ncp, "immortalContexts")).size());

        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "ephemeralContexts")).size());

        TestCase.assertEquals(0, ((List)TestUtil.getField(ncp, "leasedContexts")).size());
        TestCase.assertEquals(0, ((Set)TestUtil.getField(ncp, "leasedContextSet")).size());

        simpleServer.stop();
    }


}
