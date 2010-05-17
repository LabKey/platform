/*
 * Copyright (c) 2006-2010 LabKey Corporation
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

import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
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

    /** SQL representing this table, e.g. "SELECT * FROM Issues.Issues WHERE Container='...'" */
    @NotNull
    SQLFragment getFromSQL();

    DbSchema getSchema();

    /** getSchema().getSqlDialect() */
    SqlDialect getSqlDialect();

    List<String> getPkColumnNames();

    List<ColumnInfo> getPkColumns();

    ColumnInfo getVersionColumn();

    String getVersionColumnName();

    /** @return the default display value for this table if it's the target of a foreign key */
    String getTitleColumn();

    int getTableType();

    /** Get select list for primary column to title column. */
    NamedObjectList getSelectList();

    /** Get select list for named (hopefully unique!) column to title column. */
    NamedObjectList getSelectList(String columnName);

    /** getSelectList().get(pk) */
    String getRowTitle(Object pk) throws SQLException;

    ColumnInfo getColumn(String colName);

    List<ColumnInfo> getColumns();

    List<ColumnInfo> getUserEditableColumns();

    /** @param colNames comma separated column names */
    List<ColumnInfo> getColumns(String colNames);

    List<ColumnInfo> getColumns(String... colNameArray);

    Set<String> getColumnNameSet();

    List<FieldKey> getDefaultVisibleColumns();

    void setDefaultVisibleColumns(Iterable<FieldKey> keys);

    ButtonBarConfig getButtonBarConfig();

    String getSequence();

    /**
     * Return the default query grid view URL for the table or null.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#executeQuery} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getGridURL(Container container);

    /**
     * Return the insert URL expression for the table or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default insert view will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#insertQueryRow} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getInsertURL(Container container);

    /**
     * Return the update URL expression for a particular record or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default update view will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#updateQueryRow} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    StringExpression getUpdateURL(Set<FieldKey> columns, Container container);

    /**
     * Return the details URL expression for a particular record or null.
     * The column map passed in maps from a name of a column in this table
     * to the actual ColumnInfo used to generate the SQL for the SELECT
     * statement.  (e.g. if this is the Protocol table, the column "LSID" might
     * actually be represented by the "ProtocolLSID" column from the ProtocolApplication table).
     */
    StringExpression getDetailsURL(Set<FieldKey> columns, Container container);

    boolean hasPermission(User user, Class<? extends Permission> perm);

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

    public ColumnInfo getLookupColumn(ColumnInfo parent, String name);

    public int getCacheSize();

    public String getDescription();

    /**
     * Returns a QueryUpdateService implementation for this TableInfo,
     * or null if the table/query is not updatable.
     * @return A QueryUpdateService implementation for this table/query or null.
     */
    @Nullable
    QueryUpdateService getUpdateService();

    public enum TriggerType
    {
        INSERT, UPDATE, DELETE, SELECT;

        public String getMethodName()
        {
            String name = name();
            return name.substring(0, 1) + name.toLowerCase().substring(1, name.length());
        }
    }

    /**
     * Fire trigger for a set of rows.
     * <p>
     * The trigger is called once before and once after an entire set of rows for each of the
     * INSERT, UPDATE, DELETE trigger types.  A trigger script may set up data structures to be used
     * during validation.  In particular, the trigger script might want to do a query to populate a set of
     * legal values.
     * <b>
     * When <code>before</code> is true, the <code>errors</code> list is empty and may be added to.  Each
     * error map in the list maps from field name to error message.  When <code>before</code> is false,
     * the <code>errors</code> list will be populated with any errors created during the execution of
     * the row level trigger.  A '_rowNumber' key may be added to the error map by the trigger script
     * or will be added during {@link TableInfo#fireRowTrigger(TriggerType, boolean, Map, Map, Map)}}.
     *
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param allErrors A list of errors maps, mapping field name to error message.
     *                  If before is false, the errors created during {@link #fireRowTrigger()} will be
     *                  available in the allErrors list and include a '_rowNumber' key.
     * @throws ValidationException Throws a ValidationException if the trigger function returns false or the errors map isn't empty.
     */
    public void fireBatchTrigger(TriggerType type, boolean before, int rowCount, List<Map<String, String>> allErrors)
        throws ValidationException;

    /**
     * Fire trigger for a single row.
     * <p>
     * The trigger is called once before and once after each row for each of the INSERT, UPDATE, DELETE
     * trigger types.
     * <p>
     * The following table describes the parameters for each of the trigger types:
     * <dl>
     *   <dt><code>INSERT</code>:
     *   <dd>When <code>before</code> is true, <code>newRow</code> contains the row values to be inserted, <code>oldRow</code> is null.
     *       When <code>before</code> is false, <code>newRow</code> contains the inserted row values, <code>oldRow</code> is null.
     *
     *   <dt><code>UPDATE</code>:
     *   <dd>When <code>before</code> is true, <code>newRow</code> contains the row values to be updated, <code>oldRow</code> contains the previous version of the row.
     *       When <code>before</code> is false, <code>newRow</code> contains the updated row values, <code>oldRow</code> contains the previous version of the row.
     *
     *   <dt><code>DELETE</code>:
     *   <dd>When <code>before</code> is true, <code>newRow</code> is null, <code>oldRow</code> contains the previous version of the row.
     *       When <code>before</code> is false, <code>newRow</code> is null, <code>oldRow</code> contains the previous version of the row.
     * </dl>
     *
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param oldRow The previous row for UPDATE and DELETE
     * @param newRow The new row for INSERT and UPDATE.
     * @param allErrors A list of errors maps, mapping field name to error message.  Any errors created by the validation script will be added to the allErrors list.
     * @return true if the trigger succeeded, false if the trigger function returns false or the errors map isn't empty.
     */
    public boolean fireRowTrigger(TriggerType type, boolean before, int rowNumber,
                                  Map<String, Object> newRow, Map<String, Object> oldRow,
                                  List<Map<String, String>> allErrors);

}
