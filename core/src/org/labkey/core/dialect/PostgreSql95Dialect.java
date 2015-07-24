package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Created by adam on 7/24/2015.
 */
public class PostgreSql95Dialect extends PostgreSql94Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.add("tablesample");

        return words;
    }
}
