package org.labkey.pipeline.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.collections.NamedObjectList;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.TableInfo;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.trigger.PipelineTriggerConfig;
import org.labkey.api.pipeline.trigger.PipelineTriggerRegistry;
import org.labkey.api.pipeline.trigger.PipelineTriggerType;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.DuplicateKeyException;
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
import org.labkey.pipeline.api.PipelineQuerySchema;
import org.labkey.pipeline.api.PipelineSchema;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
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
        pipelineId.setFk(new PipelineProviderForeignKey());
        pipelineId.setInputType("select");

        return this;
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

    private class PipelineProviderForeignKey extends AbstractSelectListForeignKey
    {
        PipelineProviderForeignKey()
        {
            for (PipelineProvider pipelineProvider : PipelineService.get().getPipelineProviders())
            {
                // TODO further filter this list to known providers that work in this scenario
                addListItem(pipelineProvider.getName(), pipelineProvider.getName());
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
            String invalidMsg = "";

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
                            invalidMsg += "A pipeline trigger configuration already exists in this container for the given name: " + name + ". ";
                            break;
                        }
                    }
                }
            }

            // validate that the type is a valid registered PipelineTriggerType
            PipelineTriggerType triggerType = PipelineTriggerRegistry.get().getTypeByName(type);
            if (triggerType == null)
                invalidMsg += "Invalid pipeline trigger type: " + type + ". ";

            // validate that the pipelineId is a valid PipelineProvider
            if (PipelineService.get().getPipelineProvider(pipelineId) == null)
                invalidMsg += "Invalid pipeline provider: " + pipelineId + ". ";

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
                    invalidMsg += "Invalid JSON object for the configuration field. ";
                }
            }

            // Finally, give the PipelineTriggerType a chance to validate the configuration JSON object
            if (triggerType != null)
            {
                String typeInvalidMsg = triggerType.validateConfiguration(json);
                if (typeInvalidMsg != null)
                    invalidMsg += typeInvalidMsg;
            }

            if (invalidMsg.length() > 0)
                throw new ValidationException(invalidMsg);
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
