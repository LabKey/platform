/*
 * Copyright (c) 2010-2012 LabKey Corporation
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
package org.labkey.api.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.query.*;
import org.labkey.api.query.SimpleUserSchema.SimpleTable;

/**
 * User: kevink
 */
public class SimpleQueryUpdateService extends DefaultQueryUpdateService
{
    public SimpleQueryUpdateService(final SimpleTable queryTable, TableInfo dbTable)
    {
        super(queryTable, dbTable, new DomainUpdateHelper() {
            @Override
            public Domain getDomain()
            {
                return queryTable.getDomain();
            }

            @Override
            public ColumnInfo getObjectUriColumn()
            {
                return queryTable.getObjectUriColumn();
            }

            @Override
            public String createObjectURI()
            {
                return queryTable.createPropertyURI();
            }

            @Override
            public Iterable<PropertyColumn> getPropertyColumns()
            {
                return queryTable.getPropertyColumns();
            }
        });
    }

    @Override
    protected SimpleTable getQueryTable()
    {
        return (SimpleTable)super.getQueryTable();
    }
}
