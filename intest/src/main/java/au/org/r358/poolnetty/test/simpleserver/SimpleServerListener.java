package au.org.r358.poolnetty.test.simpleserver;

import io.netty.channel.ChannelHandlerContext;

/**
 *
 */
public interface SimpleServerListener
{

    void newValue(ChannelHandlerContext ctx, String val);

    void newConnection(ChannelHandlerContext ctx);

}
