/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.util;

public class SqlUtil
{
    public static String extractSql(String text)
    {
        if (text.startsWith("SELECT "))
            return text;
        if (text.startsWith("WITH ") && text.contains("SELECT "))
            return text;
        if (text.startsWith("PARAMETERS ") && text.contains("SELECT "))
            return text;
        var sql = text.indexOf("```sql\n");
        if (sql >= 0)
        {
            var end = text.indexOf("```", sql+7);
            if (end >= 0)
                return text.substring(sql+7,end);
        }
        return null;
    }
}
