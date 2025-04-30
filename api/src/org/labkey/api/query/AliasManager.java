/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.DatabaseIdentifier;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.JdbcHelper;
import org.labkey.api.data.dialect.MockSqlDialect;
import org.labkey.api.data.dialect.PostgreSql91Dialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.util.StringUtilsLabKey;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

public class AliasManager
{
    // SqlDialect to use when null dialect is provided. This implements "least-common denominator" rules for
    // identifiers, ensuring aliases will work on all databases.
    private static class FallBackDialect extends  MockSqlDialect
    {
        @Override
        protected int getIdentifierMaxCharLength()
        {
            // Old Oracle rule is 30 characters max
            return 30;
        }
    
        @Override
        public boolean isLegalNameChar(char ch, boolean first)
        {
            // oracle doesn't allow leading underscore
            return super.isLegalNameChar(ch, first) && !(first && ch == '_');
        }

        @Override
        public String makeLegalName(String str, boolean truncate, int reserveCount)
        {
            // Oracle rule
            String ret = super.makeLegalName(str, truncate, reserveCount);
            // PostgreSQL rule
            if (truncate)
                ret = StringUtilsLabKey.leftUtf8Bytes(ret, 63 - reserveCount);
            return ret;
        }

        @Override
        public String makeLegalName(FieldKey key, int reserveCount)
        {
            // Oracle rule - truncate to 30 characters
            String legal = super.makeLegalName(key, reserveCount);
            // PostgreSQL rule - truncate to 63 bytes
            return StringUtilsLabKey.leftUtf8Bytes(legal, 63 - reserveCount);
        }
    };

    final @NotNull SqlDialect _dialect;
    final Map<String, String> _aliases = new CaseInsensitiveHashMap<>();

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

    public static AliasManager createLabKeyAliasManager()
    {
        return new AliasManager(new _LabKeyDialect());
    }

    // null dialect is tolerated but not recommended
    public static String makeLegalName(String str, @Nullable SqlDialect dialect)
    {
        return makeLegalName(str, dialect, true);
    }

    // null dialect is tolerated but not recommended
    public static String makeLegalName(String str, @Nullable SqlDialect dialect, boolean truncate)
    {
        return makeLegalName(str, dialect, truncate, 0);
    }

    // null dialect is tolerated but not recommended
    private static String makeLegalName(String str, @Nullable SqlDialect dialect, boolean truncate, int reserveCount)
    {
        // New FallBackDialect on every call to avoid SqlDialect mem-tracker leak
        return (dialect != null ? dialect : new FallBackDialect()).makeLegalName(str, truncate, reserveCount);
    }

    // null dialect is tolerated but not recommended
    @Deprecated // TODO: Unused?
    public static String makeLegalName(FieldKey key, @Nullable SqlDialect dialect)
    {
        // New FallBackDialect on every call to avoid SqlDialect mem-tracker leak
        return (dialect != null ? dialect : new FallBackDialect()).makeLegalName(key, 0);
    }

    @Deprecated // TODO: Unused?
    public static String truncate(DatabaseIdentifier id, int to)
    {
        return truncate(id.getId(), to);
    }
  
    @Deprecated // TODO: Unused?
    public static String truncate(String str, int to)
    {
        // New FallBackDialect on every call to avoid SqlDialect mem-tracker leak
        return (dialect != null ? dialect : new FallBackDialect()).makeLegalName(key, 0);
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

/*
    public String decideAlias(FieldKey key)
    {
        String alias = _keys.get(key);
        if (null != alias)
            return alias;
        String name = null == key.getParent() ? key.getName() : key.toString();
        alias = decideAlias(name);
        _keys.put(key,alias);
        return alias;
    }


    // only for ColumnInfo.setAlias()
    public void claimAlias(FieldKey key, String proposed)
    {
        String alias = _keys.get(key);
        assert null == alias || alias.equals(proposed);
        if (null != alias)
            return;
        assert null == _aliases.get(proposed) : "duplicate alias";
        String name = null == key.getParent() ? key.getName() : key.toString();
        _aliases.put(proposed,name);
        _keys.put(key,proposed);
    }
*/

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
            if (null != (name = _aliases.get(column.getAlias())))
            {
                if (!name.equals(column.getName()))
                    throw new IllegalStateException("alias '" + column.getAlias() + "' is already in use!  the column name and alias are: " + column.getName() + " / " + column.getAlias().getId() + ".  The full set of aliases are: " + _aliases.toString()); // SEE BUG 13682 and 15475
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
        _aliases.remove(column.getAlias());
    }


    private static class _LabKeyDialect extends PostgreSql91Dialect
    {
        @Override
        protected void initializeInClauseGenerator(DbScope scope)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getProductName()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getMedianFunction()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public JdbcHelper getJdbcHelper()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        protected boolean shouldQuoteIdentifier(String id)
        {
            return true;
        }

        @Override
        public boolean isReserved(String word)
        {
            return super.isReserved(word);
        }

        @Override
        public String makeLegalIdentifierName(String id)
        {
            return super.makeLegalIdentifierName(id);
        }

        @Override
        public int getIdentifierMaxLength()
        {
            return 200;
        }
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
        public void testNullDialect()
        {
            AliasManager m = new AliasManager((SqlDialect) null);
            assertEquals("fred", m.decideAlias("fred"));
            assertEquals("fred1", m.decideAlias("fred"));
            assertEquals("fred2", m.decideAlias("fred"));
            assertEquals("X__bob", m.decideAlias("_bob"));

            String truncated = m.decideAlias("1234567890123456789012345678901234567890");
            assertEquals(27, truncated.length());
            assertTrue(truncated.getBytes(StandardCharsets.UTF_8).length < 60);
            assertEquals("X13599947545678901234567890", truncated);
            // Not an interesting test at the moment since every non-alphanumeric gets replaced with _. But this will
            // become interesting if we start allowing Unicode characters in alias names in the future.
            String unicode = "\uD83D\uDC7EA\uD83D\uDC7E\uD83E\uDD91\uD83C\uDFBB\uD83C\uDFC2\uD83D\uDC7E\uD83E\uDD91\uD83C\uDFBB\uD83C\uDFC2\uD83D\uDC7E\uD83E\uDD91\uD83C\uDFBB\uD83C\uDFC2";
            truncated = m.decideAlias(unicode);
            assertEquals(27, truncated.length());
            assertTrue(truncated.getBytes(StandardCharsets.UTF_8).length < 60);
            assertEquals("X1665827962________________", truncated);
        }
    }
}
