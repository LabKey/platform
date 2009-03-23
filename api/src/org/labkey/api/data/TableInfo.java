/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.util.NamedObjectList;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.security.User;
import org.apache.beehive.netui.pageflow.Forward;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.Set;
import java.util.Map;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Apr 27, 2006
 * Time: 11:29:43 AM
 */
public interface TableInfo
{
    public static int TABLE_TYPE_NOT_IN_DB = 0;
    public static int TABLE_TYPE_TABLE = 1;
    public static int TABLE_TYPE_VIEW = 2;


    String getName();

    /** simple name that can be used in from clause.  e.g. "Issues.Issues", may return null */
    @Nullable
    String getSelectName();

    /** SQL representign this table, e.g. "SELECT * FROM Issues.Issues WHERE Container='...'" */
    @NotNull
    SQLFragment getFromSQL();

    DbSchema getSchema();

    /** getSchema().getSqlDialect() */
    SqlDialect getSqlDialect();

    List<String> getPkColumnNames();

    List<ColumnInfo> getPkColumns();

    ColumnInfo getVersionColumn();

    String getVersionColumnName();

    String getTitleColumn();

    int getTableType();

    NamedObjectList getSelectList();

    /** getSelectList().get(pk) */
    String getRowTitle(Object pk) throws SQLException;

    ColumnInfo getColumn(String colName);

    List<ColumnInfo> getColumns();

    List<ColumnInfo> getUserEditableColumns();

    List<ColumnInfo> getColumns(String colNames);

    List<ColumnInfo> getColumns(String... colNameArray);

    Set<String> getColumnNameSet();

    List<FieldKey> getDefaultVisibleColumns();

    void setDefaultVisibleColumns(Iterable<FieldKey> keys);

    String getSequence();

    /**
     * Return the details URL expression for a particular record.
     * The column map passed in maps from a name of a column in this table
     * to the actual ColumnInfo used to generate the SQL for the SELECT
     * statement.  (e.g. if this is the Protocol table, the column "LSID" might
     * actually be represented by the "ProtocolLSID" column from the ProtocolApplication table).
     */
    StringExpressionFactory.StringExpression getDetailsURL(Map<String, ColumnInfo> columns);

    boolean hasPermission(User user, int perm);

    ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception;

    Forward insert(User user, QueryUpdateForm form) throws Exception;

    Forward update(User user, QueryUpdateForm form) throws Exception;

    /**
     * Return the method of a given name.  Methods are accessible via the QueryModule's query
     * language.  Most tables do not have methods. 
     */
    MethodInfo getMethod(String name);

    public boolean isPublic();

    public String getPublicName();

    public String getPublicSchemaName();

    public boolean needsContainerClauseAdded();

    public ContainerFilter getContainerFilter();

    public boolean isMetadataOverrideable();
}
