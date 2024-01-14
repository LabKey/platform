package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class PostgreSql_16_Dialect extends PostgreSql_15_Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.add("system_user");
        words.remove("string");

        return words;
    }
}
