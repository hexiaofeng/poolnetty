package au.org.r358.poolnetty.test.simpleserver;

import java.lang.reflect.Field;

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

}
