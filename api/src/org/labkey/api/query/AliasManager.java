/*
 * Copyright (c) 2008-2026 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.MockSqlDialect;
import org.labkey.api.data.dialect.SqlDialect;

import java.util.Collection;
import java.util.Map;

public class AliasManager
{
    private final @NotNull SqlDialect _dialect;
    private final Map<String, String> _aliases = new CaseInsensitiveHashMap<>();

    public AliasManager(@NotNull SqlDialect d)
    {
        _dialect = d;
    }

    public AliasManager(@NotNull DbSchema schema)
    {
        _dialect = schema.getSqlDialect();
    }

    public AliasManager(@NotNull TableInfo table, @Nullable Collection<ColumnInfo> columns)
    {
        this(table.getSchema());
        claimAliases(table.getColumns());
        if (columns != null)
            claimAliases(columns);
    }

    public static String makeLegalName(String str, @NotNull SqlDialect dialect)
    {
        return makeLegalName(str, dialect, true);
    }

    public static String makeLegalName(String str, @NotNull SqlDialect dialect, boolean truncate)
    {
        return makeLegalName(str, dialect, truncate, 0);
    }

    public static String makeLegalName(String str, @NotNull  SqlDialect dialect, boolean truncate, int reserveCount)
    {
        return dialect.makeLegalName(str, truncate, reserveCount);
    }

    public static String makeLegalName(FieldKey key, @NotNull  SqlDialect dialect)
    {
        return dialect.makeLegalName(key, 0);
    }

    public String decideAlias(String name)
    {
        String legalName = makeLegalName(name, _dialect, true, 3 /* Leave room for suffix */);
        String ret = legalName;
        for (int i = 1; _aliases.containsKey(ret); i++)
        {
            ret = legalName + i;
        }
        _aliases.put(ret, name);
        return ret;
    }

    public void claimAlias(String alias, String name)
    {
        _aliases.put(alias, name);
    }

    public void claimAlias(ColumnInfo column)
    {
        if (column == null)
            return;
        claimAlias(column.getAlias().getId(), column.getName());
    }

    public void ensureAlias(MutableColumnInfo column)
    {
        if (column.isAliasSet())
        {
            String name;
            if (null != (name = _aliases.get(column.getAlias().getId())))
            {
                if (!name.equals(column.getName()))
                    throw new IllegalStateException("alias '" + column.getAlias() + "' is already in use!  the column name and alias are: " + column.getName() + " / " + column.getAlias().getId() + ".  The full set of aliases are: " + _aliases); // SEE BUG 13682 and 15475
            }
            else
                claimAlias(column.getAlias().getId(), column.getName());
        }
        else
            column.setAlias(decideAlias(column.getName()));
    }

    public void claimAliases(Collection<ColumnInfo> columns)
    {
        for (ColumnInfo column : columns)
        {
            claimAlias(column);
        }
    }

    public void unclaimAlias(ColumnInfo column)
    {
        _aliases.remove(column.getAlias().getId());
    }

    public static class TestCase extends Assert
    {
        @Test
        public void test_legalNameFromName()
        {
            SqlDialect dialect = DbScope.getLabKeyScope().getSqlDialect();
            assertEquals("bob", dialect.legalNameFromName("bob"));
            assertEquals("bob1", dialect.legalNameFromName("bob1"));
            assertEquals("_bob", dialect.legalNameFromName("_bob"));
            assertEquals("X_1", dialect.legalNameFromName("1"));
            assertEquals("X_1bob", dialect.legalNameFromName("1bob"));
            assertEquals("X__bob", dialect.legalNameFromName("?bob"));
            assertEquals("bob_", dialect.legalNameFromName("bob?"));
            assertEquals("bob_by", dialect.legalNameFromName("bob?by"));
            assertNotEquals(dialect.legalNameFromName("bob+"), dialect.legalNameFromName("bob-"));
        }

        @Test
        public void test_decideAlias()
        {
            MutableInt identifierMaxCharLength = new MutableInt();

            AliasManager m = new AliasManager(new MockSqlDialect()
            {
                {{
                    // Capture the dialect's identifier max for testing below
                    identifierMaxCharLength.setValue(getIdentifierMaxCharLength());
                }}

                @Override
                public boolean isReserved(String word)
                {
                    return "select".equals(word);
                }
            });

            assertEquals("fred", m.decideAlias("fred"));
            assertEquals("fred1", m.decideAlias("fred"));
            assertEquals("fred2", m.decideAlias("fred"));

            assertEquals("X_1fred", m.decideAlias("1fred"));
            assertEquals("X_1fred1", m.decideAlias("1fred"));
            assertEquals("X_1fred2", m.decideAlias("1fred"));

            assertEquals("select_", m.decideAlias("select"));

            assertEquals(identifierMaxCharLength.addAndGet(-3), m.decideAlias("This is a very long name for a column, but it happens! go figure. " + StringUtils.repeat('x', 100)).length());
        }

        @Test
        public void testLongNameTruncation()
        {
            SqlDialect dialect = DbScope.getLabKeyScope().getSqlDialect();
            AliasManager m = new AliasManager(dialect);

            String nums = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
            String truncatedNums1 = m.decideAlias(nums);
            String truncatedNums2 = m.decideAlias(nums);
            String truncatedNums3 = m.decideAlias(nums);

            if (dialect.isSqlServer())
            {
                assertEquals(125, truncatedNums1.length());
                assertEquals(126, truncatedNums2.length());
                assertEquals(126, truncatedNums3.length());
                assertEquals("X1483201190789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890", truncatedNums1);
            }
            else
            {
                assertEquals(60, truncatedNums1.length());
                assertEquals(61, truncatedNums2.length());
                assertEquals(61, truncatedNums3.length());
                assertEquals("X14832011902345678901234567890123456789012345678901234567890", truncatedNums1);
            }

            // Not an interesting test at the moment since every non-alphanumeric gets replaced with _. But this will
            // become interesting if we start allowing Unicode characters in alias names in the future.
            String unicode = "\uD83D\uDC7EA\uD83D\uDC7E\uD83E\uDD91\uD83C\uDFBB\uD83C\uDFC2\uD83D\uDC7E\uD83E\uDD91\uD83C\uDFBB\uD83C\uDFC2\uD83D\uDC7E\uD83E\uDD91\uD83C\uDFBB\uD83C\uDFC2";
            String truncatedUnicode = m.decideAlias(unicode);
            assertEquals(29, truncatedUnicode.length());
            assertEquals("X___A________________________", truncatedUnicode);
        }
    }
}
