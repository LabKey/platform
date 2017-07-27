package org.labkey.pipeline.query;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
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
            return super.insertRow(user, container, row);
        }

        @Override
        protected Map<String, Object> updateRow(User user, Container container, Map<String, Object> row, @NotNull Map<String, Object> oldRow, boolean allowOwner, boolean retainCreation) throws InvalidKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            validateRow(row);
            return super.updateRow(user, container, row, oldRow, allowOwner, retainCreation);
        }

        private void validateRow(Map<String, Object> row) throws ValidationException
        {
            Integer rowId = row.get("RowId") != null ? (Integer) row.get("RowId") : null;
            String name = row.get("Name") != null ? row.get("Name").toString() : null;
            String type = row.get("Type") != null ? row.get("Type").toString() : null;
            String pipelineId = row.get("PipelineId") != null ? row.get("PipelineId").toString() : null;
            String invalidMsg = "";

            // validate that the config name is unique for this container
            if (name != null)
            {
                Collection<PipelineTriggerConfig> existingConfigs = PipelineTriggerRegistry.get().getConfigs(getContainer(), null, name);
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
            if (type == null || PipelineTriggerRegistry.get().getTypeByName(type) == null)
                invalidMsg += "Invalid pipeline trigger type: " + type + ". ";

            // validate that the pipelineId is a valid PipelineProvider
            if (pipelineId == null || PipelineService.get().getPipelineProvider(pipelineId) == null)
                invalidMsg += "Invalid pipeline provider: " + pipelineId + ". ";

            // validate that the configuration value parses as valid JSON
            Object configuration = row.get("Configuration");
            if (configuration != null)
            {
                try
                {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.readValue(configuration.toString(), Object.class);
                }
                catch (IOException e)
                {
                    invalidMsg += "Invalid JSON object for the configuration field. ";
                }
            }

            if (invalidMsg.length() > 0)
                throw new ValidationException(invalidMsg);
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
