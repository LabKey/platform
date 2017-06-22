/*
 * Copyright (c) 2006-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.api.query;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.Path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Maps to a column name. The full string is separated by slashes, where
 * each token is a lookup.
 * There are many different senses of "column names" for ColumnInfo's.
 * There is:
 * 1. The name of the column in the underlying real table that the
 * ColumnInfo gets its value from (ColumnInfo.getValueSQL()).
 * 2. The name of the column in the ResultSet (ColumnInfo.getAlias())
 * 3. The name of the column in an URL filter, as a POST param in an update
 * form, etc. (ColumnInfo.getName()).
 * 4. The name of a column in LabKey SQL.
 *
 * FieldKey's should only ever be used for #3 and #4.
 *
 */
public class FieldKey extends QueryKey<FieldKey>
{
    private static final String DIVIDER = "/";

    private static final QueryKey.Factory<FieldKey> FACTORY = FieldKey::new;

    /**
     * same as fromString() but URL encoded
     */
    static public FieldKey decode(String str)
    {
        return QueryKey.decode(FACTORY, DIVIDER, str);
    }


    /**
     * Construct a FieldKey from a string that may have been returned by ColumnInfo.getName()
     * or by FieldKey.toString(), or from an URL filter.
     * Try to avoid calling this on strings that are hard-coded in the source code.
     * Use FieldKey.fromParts(...) instead.  That version handles escaping the individual
     * parts of the FieldKey, and will enable us to maintain flexibility to change the
     * escaping algorithm. 
     */
    static public FieldKey fromString(String str)
    {
        return QueryKey.fromString(FACTORY, DIVIDER, str);
    }

    static public FieldKey fromString(FieldKey parent, String str)
    {
        return QueryKey.fromString(FACTORY, DIVIDER, parent, str);
    }

    @JsonCreator
    static public FieldKey fromParts(List<String> parts)
    {
        return QueryKey.fromParts(FACTORY, parts);
    }

    static public FieldKey fromParts(String...parts)
    {
        return fromParts(Arrays.asList(parts));
    }

    static public FieldKey fromParts(Enum... parts)
    {
        List<String> strings = new ArrayList<>(parts.length);
        for (Enum part : parts)
        {
            strings.add(part.toString());
        }
        return fromParts(strings);
    }


    static public FieldKey fromParts(FieldKey ... parts)
    {
        return QueryKey.fromParts(FACTORY, parts);
    }


    static public FieldKey fromPath(Path path)
    {
        List<String> strings = new ArrayList<>(path.size());
        for (String part : path)
            strings.add(part);
        return fromParts(strings);
    }



    static public FieldKey remap(FieldKey key, @Nullable FieldKey parent, @Nullable Map<FieldKey,FieldKey> remap)
    {
        FieldKey replace = remap == null ? null : remap.get(key);
        if (null != replace)
            return replace;
        else if (null != parent)
            return FieldKey.fromParts(parent, key);
        return key;
    }


    public FieldKey(@Nullable FieldKey parent, @NotNull String name)
    {
        super(parent, name);
    }

    public FieldKey(@Nullable FieldKey parent, Enum name)
    {
        super(parent, name);
    }

    @Override
    protected String getDivider()
    {
        return DIVIDER;
    }

    public FieldKey getTable()
    {
        return (FieldKey)super.getParent();
    }

    public String getRootName()
    {
        FieldKey fk = this;
        while (null != fk.getParent())
            fk = fk.getParent();
        return fk.getName();
    }

    @Override
    public FieldKey getParent()
    {
        return (FieldKey)super.getParent();
    }

    public @NotNull String getLabel()
    {
        return getName();
    }

    public @NotNull String getCaption()
    {
        return ColumnInfo.labelFromName(getName());
    }

    public boolean isAllColumns()
    {
        return false;
        // return getName().equals("*");
    }

    /**
     * Remove the root component from this key if it matches the rootPart, otherwise null.
     *
     * @param rootPart The root FieldKey name to match.
     * @return The new FieldKey with the root key removed
     */
    @Nullable
    public FieldKey removeParent(String rootPart)
    {
        List<String> parts = getParts();
        if (parts.size() > 1 && parts.get(0).equalsIgnoreCase(rootPart))
        {
            parts = parts.subList(1, parts.size());
            return FieldKey.fromParts(parts);
        }

        return null;
    }

    /**
     * Create a new child FieldKey of this FieldKey using <code>parts</code>
     */
    public FieldKey append(String... parts)
    {
        FieldKey ret = this;
        for (String part : parts)
        {
            ret = new FieldKey(ret, part);
        }
        return ret;
    }

    public int compareTo(FieldKey o)
    {
        return CASE_INSENSITIVE_ORDER.compare(this, o);
    }

    public static final Comparator<FieldKey> CASE_INSENSITIVE_STRING_ORDER = (a, b) -> {
        if (a==b) return 0;
        if (null==a) return -1;
        if (null==b) return 1;
        return String.CASE_INSENSITIVE_ORDER.compare(a.toString(), b.toString());
    };


