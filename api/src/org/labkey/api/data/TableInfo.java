/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.audit.AuditHandler;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.AggregateRowConfig;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SchemaTreeNode;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.HasPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ContainerContext;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.queryCustomView.FilterType;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Representation of a queryable unit in the database. Might be backed by a "physical" table, a database VIEW,
 * or an arbitrary generated chunk of SELECT SQL.
 */
public interface TableInfo extends TableDescription, HasPermission, SchemaTreeNode
{
    /**
     * This is used to report problems encountered during construction of the TableInfo.  These would generally be
     * reported in the schema browser or editor.  Failing to construct a TableInfo generally breaks the UI badly, so we
     * want to be able to return a usable TableInfo whenever possible and then report problems here if they the issue
     * is correctable.
     *<br>
     * See also ValidateQueryAction.
     *<br>
     * @return warnings collected during TableInfo construction.
     */
    default Collection<QueryParseException> getWarnings()
    {
        return List.of();
    }

    /** Get title, falling back to the name if title is null **/
    String getTitle();

    /** Get title field, whether null or not **/
    @Nullable
    String getTitleField();

    /**
     * Use getSQLName() instead for generating SQL.
     * getSelectName() can still be used for error messages.
     */
    @Nullable
    default String getSelectName()
    {
        var sqlf = getSQLName();
        return null == sqlf ? null : sqlf.getSQL();
    }

    /**
     * Return a schema.name that can be used directly in SQL statement
     * Use only for tables known to be real database tables, usually
     * for INSERT/UPDATE/DELETE. For SELECT use getFromSQL(alias).
     */
    @Nullable
    default SQLFragment getSQLName()
    {
        return null;
    }

    @Nullable
    String getMetaDataName();

    /**
     * SQL representing this table, e.g.
     *     "Issues.Issues <alias>"
     *     "(SELECT * FROM Issues.Issues WHERE Container='...') <alias>"
     **/
    @NotNull
    SQLFragment getFromSQL(String alias);

    /* For most tables this is the same as getFromSQL().
     * However, for some tables that want to help optimize generated SQL
     * we can ask for the columns we need to be accessible.
     *
     * Only columns returned by getColumn() or resolveColumn() should be handed in (no lookups)
     *
     * This is really intended for QueryTable
     */
    SQLFragment getFromSQL(String alias, Set<FieldKey> cols);

    DbSchema getSchema();

    @Nullable
    UserSchema getUserSchema();

    /** getSchema().getSqlDialect() */
    SqlDialect getSqlDialect();

    @NotNull List<ColumnInfo> getPkColumns();

    /** Gets all of the constraints that guarantee uniqueness in the underlying table. This includes both PRIMARY KEY and UNIQUE constraints */
    @NotNull
    Map<String, Pair<IndexType, List<ColumnInfo>>> getUniqueIndices();

    /** Gets all of the indices from the underlying table. This includes PRIMARY KEY and UNIQUE constraints, as well as non-unique INDEX */
    @NotNull
    Map<String, Pair<IndexType, List<ColumnInfo>>> getAllIndices();

    class _DoNothingAuditHandler implements AuditHandler
    {
        @Override
        public void addSummaryAuditEvent(User user, Container c, TableInfo table, QueryService.AuditAction action, Integer dataRowCount, @Nullable AuditBehaviorType auditBehaviorType, @Nullable String userComment)
        {
        }

        @Override
        public void addAuditEvent(User user, Container c, TableInfo table, @Nullable AuditBehaviorType auditType, @Nullable String userComment, QueryService.AuditAction action, List<Map<String, Object>> rows, @Nullable List<Map<String, Object>> updatedRows, boolean useTransactionAuditCache)
        {
        }
    }

    default AuditHandler getAuditHandler(@Nullable AuditBehaviorType auditBehaviorOverride)
    {
        if (!supportsAuditTracking() || auditBehaviorOverride == AuditBehaviorType.NONE || (auditBehaviorOverride == null && getAuditBehavior() == AuditBehaviorType.NONE))
            return new _DoNothingAuditHandler();
        return QueryService.get().getDefaultAuditHandler();
    }

    enum IndexType
    {
        Primary(org.labkey.data.xml.IndexType.Type.PRIMARY, true),
        Unique(org.labkey.data.xml.IndexType.Type.UNIQUE, true),
        NonUnique(org.labkey.data.xml.IndexType.Type.NON_UNIQUE, false);

