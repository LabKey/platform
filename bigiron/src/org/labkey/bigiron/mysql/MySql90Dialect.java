package org.labkey.bigiron.mysql;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.collections.CsvSet;

import java.util.Set;

public class MySql90Dialect extends MySql80Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        // Update reserved words for MySQL 9.0
        Set<String> words = super.getReservedWords();

        words.removeAll(new CsvSet("master_bind, master_ssl_verify_server_cert"));
        words.addAll(new CsvSet("parallel, qualify, tablesample"));

        return words;
    }
}
