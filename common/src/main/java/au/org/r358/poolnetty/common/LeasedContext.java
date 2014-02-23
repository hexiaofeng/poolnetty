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
 *
 */
public class LeasedContext
{
    private final long leaseID;
    private final long expireAfter;
    private final Channel channelHandlerContext;
    private final boolean immortal;
    private final int channelLifespan;
    private final Object userObject;

    public LeasedContext(long leaseID, long expireAfter, Channel channelHandlerContext, boolean ephemeral, Object userObject, int channelLifespan)
    {
        this.leaseID = leaseID;
        this.expireAfter = expireAfter;
        this.channelHandlerContext = channelHandlerContext;
        this.immortal = ephemeral;
        this.userObject = userObject;
        this.channelLifespan = channelLifespan;
    }

    public long getExpireAfter()
    {
        return expireAfter;
    }

    public boolean isImmortal()
    {
        return immortal;
    }

    public Channel getChannel()
    {
        return channelHandlerContext;
    }

    public long getLeaseID()
    {
        return leaseID;
    }

    public int getChannelLifespan()
    {
        return channelLifespan;
    }

    public Object getUserObject()
    {
        return userObject;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }

        LeasedContext that = (LeasedContext)o;

        if (expireAfter != that.expireAfter)
        {
            return false;
        }
        if (leaseID != that.leaseID)
        {
            return false;
        }
        if (immortal != that.immortal)
        {
            return false;
        }
        if (channelHandlerContext != null ? !channelHandlerContext.equals(that.channelHandlerContext) : that.channelHandlerContext != null)
        {
            return false;
        }
        if (userObject != null ? !userObject.equals(that.userObject) : that.userObject != null)
        {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = (int)(leaseID ^ (leaseID >>> 32));
        result = 31 * result + (int)(expireAfter ^ (expireAfter >>> 32));
        result = 31 * result + (channelHandlerContext != null ? channelHandlerContext.hashCode() : 0);
        result = 31 * result + (immortal ? 1 : 0);
        result = 31 * result + (userObject != null ? userObject.hashCode() : 0);
        return result;
    }


    public boolean expiredLease(long zeit)
    {
        return zeit > expireAfter;
    }
}
