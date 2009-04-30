/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SqlDialect;
import org.labkey.api.data.TableInfo;
import org.labkey.api.collections.CaseInsensitiveHashMap;

import java.util.Collection;
import java.util.Map;

public class AliasManager
{
    SqlDialect _dialect;
    Map<String, String> _aliases = new CaseInsensitiveHashMap<String>();

    public AliasManager(DbSchema schema)
    {
        _dialect = schema.getSqlDialect();
    }

    public AliasManager(TableInfo table, Collection<ColumnInfo> columns)
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
        String ret = legalNameFromName(str);
        if (null != dialect && dialect.isReserved(str))
            ret = "_" + ret;
        if (ret.length() == 0)
            return "_";
        if (ret.length() > 40)
            return ret.substring(0, 40);
        return ret;
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

    public void claimAliases(Collection<ColumnInfo> columns)
    {
        for (ColumnInfo column : columns)
        {
            claimAlias(column);
        }
    }
}
