package au.org.r358.pool;

import au.org.r358.pool.concurrent.DeferrableTask;
import au.org.r358.poolnetty.common.*;
import au.org.r358.poolnetty.common.exceptions.PoolProviderException;
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

    /**
     * Lease timeout scheduler.
     */
    protected final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();

    protected final ConnectionInfoProvider connectionInfoProvider;
    protected final ContextExceptionHandler contextExceptionHandler;
    protected final LeaseExpiredHandler leaseExpiredHandler;
    protected final PreGrantLease preGrantLease;
    protected final PreReturnToPool preReturnToPool;
    protected final BootstrapProvider bootstrapProvider;
    protected final PoolExceptionHandler poolExceptionHandler;
    protected final ExpiryHarvester expiryHarvester;

    protected final int immortalCount;
    protected final int maxEphemeralCount;
    protected final int ephemeralLifespan;
    protected final String inboundHandlerName;
    protected final int reaperInterval;

    private final int totalContexts;
    protected final Map<ChannelHandlerContext, Object> contextToCarrier = new HashMap<>();

    private final List<LeasedContext> leasedContexts = new ArrayList<>();

    /**
     * List of lease required tasks that could not be full filled without blocking the decoupler.
     */
    private final Deque<ObtainLease> leasesRequired = new ArrayDeque<>();

    /**
     * List of contexts that are immortal and do not age out.
     */
    private final List<AvailableContext> immortalContexts = new ArrayList<>();

    /**
     * List of contexts that are ephemeral.
     */
    private final List<AvailableContext> ephemeralContexts = new ArrayList<>();

    private boolean noNewLeases = false;
    private CountDownLatch stopLatch = null;


    public NettyConnectionPool(
        ConnectionInfoProvider connectionInfoProvider,
        ContextExceptionHandler contextExceptionHandler,
        LeaseExpiredHandler leaseExpiredHandler,
        PreGrantLease preGrantLease,
        PreReturnToPool preReturnToPool,
        BootstrapProvider bootstrapProvider,
        PoolExceptionHandler poolExceptionHandler,
        ExpiryHarvester expiryHarvester,
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
        this.immortalCount = immortalCount;
        this.maxEphemeralCount = maxEphemeralCount;
        this.ephemeralLifespan = ephemeralLifespan;
        this.inboundHandlerName = inboundHandlerName;
        this.reaperInterval = reaperInterval;
        this.totalContexts = immortalCount + maxEphemeralCount;
    }

    @Override
    public ChannelHandlerContext lease(final long time, final TimeUnit units)
        throws PoolProviderException
    {


        final ObtainLease ol = new ObtainLease(time, units);


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
    public void yield(ChannelHandlerContext ctx)
        throws PoolProviderException
    {
        Object carrier = contextToCarrier.get(ctx);


        if (carrier instanceof LeasedContext)
        {
            leasedContexts.remove(carrier);
            AvailableContext ac = null;

            if (((LeasedContext)carrier).isEphemeral())
            {
                ac = new AvailableContext(-1, ((LeasedContext)carrier).getChannelHandlerContext(), -1, false);
                immortalContexts.add(ac);
            }
            else
            {
                ac = new AvailableContext(System.currentTimeMillis() + ephemeralLifespan, ((LeasedContext)carrier).getChannelHandlerContext(), ephemeralLifespan, true);
                ephemeralContexts.add(ac);
            }

            contextToCarrier.put(ctx, ac);

            if (noNewLeases && leasedContexts.isEmpty())
            {
                decoupler.execute(new ShutdownTask());
            }

        }
        else if (carrier instanceof AvailableContext)
        {
            throw new PoolProviderException("Context is not out on lease.");
        }
        else
        {
            throw new PoolProviderException("Unknown context, had the lease expired.?");
        }

    }

    @Override
    public void Start(final CountDownLatch latch)
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

                if (latch != null)
                {
                    latch.countDown();
                }
            }
        });


    }

    /**
     * Stop the pool.
     *
     * @param force true to force shutdown immediately.
     * @param latch Optional latch on which countDown() is called when completed.
     */
    @Override
    public void Stop(final boolean force, final CountDownLatch latch)
    {

        decoupler.execute(new Runnable()
        {
            @Override
            public void run()
            {
                noNewLeases = true;
                stopLatch = latch;
                if (force || leasedContexts.isEmpty())
                {
                    new ShutdownTask().run();
                }
            }
        });

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
            ConnectionInfo ci = connectionInfoProvider.connectionInfo(NettyConnectionPool.this);

            final ChannelInitializer initializer = ci.getChannelInitializer();


            bs.handler(initializer);

            try
            {
                ChannelFuture future = bs.connect(ci.getRemoteSocketAddress(), ci.getLocalSocketAddress()).sync().await();


                if (future.isDone() && future.isSuccess())
                {

                    ChannelHandlerContext ctc = future.channel().pipeline().context(initializer);
                    //
                    //TODO check if there is a way to listen without adding a handler..
                    //
                    ctc.pipeline().addFirst(inboundHandlerName, new SimpleChannelInboundHandler()
                    {

                        @Override
                        public void channelWritabilityChanged(ChannelHandlerContext ctx)
                            throws Exception
                        {
                            super.channelWritabilityChanged(ctx);
                            decoupler.execute(new WritabilityChanged(ctx));
                        }

                        @Override
                        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
                            throws Exception
                        {
                            super.exceptionCaught(ctx, cause);
                            if (contextExceptionHandler.close(cause, NettyConnectionPool.this))
                            {
                                decoupler.execute(new CloseContext(ctx));
                            }
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx)
                            throws Exception
                        {
                            super.channelInactive(ctx);
                            decoupler.execute(new ChannelInactive(ctx));
                        }

                        @Override
                        protected void channelRead0(ChannelHandlerContext ctx, Object msg)
                            throws Exception
                        {
                            ctx.fireChannelRead(msg);
                        }
                    });


                    AvailableContext ac = null;
                    if (ephemeral)
                    {
                        ac = new AvailableContext(System.currentTimeMillis() + ephemeralLifespan, ctc, ephemeralLifespan, true);
                        ephemeralContexts.add(ac);
                    }
                    else
                    {
                        ac = new AvailableContext(-1, ctc, -1, false);
                        immortalContexts.add(ac);
                    }
                    contextToCarrier.put(ctc, ac);


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

        public ObtainLease(long time, TimeUnit units)
        {
            this.leaseTime = time;
            this.units = units;
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
                    LeasedContext lc = new LeasedContext(System.currentTimeMillis() + units.toMillis(leaseTime), ac.getChannelHandlerContext(), !ac.isImmortal());
                    leasedContexts.add(lc);
                    setResult(lc);
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


    /**
     * Builds the connection pool.
     * <p>
     * //TODO Example.
     * </p>
     */
    public static class Builder
    {
        private ConnectionInfoProvider connectionInfoProvider;
        private ContextExceptionHandler contextExceptionHandler;
        private LeaseExpiredHandler leaseExpiredHandler;
        private PreGrantLease preGrantLease;
        private PreReturnToPool preReturnToPool;
        private BootstrapProvider bootstrapProvider;
        private PoolExceptionHandler poolExceptionHandler;
        private ExpiryHarvester expiryHarvester;
        private String inboundHandlerName = "_pool";


        protected int immortalCount = 5;
        protected int maxEphemeralCount = 5;
        protected int ephemeralLifespan = 60000;
        protected int reaperInterval = 15000;

        public Builder(int immortalCount, int maxEphemeralCount, int ephemeralLifespan)
        {
            this.immortalCount = immortalCount;
            this.maxEphemeralCount = maxEphemeralCount;
            this.ephemeralLifespan = ephemeralLifespan;
        }

        public Builder setExpiryHarvester(ExpiryHarvester expiryHarvester)
        {
            this.expiryHarvester = expiryHarvester;
            return this;
        }

        public Builder withBootstrapProvider(BootstrapProvider bootstrapProvider)
        {
            this.bootstrapProvider = bootstrapProvider;
            return this;
        }

        public Builder withPoolExceptionHandler(PoolExceptionHandler poolExceptionHandler)
        {
            this.poolExceptionHandler = poolExceptionHandler;
            return this;
        }

        public Builder withConnectionInfoProvider(ConnectionInfoProvider connectionInfoProvider)
        {
            this.connectionInfoProvider = connectionInfoProvider;
            return this;
        }

        public Builder withContextExceptionHandler(ContextExceptionHandler contextExceptionHandler)
        {
            this.contextExceptionHandler = contextExceptionHandler;
            return this;
        }

        public Builder withLeaseExpiredHandler(LeaseExpiredHandler leaseExpiredHandler)
        {
            this.leaseExpiredHandler = leaseExpiredHandler;
            return this;
        }

        public Builder withPreGrantLease(PreGrantLease preGrantLease)
        {
            this.preGrantLease = preGrantLease;
            return this;
        }

        public Builder withPreReturnToPool(PreReturnToPool preReturnToPool)
        {
            this.preReturnToPool = preReturnToPool;
            return this;
        }

        public Builder withInboundHandlerName(String inboundHandlerName)
        {
            this.inboundHandlerName = inboundHandlerName;
            return this;
        }

        public Builder withReaperInterval(int reaperInterval)
        {
            this.reaperInterval = reaperInterval;
            return this;
        }

        public NettyConnectionPool build()
        {
            if (connectionInfoProvider == null)
            {
                throw new IllegalStateException("No connection info provider.");
            }

            if (bootstrapProvider == null)
            {
                throw new IllegalArgumentException("No Bootstrap provider.");
            }


            //
            // Default exception handling is to close the offending context ungracefully.
            //
            if (contextExceptionHandler == null)
            {
                contextExceptionHandler = new ContextExceptionHandler()
                {
                    @Override
                    public boolean close(Throwable throwable, PoolProvider provider)
                    {
                        return true;
                    }
                };
            }

            //
            // Default action is to close the expired context.
            // If you don't want to have leases then set the time to 0 when you take out the lease.
            //
            if (leaseExpiredHandler == null)
            {
                leaseExpiredHandler = new LeaseExpiredHandler()
                {
                    @Override
                    public boolean closeExpiredLease(ChannelHandlerContext context, PoolProvider provider)
                    {
                        return true;
                    }
                };
            }

            //
            // Default action is to continue to grant the lease.
            //
            if (preGrantLease == null)
            {
                preGrantLease = new PreGrantLease()
                {
                    @Override
                    public boolean continueToGrantLease(ChannelHandlerContext context, PoolProvider provider)
                    {
                        return true;
                    }
                };
            }

            if (preReturnToPool == null)
            {
                preReturnToPool = new PreReturnToPool()
                {
                    @Override
                    public boolean returnToPoolOrDisposeNow(ChannelHandlerContext context, PoolProvider provider)
                    {
                        return true;
                    }
                };
            }

            if (poolExceptionHandler == null)
            {
                poolExceptionHandler = new PoolExceptionHandler()
                {
                    @Override
                    public void handleException(Throwable th)
                    {
                        th.printStackTrace();
                    }
                };
            }


            return new NettyConnectionPool(
                connectionInfoProvider,
                contextExceptionHandler,
                leaseExpiredHandler,
                preGrantLease,
                preReturnToPool,
                bootstrapProvider,
                poolExceptionHandler,
                expiryHarvester,
                immortalCount,
                maxEphemeralCount,
                ephemeralLifespan,
                inboundHandlerName, reaperInterval);
        }
    }


    /**
     * Called when writability changes.
     */
    private class WritabilityChanged implements Runnable
    {

        private final ChannelHandlerContext context;

        public WritabilityChanged(ChannelHandlerContext ctx)
        {
            this.context = ctx;
        }


        @Override
        public void run()
        {
            //TODO This could be elaborated upon.

            Channel c = context.channel();
            if (!c.isActive() || !c.isOpen() || c.isWritable())
            {
                decoupler.execute(new CloseContext(context));
            }
        }
    }


    /**
     * Close a context task
     */
    private class CloseContext implements Runnable
    {
        private ChannelHandlerContext ctx;

        public CloseContext(ChannelHandlerContext ctx)
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
        }
    }

    /**
     * Called when the channel becomes inactive..
     */
    private class ChannelInactive implements Runnable
    {
        private final ChannelHandlerContext ctx;

        public ChannelInactive(ChannelHandlerContext ctx)
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

            if (stopLatch != null)
            {
                stopLatch.countDown();
            }

        }
    }
}
