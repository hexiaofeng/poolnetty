package au.org.r358.poolnetty.test.simpleserver.util;

import au.org.r358.poolnetty.common.PoolProvider;
import au.org.r358.poolnetty.common.PoolProviderListener;
import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class TestPoolProviderListener
    implements PoolProviderListener
{
    private CountDownLatch startedLatch = new CountDownLatch(1);
    private CountDownLatch stoppedLatch = new CountDownLatch(1);
    private CountDownLatch leaseRequested = new CountDownLatch(1);
    private CountDownLatch leaseGranted = new CountDownLatch(1);
    private CountDownLatch leaseYielded = new CountDownLatch(1);
    private CountDownLatch leaseExpired = new CountDownLatch(1);
    private CountDownLatch connectionClosed = new CountDownLatch(1);
    private CountDownLatch connectionCreated = new CountDownLatch(1);
    private CountDownLatch ephemeralReaped = new CountDownLatch(1);

    private ConcurrentLinkedQueue<Object[]> journal = new ConcurrentLinkedQueue<>();

    private enum Event
    {
        Started, Stopped, LeaseRequested, LeaseGranted, LeaseYield, LeaseExpired, ConnectionClosed, ConnectionCreated, EphemeralReaped
    }


    @Override
    public void started(PoolProvider provider)
    {
        journal.add(new Object[]{Event.Started, provider});
        startedLatch.countDown();
    }

    @Override
    public void stopped(PoolProvider provider)
    {
        journal.add(new Object[]{Event.Stopped, provider});
        stoppedLatch.countDown();
    }

    @Override
    public void leaseRequested(PoolProvider provider, int leaseTime, TimeUnit units, Object userObject)
    {
        journal.add(new Object[]{Event.LeaseRequested, provider, leaseTime, units, userObject});
        leaseRequested.countDown();

    }

    @Override
    public void leaseGranted(PoolProvider provider, Channel channel, Object userObject)
    {
        journal.add(new Object[]{Event.LeaseGranted, provider, channel, userObject});
        leaseGranted.countDown();
    }

    @Override
    public void leaseYield(PoolProvider provider, Channel channel, Object userObject)
    {
        journal.add(new Object[]{Event.LeaseYield, provider, channel, userObject});
        leaseYielded.countDown();
    }

    @Override
    public void leaseExpired(PoolProvider provider, Channel channel, Object userObject)
    {
        journal.add(new Object[]{Event.LeaseExpired, provider, channel, userObject});
        leaseExpired.countDown();
    }

    @Override
    public void connectionClosed(PoolProvider provider, Channel channel)
    {
        journal.add(new Object[]{Event.ConnectionClosed, provider, channel});
        connectionClosed.countDown();
    }

    @Override
    public void connectionCreated(PoolProvider provider, Channel channel, boolean immortal)
    {
        journal.add(new Object[]{Event.ConnectionCreated, provider, channel, immortal});
        connectionCreated.countDown();
    }

    @Override
    public void ephemeralReaped(PoolProvider poolProvider, Channel channel)
    {
        journal.add(new Object[]{Event.EphemeralReaped, poolProvider, channel});
        ephemeralReaped.countDown();
    }

    public CountDownLatch getStartedLatch()
    {
        return startedLatch;
    }

    public void setStartedLatch(CountDownLatch startedLatch)
    {
        this.startedLatch = startedLatch;
    }

    public CountDownLatch getStoppedLatch()
    {
        return stoppedLatch;
    }

    public void setStoppedLatch(CountDownLatch stoppedLatch)
    {
        this.stoppedLatch = stoppedLatch;
    }

    public CountDownLatch getLeaseRequested()
    {
        return leaseRequested;
    }

    public void setLeaseRequested(CountDownLatch leaseRequested)
    {
        this.leaseRequested = leaseRequested;
    }

    public CountDownLatch getLeaseGranted()
    {
        return leaseGranted;
    }

    public void setLeaseGranted(CountDownLatch leaseGranted)
    {
        this.leaseGranted = leaseGranted;
    }

    public CountDownLatch getLeaseYielded()
    {
        return leaseYielded;
    }

    public void setLeaseYielded(CountDownLatch leaseYielded)
    {
        this.leaseYielded = leaseYielded;
    }

    public CountDownLatch getLeaseExpired()
    {
        return leaseExpired;
    }

    public void setLeaseExpired(CountDownLatch leaseExpired)
    {
        this.leaseExpired = leaseExpired;
    }

    public CountDownLatch getConnectionClosed()
    {
        return connectionClosed;
    }

    public void setConnectionClosed(CountDownLatch connectionClosed)
    {
        this.connectionClosed = connectionClosed;
    }

    public CountDownLatch getConnectionCreated()
    {
        return connectionCreated;
    }

    public void setConnectionCreated(CountDownLatch connectionCreated)
    {
        this.connectionCreated = connectionCreated;
    }

    public CountDownLatch getEphemeralReaped()
    {
        return ephemeralReaped;
    }

    public void setEphemeralReaped(CountDownLatch ephemeralReaped)
    {
        this.ephemeralReaped = ephemeralReaped;
    }

    public ConcurrentLinkedQueue<Object[]> getJournal()
    {
        return journal;
    }

    public void setJournal(ConcurrentLinkedQueue<Object[]> journal)
    {
        this.journal = journal;
    }
}
