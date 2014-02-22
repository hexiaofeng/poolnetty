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

package au.org.r358.poolnetty.pool;

import au.org.r358.poolnetty.common.*;
import au.org.r358.poolnetty.common.exceptions.PoolProviderException;
import au.org.r358.poolnetty.pool.concurrent.DeferrableTask;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;

import java.util.*;
import java.util.concurrent.*;

/**
 *
 */
public class NettyConnectionPool implements PoolProvider
{

    /**
     * Task decoupler.
     */
    private final Executor decoupler = Executors.newSingleThreadExecutor();

    protected final ConnectionInfoProvider connectionInfoProvider;
    protected final ContextExceptionHandler contextExceptionHandler;
    protected final LeaseExpiredHandler leaseExpiredHandler;
    protected final PreGrantLease preGrantLease;
    protected final PreReturnToPool preReturnToPool;
    protected final BootstrapProvider bootstrapProvider;
    protected final PoolExceptionHandler poolExceptionHandler;
    protected final ExpiryHarvester expiryHarvester;
    protected final PostConnectEstablish postConnectEstablish;

    protected final int immortalCount;
    protected final int maxEphemeralCount;
    protected final int ephemeralLifespan;
    protected final String inboundHandlerName;
    protected final int reaperInterval;


    protected final Map<Channel, Object> contextToCarrier = new HashMap<>();

    protected final List<LeasedContext> leasedContexts = new ArrayList<>();

    protected final CopyOnWriteArraySet<PoolProviderListener> listeners = new CopyOnWriteArraySet<>();

    /**
     * List of lease required tasks that could not be full filled without blocking the decoupler.
     */
    protected final Deque<ObtainLease> leasesRequired = new ArrayDeque<>();

    /**
     * List of contexts that are immortal and do not age out.
     */
    protected final List<AvailableContext> immortalContexts = new ArrayList<>();

    /**
     * List of contexts that are ephemeral.
     */
    protected final List<AvailableContext> ephemeralContexts = new ArrayList<>();

    /**
     * List of opening connections.
     */
    protected final List<OpenConnection> connectionsInProgress = new ArrayList<>();


    protected boolean noNewLeases = false;
    protected CountDownLatch stopLatch = null;


    protected NettyConnectionPool(
        ConnectionInfoProvider connectionInfoProvider,
        ContextExceptionHandler contextExceptionHandler,
        LeaseExpiredHandler leaseExpiredHandler,
        PreGrantLease preGrantLease,
        PreReturnToPool preReturnToPool,
        BootstrapProvider bootstrapProvider,
        PoolExceptionHandler poolExceptionHandler,
        ExpiryHarvester expiryHarvester,
        PostConnectEstablish postConnectEstablish,
        int immortalCount,
        int maxEphemeralCount,
        int ephemeralLifespan, String inboundHandlerName, int reaperInterval)
    {
        this.connectionInfoProvider = connectionInfoProvider;
        this.contextExceptionHandler = contextExceptionHandler;
        this.leaseExpiredHandler = leaseExpiredHandler;
        this.preGrantLease = preGrantLease;
        this.preReturnToPool = preReturnToPool;
        this.bootstrapProvider = bootstrapProvider;
        this.poolExceptionHandler = poolExceptionHandler;
        this.expiryHarvester = expiryHarvester;
        this.postConnectEstablish = postConnectEstablish;
        this.immortalCount = immortalCount;
        this.maxEphemeralCount = maxEphemeralCount;
        this.ephemeralLifespan = ephemeralLifespan;
        this.inboundHandlerName = inboundHandlerName;
        this.reaperInterval = reaperInterval;

    }

