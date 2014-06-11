/*
 * Copyright (c) 2012 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.util.GUID;

import java.util.Arrays;
import java.util.Collection;

/**
 * For longer IN clauses, passes the values as in-line SQL instead of JDBC parameters. This works around
 * a SQLServer limitation of 2000 JDBC parameters.
 *
 * User: jeckels
 * Date: 6/10/14
 */
public class InlineInClauseGenerator implements InClauseGenerator
{
    private static final int IN_LINE_MINIMUM_COUNT = 10;

    private static final InClauseGenerator FALLBACK_GENERATOR = new ParameterMarkerInClauseGenerator();

    @Override
    public SQLFragment appendInClauseSql(SQLFragment sql, @NotNull Collection<?> params)
    {
        int inLineableCount = 0;
        for (Object param : params)
        {
            // Check if we're capable of in-lining the value
            if (param instanceof Number || param instanceof GUID)
            {
                inLineableCount++;
            }
        }

        if (inLineableCount < IN_LINE_MINIMUM_COUNT)
        {
            // Don't bother in-lining for shorter IN clauses so that we have a chance of reusing a pre-compiled
            // prepared statement
            return FALLBACK_GENERATOR.appendInClauseSql(sql, params);
        }

        String separator = "";
        sql.append("IN (");

        for (Object param : params)
        {
            sql.append(separator);
            separator = ", ";
            if (param instanceof Number)
            {
                sql.append(param);
            }
            else if (param instanceof GUID)
            {
                // No need to escape any characters in true GUIDs
                sql.append("'").append(param).append("'");
            }
            else
            {
                sql.append("?");
                sql.add(param);
            }
        }

        sql.append(")");

        return sql;
    }

    public static class TestCase
    {
        @Test
        public void testTooShortToInline()
        {
            Assert.assertEquals(
                    new SQLFragment("IN (?, ?, ?)", 1, 2, 3),
                    new InlineInClauseGenerator().appendInClauseSql(new SQLFragment(), Arrays.asList(1, 2, 3)));
        }

        @Test
        public void testMixedTypesInline()
        {
            GUID g = new GUID();
            Assert.assertEquals(
                    new SQLFragment("IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, '" + g.toString() + "')"),
                    new InlineInClauseGenerator().appendInClauseSql(new SQLFragment(), Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, g)));
        }

        @Test
        public void testUnsupportedTypes()
        {
            // We don't inline arbitrary strings
            Assert.assertEquals(
                    new SQLFragment("IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14"),
                    new InlineInClauseGenerator().appendInClauseSql(new SQLFragment(), Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14")));
        }
    }
}
