/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.data.dialect;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.SqlScanner;

import java.util.Map;

public class MutatingSqlDetector
{
    private static final Logger LOG = LogManager.getLogger(MutatingSqlDetector.class);

    private final String _sql;
    private final StringBuilder _firstWord = new StringBuilder();

    private State _state = State.SKIP_INITIAL_WHITESPACE;

    public MutatingSqlDetector(String sql)
    {
        _sql = sql;
    }

    private enum State
    {
        SKIP_INITIAL_WHITESPACE
        {
            @Override
            State getNextState(char c, StringBuilder firstWord, String sql)
            {
                if (Character.isWhitespace(c))
                    return this;

                firstWord.append(c);
                return READ_KEYWORD;
            }
        },
        READ_KEYWORD
        {
            @Override
            State getNextState(char c, StringBuilder firstWord, String sql)
            {
                if (Character.isWhitespace(c) || ';' == c)
                {
                    // Evaluate first keyword
                    String word = firstWord.toString();
                    Boolean mutatingWord = WORD_MUTATING_MAP.get(word);

                    // Special case for stored procedure with return value
                    if (mutatingWord == null && sql.startsWith("? = CALL"))
                        mutatingWord = Boolean.TRUE;

                    if (null == mutatingWord)
                        LOG.warn("Unrecognized keyword: " + word + " for SQL: " + sql);

                    if (Boolean.TRUE == mutatingWord)
                        return DONE;

                    // Unrecognized or not a mutating keyword - clear the first word and skip to the next statement (if present)
                    firstWord.setLength(0);
                    return SKIP_TO_NEXT_STATEMENT;
                }

                firstWord.append(c);
                return this;
            }
        },
        SKIP_TO_NEXT_STATEMENT
        {
            @Override
            State getNextState(char c, StringBuilder firstWord, String sql)
            {
                return ';' == c ? SKIP_INITIAL_WHITESPACE : this;
            }
        },
        DONE
        {
            @Override
            State getNextState(char c, StringBuilder firstWord, String sql)
            {
                throw new IllegalStateException("Shouldn't be calling getNextState()");
            }
        };

        abstract State getNextState(char c, StringBuilder firstWord, String sql);
    }

    private static final Map<String, Boolean> WORD_MUTATING_MAP = new CaseInsensitiveHashMap<>();

    static
    {
        WORD_MUTATING_MAP.putAll(Map.of(
            "ALTER", true,
            "CALL", true,
            "CLUSTER", true,
            "CREATE", true,
            "DELETE", true,
            "DROP", true,
            "INSERT", true,
            "TRUNCATE", true,
            "UPDATE", true
        ));

        WORD_MUTATING_MAP.putAll(Map.of(
            ";", false,  // Handle standalone semicolons (whitespace on both sides)
            "BEGIN", false,
            "DO", false,
            "END", false,
            "SELECT", false,
            "WITH", false
        ));

        // Needed for PostgreSQL
        WORD_MUTATING_MAP.putAll(Map.of(
            "ANALYZE", true,   // Typically executed after UPDATE, CREATE INDEX, et al
            "VACUUM", true,    // VACUUM is mutating
            "{call", true      // Execute a stored procedure, which is likely to be mutating
        ));

        // Needed for SQL Server
        WORD_MUTATING_MAP.putAll(Map.of(
            "DECLARE", false,
            "EXEC", true,
            "EXECUTE", true,
            "IF", true,   // Typically IF EXISTS followed by mutating action
            "SET", true,
            "sp_rename", true
        ));
    }

    public boolean isMutating()
    {
        // Extract the first word, ignoring all leading comments and whitespace
        SqlScanner scanner = new SqlScanner(_sql);
        scanner.scan(0, new SqlScanner.Handler()
        {
            @Override
            public boolean character(char c, int index)
            {
                _state = _state.getNextState(c, _firstWord, _sql);
                return State.DONE != _state;
            }
        });

        return _firstWord.length() > 0;
    }

    public String getFirstWord()
    {
        return _firstWord.toString();
    }
}
