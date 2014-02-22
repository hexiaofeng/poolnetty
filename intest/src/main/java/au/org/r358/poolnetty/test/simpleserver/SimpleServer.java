/*
 * Copyright (c) 2014 R358 https://github.com/R358
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package au.org.r358.poolnetty.test.simpleserver;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.nio.charset.Charset;

/**
 * Simple server used as an end point in testing.
 */
public class SimpleServer implements Runnable
{

    public static final Charset UTF_8 = Charset.forName("UTF-8");

    private ServerBootstrap bootstrap = null;
    private ChannelFuture channelFuture = null;

    public static void main(String[] agrs)
        throws Exception
    {

    }

    public SimpleServer(String host, int port, int backLog, final SimpleServerListener ssl)
        throws Exception
    {

        EventLoopGroup workers = new NioEventLoopGroup();
        EventLoopGroup bosses = new NioEventLoopGroup();

        bootstrap = new ServerBootstrap();

        bootstrap.group(bosses, workers);
        bootstrap.channel(NioServerSocketChannel.class);
        bootstrap.childHandler(new ChannelInitializer<SocketChannel>()
        {
            @Override
            protected void initChannel(SocketChannel ch)
                throws Exception
            {
                ChannelPipeline cpl = ch.pipeline();
                cpl.addLast("encode", new SimpleOutboundHandler(-1));
                cpl.addLast("decode", new SimpleInboundHandler());
                cpl.addLast("adapt", new ChannelInboundHandlerAdapter()
                {

                    @Override
                    public void channelActive(ChannelHandlerContext ctx)
                        throws Exception
                    {
                        super.channelActive(ctx);
                        ssl.newConnection(ctx);
                    }

                    @Override
                    public void channelRead(ChannelHandlerContext ctx, Object msg)
                        throws Exception
                    {
                        ssl.newValue(ctx, msg.toString());
                    }
                });


            }
        });

        bootstrap.localAddress(host, port);

        bootstrap.option(ChannelOption.ALLOW_HALF_CLOSURE, true);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.option(ChannelOption.SO_BACKLOG, backLog);


    }

    @Override
    public void run()
    {
        channelFuture = bootstrap.bind().syncUninterruptibly().awaitUninterruptibly();


    }

    public void start()
        throws Exception
    {

        Thread th = new Thread(this);
        th.setPriority(Thread.MIN_PRIORITY);
        th.setDaemon(true);
        th.start();


    }


    public void stop()
    {
        channelFuture.channel().close().syncUninterruptibly();
    }
}
