package au.org.r358.poolnetty.test;

import au.org.r358.poolnetty.common.*;
import au.org.r358.poolnetty.pool.NettyConnectionPool;
import au.org.r358.poolnetty.pool.NettyConnectionPoolBuilder;
import au.org.r358.poolnetty.test.simpleserver.TestUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 *
 */
@RunWith(JUnit4.class)
public class NettyConnectionPoolBuilderTest
{

    @Test
    public void testVariablesAreWhatTheyShouldBe()
        throws Exception
    {
        NettyConnectionPoolBuilder nt = new NettyConnectionPoolBuilder(1, 2, 3);

        PostConnectEstablish pce = new PostConnectEstablish()
        {
            @Override
            public void establish(Channel ctx, PoolProvider provider, Runnable completeTask)
            {

            }
        };

        nt.withPostConnectEstablish(pce);

        ExpiryHarvester expH = new ExpiryHarvester()
        {
            @Override
            public List<LeasedContext> reapHarvest(List<LeasedContext> currentLeases)
            {
                return null;
            }
        };

        nt.withExpiryHarvester(expH);

        BootstrapProvider bsp = new BootstrapProvider()
        {
            @Override
            public Bootstrap createBootstrap(PoolProvider poolProvider)
            {
                return null;
            }
        };

        nt.withBootstrapProvider(bsp);

        PoolExceptionHandler peh = new PoolExceptionHandler()
        {
            @Override
            public void handleException(Throwable th)
            {

            }
        };


        nt.withPoolExceptionHandler(peh);

        ConnectionInfoProvider cip = new ConnectionInfoProvider()
        {
            @Override
            public ConnectionInfo connectionInfo(PoolProvider poolProvider)
            {
                return null;
            }
        };

        nt.withConnectionInfoProvider(cip);


        ContextExceptionHandler ceh = new ContextExceptionHandler()
        {
            @Override
            public boolean close(Throwable throwable, PoolProvider provider)
            {
                return false;
            }
        };

        nt.withContextExceptionHandler(ceh);

        LeaseExpiredHandler leh = new LeaseExpiredHandler()
        {
            @Override
            public boolean closeExpiredLease(ChannelHandlerContext context, PoolProvider provider)
            {
                return false;
            }
        };
        nt.withLeaseExpiredHandler(leh);

        PreGrantLease pgl = new PreGrantLease()
        {
            @Override
            public boolean continueToGrantLease(Channel context, PoolProvider provider)
            {
                return false;
            }
        };
        nt.withPreGrantLease(pgl);

        PreReturnToPool prp = new PreReturnToPool()
        {
            @Override
            public boolean returnToPoolOrDisposeNow(ChannelHandlerContext context, PoolProvider provider)
            {
                return false;
            }
        };

        nt.withPreReturnToPool(prp);

        String inHandName = "sardine";
        nt.withInboundHandlerName(inHandName);

        int reaperInterval = 1;
        nt.withReaperInterval(reaperInterval);


        NettyConnectionPool ncp = nt.build();

        TestCase.assertEquals(pce, TestUtil.getField(ncp, "postConnectEstablish"));
        TestCase.assertEquals(expH, TestUtil.getField(ncp, "expiryHarvester"));
        TestCase.assertEquals(bsp, TestUtil.getField(ncp, "bootstrapProvider"));
        TestCase.assertEquals(peh, TestUtil.getField(ncp, "poolExceptionHandler"));
        TestCase.assertEquals(cip, TestUtil.getField(ncp, "connectionInfoProvider"));
        TestCase.assertEquals(ceh, TestUtil.getField(ncp, "contextExceptionHandler"));
        TestCase.assertEquals(leh, TestUtil.getField(ncp, "leaseExpiredHandler"));
        TestCase.assertEquals(pgl, TestUtil.getField(ncp, "preGrantLease"));
        TestCase.assertEquals(prp, TestUtil.getField(ncp, "preReturnToPool"));
        TestCase.assertEquals(inHandName, TestUtil.getField(ncp, "inboundHandlerName"));
        TestCase.assertEquals(reaperInterval, TestUtil.getField(ncp, "reaperInterval"));
        TestCase.assertEquals(1, TestUtil.getField(ncp, "immortalCount"));
        TestCase.assertEquals(2, TestUtil.getField(ncp, "maxEphemeralCount"));
        TestCase.assertEquals(3, TestUtil.getField(ncp, "ephemeralLifespan"));


    }

}
