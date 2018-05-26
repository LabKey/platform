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
        words.addAll(new CsvSet("admin, columns, cube, cume_dist, dense_rank, empty, events, except, first_value, " +
                "function, grouping, groups, indexes, lag, last_value, lead, nth_value, ntile, of, over, parameters, percent_rank, " +
                "rank, recursive, routines, row, row_number, rows, system, tables, triggers, window"));

        return words;
    }
}
