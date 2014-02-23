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

package au.org.r358.poolnetty.test.funcobs;

import au.org.r358.poolnetty.common.*;
import au.org.r358.poolnetty.pool.NettyConnectionPool;
import au.org.r358.poolnetty.pool.NettyConnectionPoolBuilder;
import au.org.r358.poolnetty.test.simpleserver.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Functional observation of Ephemeral use and clean up.
 * Uses single connection only.
 */
@RunWith(JUnit4.class)
public class EphemeralTest
{

    SimpleServer simpleServer = null;

    @After
    public void tearDown()
        throws Exception
    {

        if (simpleServer != null)
        {
            simpleServer.stop();
            simpleServer = null;
        }
    }

    /**
     * In this test an ephemeral connection is made as a result of a lease request.
     * Messages are exchanged and the channel is reaped and closed after yielding and has sat idle.
     *
     * @throws Exception
     */
    @Test
    public void testAgeOutOfEphemeral()
        throws Exception
    {

        final CountDownLatch startedLatch = new CountDownLatch(1);
        final CountDownLatch stopLatch = new CountDownLatch(1);
        final CountDownLatch connectionOpenedLatch = new CountDownLatch(1);
        final CountDownLatch connectionClosedLatch = new CountDownLatch(1);

        final CountDownLatch ephemeralAgedOut = new CountDownLatch(1);
        final CountDownLatch afterYeild = new CountDownLatch(1);


        final AtomicReference<String> messageAtServer = new AtomicReference<>(); // I need to set the message into something!

        final List<Object> listOfUserObjectReports = new ArrayList<>();

        final String originalMessage = "The cat sat on the mat.";


        //
        // Pool listener.
        //
        PoolProviderListener ppl = new PoolProviderListener()
        {
            @Override
            public void started(PoolProvider provider)
            {
                startedLatch.countDown();
            }

            @Override
            public void stopped(PoolProvider provider)
            {
                stopLatch.countDown();
            }

            @Override
            public void leaseRequested(PoolProvider provider, int leaseTime, TimeUnit units, Object userObject)
            {

                listOfUserObjectReports.add(userObject.toString() + ".request");
            }

            @Override
            public void leaseGranted(PoolProvider provider, Channel channel, Object userObject)
            {

                listOfUserObjectReports.add(userObject.toString() + ".granted");
            }

            @Override
            public void leaseYield(PoolProvider provider, Channel channel, Object userObject)
            {

                listOfUserObjectReports.add(userObject.toString() + ".yield");
                afterYeild.countDown();
            }

            @Override
            public void leaseExpired(PoolProvider provider, Channel channel, Object userObject)
            {
                // Not tested here..
            }

            @Override
            public void connectionClosed(PoolProvider provider, Channel ctx)
            {
                connectionClosedLatch.countDown();
            }

            @Override
            public void connectionCreated(PoolProvider provider, Channel ctx, boolean immortal)
            {
                connectionOpenedLatch.countDown();
            }

            @Override
            public void ephemeralReaped(PoolProvider poolProvider, Channel channel)
            {
                ephemeralAgedOut.countDown();
            }
        };


        //
        // The simple server side for testing.
        //

        simpleServer = new SimpleServer("127.0.0.1", 1887, 10, new SimpleServerListener()
        {

            @Override
            public void newConnection(ChannelHandlerContext ctx)
            {

            }

            @Override
            public void newValue(ChannelHandlerContext ctx, String val)
            {
                messageAtServer.set(val);
                ctx.writeAndFlush(val);
            }
        });

        simpleServer.start();


        //
        // Build the pool.
        //

        NettyConnectionPoolBuilder ncb = new NettyConnectionPoolBuilder(0, 1, 2000);


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
        //
        // Should start as normal but with no immortal connections made.
        //
        TestCase.assertTrue("Not started..", startedLatch.await(5, TimeUnit.SECONDS));


        String userObject = "Foo!";

        //
        // Lease a channel, which should trigger channel opening..
        //
        Channel ctx = ncp.lease(10, TimeUnit.DAYS, userObject);

        TestCase.assertTrue("Opening connection..", connectionOpenedLatch.await(5, TimeUnit.SECONDS));


        final CountDownLatch respLatch = new CountDownLatch(1);
        final AtomicReference<String> respValue = new AtomicReference<>();

        //
        // Remember that any mods you make the pipeline when you have leased the channel
        // Will impact the next lease holder.
        //

        ctx.pipeline().addLast("_foo_", new ChannelInboundHandlerAdapter()
        {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg)
                throws Exception
            {

                respValue.set(msg.toString());
                respLatch.countDown();
            }
        });


        // Send the message.
        ctx.writeAndFlush(originalMessage);

        //
        // Did we get a response back from the server.
        //
        TestCase.assertTrue("Echo from server.", respLatch.await(5, TimeUnit.SECONDS));


        //
        // Hold off on yielding the lease, the connection should not be reaped when it is leased.
        //


        if (ephemeralAgedOut.await(4, TimeUnit.SECONDS))
        {
            TestCase.fail("Ephemeral connection was harvested while leased.");
        }


        //
        // Yield lease.
        //
        ncp.yield(ctx);

        if (!afterYeild.await(5, TimeUnit.SECONDS))
        {
            TestCase.fail("Yield did not occur.");
        }


        List l = (List)TestUtil.getField(ncp, "ephemeralContexts");

        //
        // Should be 1 ephemeral contexts.
        //
        TestCase.assertEquals(1, l.size());


        //
        // After yield the ephemeral connection should live on for one unit of its lifespan.
        //

        long tstart = System.currentTimeMillis();

        if (!ephemeralAgedOut.await(4, TimeUnit.SECONDS))
        {
            TestCase.fail("Ephemeral connection was not harvested.");
        }

        long duration = System.currentTimeMillis() - tstart;
        if (duration < 2000)
        {
            TestCase.fail("Ephemeral expired early.");
        }


        l = (List)TestUtil.getField(ncp, "ephemeralContexts");

        //
        // Should be no ephemeral contexts.
        //
        TestCase.assertEquals(0, l.size());

        //
        // We should see a connection closed.
        //
        TestCase.assertTrue("Connection Not Closed.", connectionClosedLatch.await(5, TimeUnit.SECONDS));

        ncp.stop(false);

        TestCase.assertTrue("Not stopped.", stopLatch.await(5, TimeUnit.SECONDS));

        //
        // Check we got back what we sent etc.
        //


        TestCase.assertEquals(originalMessage, messageAtServer.get());
        TestCase.assertEquals(originalMessage, respValue.get());


        //
        // - Request lease, Lease granted , Lease yielded check order.
        //
        TestCase.assertEquals(3, listOfUserObjectReports.size()); // Should only be 3 reports.

        TestCase.assertEquals(userObject + ".request", listOfUserObjectReports.get(0));
        TestCase.assertEquals(userObject + ".granted", listOfUserObjectReports.get(1));
        TestCase.assertEquals(userObject + ".yield", listOfUserObjectReports.get(2));

        simpleServer.stop();
    }

}
