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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;

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
     * Get Domain associated with this TableInfo if any.
     * @return
     */
    @Nullable
    public Domain getDomain();

    /**
     * Get DomainKind associated with this TableInfo if any.
     * Domain may or may not exist even if DomainKind is available.
     * @return
     */
    @Nullable
    public DomainKind getDomainKind();

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
     * Executes any trigger scripts for this table.
     *
     * The trigger should be called once before and once after an entire set of rows for each of the
     * INSERT, UPDATE, DELETE trigger types.  A trigger script may set up data structures to be used
     * during validation.  In particular, the trigger script might want to do a query to populate a set of
     * legal values.
     * <p>
     * The <code>errors</code> parameter holds validation error messages for the entire row set.
     * If errors are created during the row level trigger script, they should be added as nested ValidationExceptions.
     * The ValidationException will be thrown after executing the trigger scripts if it contains any errors.
     * <p>
     * Example usage:
     * <pre>
     *   ValidationException errors = new ValidationException();
     *   getQueryTable().fireBatchTrigger(TableInfo.TriggerType.UPDATE, true, errors);
     *
     *   List&lt;Map&lt;String, Object>> result = new ArrayList&lt;Map&lt;String, Object>>(rows.size());
     *   for (int i = 0; i &lt; rows.size(); i++)
     *   {
     *       try
     *       {
     *           Map<String, Object> row = rows.get(i);
     *           Map<String, Object> oldRow = getRow( ... );
     *           if (oldRow == null)
     *               throw new NotFoundException("The existing row was not found.");
     *
     *           getQueryTable().fireRowTrigger(TableInfo.TriggerType.UPDATE, true, i, row, oldRow);
     *           Map<String, Object> updatedRow = updateRow(user, container, row, oldRow);
     *           if (updatedRow == null)
     *               continue;
     *
     *           getQueryTable().fireRowTrigger(TableInfo.TriggerType.UPDATE, false, i, updatedRow, oldRow);
     *           result.add(updatedRow);
     *       }
     *       catch (ValidationException vex)
     *       {
     *           errors.addNested(vex);
     *       }
     *   }
     *
     *   // Firing the after batch trigger will throw a ValidationException if
     *   // any errors were generated during the row triggers or the during after batch trigger.
     *   getQueryTable().fireBatchTrigger(TableInfo.TriggerType.UPDATE, false, errors);
     * </pre>
     *
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param errors Any errors created by the validation script will be added to the errors collection.
     * @throws ValidationException if the trigger function returns false or the errors map isn't empty.
     */
    public void fireBatchTrigger(TriggerType type, boolean before, ValidationException errors)
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
     *   <dd><ul>
     *       <li>before: <code>newRow</code> contains the row values to be inserted, <code>oldRow</code> is null.
     *       <li>after: <code>newRow</code> contains the inserted row values, <code>oldRow</code> is null.
     *       </li>
     *
     *   <dt><code>UPDATE</code>:
     *   <dd><ul>
     *       <li>before: <code>newRow</code> contains the row values to be updated, <code>oldRow</code> contains the previous version of the row.
     *       <li>after: <code>newRow</code> contains the updated row values, <code>oldRow</code> contains the previous version of the row.
     *       </ul>
     *
     *   <dt><code>DELETE</code>:
     *   <dd><ul>
     *       <li>before: <code>oldRow</code> contains the previous version of the row.
     *       <li>after: <code>newRow</code> is null, <code>oldRow</code> contains the previous version of the row.
     *       </li>
     * </dl>
     *
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param oldRow The previous row for UPDATE and DELETE
     * @param newRow The new row for INSERT and UPDATE.
     * @throws ValidationException if the trigger function returns false or the errors map isn't empty.
     */
    public void fireRowTrigger(TriggerType type, boolean before, int rowNumber,
                                  Map<String, Object> newRow, Map<String, Object> oldRow)
            throws ValidationException;

}
