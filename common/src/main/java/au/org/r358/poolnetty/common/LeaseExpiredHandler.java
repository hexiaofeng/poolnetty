package au.org.r358.poolnetty.common;

import io.netty.channel.ChannelHandlerContext;

/**
 * Called when a context has expired its lease.
 */
public interface LeaseExpiredHandler
{
    /**
     * <p>Close the context (true) due to expiration of lease.</p>
     * <p>Closing the context will be ungraceful with respect to the current lease holder.</p>
     * <p>All the pool can do is close the connection, as it cannot force the end point to give up the lease.</p>
     *
     * @param context  The context.
     * @param provider The provider.
     * @return true to close the context.
     */
    boolean closeExpiredLease(ChannelHandlerContext context, PoolProvider provider);
}
