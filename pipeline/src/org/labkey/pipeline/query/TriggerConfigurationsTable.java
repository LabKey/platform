package org.labkey.pipeline.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
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
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.InvalidKeyException;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.util.SimpleNamedObject;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.NotFoundException;
import org.labkey.pipeline.api.PipelineQuerySchema;
import org.labkey.pipeline.api.PipelineSchema;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        protected Map<String, Object> insertRow(User user, Container container, Map<String, Object> row) throws DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            validateRow(row);
            Map<String, Object> newRow = super.insertRow(user, container, row);
            startIfEnabled(container, newRow);
            return newRow;
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            validateRow(row);
            Map<String, Object> newRow = super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);
            // TODO need to also handle the start/stop for a change to type or pipelineId
            startIfEnabled(container, newRow);
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

        private void startIfEnabled(Container container, Map<String, Object> row)
        {
            boolean enabled = Boolean.parseBoolean(row.getOrDefault("Enabled", false).toString());
            PipelineTriggerConfig config = PipelineTriggerRegistry.get().getConfigByName(container, getStringFromRow(row, "Name"));

            if (config != null)
            {
                if (enabled)
                    config.start();
                else
                    config.stop();
            }
        }

        private void validateRow(Map<String, Object> row) throws ValidationException
        {
            Integer rowId = row.get("RowId") != null ? (Integer) row.get("RowId") : null;
            String name = getStringFromRow(row, "Name");
            String type = getStringFromRow(row, "Type");
            String pipelineId = getStringFromRow(row, "PipelineId");
            boolean isEnabled = Boolean.parseBoolean(row.get("Enabled").toString());

            List<ValidationError> validationErrors = new ArrayList<>();

            // validate that the config name is unique for this container
            if (name != null)
            {
                Collection<PipelineTriggerConfig> existingConfigs = PipelineTriggerRegistry.get().getConfigs(getContainer(), null, name, false);
                if (!existingConfigs.isEmpty())
                {
                    for (PipelineTriggerConfig existingConfig : existingConfigs)
                    {
                        if (rowId == null || !rowId.equals(existingConfig.getRowId()))
                        {
                            validationErrors.add(new PropertyValidationError("A pipeline trigger configuration already exists in this container for the given name: " + name, "name"));
                            break;
                        }
                    }
                }
            }

            // validate that the type is a valid registered PipelineTriggerType
            PipelineTriggerType triggerType = PipelineTriggerRegistry.get().getTypeByName(type);
            if (triggerType == null)
                validationErrors.add(new PropertyValidationError("Invalid pipeline trigger type: " + type, "type"));

            // validate that the pipelineId is a valid TaskPipeline
            try
            {
                PipelineJobService.get().getTaskPipeline(pipelineId);
            }
            catch (NotFoundException e)
            {
                validationErrors.add(new PropertyValidationError("Invalid pipeline task id: " + pipelineId, "pipelineId"));
            }

            // validate that the configuration value parses as valid JSON
            Object configuration = row.get("Configuration");
            JSONObject json = null;
            if (configuration != null)
            {
                try
                {
                    ObjectMapper mapper = new ObjectMapper();
                    json = mapper.readValue(configuration.toString(), JSONObject.class);
                }
                catch (IOException e)
                {
                    validationErrors.add(new PropertyValidationError("Invalid JSON object for the configuration field: " + e.toString(), "configuration"));
                }
            }

            // Finally, give the PipelineTriggerType a chance to validate the configuration JSON object
            if (triggerType != null)
            {
                List<String> configErrors = triggerType.validateConfiguration(pipelineId, isEnabled, json);
                for (String msg : configErrors)
                    validationErrors.add(new PropertyValidationError(msg, "configuration"));
            }

            if (!validationErrors.isEmpty())
                throw new ValidationException(validationErrors);
        }

        private String getStringFromRow(Map<String, Object> row, String key)
        {
            return row.get(key) != null ? row.get(key).toString() : null;
        }
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
