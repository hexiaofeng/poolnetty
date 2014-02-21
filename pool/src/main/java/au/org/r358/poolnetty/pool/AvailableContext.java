package au.org.r358.poolnetty.pool;

import io.netty.channel.ChannelHandlerContext;

/**
 * A wrapper for the context.
 * Not thread safe.
 */
public class AvailableContext
{
    private final long closeAfter;
    private final ChannelHandlerContext channelHandlerContext;
    private final int lifespan;
    private final boolean immortal;

    public AvailableContext(long closeAfter, ChannelHandlerContext channelHandlerContext, int lifespan, boolean immortal)
    {
        this.closeAfter = closeAfter;
        this.channelHandlerContext = channelHandlerContext;
        this.lifespan = lifespan;
        this.immortal = immortal;
    }

    public ChannelHandlerContext getChannelHandlerContext()
    {
        return channelHandlerContext;
    }


    /**
     * Has this context expired.
     *
     * @param notAfter Not after.
     * @return
     */
    public boolean expired(long notAfter)
    {
        return !immortal & notAfter < closeAfter;
    }

    public boolean isImmortal()
    {
        return immortal;
    }
}
