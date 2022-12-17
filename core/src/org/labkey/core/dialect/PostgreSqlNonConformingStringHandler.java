/*
 * Copyright (c) 2011-2013 LabKey Corporation
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

package org.labkey.core.dialect;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.dialect.StandardDialectStringHandler;

/*
* User: adam
* Date: Aug 13, 2011
* Time: 4:10:10 PM
*/

// Adds support for backslash escaping in string literals
public class PostgreSqlNonConformingStringHandler extends StandardDialectStringHandler
{
    // TODO: I don't think this is necessary... non-conforming strings and backslash_quote settings still allow '' for quote escaping;
    // they don't require \'
    @Override
    public String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(StringUtils.replace(str, "\\", "\\\\"), "'", "''") + "'";
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
                    continue;
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
