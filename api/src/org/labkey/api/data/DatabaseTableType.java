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

package org.labkey.api.data;

// Database table types that we pass into JDBC metadata method getTables(). Some databases support a subset of these
// types (e.g., SQL Server doesn't support materialized views), but getTables() just ignores types it doesn't know.
public enum DatabaseTableType
{
    TABLE,
    VIEW,
    MATERIALIZED_VIEW
    {
        @Override
        public String getJdbcTypeName()
        {
            return "MATERIALIZED VIEW";
        }
    },
    NOT_IN_DB;

    public String getJdbcTypeName()
    {
        return name();
    }
}