        private final org.labkey.data.xml.IndexType.Type.Enum _xmlIndexType;
        private final boolean _unique;

        IndexType(org.labkey.data.xml.IndexType.Type.Enum xmlIndexType, boolean unique)
        {
            _xmlIndexType = xmlIndexType;
            _unique = unique;
        }

        public org.labkey.data.xml.IndexType.Type.Enum getXmlIndexType()
        {
            return _xmlIndexType;
        }

        public boolean isUnique()
        {
            return _unique;
        }

        public static IndexType getForXmlIndexType(org.labkey.data.xml.IndexType.Type.Enum xmlIndexType)
        {
            for (IndexType indexType : IndexType.values())
            {
                if(indexType.getXmlIndexType().equals(xmlIndexType)){
                    return indexType;
                }
            }
            throw new EnumConstantNotPresentException(IndexType.class, xmlIndexType.toString());
        }
    }

    /** Get a list of columns that specifies a unique key, may return the same result as getPKColumns()
     * However, whereas getPkColumns() will usually return a 'short' pk, such as "rowid" or "lsid", this
     * should return a more verbose AK that is most semantically useful.
     *
     * For instance for a flow result it might return (container, run, sample, stim, populationName) rather
     * than (container, analysisId)
     *
     * NOTE: unlike PK, this does not guarantee that columns are NON-NULL
     * NOTE: Postgres does not consider rows with NULL values to be "equal" so NULLs may be repeated!
     */
    @NotNull List<ColumnInfo> getAlternateKeyColumns();

    @NotNull default Set<String> getAltKeysForUpdate()
    {
       return Collections.emptySet();
    }

    @Nullable default Set<String> getDisabledSystemFields()
    {
        return Collections.emptySet();
    }

    /**
     * @return a Map<columnName, alternativeColumnName> of additionally required fields during import.
     * Example usage is specifying the set of required lineage columns during sample/dataclass data creation.
     */
    @NotNull default Map<String, String> getAdditionalRequiredInsertColumns()
    {
        return Collections.emptyMap();
    }

    ColumnInfo getVersionColumn();

    String getVersionColumnName();

    boolean hasDefaultTitleColumn();

    DatabaseTableType getTableType();

    /**
     * Get select list for named (hopefully unique!) column to title column, including filter on table.
     * If maxRows is exceeded, the NamedObjectList will be marked as incomplete.
     * When maxRows is null a default maxRows will be used. To select all rows, set maxRows to {@link Table#ALL_ROWS}.
     * @see NamedObjectList#isComplete()
     */
    @NotNull NamedObjectList getSelectList(String columnName, List<FilterType> filters, Integer maxRows, String titleColumn);

    ColumnInfo getColumn(@NotNull String colName);

    // same as getColumn(colName.getName()), does not need to support lookup columns
    ColumnInfo getColumn(@NotNull FieldKey colName);

    List<ColumnInfo> getColumns();

    List<ColumnInfo> getUserEditableColumns();

    /** @param colNames comma separated column names */
    List<ColumnInfo> getColumns(String colNames);

    List<ColumnInfo> getColumns(String... colNameArray);

    Set<String> getColumnNameSet();

    default String getDbSequenceName(String columnName)
    {
        return (this.getSchema().getName() + ":" + this.getName() + ":" + columnName).toLowerCase();
    }

    /**
     * Return a list of ColumnInfos that make up the extended set of
     * columns that could be considered a part of this table by default.
     * For the majority of tables, this is simply the columns returned from
     * {@link TableInfo#getColumns()} plus any additional columns from
     * {@link TableInfo#getDefaultVisibleColumns()).
     *
     * For other tables, the extended column set could include some columns
     * from lookup tables.
     *
     * @param includeHidden Include hidden columns.
     * @return All columns.
     */
    Map<FieldKey, ColumnInfo> getExtendedColumns(boolean includeHidden);


    /**
     * @return the {@link org.labkey.api.query.FieldKey}s that should be part of the default view of the table,
     * assuming that no other default view has been configured.
     */
    List<FieldKey> getDefaultVisibleColumns();

    /**
     * @param keys the new set of columns to show when there is no explicit default view defined. Use null to indicate
     * that the list should be inferred based on the column metadata
     */
    void setDefaultVisibleColumns(@Nullable Iterable<FieldKey> keys);

