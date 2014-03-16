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

package org.r358.poolnetty.test;

import org.r358.poolnetty.pool.concurrent.DeferrableTask;
import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.*;

/**
 *
 */
@RunWith(JUnit4.class)
public class DeferrableTaskTest
{
    private Executor exec = Executors.newSingleThreadExecutor();


    @Test
    public void testTaskCompletes()
        throws Exception
    {
        final Integer expected = new Integer(10);


        DeferrableTask<Integer> dit = new DeferrableTask<Integer>()
        {
            @Override
            public void defer()
                throws Exception
            {

            }

            @Override
            public boolean runOrDefer()
                throws Exception
            {
                Thread.sleep(1000);
                setResult(expected);
                return false;
            }
        };


        exec.execute(dit);

        TestCase.assertEquals(dit.get(), expected);


    }


    @Test
    public void testTaskThrowsExecutionException()
        throws Exception
    {
        final Integer notExpected = new Integer(10);


        DeferrableTask<Integer> dit = new DeferrableTask<Integer>()
        {
            @Override
            public void defer()
                throws Exception
            {

            }

            @Override
            public boolean runOrDefer()
                throws Exception
            {
                Thread.sleep(1000);
                throw new IllegalStateException("Should fail.");

            }
        };


        exec.execute(dit);

        try
        {
            dit.get();
            TestCase.fail("Exception should be thrown.");
        }
        catch (Exception ex)
        {
            TestCase.assertEquals(ExecutionException.class, ex.getClass());
        }
    }

    @Test
    public void testDeferment()
        throws Exception
    {
        final Integer notExpected = new Integer(10);


        DeferrableTask<Integer> dit = new DeferrableTask<Integer>()
        {
            int t = 0;


            @Override
            public void defer()
                throws Exception
            {
                t = 1;
                exec.execute(this);
            }

            @Override
            public boolean runOrDefer()
                throws Exception
            {
                Thread.sleep(1000);
                if (t == 0)
                {
                    return true;
                }

                setResult(t + 1);
                return false;
            }
        };


        exec.execute(dit);

        int res = dit.get();

        TestCase.assertEquals(2, res);
    }


    @Test
    public void testTaskCompletesWithTimeout()
        throws Exception
    {
        final Integer expected = new Integer(10);


        DeferrableTask<Integer> dit = new DeferrableTask<Integer>()
        {
            @Override
            public void defer()
                throws Exception
            {

            }

            @Override
            public boolean runOrDefer()
                throws Exception
            {
                Thread.sleep(1000);
                setResult(expected);
                return false;
            }
        };


        exec.execute(dit);

        TestCase.assertEquals(dit.get(5, TimeUnit.SECONDS), expected);


    }


    @Test
    public void testTaskThrowsExecutionExceptionWithTimeout()
        throws Exception
    {
        final Integer notExpected = new Integer(10);


        DeferrableTask<Integer> dit = new DeferrableTask<Integer>()
        {
            @Override
            public void defer()
                throws Exception
            {

            }

            @Override
            public boolean runOrDefer()
                throws Exception
            {
                Thread.sleep(1000);
                throw new IllegalStateException("Should fail.");

            }
        };


        exec.execute(dit);

        try
        {
            dit.get(5, TimeUnit.SECONDS);
            TestCase.fail("Exception should be thrown.");
        }
        catch (Exception ex)
        {
            TestCase.assertEquals(ExecutionException.class, ex.getClass());
        }
    }


    @Test
    public void testActualTimeout()
        throws Exception
    {
        final Integer expected = new Integer(10);


        DeferrableTask<Integer> dit = new DeferrableTask<Integer>()
        {
            @Override
            public void defer()
                throws Exception
            {

            }

            @Override
            public boolean runOrDefer()
                throws Exception
            {
                Thread.sleep(5000);
                setResult(expected);
                return false;
            }
        };


        exec.execute(dit);
        try
        {
            dit.get(1, TimeUnit.SECONDS);
            TestCase.fail();
        }
        catch (Exception ex)
        {
            TestCase.assertEquals(TimeoutException.class, ex.getClass());
        }


    }


}
