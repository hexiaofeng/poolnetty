package au.org.r358.poolnetty.common;

import io.netty.channel.ChannelHandlerContext;

/**
 * Intercept contexts before they are returned to the pool.
 * <p/>
 * <p>The Pool provider will keep ChannelHandlerContexts around until they expire or get closed,
 * this handler gives the users of the pool a mechanism to remove contexts outside of that.</p>
 */
public interface PreReturnToPool
{

    /**
     * Return the context to the pool (true) or have it disposed of now.
     *
     * @param context  The context.
     * @param provider The provider.
     * @return true to return to pool or false to be disposed.
     */
    boolean returnToPoolOrDisposeNow(ChannelHandlerContext context, PoolProvider provider);
}
