package au.org.r358.poolnetty.common;

import au.org.r358.poolnetty.common.exceptions.PoolProviderException;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * All pool implementations implement this interface is provides the basic concepts of:
 * <ol>
 * <li><B>Request</B> Lease a resource from the pool.</li>
 * <li><B>Release</B> Yield a resource back to the pool.</li>
 * </ol>
 */
public interface PoolProvider
{
    /**
     * Request a ChannelHandlerContext from the pool
     *
     * @return The context.
     * @throws Exception
     */
    ChannelHandlerContext lease(long leaseTime, TimeUnit units)
        throws PoolProviderException;

    /**
     * Release a context back to the pool
     *
     * @param ctx The context to release.
     * @throws PoolProviderException if the context is unknown to the pool or declared lost to the pool because lease time expired.
     */
    void yield(ChannelHandlerContext ctx)
        throws PoolProviderException;

    void Start(CountDownLatch latch)
        throws Exception;

    void Stop(boolean force, CountDownLatch latch);
}
