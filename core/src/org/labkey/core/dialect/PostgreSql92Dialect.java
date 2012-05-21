package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * User: adam
 * Date: 5/21/12
 * Time: 8:52 AM
 */
public class PostgreSql92Dialect extends PostgreSql91Dialect
{
    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.add("collation");

        return words;
    }
 }
