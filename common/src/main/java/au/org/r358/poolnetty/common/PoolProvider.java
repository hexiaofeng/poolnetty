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

package au.org.r358.poolnetty.common;

import au.org.r358.poolnetty.common.exceptions.PoolProviderException;
import io.netty.channel.ChannelHandlerContext;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * All pool implementations implement this interface is provides the basic concepts of:
 * <ol>
 * <li><B>Request</B> Lease a resource from the pool.</li>
 * <li><B>Release</B> Yield a resource back to the pool.</li>
 * </ol>
 */
public interface PoolProvider
{
    /**
     * Request a ChannelHandlerContext from the pool
     *
     * @return The context.
     * @throws Exception
     */
    ChannelHandlerContext lease(long leaseTime, TimeUnit units)
        throws PoolProviderException;

    /**
     * Release a context back to the pool
     *
     * @param ctx The context to release.
     * @throws PoolProviderException if the context is unknown to the pool or declared lost to the pool because lease time expired.
     */
    void yield(ChannelHandlerContext ctx)
        throws PoolProviderException;

    void Start(CountDownLatch latch)
        throws Exception;

    void Stop(boolean force, CountDownLatch latch);
}
