package org.labkey.bigiron.mysql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;

import java.util.Set;

/**
 * User: adam
 * Date: 3/19/2014
 * Time: 7:49 AM
 */
public class MySql56Dialect extends MySqlDialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        // Add new reserved words in MySQL 5.6; see http://dev.mysql.com/doc/refman/5.6/en/reserved-words.html
        Set<String> words = super.getReservedWords();

        // NOTE: ONE_SHOT, SQL_AFTER_GTIDS, and SQL_BEFORE_GTIDS are listed as reserved words in the docs, but don't
        // seem to behave like reserved words... so leave them out for now.
        words.addAll(new CsvSet("get, io_after_gtids, io_before_gtids, master_bind, partition"));

        return words;
    }
}
