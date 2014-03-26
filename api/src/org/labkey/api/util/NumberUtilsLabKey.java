package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * User: adam
 * Date: 3/26/2014
 * Time: 9:31 AM
 */
public class NumberUtilsLabKey
{
    /*
        This is like NumberUtils.isNumber(), except it works like you'd expect. In other words, it doesn't require
        Java formatted numbers with type characters.

        It's obviously just a convenience method for determining if Double.parseDouble(String str) will throw
        NumberFormatException, but we do this kind of check in a lot of places.
    */
    public static boolean isNumber(String str)
    {
        if (StringUtils.isEmpty(str))
            return false;

        try
        {
            double d = Double.parseDouble(str);
            return true;
        }
        catch (NumberFormatException e)
        {
            return false;
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testNumbers()
        {
            numbers("1", "-1", "+1", "23", "123456", "-123456", "+123456", "0.4790", "-0.4790", ".4790", "-.4790", "+.4790", "-9.156013e-002", "0x1.6e8p14");
            notNumbers(null, "", "   ", "\n", "\n", "-0.4790X", "123ABC", "-123ABC", "0.4790B", "0xABQCD");
        }

        private void numbers(String... strings)
        {
            for (String string : strings)
                assertTrue("isNumber(\"" + string + "\" should have returned true!", isNumber(string));
        }

        private void notNumbers(String... strings)
        {
            for (String string : strings)
                assertFalse("isNumber(\"" + string + "\" should have returned false!", isNumber(string));
        }
    }
}
