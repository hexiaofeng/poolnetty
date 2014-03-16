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

package org.r358.poolnetty.test.funcobs;

import org.r358.poolnetty.common.*;
import org.r358.poolnetty.pool.NettyConnectionPool;
import org.r358.poolnetty.pool.NettyConnectionPoolBuilder;
import org.r358.poolnetty.pool.reaper.FullPassSimpleLeaseReaper;
import org.r358.poolnetty.test.simpleserver.SimpleInboundHandler;
import org.r358.poolnetty.test.simpleserver.SimpleOutboundHandler;
import org.r358.poolnetty.test.simpleserver.SimpleServer;
import org.r358.poolnetty.test.simpleserver.SimpleServerListener;
import org.r358.poolnetty.test.simpleserver.util.TestPoolProviderListener;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@RunWith(JUnit4.class)
public class LifecycleTest
{

    /**
     * Test that during unforced shutdown it:
     * <ul>
     * <l1>Await the return of all leased connections.</l1>
     * <li>Blocks the leasing of connections.</li>
     * <p/>
     * <p>Also test that leases are not granted during this time.</p>
     * </ul>
     *
     * @throws Exception
     */
    @Test
    public void testUnforcedShutdownDuringLease()
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
        ncb.withImmortalCount(2);
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

        ncp.start(0, TimeUnit.SECONDS);

        LeasedChannel firstLease = ncp.lease(5, TimeUnit.SECONDS, "aardvarks");

        ncp.stop(false);

        //
        // Should not stop because channel is being leased.
        //
        TestCase.assertFalse(ppl.getStoppedLatch().await(1, TimeUnit.SECONDS));

        //
        // Should not grant lease as shutdown is pending.
        //
        try
        {
            ncp.lease(3, TimeUnit.SECONDS, "ground pigs");
            TestCase.fail();

        }
        catch (Exception ex)
        {

        }


        Future<LeasedChannel> future = ncp.leaseAsync(1, TimeUnit.SECONDS, "Erdferkel");
        try
        {
            future.get(1, TimeUnit.SECONDS);
            TestCase.fail(); // Should fail.
        }
        catch (Exception ex)
        {

        }

        ncp.leaseAsync(1, TimeUnit.SECONDS, "Erdferkel", new LeaseListener()
        {
            @Override
            public void leaseRequest(boolean success, LeasedChannel channel, Throwable th)
            {
                TestCase.assertFalse(success);
                TestCase.assertTrue(th instanceof IllegalArgumentException);
            }
        });


        firstLease.yield();

        TestCase.assertTrue(ppl.getStoppedLatch().await(5, TimeUnit.SECONDS));

        simpleServer.stop();

    }


    @Test
    public void testLeaseCancellationViaFuture()
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
        ncb.withMaxEphemeralCount(0);
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

        ncp.start(0, TimeUnit.SECONDS);

        //
        // Get the first lease.
        //
        LeasedChannel firstLease = ncp.lease(5, TimeUnit.SECONDS, "aardvarks");


        final AtomicBoolean failedInListener = new AtomicBoolean(false);

        Future<LeasedChannel> secondLease = ncp.leaseAsync(10, TimeUnit.SECONDS, "Erdferkel", new LeaseListener()
        {
            @Override
            public void leaseRequest(boolean success, LeasedChannel channel, Throwable th)
            {
                failedInListener.set(true);
            }
        });

        try
        {
            secondLease.get(1, TimeUnit.SECONDS);
            TestCase.fail();
        }
        catch (Exception ex)
        {
            TestCase.assertEquals(TimeoutException.class, ex.getClass());
        }

        secondLease.cancel(false); // The flag has no effect.

        firstLease.yield();


        //
        // Lease cancellation is asynchronous to the pool, but the detection of a canceled lease is done
        // 1. Before the lease logic executes on the lease request.
        // 2. After the lease logic executes, which will then cause an immediate yield to execute.
        //
        // However if the lease is pending it will be sitting in a holding queue and will be removed from there.
        //
        // The future listener event is driven from the future so calling cancel() on that will fire
        // the future listener on the thread that called cancel but the pool may fire leaseCanceled
        // potentially at some point in between because it is driven from the pools thread.
        //
        // Between those two sources 0f information the order of notification is indeterminate.

        //
        // The call to cancel() may also through an illegal state exception if the granting of the lease is in
        // progress at that moment.
        //
        // For testing sake we give it a moment to settle.

        Thread.sleep(500); // Excessive.

        TestCase.assertTrue(secondLease.isCancelled());
        TestCase.assertTrue(failedInListener.get());
        TestCase.assertTrue(ppl.getLeaseCanceled().await(5, TimeUnit.SECONDS));


        secondLease = ncp.leaseAsync(10, TimeUnit.SECONDS, "Foo");

        try
        {
            LeasedChannel lc = secondLease.get(1, TimeUnit.SECONDS);
            TestCase.assertEquals("Foo", lc.getUserObject());
        }
        catch (Exception ex)
        {
            TestCase.fail();
        }


        ncp.stop(true);
        TestCase.assertTrue(ppl.getStoppedLatch().await(5, TimeUnit.SECONDS));


        simpleServer.stop();
    }

}
