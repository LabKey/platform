/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.core;

import org.apache.commons.lang.StringUtils;
import org.labkey.api.data.dialect.StandardDialectStringHandler;

import java.util.regex.Pattern;

/*
* User: adam
* Date: Aug 13, 2011
* Time: 4:10:10 PM
*/
public class PostgreSqlNonConformingStringHandler extends StandardDialectStringHandler
{
    // Supports '' and backslash escaping.  Previous pattern '([^\\\\']|('')|(\\\\.))*' would explode with long string literals.
    private static final Pattern _stringLiteralPattern = Pattern.compile("'[^\\\\']*(?:(?:''[^'\\\\]*)*(?:\\\\.[^'\\\\]*)*)*'");

    @Override
    public Pattern getStringLiteralPattern()
    {
        return _stringLiteralPattern;
    }

    @Override
    public String quoteStringLiteral(String str)
    {
        return "'" + StringUtils.replace(StringUtils.replace(str, "\\", "\\\\"), "'", "''") + "'";
    }
}
