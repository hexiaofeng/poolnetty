package au.org.r358.poolnetty.common;

import io.netty.channel.ChannelInitializer;

import java.net.SocketAddress;

/**
 *
 */
public class ConnectionInfo
{
    private final ChannelInitializer channelInitializer;


    private final SocketAddress remoteSocketAddress;
    private final SocketAddress localSocketAddress;


    public ConnectionInfo(SocketAddress remoteSocketAddress, SocketAddress localSocketAddress, ChannelInitializer channelInitializer)
    {
        this.channelInitializer = channelInitializer;
        this.remoteSocketAddress = remoteSocketAddress;
        this.localSocketAddress = localSocketAddress;
    }

    public ChannelInitializer getChannelInitializer()
    {
        return channelInitializer;
    }

    public SocketAddress getRemoteSocketAddress()
    {
        return remoteSocketAddress;
    }

    public SocketAddress getLocalSocketAddress()
    {
        return localSocketAddress;
    }
}
