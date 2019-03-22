/*
 * Copyright (c) 2011-2015 LabKey Corporation
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

package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.DialectStringHandler;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.dialect.StandardDialectStringHandler;

import java.util.Date;

/**
 * User: kevink
 */
public final class JdbcUtil
{
    private JdbcUtil() { }

    // Drop the JDBC parameters into the SQL string so that it can be executed directly in a database tool for debugging purposes.
    public static String format(SQLFragment fragment, SqlDialect dialect)
    {
        return format(fragment, dialect.getStringHandler());
    }


    // Not recommended -- without a dialect, we use SQL standard parsing of identifiers and string literals...
    // which might not be correct for the incoming SQL.
    public static String format(SQLFragment fragment)
    {
        return format(fragment, new StandardDialectStringHandler());
    }


    private static String format(SQLFragment fragment, DialectStringHandler handler)
    {
        return handler.substituteParameters(fragment);
    }


    // handle equality check for various row version types, LONG, ROWVERSION, TIMESTAMP (date time)
    public static boolean rowVersionEqual(@NotNull Object a, @NotNull Object b)
    {
        if (a == null || b == null)
            return false;
        if (a.getClass() != b.getClass())
            throw new IllegalStateException();
        if (a instanceof Number)
            return ((Number)a).longValue() == ((Number)b).longValue();
        if (a instanceof Date)
            return ((Date)a).getTime() == ((Date)b).getTime();
        if (a.getClass().isArray())
        {
            byte[] array = (byte[])a, brray = (byte[])b;
            for (int i=0 ; i<8 ; i++)
                if (array[i] != brray[i]) return false;
            return true;
        }
        throw new IllegalStateException();
    }

    public static String rowVersionToString(Object a)
    {
        if (null == a)
            return null;
        if (a instanceof Number)
            return String.valueOf(((Number)a).longValue());
        if (a instanceof Date)
            return String.valueOf(((Date)a).getTime());
        if (a.getClass().isArray())
        {
            StringBuilder sb = new StringBuilder(16);
            byte[] array = (byte[])a;
            for (int i=0 ; i<8 ; i++)
                sb.append(String.format("%02x", array[i]));
            return sb.toString();
        }
        throw new IllegalStateException();
    }
}
