/*
 * Copyright (c) 2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.AbstractValueTransformingDisplayColumn;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
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
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.pipeline.PipelineController;
import org.labkey.pipeline.api.PipelineQuerySchema;
import org.labkey.pipeline.api.PipelineSchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TriggerConfigurationsTable extends SimpleUserSchema.SimpleTable<PipelineQuerySchema>
{
    public TriggerConfigurationsTable(PipelineQuerySchema schema)
    {
        super(schema, PipelineSchema.getInstance().getTableInfoTriggerConfigurations());
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

        ColumnInfo type = getColumn("Type");
        type.setFk(new PipelineTriggerTypeForeignKey());
        type.setInputType("select");

        ColumnInfo pipelineId = getColumn("PipelineId");
        pipelineId.setFk(new TaskPipelineForeignKey());
        pipelineId.setInputType("select");

        ColumnInfo pipelineTaskCol = new AliasedColumn("PipelineTask", getColumn("PipelineId"));
        pipelineTaskCol.setDisplayColumnFactory(PipelineTaskDisplayColumn::new);
        pipelineTaskCol.setReadOnly(true);
        pipelineTaskCol.setShownInInsertView(false);
        pipelineTaskCol.setShownInUpdateView(false);
        pipelineTaskCol.setTextAlign("left");
        addColumn(pipelineTaskCol);

        ColumnInfo statusCol = new AliasedColumn("Status", getColumn("RowId"));
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
    public boolean hasPermission(UserPrincipal user, Class<? extends Permission> perm)
    {
        return getContainer().hasPermission(user, AdminOperationsPermission.class);
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new TriggerConfigurationsUpdateService(this);
    }

    private class PipelineTriggerTypeForeignKey extends AbstractSelectListForeignKey
    {
        PipelineTriggerTypeForeignKey()
        {
            for (PipelineTriggerType pipelineTriggerType : PipelineTriggerRegistry.get().getTypes())
               addListItem(pipelineTriggerType.getName(), pipelineTriggerType.getName());
        }
    }

    private class PipelineTaskDisplayColumn extends AbstractValueTransformingDisplayColumn<String, String>
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
                    TaskPipeline taskPipeline = PipelineJobService.get().getTaskPipeline(pipelineIdStr);
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

    private class StatusDisplayColumn extends AbstractValueTransformingDisplayColumn<Integer, String>
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
        TaskPipelineForeignKey()
        {
            for (TaskPipeline taskPipeline : PipelineJobService.get().getTaskPipelines(getContainer()))
            {
                if (taskPipeline instanceof FileAnalysisTaskPipeline)
                {
                    FileAnalysisTaskPipeline fatp = (FileAnalysisTaskPipeline) taskPipeline;
                    if (fatp.isAllowForTriggerConfiguration())
                        addListItem(taskPipeline.getId().toString(), taskPipeline.getDescription());
                }
            }
        }
    }

    private class TriggerConfigurationsUpdateService extends DefaultQueryUpdateService
    {
        public TriggerConfigurationsUpdateService(TriggerConfigurationsTable table)
        {
            super(table, table.getRealTable());
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

        @Override
        public List<Map<String, Object>> updateRows(User user, Container container, List<Map<String, Object>> rows, List<Map<String, Object>> oldKeys, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext) throws InvalidKeyException, QueryUpdateServiceException, SQLException
        {
            List<Map<String, Object>> ret = new LinkedList<>();
            for (Map<String, Object> row : rows)
            {
                try
                {
                    ret.add(updateRow(user, container, row, row, false, true));
                }
                catch (ValidationException e)
                {
                    ret.remove(row);
                }
            }
            return ret;
        }

        @Override
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
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

            Map<String, Object> newRow = super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);

            // call the stop() method for this config if it was successfully updated
            if (config != null)
                config.stop();

            String newName = getStringFromRow(newRow, "Name");
            startIfEnabled(container, newName, newRow);
            return newRow;
        }

        @Override
        protected Map<String, Object> deleteRow(User user, Container container, Map<String, Object> oldRowMap) throws QueryUpdateServiceException, SQLException, InvalidKeyException
        {
            String name = getStringFromRow(oldRowMap, "Name");
            PipelineTriggerConfig config = PipelineTriggerRegistry.get().getConfigByName(container, name);

            Map<String, Object> deleteRow = super.deleteRow(user, container, oldRowMap);

            // call the stop() method for this config if it was successfully deleted
            if (config != null)
                config.stop();

            return deleteRow;
        }

        private void startIfEnabled(Container container, String name, Map<String, Object> row)
        {
            boolean enabled = Boolean.parseBoolean(row.getOrDefault("Enabled", false).toString());
            PipelineTriggerConfig config = PipelineTriggerRegistry.get().getConfigByName(container, name);

            if (config != null)
            {
                if (enabled)
                    config.start();
                else
                    config.stop();
            }
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

    private abstract class AbstractSelectListForeignKey extends AbstractForeignKey
    {
        NamedObjectList _list = new NamedObjectList();

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
        public NamedObjectList getSelectList(RenderContext ctx)
        {
            return _list;
        }

        public void addListItem(String key, String value)
        {
            _list.put(new SimpleNamedObject(key, value));
        }
    }
}
