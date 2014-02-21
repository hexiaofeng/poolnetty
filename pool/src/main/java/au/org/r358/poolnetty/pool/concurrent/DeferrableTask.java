package au.org.r358.poolnetty.pool.concurrent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A Task that can reschedule its execution.
 */
public abstract class DeferrableTask<V> implements Runnable
{
    protected V result = null;
    protected ExecutionException executionException = null;
    protected CountDownLatch latch = new CountDownLatch(1);
    protected boolean firstAttempt = true;

    public abstract void defer()
        throws Exception;

    public abstract boolean runOrDefer()
        throws Exception;

    @Override
    public void run()
    {
        try
        {
            if (runOrDefer())
            {
                firstAttempt = false;
                defer();
            }
            else
            {
                latch.countDown();
            }
        }
        catch (Exception ex)
        {
            executionException = new ExecutionException(ex);
            latch.countDown();
        }
    }

    protected void setResult(V result)
    {
        this.result = result;
        latch.countDown();
    }


    public V get()
        throws InterruptedException, ExecutionException
    {

        latch.await();
        if (executionException != null)
        {
            throw executionException;
        }

        return result;
    }


    public V get(long timeout, TimeUnit unit)
        throws InterruptedException, ExecutionException, TimeoutException
    {
        if (!latch.await(timeout, unit))
        {
            throw new TimeoutException("Get timed out.");
        }

        if (executionException != null)
        {
            throw executionException;
        }

        return result;
    }


}
