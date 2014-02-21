package au.org.r358.poolnetty.common;

import java.util.List;

/**
 * Implementations of this class are responsible for reaping expired leases.
 * <p>Consider how your implementation will function id there are 1000's of leases.</p>
 */
public interface ExpiryHarvester
{

    /**
     * Reap the harvest.
     *
     * @param currentLeases List of current leases.
     * @return A List of leases to be reaped.
     */
    List<LeasedContext> reapHarvest(List<LeasedContext> currentLeases);


}
