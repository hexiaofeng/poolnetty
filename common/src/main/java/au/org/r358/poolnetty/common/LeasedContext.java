package au.org.r358.poolnetty.common;

import io.netty.channel.ChannelHandlerContext;

/**
 *
 */
public class LeasedContext
{
    private final long expireAfter;
    private final ChannelHandlerContext channelHandlerContext;
    private final boolean ephemeral;

    public LeasedContext(long expireAfter, ChannelHandlerContext channelHandlerContext, boolean ephemeral)
    {
        this.expireAfter = expireAfter;
        this.channelHandlerContext = channelHandlerContext;
        this.ephemeral = ephemeral;
    }

    public long getExpireAfter()
    {
        return expireAfter;
    }

    public boolean isEphemeral()
    {
        return ephemeral;
    }

    public ChannelHandlerContext getChannelHandlerContext()
    {
        return channelHandlerContext;
    }
}
