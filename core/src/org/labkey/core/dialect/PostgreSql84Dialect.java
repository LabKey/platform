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

/*
* User: adam
* Date: May 22, 2011
* Time: 9:27:56 PM
*/
public class PostgreSql84Dialect extends PostgreSql83Dialect
{
    @Override
    public String getAdminWarningMessage()
    {
        return null;
    }

    // 8.4 added a built-in array_agg() aggregate function. Once we remove support for 8.3 we can drop our custom aggregate function.
    // TODO: A great idea... but we need to make this work on PostgreSQL DOMAINS (e.g., LSID, EntityId, etc.) or avoid them
    @Override
    protected String getArrayAggregateFunctionName()
    {
        return "array_agg";
    }
}
