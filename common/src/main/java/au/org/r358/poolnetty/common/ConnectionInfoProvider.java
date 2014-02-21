package au.org.r358.poolnetty.common;

/**
 * A ConnectionInfoProvider provides information for the pool so that it can create new connections.
 * <p>For Example:</p>
 * <p>If a pool is defined to have a number of fixed connections an implementation of this interface would be called by the pool to create those contexts.</p>
 * <p>As netty context is much more than a simple connection, the complexity of setting up handlers needs to be delegated.</p>
 * <p>It is safe to return the same instance of ConnectionInfo.</p>
 * <p/>
 * <p>Note that the pool will add a transparent ChannelInboundHandler and ChannelOutboundHandler.</p>
 * <p><B>Thread Safety:</B> A PoolProvider guarantees to sequentially call this interface during connection creation, if an implementation is shared between pool providers then no such guarantees are made.</p>
 */
public interface ConnectionInfoProvider
{

    /**
     * Provide the connection info so the PoolProvider can create a connection.
     *
     * @param poolProvider The calling pool provider.
     * @return An instance of ConnectionInfo.
     */
    ConnectionInfo connectionInfo(PoolProvider poolProvider);
}