    ButtonBarConfig getButtonBarConfig();

    AggregateRowConfig getAggregateRowConfig();

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
     * Return the import URL expression for the table or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default import view will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#importData} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getImportDataURL(Container container);

    /**
     * Return the delete URL expression for the table or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default action will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#deleteQueryRows} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     */
    ActionURL getDeleteURL(Container container);

    /**
     * Return the update URL expression for a particular record or null.
     * If the table provides an update service via {@link #getUpdateService()},
     * a default update view will be provided.
     * Instead of calling this method directly, callers should pass
     * {@link QueryAction#updateQueryRow} to
     * {@link org.labkey.api.query.QueryView#urlFor(QueryAction)} or
     * {@link org.labkey.api.query.UserSchema#urlFor(org.labkey.api.query.QueryAction)}.
     * @param columns if null, implementations should simply return their first (and potentially only) details URL,
     *                or null if they don't have one.
     *                If non-null, the set of columns that are available from the ResultSet so that an appropriate
     *                URL can be chosen
     */
    StringExpression getUpdateURL(@Nullable Set<FieldKey> columns, Container container);

    /**
     * Return the details URL expression for a particular record or null.
     * @param columns if null, implementations should simply return their first (and potentially only) details URL,
     *                or null if they don't have one.
     *                If non-null, the set of columns that are available from the ResultSet so that an appropriate
     *                URL can be chosen
     */
    StringExpression getDetailsURL(@Nullable Set<FieldKey> columns, Container container);
    boolean hasDetailsURL();

    /**
     * Return the method of a given name.  Methods are accessible via the QueryModule's query
     * language.  Most tables do not have methods. 
     */
    default MethodInfo getMethod(String name)
    {
        return null;
    }

    /**
     * return the fieldkeys of the columns that methods in on this table depend on.
     * We could do this per method, but in practice it's a short list.
     */
    default Set<FieldKey> getMethodRequiredFieldKeys()
    {
        return Set.of();
    }

    /**
     * Returns a string that will appear on the default import page and as the top line
     * of the default generated Excel template
     */
    String getImportMessage();

    /**
     * Returns a list of templates (label / URL) that should be used as the options for excel upload
     * Each URL should either point to a static template file or an action to generate the template.
     * If no custom templates have been provided, it will return the default URL
     */
    List<Pair<String, String>> getImportTemplates(ViewContext ctx);

    /**
     * Returns a list of the raw import templates (without substituting the container).  This is intended to be
     * used by FilteredTable or other instances that need to copy the raw values from a parent table.  In general,
     * getImportTemplates() should be used instead
     */
    List<Pair<String, StringExpression>> getRawImportTemplates();

    // Most datasets do not have a container column
    boolean hasContainerColumn();

    boolean needsContainerClauseAdded();

    @Nullable
    ContainerFilter getContainerFilter();

    /**
     * Finds and applies the first metadata xml from active modules in the schema's container and then the first user-created metadata in the container hierarchy.
     *
     * @see QueryService#findMetadataOverride(UserSchema, String, boolean, boolean, Collection, Path)
     */
    void overlayMetadata(String tableName, UserSchema schema, Collection<QueryException> errors);

    void overlayMetadata(Collection<TableType> metadata, UserSchema schema, Collection<QueryException> errors);

    /** @return whether this table accepts XML metadata configuration to be overlaid on the default level of metadata */
    boolean isMetadataOverrideable();

    ColumnInfo getLookupColumn(ColumnInfo parent, String name);

    int getCacheSize();

    String getDescription();

    /**
     * Get Domain associated with this TableInfo if any.
     */
    @Nullable
    Domain getDomain();

    /**
     * Get DomainKind associated with this TableInfo if any.
     * Domain may or may not exist even if DomainKind is available.
     */
    @Nullable
    DomainKind getDomainKind();

    /**
     * Returns a QueryUpdateService implementation for this TableInfo,
     * or null if the table/query is not updatable.
     * @return A QueryUpdateService implementation for this table/query or null.
     */
    @Nullable
    QueryUpdateService getUpdateService();

    enum TriggerType
    {
        INSERT, UPDATE, DELETE, SELECT, TRUNCATE, MOVE;

        public String getMethodName()
        {
            String name = name();
            return name.substring(0, 1) + name.toLowerCase().substring(1, name.length());
        }
    }

