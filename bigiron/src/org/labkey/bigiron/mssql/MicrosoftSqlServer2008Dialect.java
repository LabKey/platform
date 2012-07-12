/*
 * Copyright (c) 2008-2011 LabKey Corporation
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

package org.labkey.bigiron.mssql;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.data.SQLFragment;

import java.util.Set;

/**
 * User: kevink
 * Date: Jan 28, 2008 2:56:27 PM
 */
public class MicrosoftSqlServer2008Dialect extends MicrosoftSqlServer2005Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.removeAll(new CsvSet("dump, load"));
        return words;
    }

    @Override
    public boolean supportsGroupConcat()
    {
        return true;
    }

    // Uses custom CLR aggregate function defined in core-12.10-12.20.sql
    @Override
    public SQLFragment getGroupConcat(SQLFragment sql, boolean distinct, boolean sorted)
    {
        // SQL Server does not support aggregates on sub-queries; return a string constant in that case to keep from
        // blowing up. TODO: Don't pass sub-selects into group_contact.
        if (StringUtils.containsIgnoreCase(sql.getSQL(), "SELECT"))
            return new SQLFragment("'NOT SUPPORTED'");

        SQLFragment result = new SQLFragment("core.GROUP_CONCAT");

        if (sorted)
        {
            result.append("_S");
        }

        result.append("(");

        if (distinct)
        {
            result.append("DISTINCT ");
        }

        result.append(sql);

        if (sorted)
        {
            result.append(", 1");
        }

        result.append(")");

        return result;
    }
}
