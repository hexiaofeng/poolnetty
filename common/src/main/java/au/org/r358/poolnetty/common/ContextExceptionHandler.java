package au.org.r358.poolnetty.common;

/**
 * <p>>Called when a context has thrown an exception, implementations of this interface must determine if the connection pool is to dispose of the context closing it if possible.</p>
 * <p>Generally if the context is emitting exceptions its probably broken, or the input is not be handled correctly, keeping a failing context in the pool may open up other subtle issues.</p>
 */
public interface ContextExceptionHandler
{
    /**
     * Close because of throwable.
     *
     * @param throwable The throwable.
     * @param provider  The pool provider.
     * @return true to close, false to leave in pool.
     */
    boolean close(Throwable throwable, PoolProvider provider);


}
