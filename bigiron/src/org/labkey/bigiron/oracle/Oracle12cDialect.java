/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.bigiron.oracle;

import org.labkey.api.data.SQLFragment;

/**
 * Created by Josh on 11/25/2015.
 */
public class Oracle12cDialect extends Oracle11gR2Dialect
{
    // Nothing Oracle 12c specific yet, but this is the place to add extra keywords and such

    // TODO: Could implement limitRows() methods via OFFSET / FETCH (see LimitRowsSqlGenerator.limitRows()), but we need
    // a test instance of Oracle 12c.
    @Override
    public SQLFragment limitRows(SQLFragment select, SQLFragment from, SQLFragment filter, String order, String groupBy, int maxRows, long offset)
    {
        return super.limitRows(select, from, filter, order, groupBy, maxRows, offset);
    }
}
