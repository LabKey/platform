/*
 * Copyright (c) 2012-2015 LabKey Corporation
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
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Maps to a schema name.  The full string is separated by dots, where
 * each token is a schema in the schema hierarchy.
 */
public class SchemaKey extends QueryKey<SchemaKey>
{
    private static final String DIVIDER = ".";

    private static final QueryKey.Factory<SchemaKey> FACTORY = SchemaKey::new;

    /**
     * same as fromString() but URL encoded
     */
    static public SchemaKey decode(String str)
    {
        return QueryKey.decode(FACTORY, DIVIDER, str);
    }


    /**
     * Construct a SchemaKey from a string that may have been returned by UserSchema.getSchemaName()
     * or by SchemaKey.toString(), or from an URL filter.
     * Try to avoid calling this on strings that are hard-coded in the source code.
     * Use SchemaKey.fromParts(...) instead.  That version handles escaping the individual
     * parts of the SchemaKey, and will enable us to maintain flexibility to change the
     * escaping algorithm.
     */
    static public SchemaKey fromString(String str)
    {
        return QueryKey.fromString(FACTORY, DIVIDER, str);
    }

    static public SchemaKey fromString(SchemaKey parent, String str)
    {
        return SchemaKey.fromString(FACTORY, DIVIDER, parent, str);
    }

    @JsonCreator
    static public SchemaKey fromParts(List<String> parts)
    {
        return QueryKey.fromParts(FACTORY, parts);
    }

    static public SchemaKey fromParts(String...parts)
    {
        return fromParts(Arrays.asList(parts));
    }

    static public SchemaKey fromParts(Enum... parts)
    {
        List<String> strings = new ArrayList<>(parts.length);
        for (Enum part : parts)
        {
            strings.add(part.toString());
        }
        return fromParts(strings);
    }


    static public SchemaKey fromParts(SchemaKey... parts)
    {
        return QueryKey.fromParts(FACTORY, parts);
    }



    public SchemaKey(SchemaKey parent, @NotNull String name)
    {
        super(parent, name);
    }

    public SchemaKey(SchemaKey parent, Enum name)
    {
        super(parent, name.toString());
    }

    @Override
    protected String getDivider()
    {
        return DIVIDER;
    }

    @Override
    public SchemaKey getParent()
    {
        return (SchemaKey)super.getParent();
    }

    public int compareTo(SchemaKey o)
    {
        return CASE_INSENSITIVE_ORDER.compare(this, o);
    }

    /**
     * Sorts SchemaKeys in string order:
     *   A
     *   A/B
     *   B
     */
    public static final Comparator<SchemaKey> CASE_INSENSITIVE_STRING_ORDER = (a, b) -> {
        if (a==b) return 0;
        if (null==a) return -1;
        if (null==b) return 1;
        return String.CASE_INSENSITIVE_ORDER.compare(a.toString(), b.toString());
    };

    /**
     * Sorts SchemaKeys by siblings first then children:
     *   A
     *   B
     *   A/B
     */
    public static final Comparator<SchemaKey> CASE_INSENSITIVE_ORDER = new Comparator<SchemaKey>()
    {
        @Override
        public int compare(SchemaKey a, SchemaKey b)
        {
            if (a==b) return 0;
            if (null==a) return -1;
            if (null==b) return 1;
            int c = compare(a.getParent(), b.getParent());
            return c!=0 ? c : String.CASE_INSENSITIVE_ORDER.compare(a.getName(),b.getName());
        }
    };

    public static final Comparator<SchemaKey> CASE_SENSITIVE_ORDER = new Comparator<SchemaKey>()
    {
        @Override
        public int compare(SchemaKey a, SchemaKey b)
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

            if (value instanceof SchemaKey)
                return value;

            if (value instanceof String)
                return SchemaKey.fromString((String)value);
            else if (value instanceof String[])
                return SchemaKey.fromParts((String[])value);
            else if (value instanceof SchemaKey[])
                return SchemaKey.fromParts((SchemaKey[])value);
            else if (value instanceof List)
                // XXX: convert items in List?
                return SchemaKey.fromParts((List)value);

            throw new ConversionException("Could not convert '" + value + "' to a SchemaKey");
        }
    }


    public static class TestCase extends Assert
    {
        @Test
        public void test()
        {
            assertTrue(new SchemaKey(null,"a").compareTo(new SchemaKey(null,"a")) == 0);
            assertTrue(new SchemaKey(null,"a").compareTo(new SchemaKey(null,"A")) == 0);
            assertTrue(new SchemaKey(null,"a").compareTo(new SchemaKey(null,"b")) < 0);
            assertTrue(new SchemaKey(null,"a").compareTo(new SchemaKey(null,"B")) < 0);
            assertTrue(new SchemaKey(null,"A").compareTo(new SchemaKey(null,"a")) == 0);
            assertTrue(new SchemaKey(null,"A").compareTo(new SchemaKey(null,"A")) == 0);
            assertTrue(new SchemaKey(null,"A").compareTo(new SchemaKey(null,"b")) < 0);
            assertTrue(new SchemaKey(null,"A").compareTo(new SchemaKey(null,"B")) < 0);

            assertTrue(fromParts("a","b").compareTo(fromParts("a","b")) == 0);
            assertTrue(fromParts("a","b").compareTo(fromParts("b","a")) < 0);
            assertTrue(fromParts("b","a").compareTo(fromParts("a","b")) > 0);
            assertTrue(fromParts("a","b").compareTo(fromParts("a","c")) < 0);

            // shorter sorts first, don't really care but that's easier given the data structure
            assertTrue(SchemaKey.fromParts("z").compareTo(fromParts("a","b")) < 0);
            assertTrue(SchemaKey.fromParts("a","b").compareTo(fromParts("z")) > 0);

            assertEquals(SchemaKey.fromParts("assay", "Viral Loads", "Viral Loads").toString(), "assay.Viral Loads.Viral Loads");
        }
    }
}
