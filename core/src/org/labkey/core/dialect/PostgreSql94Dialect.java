package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * User: adam
 * Date: 8/5/2014
 * Time: 10:49 PM
 */
public class PostgreSql94Dialect extends PostgreSql93Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.remove("over");

        return words;
    }
}
