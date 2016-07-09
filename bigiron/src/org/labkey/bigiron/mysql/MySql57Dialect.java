package org.labkey.bigiron.mysql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;

import java.util.Set;

/**
 * Created by adam on 7/9/2016.
 */
public class MySql57Dialect extends MySql56Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        // Add new reserved words in MySQL 5.7; see http://dev.mysql.com/doc/refman/5.7/en/keywords.html
        Set<String> words = super.getReservedWords();

        words.addAll(new CsvSet("generated, optimizer_costs, sql_buffer_result, sql_cache, sql_no_cache, stored, virtual"));

        return words;
    }
}