    public static final Comparator<FieldKey> CASE_INSENSITIVE_ORDER = new Comparator<FieldKey>()
    {
        @Override
        public int compare(FieldKey a, FieldKey b)
        {
            if (a==b) return 0;
            if (null==a) return -1;
            if (null==b) return 1;
            int c = compare(a.getParent(), b.getParent());
            return c!=0 ? c : String.CASE_INSENSITIVE_ORDER.compare(a.getName(),b.getName());
        }
    };

    public static final Comparator<FieldKey> CASE_SENSITIVE_ORDER = new Comparator<FieldKey>()
    {
        @Override
        public int compare(FieldKey a, FieldKey b)
        {
            if (a==b) return 0;
            if (null==a) return -1;
            if (null==b) return 1;
            int c = compare(a.getParent(), b.getParent());
            return c!=0 ? c : a.getName().compareTo(b.getName());
        }
    };


    public static final class Converter implements org.apache.commons.beanutils.Converter
    {
        @Override
        public Object convert(Class type, Object value)
        {
            if (value == null)
                return null;

            if (value instanceof FieldKey)
                return value;

            if (value instanceof String)
                return FieldKey.fromString((String)value);
            else if (value instanceof String[])
                return FieldKey.fromParts((String[])value);
            else if (value instanceof FieldKey[])
                return FieldKey.fromParts((FieldKey[])value);
            else if (value instanceof List)
                // XXX: convert items in List?
                return FieldKey.fromParts((List)value);

            throw new ConversionException("Could not convert '" + value + "' to a FieldKey");
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void testCompare()
        {
            assertTrue(new FieldKey(null,"a").compareTo(new FieldKey(null,"a")) == 0);
            assertTrue(new FieldKey(null,"a").compareTo(new FieldKey(null,"A")) == 0);
            assertTrue(new FieldKey(null,"a").compareTo(new FieldKey(null,"b")) < 0);
            assertTrue(new FieldKey(null,"a").compareTo(new FieldKey(null,"B")) < 0);
            assertTrue(new FieldKey(null,"A").compareTo(new FieldKey(null,"a")) == 0);
            assertTrue(new FieldKey(null,"A").compareTo(new FieldKey(null,"A")) == 0);
            assertTrue(new FieldKey(null,"A").compareTo(new FieldKey(null,"b")) < 0);
            assertTrue(new FieldKey(null,"A").compareTo(new FieldKey(null,"B")) < 0);

            assertTrue(fromParts("a","b").compareTo(fromParts("a","b")) == 0);
            assertTrue(fromParts("a","b").compareTo(fromParts("b","a")) < 0);
            assertTrue(fromParts("b","a").compareTo(fromParts("a","b")) > 0);
            assertTrue(fromParts("a","b").compareTo(fromParts("a","c")) < 0);

            // shorter sorts first, don't really care but that's easier given the datastructure
            assertTrue(FieldKey.fromParts("z").compareTo(fromParts("a","b")) < 0);
            assertTrue(FieldKey.fromParts("a","b").compareTo(fromParts("z")) > 0);
        }

        @Test
        public void testParse()
        {
            assertEquals(FieldKey.fromParts("Slash/Separated"), FieldKey.fromString("Slash$SSeparated"));
            assertEquals(FieldKey.fromParts("Dollar$Separated"), FieldKey.fromString("Dollar$DSeparated"));
            assertEquals(FieldKey.fromParts("Tilde~Separated"), FieldKey.fromString("Tilde$TSeparated"));
            assertEquals(FieldKey.fromParts("Parent", "Tilde~Separated"), FieldKey.fromString("Parent/Tilde$TSeparated"));
        }

        @Test
        public void testConcat()
        {
            assertEquals(FieldKey.fromParts(FieldKey.fromParts("Parent"), FieldKey.fromParts("Child")), FieldKey.fromParts("Parent", "Child"));
            //noinspection RedundantCast -- IntelliJ inspection is wrong; this FieldKey cast is actually required
            assertEquals(FieldKey.fromParts((FieldKey)null, FieldKey.fromParts("Parent"), FieldKey.fromParts("Child")), FieldKey.fromParts("Parent", "Child"));
        }

        @Test
        public void testEncode()
        {
            assertEquals("Slash$SSeparated", FieldKey.fromParts("Slash/Separated").toString());
            assertEquals("Dollar$DSeparated", FieldKey.fromParts("Dollar$Separated").toString());
            assertEquals("Tilde$TSeparated", FieldKey.fromParts("Tilde~Separated").toString());
            assertEquals("Parent/Tilde$TSeparated", FieldKey.fromParts("Parent", "Tilde~Separated").toString());
        }

        @Test
        public void testToLabKeySQL()
        {
            assertEquals("\"Slash/Separated\"", FieldKey.fromParts("Slash/Separated").toSQLString());
            assertEquals("\"Parent\".\"Child\"", FieldKey.fromParts("Parent", "Child").toSQLString());
            assertEquals("\"With\"\"Quote\"", FieldKey.fromParts("With\"Quote").toSQLString());
        }
    }
}
