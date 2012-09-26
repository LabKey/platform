/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.data.TableInfo;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AliasManager
{
    SqlDialect _dialect;
    Map<String, String> _aliases = new CaseInsensitiveHashMap<String>();
    Map<FieldKey, String> _keys = new HashMap<FieldKey,String>();


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
        for (int i = 0; i < str.length(); i ++)
        {
            if (!isLegalNameChar(str.charAt(i), i == 0))
                return false;
        }
        return true;
    }

    private static String legalNameFromName(String str)
    {
        StringBuilder buf = null;
        for (int i = 0; i < str.length(); i ++)
        {
            if (isLegalNameChar(str.charAt(i), i == 0))
                continue;
            if (buf == null)
            {
                buf = new StringBuilder(str.length());
            }
            buf.append(str.substring(buf.length(), i));
            buf.append("_");
        }
        if (buf == null)
            return str;
        buf.append(str.substring(buf.length(), str.length()));
        return buf.toString();
    }


    private String makeLegalName(String str)
    {
        return makeLegalName(str, _dialect);
    }


    public static String makeLegalName(String str, SqlDialect dialect)
    {
        return makeLegalName(str, dialect, true);
    }


    public static String makeLegalName(String str, SqlDialect dialect, boolean truncate)
    {
        String ret = legalNameFromName(str);
        if (null != dialect && dialect.isReserved(str))
            ret = "_" + ret;
        if (ret.length() == 0)
            return "_";
        return truncate ? truncate(ret, 40) : ret;
    }


    public static String makeLegalName(FieldKey key, SqlDialect dialect)
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
        return truncate(sb.toString(), 40);
    }


    public static String truncate(String str, int to)
    {
        int len = str.length();
        if (len <= to)
            return str;
        String n = String.valueOf((str.hashCode()&0x7fffffff));
        return str.charAt(0) + n + str.substring(len-(to-n.length()-1));
    }


    private String findUniqueName(String name)
    {
        name = makeLegalName(name);
        String ret = name;
        for (int i = 1; _aliases.containsKey(ret); i ++)
        {
            ret = name + i;
        }
        return ret;
    }


    public String decideAlias(String name)
    {
        String ret = findUniqueName(name);
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
    public void ensureAlias(ColumnInfo column, @Nullable String extra)
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
}