    @Override
    public Channel lease(final int time, final TimeUnit units, final Object userObject)
        throws PoolProviderException
    {

        final ObtainLease ol = new ObtainLease(time, units, userObject);

        fireLeaseRequested(time, units, userObject);
        decoupler.execute(ol);

        try
        {
            return ol.get().getChannelHandlerContext();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new PoolProviderException("Interrupted: " + e.getMessage(), e);
        }
        catch (ExecutionException e)
        {
            throw new PoolProviderException("Execution Failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void yield(final Channel ctx)
        throws PoolProviderException
    {

        decoupler.execute(new Runnable()
        {
            @Override
            public void run()
            {


                Object carrier = contextToCarrier.get(ctx);


                if (carrier instanceof LeasedContext)
                {
                    leasedContexts.remove(carrier);
                    AvailableContext ac = null;

                    if (((LeasedContext)carrier).isImmortal())
                    {
                        ac = new AvailableContext(-1, ((LeasedContext)carrier).getChannelHandlerContext(), -1, true);
                        immortalContexts.add(ac);
                    }
                    else
                    {
                        ac = new AvailableContext(System.currentTimeMillis() + ephemeralLifespan, ((LeasedContext)carrier).getChannelHandlerContext(), ephemeralLifespan, false);
                        ephemeralContexts.add(ac);
                    }

                    contextToCarrier.put(ctx, ac);

                    if (noNewLeases && leasedContexts.isEmpty())
                    {
                        decoupler.execute(new ShutdownTask());
                    }
                    else
                    {
                        pollNextRequestOntoDecoupler();
                    }

                    fireLeaseYield(NettyConnectionPool.this, ctx, ((LeasedContext)carrier).getUserObject());
                }
                else if (carrier instanceof AvailableContext)
                {
                    poolExceptionHandler.handleException(new PoolProviderException("Context is not out on lease."));
                }
                else
                {
                    poolExceptionHandler.handleException(new PoolProviderException("Unknown channel, has the lease expired.?"));
                }
            }
        });
    }

    @Override
    public void start()
        throws Exception
    {

        decoupler.execute(new Runnable()
        {
            @Override
            public void run()
            {
                for (int t = 0; t < immortalCount; t++)
                {
                    new OpenConnection(false).run();
                }

                fireStarted();

            }
        });


    }

    /**
     * stop the pool.
     *
     * @param force true to force shutdown immediately.
     */
    @Override
    public void stop(final boolean force)
    {

        decoupler.execute(new Runnable()
        {
            @Override
            public void run()
            {
                noNewLeases = true;

                if (force || leasedContexts.isEmpty())
                {
                    new ShutdownTask().run();
                }


            }
        });

    }

    @Override
    public void execute(Runnable runnable)
    {
        decoupler.execute(runnable);
    }

    @Override
    public void addListener(PoolProviderListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void removeListener(PoolProviderListener listener)
    {
        listeners.remove(listener);
    }

    /**
     * Apply pre-lease to a list of free contexts and grab the first one that passes.
     *
     * @param contextList The list of AvailableContexts.
     * @return An AvailableContext object or null if none were found.
     */
    private AvailableContext applyPreLease(List<AvailableContext> contextList)
    {

        if (!contextList.isEmpty())
        {
            int c = contextList.size();

            while (--c >= 0)
            {
                AvailableContext ac = contextList.remove(0);
                if (preGrantLease.continueToGrantLease(ac.getChannelHandlerContext(), NettyConnectionPool.this))
                {
                    return ac;
                }
                else
                {
                    contextList.add(ac);
                    // Append what we cannot lease to the end with the assumption that the
                    // next in the list will able to be leased.
                }

            }
        }

        return null;
    }


    protected void fireStarted()
    {
        for (PoolProviderListener l : listeners)
        {
            l.started(this);
        }
    }

    protected void fireStopped()
    {
        for (PoolProviderListener l : listeners)
        {
            l.stopped(this);
        }
    }

    protected void fireLeaseRequested(int leaseTime, TimeUnit units, Object userObject)
    {
        for (PoolProviderListener l : listeners)
        {
            l.leaseRequested(this, leaseTime, units, userObject);
        }
    }

    protected void fireLeaseGranted(PoolProvider provider, Channel channel, Object userObject)
    {
        for (PoolProviderListener l : listeners)
        {
            l.leaseGranted(this, channel, userObject);
        }
    }

    protected void fireLeaseYield(PoolProvider provider, Channel channel, Object userObject)
    {
        for (PoolProviderListener l : listeners)
        {
            l.leaseYield(this, channel, userObject);
        }
    }

    protected void fireLeaseExpired(PoolProvider provider, Channel channel, Object userObject)
    {
        for (PoolProviderListener l : listeners)
        {
            l.leaseExpired(this, channel, userObject);
        }
    }

    protected void fireConnectionClosed(Channel ctx)
    {
        for (PoolProviderListener l : listeners)
        {
            l.connectionClosed(this, ctx);
        }
    }

    protected void fireConnectionCreated(Channel ctx, boolean immortal)
    {
        for (PoolProviderListener l : listeners)
        {
            l.connectionCreated(this, ctx, immortal);
        }
    }


    /**
     * Open a connection.
     */
    private class OpenConnection implements Runnable
    {


        private final boolean ephemeral;

        private OpenConnection(boolean ephemeral)
        {

            this.ephemeral = ephemeral;
        }

        @Override
        public void run()
        {
            Bootstrap bs = bootstrapProvider.createBootstrap(NettyConnectionPool.this);
            final ConnectionInfo ci = connectionInfoProvider.connectionInfo(NettyConnectionPool.this);

            final ChannelInitializer initializer = ci.getChannelInitializer();


            bs.handler(initializer);

            try
            {
                ChannelFuture future = bs.connect(ci.getRemoteSocketAddress(), ci.getLocalSocketAddress()).sync().await();


                if (future.isDone() && future.isSuccess())
                {


                    //
                    //TODO check if there is a way to listen without adding a handler..
                    //
                    future.channel().pipeline().addLast(inboundHandlerName, new SimpleChannelInboundHandler()
                    {

                        @Override
                        public void channelWritabilityChanged(ChannelHandlerContext ctx)
                            throws Exception
                        {
                            super.channelWritabilityChanged(ctx);
                            decoupler.execute(new WritabilityChanged(ctx.channel()));
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                            throws Exception
                        {
                            super.exceptionCaught(ctx, cause);
                            if (contextExceptionHandler.close(cause, NettyConnectionPool.this))
                            {
                                decoupler.execute(new CloseContext(ctx.channel()));
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx)
                            throws Exception
                        {
                            super.channelInactive(ctx);
                            decoupler.execute(new ChannelInactive(ctx.channel()));
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                            throws Exception
                        {
                            ctx.fireChannelRead(msg);
                        }
                    });

                    final Channel ctc = future.channel();


                    //
                    // Do post connect establish phase.
                    //
                    postConnectEstablish.establish(ctc, NettyConnectionPool.this, new Runnable()
                    {

                        @Override
                        public void run()
                        {
                            AvailableContext ac = null;
                            if (ephemeral)
                            {
                                ac = new AvailableContext(System.currentTimeMillis() + ephemeralLifespan, ctc, ephemeralLifespan, true);
                                fireConnectionCreated(ctc, true);
                                ephemeralContexts.add(ac);
                            }
                            else
                            {
                                ac = new AvailableContext(-1, ctc, -1, false);
                                fireConnectionCreated(ctc, false);
                                immortalContexts.add(ac);
                            }
                            contextToCarrier.put(ctc, ac);

                            //
                            // If there are requests pending that are off the decoupler then
                            // put the first one in the deque back on the decoupler before exiting.
                            //
                            pollNextRequestOntoDecoupler();
                            connectionsInProgress.remove(OpenConnection.this);

                        }
                    });


                }
                else
                {
                    //
                    // Connection opening failed.
                    //
                    connectionsInProgress.remove(OpenConnection.this);
                }

            }
            catch (InterruptedException iex)
            {
                //TODO decide if we interrupt the decoupler.. probably not.
                poolExceptionHandler.handleException(iex);
            }
        }
    }

    /**
     * Obtain a lease deferrable task.
     */
    private class ObtainLease extends DeferrableTask<LeasedContext>
    {
        private final long leaseTime;
        private final TimeUnit units;
        private final Object userObject;

        public ObtainLease(long time, TimeUnit units, Object userObject)
        {
            this.leaseTime = time;
            this.units = units;
            this.userObject = userObject;
        }


        @Override
        public void defer()
            throws Exception
        {
            leasesRequired.addFirst(this); // Put this back on the front of deque for later pickup.
        }

        @Override
        public boolean runOrDefer()
            throws Exception
        {

            if (noNewLeases)
            {
                throw new IllegalStateException("Pool is shutting down.");
            }

            //
            // Check this is not stepping in front of older requests.
            //
            if (firstAttempt && !leasesRequired.isEmpty())
            {
                leasesRequired.addLast(this);
                return true;             // Defer this till later.
            }

            //
            // Can we satisfy this immediately
            //

            AvailableContext ac = applyPreLease(immortalContexts);   // From immortals.

            if (ac == null)
            {
                ac = applyPreLease(ephemeralContexts); // From ephemeral.
            }


            if (ac != null)
            {
                //
                // Has it expired.
                //
                if (ac.expired(System.currentTimeMillis()))
                {
                    ac.getChannelHandlerContext().close();
                    decoupler.execute(new CloseContext(ac.getChannelHandlerContext()));
                }
                else
                {
                    LeasedContext lc = new LeasedContext(System.currentTimeMillis() + units.toMillis(leaseTime), ac.getChannelHandlerContext(), !ac.isImmortal(), userObject);
                    leasedContexts.add(lc);
                    contextToCarrier.put(lc.getChannelHandlerContext(), lc);


                    //
                    // We got a lease the next one might too.
                    //
                    pollNextRequestOntoDecoupler();


                    setResult(lc);
                    fireLeaseGranted(NettyConnectionPool.this, lc.getChannelHandlerContext(), userObject);


                    return false; // Lease granted.
                }
            }

            //
            // Can we add another connection.
            //
            if (ephemeralContexts.size() < maxEphemeralCount)
            {
                decoupler.execute(new OpenConnection(true));
            }

            return true;
        }


    }

    private void pollNextRequestOntoDecoupler()
    {
        if (leasesRequired.isEmpty())
        {
            return;
        }

        decoupler.execute(leasesRequired.pollFirst());
    }


    /**
     * Called when writability changes.
     */
    private class WritabilityChanged implements Runnable
    {

        private final Channel channel;

        public WritabilityChanged(Channel ctx)
        {
            this.channel = ctx;
        }


        @Override
        public void run()
        {
            //TODO This could be elaborated upon.


            if (!channel.isActive() || !channel.isOpen() || channel.isWritable())
            {
                decoupler.execute(new CloseContext(channel));
            }
        }
    }


    /**
     * Close a channel task
     */
    private class CloseContext implements Runnable
    {
        private Channel ctx;

        public CloseContext(Channel ctx)
        {
            this.ctx = ctx;
        }

        @Override
        public void run()
        {
            Object o = contextToCarrier.remove(ctx);
            if (o instanceof AvailableContext)
            {
                immortalContexts.remove(o);
                ephemeralContexts.remove(o);
            }
            else
            {
                leasedContexts.remove(o);
            }

            ctx.close();
            fireConnectionClosed(ctx);
        }
    }


    /**
     * Called when the channel becomes inactive..
     */
    private class ChannelInactive implements Runnable
    {
        private final Channel ctx;

        public ChannelInactive(Channel ctx)
        {
            this.ctx = ctx;
        }

        @Override
        public void run()
        {
            decoupler.execute(new CloseContext(ctx));
        }
    }

    private class ShutdownTask implements Runnable
    {
        @Override
        public void run()
        {

            for (LeasedContext lc : leasedContexts)
            {
                lc.getChannelHandlerContext().close();
            }

            for (AvailableContext ac : immortalContexts)
            {

                try
                {
                    ac.getChannelHandlerContext().close();
                }
                catch (Exception ex)
                {
                    poolExceptionHandler.handleException(ex);
                }
            }

            for (AvailableContext ac : ephemeralContexts)
            {

                try
                {
                    ac.getChannelHandlerContext().close();
                }
                catch (Exception ex)
                {
                    poolExceptionHandler.handleException(ex);
                }
            }

            fireStopped();

        }
    }
}
