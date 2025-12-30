package org.labkey.api.util;

import org.junit.Assert;
import org.junit.Test;

/**
 * This Class to help with dealing with Object that may represent an integer number (char,short,int,long).  It
 * is meant to fill in the small gap between Java (e.g. casts and instanceof) and ConvertUtils. Hopefully, this
 * class makes it just a little easier to deal with Integer valued Numbers.
 * <br>
 * Unfortunately, Number does not help with detecting integer/noninteger types, so this class only handles Object
 * instances that have corresponding to the primitive types. {@code boolean}, {@code byte}, {@code char},
 * {@code short}, {@code int}, {@code long}, {@code float}, and {@code double})
 * <br>
 * Because "Integer" is kind of ambiguous, I will use "Integral" to mean any integer type.  For now, I am not including
 * Boolean or Character as "Integral", change this if it seems useful.
 */

public class IntegerUtils
{
    /** Return true if Object is Byte, Short, Integer, or Long */
    public static boolean isIntegral(Object o)
    {
        if (null == o)
            return false;
        final Class<?> c = o.getClass();
        return c == Byte.class || c == Short.class || c==Integer.class || c==Long.class;
    }

    /** returns Numeric object as a Long.  This method will throw if not Object is not Integral. */
    public static Long asLong(Object o)
    {
        if (!isIntegral(o))
            return (Long)o;  // throw ClassCastException
        return o.getClass() == Long.class ? (Long)o : Long.valueOf(((Number)o).longValue());
    }

    /** returns Numeric object as an Integer.  This method will throw if not Object is not Integral or is out of range. */
    public static Integer asInteger(Object o) throws ClassCastException
    {
        if (null == o)
            return null;
        if (!isIntegral(o))
            return (Integer)o;  // throw ClassCastException
        if (o.getClass() == Integer.class)
            return (Integer)o;
        long l = ((Number)o).longValue();
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid int value: " + l);
        return Integer.valueOf((int)l);
    }

    /** Return an Integral value as Integer. Return null if not Integral or cannot be represented as an Integer
     * NOTE: this method never throws, so any IF using this method should usually be accompanied by an ELSE.
     * NOTE: This method returns Object so it can be used with instance of as in the example
     *<br>
     * Usages:
     *  {@code  if (null != (i = (Integer)asIntegerElseNull()))
     *      return i;
     *  else
     *      throw ..
     *  <br>
     *  if (asIntegerElseNull() instanceof Integer i))
     *      return i
     *  else
     *      throw .. }
     */
    public static Object asIntegerElseNull(Object o)
    {
        if (null == o)
            return null;
        final Class<?> c = o.getClass();
        if (c == Integer.class)
            return o;
        if (c == Byte.class || c == Short.class)
            return Integer.valueOf(((Number)o).intValue());
        if (c == Long.class)
        {
            long l = ((Number) o).longValue();
            return Integer.MIN_VALUE <= l && l <= Integer.MAX_VALUE ? Integer.valueOf((int) l) : null;
        }
        return null;
    }

    /** Return an Integral value as Long. Return null if not Integral.
     * NOTE: this method never throws, so any IF using this method should usually be accompanied by an ELSE.
     * NOTE: This method returns Object so it can be used with instance of as in the example
     *<br>
     * Usages:
     *  {@code  if (null != (i = (Long)asIntegerElseNull()))
     *      return i;
     *  else
     *      throw ..
     *  <br>
     *  if (asIntegerElseNull() instanceof Long i))
     *      return i
     *  else
     *      throw .. }
     */
    public static Object asLongElseNull(Object o)
    {
        if (null == o)
            return null;
        final Class<?> c = o.getClass();
        if (c == Long.class)
            return o;
        if (c == Byte.class || c == Short.class || c==Integer.class)
            return Long.valueOf(((Number)o).longValue());
        return null;
    }

    /** Compare two Integral numbers, throws ClassCastException */
    public static boolean integerEquals(Object a, Object b) throws ClassCastException
    {
        if (a == null || b == null)
            return a == b;
        if (isIntegral(a) && isIntegral(b))
            return ((Number)a).longValue() == ((Number)b).longValue();
        // Let's throw a ClassCastException, these can't both be Long
        return ((Long)a).longValue() == ((Long)b).longValue();
    }

    /** Like Objects.equals(), but with integral compare
     * CONSIDER: add Float/Double compare */
    public static boolean objectEquals(Object a, Object b)
    {
        // Objects.equals(a,b) ---> return (a == b) || (a != null && a.equals(b));
        if (a == null || b == null)
            return a == b;
        if (a.equals(b))
            return true;
        if (isIntegral(a) && isIntegral(b))
            return ((Number)a).longValue() == ((Number)b).longValue();
        return false;
    }


    public static class TestCase extends Assert
    {
        @Test
        public void is()
        {
            //noinspection ConstantValue
            assertFalse(isIntegral(null));
            assertFalse(isIntegral(Boolean.FALSE));
            assertTrue(isIntegral(Byte.valueOf((byte) 0)));
            assertFalse(isIntegral(Character.valueOf('a')));
            assertTrue(isIntegral(Integer.valueOf(0)));
            assertTrue(isIntegral(Long.valueOf(0L)));
            assertTrue(isIntegral(Short.valueOf((short) 0)));
            assertFalse(isIntegral("0"));
        }

        @Test
        public void as()
        {
            assertEquals(Long.valueOf(1L), asLong(Short.valueOf((short) 1)));
            assertEquals(Long.valueOf(1L), asLong(Integer.valueOf(1)));
            assertEquals(Long.valueOf(1L), asLong(Long.valueOf(1)));

            assertEquals(Integer.valueOf(1), asInteger(Short.valueOf((short) 1)));
            assertEquals(Integer.valueOf(1), asInteger(Integer.valueOf(1)));
            assertEquals(Integer.valueOf(1), asInteger(Long.valueOf(1)));

            try
            {
                Integer I = asInteger(Long.valueOf((long) Integer.MAX_VALUE + 1L));
                fail("should have thown");
            }
            catch (ClassCastException e)
            {
                // success
            }
        }

        @Test
        public void eq()
        {
            assertTrue(integerEquals(null, null));
            assertTrue(integerEquals(Short.valueOf((short) 1), Long.valueOf(1)));
            assertTrue(integerEquals(Integer.valueOf(1), Long.valueOf(1)));
            assertTrue(integerEquals(Integer.valueOf(1), Short.valueOf((short) 1)));
            assertFalse(integerEquals(Short.valueOf((short) 1), Long.valueOf((long) Integer.MAX_VALUE + 1L)));
            assertTrue(integerEquals(Long.valueOf((long) Integer.MAX_VALUE + 1L), Long.valueOf((long) Integer.MAX_VALUE + 1L)));
            try
            {
                integerEquals(Long.valueOf(1), "1");
            }
            catch (ClassCastException e)
            {
                // success
            }

            assertTrue(objectEquals(null, null));
            assertTrue(objectEquals(Short.valueOf((short) 1), Long.valueOf(1)));
            assertTrue(objectEquals(Integer.valueOf(1), Long.valueOf(1)));
            assertTrue(objectEquals(Integer.valueOf(1), Short.valueOf((short) 1)));
            assertFalse(objectEquals(Short.valueOf((short) 1), Long.valueOf((long) Integer.MAX_VALUE + 1L)));
            assertTrue(objectEquals(Long.valueOf((long) Integer.MAX_VALUE + 1L), Long.valueOf((long) Integer.MAX_VALUE + 1L)));
            assertFalse(objectEquals(Long.valueOf(1), "1"));
        }
    }
}
