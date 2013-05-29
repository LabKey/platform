/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.query.reports.getdata;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.UserSchema;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * User: jeckels
 * Date: 5/15/13
 */
public interface QueryReportDataSource extends ReportDataSource
{
    public QueryDefinition getQueryDefinition();

    public String getLabKeySQL();

    public Map<FieldKey, ColumnInfo> getColumnMap(Collection<FieldKey> requiredInputs);

    @NotNull
    public UserSchema getSchema();

    public static class DummyQueryDataSource implements QueryReportDataSource
    {
        private static final String DUMMY_SCHEMA_TABLE = "mySchema.myTable";

        @Override
        public QueryDefinition getQueryDefinition()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getLabKeySQL()
        {
            return DUMMY_SCHEMA_TABLE;
        }

        @NotNull
        @Override
        public UserSchema getSchema()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<FieldKey, ColumnInfo> getColumnMap(Collection<FieldKey> requiredInputs)
        {
            return Collections.emptyMap();
        }
    }
}
