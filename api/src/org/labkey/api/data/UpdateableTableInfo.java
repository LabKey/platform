/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.security.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * Extensions to {@link TableInfo} to make it support inserts/updates/deletes via the ETL engine and other
 * data modification approaches.
 * User: matthewb
 * Date: Nov 15, 2010
 */
public interface UpdateableTableInfo extends TableInfo
{
    enum ObjectUriType
    {
        schemaColumn,       // copy from a column in the root table
        generateUrn        // call GUID.makeURN()
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
     * e.g. for list if the key column is named "Name" then this map should have ("key" -> "Name")
     *
     * TODO this should probably be handled by a transform step, and removed from this api
     */
    @Nullable
    CaseInsensitiveHashMap<String> remapSchemaColumns();

    /**
     * if table.getDomain() is not null, you can use this method to skip properties that should not be
     * persisted in the exp.ObjectProperties. This happens when there is a PropertyDescriptor that actually
     * describes a column in the base-table (e.g. Key column in list)
     */
    @Nullable
    CaseInsensitiveHashSet skipProperties();


    /**
     * Insert multiple rows into the database
     *
     * The default behavior will just match up columns in the data iterator to parameters from insertStatement().
     * Conversion, Validation, and default values should be setup before calling this method.  Most special cases
     * can be handled by the default implementation which will look at skipProperties() and remapSchemaColumns().
     * 
     * If there are idiosyncratic column mapping problems (hidden columns?) etc, you can override this method.
     * Try to use generic code paths to get the input data iterator to match the parameters when possible.
     *
     * This method is _NOT_ usually called directly. See TableInfo.getUpdateService(), and StandardDataIteratorBuilder.
     */
    DataIteratorBuilder persistRows(DataIteratorBuilder data, DataIteratorContext context);

    /** persist one row in the database
     *
     * Does not touch files/attachments
     * Does not check security
     * Does not do validation (beyond what the database will do)
     *
     * The ParameterMap (better name?) should act pretty much like a PreparedStatement with
     * names parameters (rather than ordinal parameters).  Might actually execute java code
     * but that shouldn't make a difference to the caller.
     */
    Parameter.ParameterMap insertStatement(Connection conn, User user) throws SQLException;
    Parameter.ParameterMap updateStatement(Connection conn, User user, Set<String> columns) throws SQLException;
    Parameter.ParameterMap deleteStatement(Connection conn) throws SQLException;
}
