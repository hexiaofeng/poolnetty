package au.org.r358.poolnetty.test.simpleserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 *
 */
public class TestUtil
{

    public static Object getField(Object source, String fieldName)
        throws Exception
    {
        Class cl = source.getClass();


        Field f = cl.getDeclaredField(fieldName);
        f.setAccessible(true);

        return f.get(source);

    }

    public static Object callMethod(Object source, String methodName, Object... params)
        throws Exception
    {
        Class cl = source.getClass();


        Class[] pt = new Class[params.length];
        int a = 0;
        for (Object o : params)
        {
            pt[a++] = o.getClass();
        }

        Method m = cl.getDeclaredMethod(methodName, pt);
        m.setAccessible(true);

        return m.invoke(source, params);
    }

}
