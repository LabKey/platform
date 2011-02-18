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

package org.labkey.api.util;

import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.Parameter;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;

/**
 * User: kevink
 */
public final class JdbcUtil
{
    private JdbcUtil() { }

    /**
     * Drop the JDBC parameters into the SQL string so that it can be executed directly in a database tool
     * for debugging purposes.
     */
    public static String format(String sql, List<Object> params)
    {
        if (null != params && !params.isEmpty())
        {
            // Question marks in comments and string literals aren't supported right now - so we only do substitution
            // if the number of parameter markers equals the number of parameters

            // Pad with spaces to prevent question marks from being the first or last entries, which would confuse
            // our split() count
            String[] pieces = (" " + sql + " ").split("\\?");
            if (pieces.length == params.size() + 1)
            {
                StringBuilder result = new StringBuilder(pieces[0]);
                for (int i = 0; i < params.size(); i++)
                {
                    Object o = params.get(i);
                    Object value = o;
                    try { value = Parameter.getValueToBind(o); } catch (SQLException x) {;}
                    if (value == null)
                    {
                        result.append("NULL");
                    }
                    else if (value instanceof String)
                    {
                        result.append("'");
                        result.append(((String)value).replace("'", "''"));
                        result.append("'");
                    }
                    else if (value instanceof Date)
                    {
                        result.append("'");
                        result.append(DateUtil.formatDateTime((Date)value));
                        result.append("'");
                    }
                    else if (value instanceof Boolean)
                    {
                        result.append(CoreSchema.getInstance().getSqlDialect().getBooleanLiteral(((Boolean)value).booleanValue()));
                    }
                    else
                    {
                        result.append(value.toString());
                    }
                    result.append(pieces[i + 1]);
                }
                return result.toString();
            }
        }
        return sql;
    }

}
