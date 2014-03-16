Pool Netty
==========

Netty client connection pooling.

State:
------
16-Mar-2014: Renamed packages to reflect Maven group prefix 'org.r358.' Fixed Javadoc. Created Release 0.1.0

Building:
---------

Download and install [Gradle](http://www.gradle.org)


To org.r358.poolnetty.test and produce coverage report:

```
   gradle clean org.r358.poolnetty.test coverage_report

   # Build will print absolute path to coverage report, open it in your browser.

```


To clean and package:

```
  gradle clean org.r358.poolnetty.test sourcesJar javadocJar install


```

## Quick Start:
Use the following to get you started quickly.

### Basic set up.

```java

 NettyConnectionPoolBuilder ncb = new NettyConnectionPoolBuilder(immortalCount, maxEphemeral, ephemeralLifespanMillis);


         final EventLoopGroup elg = new NioEventLoopGroup();


         //
         // Create the boot strap.
         //
         ncb.withBootstrapProvider(new BootstrapProvider()
         {
             @Override
             public Bootstrap createBootstrap(PoolProvider poolProvider)
             {
                 Bootstrap bs = new Bootstrap();
                 bs.group(elg);
                 bs.channel(NioSocketChannel.class);
                 bs.option(ChannelOption.SO_KEEPALIVE, true);
                 bs.option(ChannelOption.AUTO_READ, true);
                 return bs;
             }
         });


         //
         // Sets up the connection info and the channel initializer.
         //
         ncb.withConnectionInfoProvider(new ConnectionInfoProvider()
         {
             @Override
             public ConnectionInfo connectionInfo(PoolProvider poolProvider)
             {

                 return new ConnectionInfo(new InetSocketAddress("127.0.0.1", 1887), null, new ChannelInitializer()
                 {
                     @Override
                     protected void initChannel(Channel ch)
                         throws Exception
                     {
                         ch.pipeline().addLast("decode", new SimpleInboundHandler(10));
                         ch.pipeline().addLast("encode", new SimpleOutboundHandler(10));
                     }
                 });


             }
         });


         //
         // Make the pool add listener and start.
         //
         NettyConnectionPool ncp = ncb.build();

         //
         // Start the pool.
         //

         ncp.start();

```

### Obtain a lease
There are three ways to obtain a lease.

```java

 //
 // Blocking
 //

   LeasedChannel chan = ncp.lease(10, TimeUnit.Seconds, userObject);

 //
 // Using a future.
 //

  Future<LeasedChannel> chanFuture = ncp.leaseAsync(10, TimeUnit.DAYS, userObject);

  //
  // Using a callback. (You also get back future.)
  //

  ncp.leaseAsync(10, TimeUnit.DAYS, userObject, new LeaseListener()
        {
            @Override
            public void leaseRequest(boolean success, LeasedChannel channel, Throwable th)
            {
              // Do work..
            }
        });


```

### Canceling lease requests
You can call Future#cancel() and it will try to cancel the lease request on a best effort basis.



### Yield a lease
Yielding a lease means giving it back to the pool.

```java

 //
 // Directly back to the pool.
 //

 ncp.yield(chan);

 //
 // From instances of LeasedChannel
 //

 chan.yield();

```

## Getting notification

## Events from the pool
Implement PoolProviderListener directly or extend PoolProviderListenerAdapter to receive notification of events generate by the pool.

For Example using the Adapter to keep code size down.

```java
  NettyConnectionPool ncc =
  ncp.addListener(new PoolProviderListenerAdapter()
         {

             @Override
             public void connectionCreated(PoolProvider provider, Channel channel, boolean immortal)
             {
                 openedConnections.countDown();
             }

             @Override
             public void leaseGranted(PoolProvider provider, Channel channel, Object userObject)
             {
                 leaseAll.countDown();
             }

             @Override
             public void leaseYield(PoolProvider provider, Channel channel, Object userObject)
             {
                 yieldedConnections.countDown();
             }

             @Override
             public void connectionClosed(PoolProvider provider, Channel channel)
             {
                 closedConnections.countDown();
             }
         });

```

## Events from the LeasedChannel

LeasedChannel transparently wraps the Netty Channel and adds a void yield() and onLeaseExpire() methods.
Users can get direct notification of lease expiration by supplying a implementation of ValueEvent<Leasee>.

For Example:

```java
     lchan.onLeaseExpire(new ValueEvent<Leasee>()
            {
                @Override
                public void on(Leasee value)
                {
                    // Expired.
                }
            });

```

*Note:*
Only a single ValueEvent<Lease> can be used, this is not an accumulative list of listeners.



## Interfaces
As Netty is so configurable, poolnetty provides a lot of options for configuration and customisation of the pools
function.

*Note:*
BootStrapProvider and ConnectionInfoProvider need to be implemented, all others have default implementations.


<table>
<tr><th>Interface</th><th>Description</th></tr>
<tr><td>BootstrapProvider</td><td>Is called when making a connection to provide a configured bootstrap.</td></tr>
<tr><td>ConnectionInfoProvider</td><td>Is called when making a connection to supply the local and remote addresses and
a channel initializer.</td></tr>
<tr><td>ContextExceptionHandler</td><td>Is called when a channel throws an exception, with the option of closing the channel.</td></tr>
<tr><td>ExpiryReaper</td><td>Implementations of this are called to nominate expired leases for later processing.</td></tr>

<tr><td>LeaseExpiredHandler</td><td>Is called on each expired lease and provides the option of terminating the channel.</td></tr>

<tr><td>PoolExceptionHandler</td><td>Exception emitted by netty or the pool are funneled through this.</td></tr>

<tr><td>PoolProviderListener</td><td>Pool listener.</td></tr>

<tr><td>PostConnectEstablish</td><td>Called once a connection is established and allows users to perform final setup
of the connection. For example logging into the end service at the other end of the connection. Please see the Javadoc.</td></tr>

<tr><td>PreGrantLease</td><td>Gives users the ability to block the granting of a lease.</td></tr>
<tr><td>PreReturnToPool</td><td>Gives users the chance to make closure decisions on a channel as it returns to the pool.</td></tr>
</table>



## Notes on threading

The Pool Provider is a decoupled implementation where a single executor is responsible for executing tasks that provide
the pools function. Looking at the code there is no synchronisation because the assumption is that everything is
being executed on one thread.

In some cases rather than block some tasks will defer execution and queue up in a deque or hand themselves to another
task that they require the completion of. This task will then ensure the dependent task is executed at the appropriate time.

The general ambition is to keep the executor service (decoupler) free of obstructions, while endeavouring to move the
blocking tasks are out of the way until they need to modify structures within the pool.

To execute a runnable on the Pools decoupler:

```java
  ncp.execute(new Runnable(){
     public void run(){ .. etc ..  }
  });
```

There is one exception to the concurrency model and that is the pool Listeners which use a CopyOnWriteArraySet. This was
done because it is unlikely that there will be a lot of changes to pool listener list and some events are not fired from
the decoupler.
