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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.dialect.MockSqlDialect;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

import java.util.Collection;
import java.util.Map;

public class AliasManager
{
    SqlDialect _dialect;
    Map<String, String> _aliases = new CaseInsensitiveHashMap<>();

    private AliasManager(SqlDialect d)
    {
        _dialect = d;
    }

    public AliasManager(DbSchema schema)
    {
        _dialect = null==schema ? null :  schema.getSqlDialect();
    }

    public AliasManager(@NotNull TableInfo table, @Nullable Collection<ColumnInfo> columns)
    {
        this(table.getSchema());
        claimAliases(table.getColumns());
        if (columns != null)
            claimAliases(columns);
    }


    private static boolean isLegalNameChar(char ch, boolean first)
    {
        if (ch >= 'A' && ch <= 'Z' || ch >= 'a' && ch <= 'z' || ch == '_')
            return true;
        if (first)
            return false;
        if (ch >= '0' && ch <= '9')
            return true;
        return false;
    }

    public static boolean isLegalName(String str)
    {
        int length = str.length();
        for (int i = 0; i < length; i ++)
        {
            if (!isLegalNameChar(str.charAt(i), i == 0))
                return false;
        }
        return true;
    }

    public static String legalNameFromName(String str)
    {
        int i;
        char ch=0;

        int length = str.length();
        for (i = 0; i < length; i ++)
        {
            ch = str.charAt(i);
            if (!isLegalNameChar(ch, i==0))
                break;
        }
        if (i==length)
            return str;

        StringBuilder sb = new StringBuilder(length+20);
        if (i==0 && isLegalNameChar(ch, false))
        {
            i++;
            sb.append('_').append(ch);
        }
        else
        {
            sb.append(str.substring(0, i));
        }

        for ( ; i < length ; i ++)
        {
            ch = str.charAt(i);
            boolean isLegal = isLegalNameChar(ch, i==0);
            if (isLegal)
            {
                sb.append(ch);
            }
            else
            {
                switch (ch)
                {
                    case '+' : sb.append("_plus_"); break;
                    case '-' : sb.append("_minus_"); break;
                    case '(' : sb.append("_lp_"); break;
                    case ')' : sb.append("_rp_"); break;
                    case '/' : sb.append("_fs_"); break;
                    case '\\' : sb.append("_bs_"); break;
                    case '&' : sb.append("_amp_"); break;
                    case '<' : sb.append("_lt_"); break;
                    case '>' : sb.append("_gt_"); break;
                    default: sb.append("_"); break;
                }
            }
        }
        return null == sb ? str : sb.toString();
    }


    private String makeLegalName(String str)
    {
        return makeLegalName(str, _dialect);
    }

    private String makeLegalName(String str, int reserveCount)
    {
        return makeLegalName(str, _dialect, true, false, reserveCount);
    }

    public static String makeLegalName(String str, @Nullable SqlDialect dialect, boolean useLegacyMaxLength)
    {
        return makeLegalName(str, dialect, true, useLegacyMaxLength);
    }


    public static String makeLegalName(String str, @Nullable SqlDialect dialect)
    {
        return makeLegalName(str, dialect, true, false);
    }


    public static String makeLegalName(String str, @Nullable SqlDialect dialect, boolean truncate, boolean useLegacyMaxLength)
    {
        return makeLegalName(str, dialect, truncate, useLegacyMaxLength, 0);
    }

    private static String makeLegalName(String str, @Nullable SqlDialect dialect, boolean truncate, boolean useLegacyMaxLength, int reserveCount)
    {
        String ret = legalNameFromName(str);
        if (null != dialect && dialect.isReserved(ret))
            ret = "_" + ret;
        int length = ret.length();
        if (0 == length)
            return "_";
        // we use 28 here because Oracle has a limit or 30 characters, and that is likely the shortest restriction
        int maxLength = useLegacyMaxLength ? 40 : (dialect == null ? 28 : dialect.getIdentifierMaxLength());
        if (reserveCount > 0)
            maxLength -= reserveCount;
        if (maxLength < 5)
            throw new IllegalStateException("Maxlength for legal name too small: " + maxLength);
        return (truncate && length > maxLength) ? truncate(ret, maxLength) : ret;
    }


