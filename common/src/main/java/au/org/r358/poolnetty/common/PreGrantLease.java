package au.org.r358.poolnetty.common;

import io.netty.channel.ChannelHandlerContext;

/**
 * Intercept the lease granting process before the context is leased.
 * <p>Gives implementers the option of stopping a lease from occurring
 * or perhaps firing a message down the pipe to wake the other end up.</p>
 */
public interface PreGrantLease
{
    /**
     * Continue to grant lease (true)
     *
     * @param context  The context to be leased.
     * @param provider The provider of the lease.
     * @return true to grant the lease, false to deny the lease.
     */
    boolean continueToGrantLease(ChannelHandlerContext context, PoolProvider provider);
}
