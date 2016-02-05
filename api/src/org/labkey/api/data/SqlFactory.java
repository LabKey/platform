/*
 * Copyright (c) 2012-2016 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * User: adam
 * Date: 1/22/12
 * Time: 2:33 PM
 */
public interface SqlFactory
{
    /** Returns the SQL to execute. If null, execution is skipped and handler is called with null parameters, allowing
        it to return its default value, e.g., empty map, empty array, 0 count, etc. */
    @Nullable SQLFragment getSql();

    /** Returns the value to set on Statement.maxRows(). Null in most cases; only needed on SAS (which has no LIMIT SQL syntax) */
    @Nullable Integer getStatementMaxRows();

    void processResultSet(ResultSet rs) throws SQLException;
}
