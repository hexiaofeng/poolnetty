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

import io.netty.channel.Channel;


/**
 * Called once the connection is finished, that is after a connection is made and the pipeline is in place.
 * <p/>
 * At this point you can asynchronously do whatever needs to be done, like logging into a DB and so on.
 * When complete, you need to put the completeTask on the pool providers decoupler thread.
 * Example:
 * <code>
 * public void establish(Channel channel, PoolProvider provider, Runnable completeTask) {
 * // Do stuff to channel, send messages, negotiate with other end etc.
 * // Then when all that is done you must ensure the completeTask is executed by the pool provider.
 * provider.execute(completeTask)
 * }
 * </code>
 */
public interface PostConnectEstablish
{
    /**
     * This is your notification to perform any completion required for the connection, at this point the pipeline has already been established.
     * This phase can be used to do things like "log into a db" etc.
     * <p/>
     * This method is called on the pools decoupler and it is advisable for you to complete this phase asynchronously.
     * When you are complete, place the Runnable from completeTask onto the pool providers decoupler.
     *
     * @param channel      The channel.
     * @param provider     The provider.
     * @param completeTask The task to put back on the providers decoupler via provider.execute(completeTask)
     */
    void establish(Channel channel, PoolProvider provider, Runnable completeTask);
}
