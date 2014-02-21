package au.org.r358.poolnetty.common.exceptions;

/**
 * Base of all pool provider exceptions.
 */
public class PoolProviderException extends Exception
{
    public PoolProviderException(String message)
    {
        super(message);
    }

    public PoolProviderException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
