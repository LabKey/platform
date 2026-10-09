/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
package org.labkey.pipeline.query;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.AbstractValueTransformingDisplayColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.pipeline.PipelineJobService;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisTaskPipeline;
import org.labkey.api.pipeline.trigger.PipelineTriggerConfig;
import org.labkey.api.pipeline.trigger.PipelineTriggerRegistry;
import org.labkey.api.pipeline.trigger.PipelineTriggerType;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.api.PipelineManager;
import org.labkey.pipeline.api.PipelineQuerySchema;
import org.labkey.pipeline.api.PipelineSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TriggerConfigurationsTable extends SimpleUserSchema.SimpleTable<PipelineQuerySchema>
{
    private static final String CONFIGURATION = "Configuration";
    private static final String CUSTOM_CONFIGURATION = "CustomConfiguration";
    private static final String PARAMETER_FUNCTION = "parameterFunction";

    public TriggerConfigurationsTable(PipelineQuerySchema schema, ContainerFilter cf)
    {
        super(schema, PipelineSchema.getInstance().getTableInfoTriggerConfigurations(), cf);
        setTitle("Pipeline Trigger Configurations");

        // disable the insert new button if there are no registered pipeline trigger types
        if (PipelineTriggerRegistry.get().getTypes().isEmpty())
            setInsertURL(AbstractTableInfo.LINK_DISABLER);

        setImportURL(AbstractTableInfo.LINK_DISABLER);
    }

    @Override
    public SimpleUserSchema.SimpleTable<PipelineQuerySchema> init()
    {
        super.init();

        var type = getMutableColumnOrThrow("Type");
        type.setFk(new PipelineTriggerTypeForeignKey(getUserSchema(), getContainerFilter()));
        type.setInputType("select");

        var pipelineId = getMutableColumnOrThrow("PipelineId");
        pipelineId.setFk(new TaskPipelineForeignKey(getUserSchema(), getContainerFilter()));
        pipelineId.setInputType("select");

        var pipelineTaskCol = new AliasedColumn("PipelineTask", getColumn("PipelineId"));
        pipelineTaskCol.setDisplayColumnFactory(PipelineTaskDisplayColumn::new);
        pipelineTaskCol.setReadOnly(true);
        pipelineTaskCol.setShownInInsertView(false);
        pipelineTaskCol.setShownInUpdateView(false);
        pipelineTaskCol.setTextAlign("left");
        addColumn(pipelineTaskCol);

        var statusCol = new AliasedColumn("Status", getColumn("RowId"));
        statusCol.setDisplayColumnFactory(StatusDisplayColumn::new);
        statusCol.setReadOnly(true);
        statusCol.setShownInInsertView(false);
        statusCol.setShownInUpdateView(false);
        statusCol.setTextAlign("left");
        addColumn(statusCol);

        return this;
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        List<FieldKey> cols = new ArrayList<>();
        cols.add(FieldKey.fromParts("Name"));
        cols.add(FieldKey.fromParts("Description"));
        cols.add(FieldKey.fromParts("Enabled"));
        cols.add(FieldKey.fromParts("LastChecked"));
        cols.add(FieldKey.fromParts("Status"));
        cols.add(FieldKey.fromParts("Type"));
        cols.add(FieldKey.fromParts("PipelineTask"));
        cols.add(FieldKey.fromParts("Configuration"));
        cols.add(FieldKey.fromParts("CustomConfiguration"));
        return cols;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, perm == ReadPermission.class ? perm : AdminPermission.class);
    }

    @Override
    public TriggerConfigurationsUpdateService getUpdateService()
    {
        return new TriggerConfigurationsUpdateService(this);
    }

    private static class PipelineTriggerTypeForeignKey extends AbstractSelectListForeignKey
    {
        PipelineTriggerTypeForeignKey(QuerySchema sourceSchema, ContainerFilter cf)
        {
            super(sourceSchema, cf);
            for (PipelineTriggerType<?> pipelineTriggerType : PipelineTriggerRegistry.get().getTypes())
               addListItem(pipelineTriggerType.getName(), pipelineTriggerType.getName());
        }
    }

    private static class PipelineTaskDisplayColumn extends AbstractValueTransformingDisplayColumn<String, String>
    {
        public PipelineTaskDisplayColumn(ColumnInfo pipelineIdCol)
        {
            super(pipelineIdCol, String.class);
        }

        @Override
        protected String transformValue(String pipelineIdStr)
        {
            if (pipelineIdStr != null)
            {
                try
                {
                    TaskPipeline<?> taskPipeline = PipelineJobService.get().getTaskPipeline(pipelineIdStr);
                    return taskPipeline.getDescription();
                }
                catch (NotFoundException e)
                {
                    return "Invalid pipeline task id: " + pipelineIdStr + ". ";
                }
            }
            
            return null;
        }
    }

    private static class StatusDisplayColumn extends AbstractValueTransformingDisplayColumn<Integer, String>
    {
        public StatusDisplayColumn(ColumnInfo rowIdCol)
        {
            super(rowIdCol, String.class);
        }

        @Override
        protected String transformValue(Integer rowId)
        {
            PipelineTriggerConfig config = PipelineTriggerRegistry.get().getConfigById(rowId);
            if (config != null)
                return config.getStatus();

            return null;
        }
    }

    /**
     * Note: context.xml files that set this property must reside in a module that extends {@link org.labkey.api.module.SpringModule}
     */
    private class TaskPipelineForeignKey extends AbstractSelectListForeignKey
    {
        TaskPipelineForeignKey(QuerySchema sourceSchema, ContainerFilter cf)
        {
            super(sourceSchema, cf);
            for (TaskPipeline<?> taskPipeline : PipelineJobService.get().getTaskPipelines(getContainer()))
            {
                if (taskPipeline instanceof FileAnalysisTaskPipeline fatp)
                {
                    if (fatp.isAllowForTriggerConfiguration())
                        addListItem(taskPipeline.getId().toString(), taskPipeline.getDescription());
                }
            }
        }
    }

    public class TriggerConfigurationsUpdateService extends DefaultQueryUpdateService
    {
        public TriggerConfigurationsUpdateService(TriggerConfigurationsTable table)
        {
            super(table, table.getRealTable());
        }

        @Override
        protected Set<String> getAutoPopulatedColumns()
        {
            Set<String> defCols = new HashSet<>(super.getAutoPopulatedColumns());
            defCols.add("PipelineTask");
            return Collections.unmodifiableSet(defCols);
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws DuplicateKeyException, QueryUpdateServiceException, SQLException
        {
            List<Map<String, Object>> ret = new LinkedList<>();
            for (Map<String, Object> row : rows)
            {
                try
                {
                    ret.add(insertRow(user, container, row));
                }
                catch (ValidationException e)
                {
                    errors.addRowError(e);
                    ret.remove(row);
                }
            }
            return ret;
        }

        /**
         * The data iterator skips insertRow()/updateRow(), so it would bypass validateConfiguration() and never start or stop listeners.
         * Reports an error instead of throwing UnsupportedOperationException because the import action shows context errors
         * to the user but lets runtime exceptions escape as a 500.
         */
        @Override
        public int loadRows(User user, Container container, DataIteratorBuilder rows, @Nullable ArrayList<Map<String, Object>> outputRows, DataIteratorContext context, @Nullable Map<String, Object> extraScriptContext)
        {
            context.getErrors().addRowError(new ValidationException("Bulk loading pipeline trigger configurations is not supported."));
            return 0;
        }

        @Override
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
        {
            if (oldKeys != null && rows.size() != oldKeys.size())
                throw new IllegalArgumentException("rows and oldKeys are required to be the same length, but were " + rows.size() + " and " + oldKeys.size() + " in length, respectively");

            List<Map<String, Object>> ret = new ArrayList<>(rows.size());
            for (int i = 0; i < rows.size(); i++)
            {
                Map<String, Object> row = rows.get(i);
                try
                {
                    Map<String, Object> oldRow = getRow(user, container, oldKeys == null ? row : oldKeys.get(i));
                    // getRow() selects by RowId alone, so the row may belong to another folder
                    if (oldRow == null || !container.getId().equals(Objects.toString(oldRow.get("Container"), null)))
                        throw new ValidationException("Pipeline trigger configuration not found in this folder.");

                    ret.add(updateRow(user, container, row, oldRow, false, true));
                }
                catch (ValidationException e)
                {
                    errors.addRowError(e);
                }
            }

            if (errors.hasErrors())
                throw errors;

            return ret;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            validateRunAsUser(user, container, row);
            Map<String, Object> newRow = super.insertRow(user, container, row);
            String name = getStringFromRow(newRow, "Name");
            startIfEnabled(container, name, newRow);
            return newRow;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            String name = getStringFromRow(oldRow, "Name");
            PipelineTriggerConfig config = PipelineTriggerRegistry.get().getConfigByName(container, name);

            validateRunAsUser(user, container, row);
            Map<String, Object> newRow = super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);

            // call the stop() method for this config if it was successfully updated
            if (config != null)
                afterCommit(config::stop);

            String newName = getStringFromRow(newRow, "Name");
            startIfEnabled(container, newName, newRow);
            return newRow;
        }

        // Checked here rather than in insertRow()/updateRow() because alias keys (label, propertyURI) are resolved or dropped by now
        @Override
        protected Map<String, Object> _insert(User user, Container c, Map<String, Object> row) throws SQLException, ValidationException
        {
            validateConfiguration(user, null, row);
            return super._insert(user, c, row);
        }

        @Override
        protected Map<String, Object> _update(User user, Container c, Map<String, Object> row, Map<String, Object> oldRow, Object[] keys) throws SQLException, ValidationException
        {
            validateConfiguration(user, oldRow, row);
            return super._update(user, c, row, oldRow, keys);
        }

        private void validateConfiguration(User user, @Nullable Map<String, Object> oldRow, Map<String, Object> newRow) throws ValidationException
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>(newRow);
            parseJson(row, CUSTOM_CONFIGURATION);
            if (oldRow != null && !row.containsKey(CONFIGURATION))
                return;

            JSONObject configuration = parseJson(row, CONFIGURATION);

            // GH Issue 1524: the Parameter Function runs as server-side script, so only script authors may add, change, or clear it
            if (user.isTrustedAnalyst())
                return;

            JSONObject oldConfiguration = null;
            if (oldRow != null)
            {
                try
                {
                    oldConfiguration = parseJson(new CaseInsensitiveHashMap<>(oldRow), CONFIGURATION);
                }
                catch (ValidationException ignored)
                {
                    // A stored value that isn't valid JSON can't run, so it has no function to preserve
                }
            }

            if (!Objects.equals(getParameterFunction(oldConfiguration), getParameterFunction(configuration)))
                throw new ValidationException("You must be either a PlatformDeveloper or TrustedAnalyst to add, change, or remove a Parameter Function.");
        }

        /** Empty is allowed because FileWatcherPipelineTriggerConfig reads it as {}; anything else must parse there too */
        private static @Nullable JSONObject parseJson(Map<String, Object> row, String column) throws ValidationException
        {
            Object value = row.get(column);
            if (value == null || StringUtils.isEmpty(value.toString()))
                return null;

            try
            {
                return new JSONObject(value.toString());
            }
            catch (JSONException e)
            {
                throw new ValidationException("Invalid JSON for " + column + ": " + e.getMessage(), column);
            }
        }

        /** Extracts the function as FileWatcherPipelineTriggerConfig does, where any non-null value runs via toString() */
        private static @Nullable String getParameterFunction(@Nullable JSONObject configuration)
        {
            if (configuration == null)
                return null;

            String function = Objects.toString(configuration.toMap().get(PARAMETER_FUNCTION), null);
            return StringUtils.isBlank(function) ? null : function;
        }

        /** Implement to make sure the listener gets unregistered */
        @Override
        public int truncateRows(User user, Container container) throws QueryUpdateServiceException, SQLException
        {
            TableSelector ts = new TableSelector(TriggerConfigurationsTable.this);
            Collection<Map<String, Object>> rowsToDelete = ts.getMapCollection();
            for (Map<String, Object> rowMap : rowsToDelete)
            {
                try
                {
                    deleteRow(user, container, rowMap);
                }
                catch (InvalidKeyException e)
                {
                    throw new QueryUpdateServiceException(e);
                }
            }
            return rowsToDelete.size();
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            String name = getStringFromRow(oldRowMap, "Name");
            PipelineTriggerConfig config = PipelineTriggerRegistry.get().getConfigByName(container, name);

            if (config != null)
            {
                PipelineTriggerRegistry.get().purgeTriggeredEntries(config);
            }
            Map<String, Object> deleteRow = super.deleteRow(user, container, oldRowMap);

            // call the stop() method for this config if it was successfully deleted
            if (config != null)
                afterCommit(config::stop);

            return deleteRow;
        }

        private void startIfEnabled(Container container, String name, Map<String, Object> row)
        {
            boolean enabled = Boolean.parseBoolean(row.getOrDefault("Enabled", false).toString());

            afterCommit(() -> {
                PipelineTriggerConfig config = PipelineTriggerRegistry.get().getConfigByName(container, name);

                if (config != null)
                {
                    if (enabled)
                        config.start();
                    else
                        config.stop();
                }
            });
        }

        /** Listeners aren't transactional, so change them only once the caller's transaction commits (immediately if there is none) */
        private void afterCommit(Runnable task)
        {
            getDbTable().getSchema().getScope().addCommitTask(task, DbScope.CommitTaskOption.POSTCOMMIT);
        }

        private void validateRunAsUser(User user, Container container, Map<String, Object> row) throws ValidationException
        {
            Map<String, Object> ciRow = new CaseInsensitiveHashMap<>(row);
            String error = PipelineManager.validateTriggerRunAsUser(container, user, getStringFromRow(ciRow, "Configuration"), getStringFromRow(ciRow, "CustomConfiguration"));
            if (error != null)
                throw new ValidationException(error);
        }

        private String getStringFromRow(Map<String, Object> row, String key)
        {
            return row.get(key) != null ? row.get(key).toString() : null;
        }
    }


    @Override
    public boolean hasDetailsURL()
    {
        return false;
    }

    @Override
    public StringExpression getDetailsURL(@Nullable Set<FieldKey> columns, Container container)
    {
        return DetailsURL.fromString(
                "pipeline/createPipelineTrigger.view?rowId=${rowId}",
                null,
                StringExpressionFactory.AbstractStringExpression.NullValueBehavior.NullResult);
    }

    @Override
    public ActionURL getInsertURL(Container container)
    {
        return new ActionURL(PipelineController.CreatePipelineTriggerAction.class, container);
    }

    @Override
    public StringExpression getUpdateURL(@Nullable Set<FieldKey> columns, Container container)
    {
        return getDetailsURL(columns, container);
    }

    private abstract static class AbstractSelectListForeignKey extends AbstractForeignKey
    {
        NamedObjectList _list = new NamedObjectList();

        protected AbstractSelectListForeignKey(QuerySchema sourceSchema, ContainerFilter cf)
        {
            super(sourceSchema, cf);
        }

        @Override
        public ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            return parent;
        }

        @Override
        public TableInfo getLookupTableInfo()
        {
            return null;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public @NotNull NamedObjectList getSelectList(RenderContext ctx)
        {
            return _list;
        }

        public void addListItem(String key, String value)
        {
            _list.put(new SimpleNamedObject(key, value));
        }
    }
}
