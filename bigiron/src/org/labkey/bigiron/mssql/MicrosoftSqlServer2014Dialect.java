/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.bigiron.mssql;

import org.labkey.api.data.dialect.TableResolver;

/**
 * User: adam
 * Date: 4/10/2014
 * Time: 6:59 PM
 */
public class MicrosoftSqlServer2014Dialect extends MicrosoftSqlServer2012Dialect
{
    public MicrosoftSqlServer2014Dialect(TableResolver tableResolver)
    {
        super(tableResolver);
    }
}
