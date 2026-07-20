/*
 * Copyright (c) 2011-2026 LabKey Corporation
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

import org.apache.commons.lang3.Strings;

// Adds support for backslash escaping in string literals
public class BackslashEscapingStringHandler extends StandardDialectStringHandler
{
    @Override
    public String quoteStringLiteral(String str)
    {
        return "'" + Strings.CS.replace(Strings.CS.replace(str, "\\", "\\\\"), "'", "''") + "'";
    }


    @Override
    protected int findEndOfStringLiteral(CharSequence sql, int current)
    {
        boolean skipNext = false;

        while (current < sql.length())
        {
            char c = sql.charAt(current++);

            if (skipNext)
            {
                skipNext = false;
            }
            else
            {
                if (c == '\\')
                {
                    skipNext = true;
                }
                else if (c == '\'')
                {
                    break;
                }
            }
        }

        return current;
    }
}
