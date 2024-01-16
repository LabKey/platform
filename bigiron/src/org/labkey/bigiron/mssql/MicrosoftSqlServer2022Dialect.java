package org.labkey.bigiron.mssql;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class MicrosoftSqlServer2022Dialect extends MicrosoftSqlServer2019Dialect
{
    @Override
    protected @NotNull Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.remove("within");

        return words;
    }
}