    /**
     * Enumeration of trigger methods that may be implemented in trigger scripts.
     */
    enum TriggerMethod
    {
        init, complete,
        beforeInsert, afterInsert,
        beforeUpdate, afterUpdate,
        beforeDelete, afterDelete
    }


    /**
     * Queries may have named parameters, SELECT queries (the only kind we have right now) may
     * return TableInfos.  This is how you find out if a TableInfo representing a query has named
     * parameters
     */

    @NotNull
    Collection<QueryService.ParameterDecl> getNamedParameters();

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
     *   getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, true, errors);
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
     *           getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.UPDATE, true, i, row, oldRow);
     *           Map<String, Object> updatedRow = updateRow(user, container, row, oldRow);
     *           if (updatedRow == null)
     *               continue;
     *
     *           getQueryTable().fireRowTrigger(container, TableInfo.TriggerType.UPDATE, false, i, updatedRow, oldRow);
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
     *   getQueryTable().fireBatchTrigger(container, TableInfo.TriggerType.UPDATE, false, errors);
     * </pre>
     *
     * @param c The current Container.
     * @param user the current user
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param errors Any errors created by the validation script will be added to the errors collection.
     * @param extraContext Optional additional bindings to set in the script's context when evaluating.
     * @throws BatchValidationException if the trigger function returns false or the errors map isn't empty.
     */
    void fireBatchTrigger(Container c, User user, TriggerType type, boolean before, BatchValidationException errors, Map<String, Object> extraContext)
            throws BatchValidationException;

    default void fireRowTrigger(Container c, User user, TriggerType type, boolean before, int rowNumber,
                        @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, Map<String, Object> extraContext)
            throws ValidationException
    {
        fireRowTrigger(c, user, type, before, rowNumber, newRow, oldRow, extraContext, null);
    }

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
     * @param c The current Container.
     * @param user the current user
     * @param type The TriggerType for the event.
     * @param before true if the trigger is before the event, false if after the event.
     * @param newRow The new row for INSERT and UPDATE.
     * @param oldRow The previous row for UPDATE and DELETE
     * @param extraContext Optional additional bindings to set in the script's context when evaluating.
     * @param existingRecord Optional existing record for the row, used for merge operation to differentiate new vs existing row
     * @throws ValidationException if the trigger function returns false or the errors map isn't empty.
     */
    void fireRowTrigger(Container c, User user, TriggerType type, boolean before, int rowNumber,
                        @Nullable Map<String, Object> newRow, @Nullable Map<String, Object> oldRow, Map<String, Object> extraContext, @Nullable Map<String, Object> existingRecord)
            throws ValidationException;

    /**
     * Return true if there are trigger scripts associated with this table.
     */
    boolean hasTriggers(@Nullable Container c);

    /**
     * Return true if all trigger scripts support streaming.
     */
    default boolean canStreamTriggers(Container c) { return false; }

    /**
     * Reset the trigger script context by reloading them. Note there could still be caches that need to be reset
     * and script init() to rerun.
     *
     * @param c The current container
     */
    void resetTriggers(Container c);

    /**
     * Returns true if the underlying database table has triggers.
     */
    default boolean hasDbTriggers() { return false; }

    /* for asserting that tableinfo is not changed unexpectedly */
    void setLocked(boolean b);
    boolean isLocked();

    boolean supportsContainerFilter();
    boolean hasUnionTable();

    /**
     * Returns a ContainerContext for this table or null if ContainerContext is not supported.
     *
     * @return The ContainerContext for this table or null.
     */
    @Nullable
    ContainerContext getContainerContext();

    /**
     * Return the FieldKey of the Container column for this table.
     * If the value is non-null then getContainerContext() will
     * return a FieldKeyContext using the container FieldKey.
     *
     * @return FieldKey of the Container column.
     */
    @Nullable
    public FieldKey getContainerFieldKey();

    /**
     * Returns whether this table supports audit tracking of insert, updates and deletes by implementing the
     * AuditConfigurable interface.
     */
    boolean supportsAuditTracking();

    /**
     * @return set of all columns involved in the query
     */
    Set<ColumnInfo> getAllInvolvedColumns(Collection<ColumnInfo> selectColumns);

    /** see interface AuditConfigurable */
    default AuditBehaviorType getAuditBehavior()
    {
        return AuditBehaviorType.NONE;
    }

    /**
     * Retrieves the audit behavior for this table taking into account, in order of precedence:
     *  - the setting from the XML file (always returned if there is a value set)
     *  - the override value provided
     *  - the value supplied by this table's implementation
     *
     * @param overrideValue value used to override the behavior type provided by the table implementation
     * @return audit behavior for this table
     */
    default AuditBehaviorType getAuditBehavior(@Nullable AuditBehaviorType overrideValue)
    {
        AuditBehaviorType type = getXmlAuditBehaviorType();
        if (type != null)
            return type;
        if (overrideValue != null)
            return overrideValue;
        return getAuditBehavior();
    }

    default AuditBehaviorType getAuditBehavior(@Nullable String overrideValue)
    {
        if (overrideValue != null)
        {
            try
            {
                return getAuditBehavior(AuditBehaviorType.valueOf(overrideValue));
            }
            catch (IllegalArgumentException ignored)
            {
            }
        }
        return getAuditBehavior();
    }

    /* Can be used to distinguish AuditBehaviorType.NONE vs absent xml audit config */
    default AuditBehaviorType getXmlAuditBehaviorType()
    {
        return null;
    }

    /* fields to include in detailed UPDATE audit log, even if no change is made to field value */
    @NotNull
    default Set<String> getExtraDetailedUpdateAuditFields()
    {
        return Collections.emptySet();
    }


    // we exclude these from the detailed record because they are already on the audit record itself and
    // depending on the data iterator behavior (e.g., for ExpDataIterators.getDataIterator), these values
    // time of creating the audit log may actually already have been updated so the difference shown will be incorrect.
    Set<String> defaultExcludedDetailedUpdateAuditFields = CaseInsensitiveHashSet.of("Modified", "ModifiedBy", "Created", "CreatedBy");

    @NotNull
    default Set<String> getExcludedDetailedUpdateAuditFields()
    {
        return defaultExcludedDetailedUpdateAuditFields;
    }


    /**
     * Returns the row primary key column to use for audit history details. Note, this must
     * be a single key as we don't support multiple column primary keys for audit details.
     */
    @Nullable
    default FieldKey getAuditRowPk()
    {
        return null;
    }

    default boolean hasInsertURLOverride()
    {
        return false;
    }

    default boolean hasUpdateURLOverride()
    {
        return false;
    }

    default boolean hasDeleteURLOverride()
    {
        return false;
    }

    /**
     * Allow QueryView to render insert, update, and other buttons if the metadata xml provides URL
     * overrides and the underlying table doesn't support insert/update/delete operations,
     * such as a query table.
     */
    default boolean allowQueryTableURLOverrides()
    {
        return false;
    };

    /**
     * Max PHI across all columns in the table.
     */
    default PHI getMaxPhiLevel()
    {
        return getColumns().stream()
                .map(ColumnRenderProperties::getPHI)
                .max(Comparator.comparingInt(PHI::getRank))
                .orElse(PHI.NotPHI);
    }

    /**
     * Get the max allowed PHI for the current user in the current container.
     */
    default PHI getUserMaxAllowedPhiLevel()
    {
        UserSchema schema = getUserSchema();
        if (schema == null)
            return PHI.NotPHI;

        return ComplianceService.get().getMaxAllowedPhi(schema.getContainer(), schema.getUser());
    }

    /**
     * Return true if the current user is allowed the maximum phi level set across all columns.
     */
    default boolean canUserAccessPhi()
    {
        return getMaxPhiLevel().isLevelAllowed(getUserMaxAllowedPhiLevel());
    }

    /**
     * Useful helper to combine permission check/throw.  This is especially useful for tables where permissions can
     * differ from the containing schema/container.
     *
     * TableInfo users should still always check hasPermission() as early as possible for best error handling, and
     * to fail-fast (or degrade gracefully).
     */
    default void checkReadBeforeExecute()
    {
        assert null != getUserSchema(); // this is only for tables in UserSchema
        if (null != getUserSchema() && !hasPermission(getUserSchema().getUser(), ReadPermission.class))
            throw new UnauthorizedException(getUserSchema().getSchemaName() + "." + getName());
    }

    default List<Sort.SortField> getSortFields()
    {
        return List.of();
    }

    default boolean supportsInsertOption(QueryUpdateService.InsertOption option)
    {
        return true;
    }

    default void setAllowCalculatedColumns(boolean allowCalculatedColumns) {}
}