    public static String makeLegalName(FieldKey key, @Nullable SqlDialect dialect, boolean useLegacyMaxLength)
    {
        if (key.getParent() == null)
            return makeLegalName(key.getName(), dialect);
        StringBuilder sb = new StringBuilder();
        String connector = "";
        for (String part : key.getParts())
        {
            sb.append(connector);
            sb.append(legalNameFromName(part));
            connector = "_";
        }
        // we use 28 here because Oracle has a limit or 30 characters, and that is likely the shortest restriction
        return truncate(sb.toString(), useLegacyMaxLength ? 40 : (dialect == null ? 28 : dialect.getIdentifierMaxLength()));
    }


    public static String truncate(String str, int to)
    {
        int len = str.length();
        if (len <= to)
            return str;
        String n = String.valueOf((str.hashCode()&0x7fffffff));
        return str.charAt(0) + n + str.substring(len-(to-n.length()-1));
    }


    public String decideAlias(String name)
    {
        return checkAndFinishAlias(makeLegalName(name), name);
    }

    public String decideAlias(String name, String preferred)
    {
        if (!_aliases.containsKey(preferred))
        {
            _aliases.put(preferred, name);
            return preferred;
        }
        return checkAndFinishAlias(makeLegalName(name), name);
    }

    public String decideAlias(String name, int reserveCount)
    {
        return checkAndFinishAlias(makeLegalName(name, reserveCount), name);
    }

    private String checkAndFinishAlias(String legalName, String name)
    {
        String ret = legalName;
        for (int i = 1; _aliases.containsKey(ret); i ++)
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
        claimAlias(column.getAlias(), column.getName());
    }


    /* assumes won't be called on same columninfo twice
     * does not assume that names are unique (e.g. might be fieldkey.toString() or just fieldKey.getname())
     */
    public void ensureAlias(ColumnInfo column, @Nullable String extra)          // TODO: any external modules use this?
    {
        if (column.isAliasSet())
        {
            if (_aliases.get(column.getAlias()) != null)
                throw new IllegalStateException("alias '" + column.getAlias() + "' is already in use!  the column name and alias are: " + column.getName() + " / " + column.getAlias() + ".  The full set of aliases are: " + _aliases.toString()); // SEE BUG 13682 and 15475
            claimAlias(column.getAlias(), column.getName());
        }
        else
            column.setAlias(decideAlias(column.getName() + StringUtils.defaultString(extra,"")));
    }

    public void ensureAlias(ColumnInfo column)
    {
        if (column.isAliasSet())
        {
            if (_aliases.get(column.getAlias()) != null)
                throw new IllegalStateException("alias '" + column.getAlias() + "' is already in use!  the column name and alias are: " + column.getName() + " / " + column.getAlias() + ".  The full set of aliases are: " + _aliases.toString()); // SEE BUG 13682 and 15475
            claimAlias(column.getAlias(), column.getName());
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



    public static class TestCase extends Assert
    {
        @Test
        public void test_legalNameFromName()
        {
            assertEquals("bob", legalNameFromName("bob"));
            assertEquals("bob1", legalNameFromName("bob1"));
            assertEquals("_1", legalNameFromName("1"));
            assertEquals("_1bob", legalNameFromName("1bob"));
            assertEquals("_bob", legalNameFromName("?bob"));
            assertEquals("bob_", legalNameFromName("bob?"));
            assertEquals("bob_by", legalNameFromName("bob?by"));
            assertFalse(legalNameFromName("bob+").equals(legalNameFromName("bob-")));
        }

        @Test
        public void test_decideAlias()
        {
            AliasManager m = new AliasManager(new MockSqlDialect()
            {
                @Override
                public boolean isReserved(String word)
                {
                    return "select".equals(word);
                }
            });

            assertEquals("fred", m.decideAlias("fred"));
            assertEquals("fred1", m.decideAlias("fred"));
            assertEquals("fred2", m.decideAlias("fred"));

            assertEquals("_1fred", m.decideAlias("1fred"));
            assertEquals("_1fred1", m.decideAlias("1fred"));
            assertEquals("_1fred2", m.decideAlias("1fred"));

            assertEquals("_select", m.decideAlias("select"));

            assertEquals(m._dialect.getIdentifierMaxLength(), m.decideAlias("This is a very long name for a column, but it happens! go figure.").length());
        }
    }
}
