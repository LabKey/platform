package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PostgreSql_15_Dialect extends PostgreSql_14_Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.add("string");

        return words;
    }
}
