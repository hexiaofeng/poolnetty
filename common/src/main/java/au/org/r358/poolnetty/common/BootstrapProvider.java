package au.org.r358.poolnetty.common;

import io.netty.bootstrap.Bootstrap;

/**
 * Create a new Bootstrap.
 */
public interface BootstrapProvider
{

    /**
     * Create a new bootstap, use this to configure the bootstrap, how you want it.
     *
     * @param poolProvider The pool provider.
     * @return The bootstrap.
     */
    Bootstrap createBootstrap(PoolProvider poolProvider);

}
