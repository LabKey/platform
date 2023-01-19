package org.labkey.pipeline;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.old.JSONObject;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.security.User;
import org.labkey.api.trigger.TriggerConfiguration;
import org.labkey.pipeline.api.PipelineSchema;

import java.io.IOException;
import java.util.List;

public class PipelineUpgradeCode implements UpgradeCode
{
    private static final Logger LOG = LogManager.getLogger(PipelineUpgradeCode.class);

    /**
     * Called from pipeline-23.000-23.001.sql
     * For existing SampleReloadTask, update "mergeData" to "insertOption", update "false" to "INSERT", set "true" to "MERGE"
     */
    public static void updateSampleReloadTaskMergeDataOptions(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        TableInfo tinfo = PipelineSchema.getInstance().getTableInfoTriggerConfigurations();

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("pipelineid"), "FileAnalysisTaskPipeline:SampleReloadTask", CompareType.CONTAINS);

        List<TriggerConfiguration> triggers = new TableSelector(tinfo, filter, null).getArrayList(TriggerConfiguration.class);

        final String oldKey = "mergeData";

        if (!triggers.isEmpty())
            LOG.info("Updating 'mergeData' option for " +  triggers.size() + " 'Import Samples from Data File' pipeline trigger(s).");
        try (DbScope.Transaction tx = PipelineSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            User user = User.getSearchUser();
            for (TriggerConfiguration trigger : triggers)
            {
                Object customConfiguration = trigger.getCustomConfiguration();
                if (customConfiguration != null && !customConfiguration.toString().equals("") && customConfiguration.toString().contains(oldKey))
                {
                    JSONObject json = null;
                    try
                    {
                        ObjectMapper mapper = new ObjectMapper();
                        json = mapper.readValue(customConfiguration.toString(), JSONObject.class);
                        boolean isMerge = json.optBoolean(oldKey);
                        json.remove(oldKey);
                        json.put("insertOption", isMerge ? QueryUpdateService.InsertOption.MERGE.name() : QueryUpdateService.InsertOption.INSERT.name());

                        SQLFragment sql = new SQLFragment("UPDATE ").append(tinfo, "")
                                .append(" SET customconfiguration =  ?")
                                .add(json.toString())
                                .append(" WHERE rowId = ?")
                                .add(trigger.getRowId());

                        new SqlExecutor(tinfo.getSchema()).execute(sql);
                    }
                    catch (IOException e)
                    {
                        throw new IllegalArgumentException("Invalid JSON object for the configuration field: " + e);
                    }
                }
            }

            tx.commit();
        }

    }

}
