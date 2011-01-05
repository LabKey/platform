/*
 * Copyright (c) 2010-2010 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.security.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: Nov 15, 2010
 * Time: 6:02:12 PM
 */
public interface UpdateableTableInfo
{
    public enum ObjectUriType
    {
        schemaColumn,   // copy from a column in the root table
        generateUrn     // call GUID.makeURN()
    }

    /*
     * SchemaTableInfo contain enough information to generate insert/update/delete SQL
     *
     * This metadata is meant to provide enough metadata to generate SQL for any TableInfo
     * that makes sense.
     */

    /* these are capability checks not permission checks */
    boolean insertSupported();
    boolean updateSupported();
    boolean deleteSupported();

    /*
     * all updatable tables have a 'root' SchemaTableInfo
     * For list it might be exp.IndexInteger or exp.IndexVarchar
     *
     * may not be null if any of insert/update/delete are supported
     */
    TableInfo getSchemaTableInfo();

    // cf getDomainKind()
    // cf getDomain()

    ObjectUriType getObjectUriType();

    // name of the column from getSchemaTableInfo() that joins to exp.object.objecturi
    @Nullable
    String getObjectURIColumnName();

    // name of column that contains exp.object.objectid
    @Nullable
    String getObjectIdColumnName();

    /**
     * Some columns in the SchemaTableInfo may be aliased in the QueryTableInfo.  This map describes the mapping.
     *
     * physical column -> query column
     *
     * e.g. for list if the key column is named "Name" then this map should have ("key" -> "Name")
     */
    @Nullable
    CaseInsensitiveHashMap<String> remapSchemaColumns();


    Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException;
}
