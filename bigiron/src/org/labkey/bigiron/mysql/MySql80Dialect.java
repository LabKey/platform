/*
 * Copyright (c) 2018 LabKey Corporation
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
package org.labkey.bigiron.mysql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;

import java.util.Set;

public class MySql80Dialect extends MySql57Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        // Add new reserved words in MySQL 8.0; see http://dev.mysql.com/doc/refman/8.0/en/keywords.html
        Set<String> words = super.getReservedWords();

        words.remove("sql_cache");
        words.addAll(new CsvSet("admin, columns, cube, cume_dist, dense_rank, empty, events, except, first_value, function, " +
                "grouping, groups, indexes, json_table, lag, last_value, lateral, lead, nth_value, ntile, of, over, parameters, " +
                "percent_rank, rank, recursive, routines, row, row_number, rows, system, tables, triggers, window"));

        return words;
    }
}
