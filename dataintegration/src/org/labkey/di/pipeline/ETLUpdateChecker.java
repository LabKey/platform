package org.labkey.di.pipeline;

import org.apache.log4j.Logger;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.view.ViewBackgroundInfo;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

/**
 * User: jeckels
 * Date: 3/13/13
 */
public class ETLUpdateChecker implements Job
{
    private static final Logger LOG = Logger.getLogger(ETLUpdateChecker.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException
    {
        ETLUpdateCheckerInfo info = ETLUpdateCheckerInfo.getFromJobDetail(context.getJobDetail());

        UserSchema schema = QueryService.get().getUserSchema(info.getUser(), info.getContainer(), info.getETLDescriptor().getSourceSchema());
        if (schema == null)
        {
            LOG.warn("Unable to find schema " + info.getETLDescriptor().getSourceSchema() + " in " + info.getContainer().getPath());
        }
        else
        {
            TableInfo tableInfo = schema.getTable(info.getETLDescriptor().getSourceQuery());
            if (tableInfo == null)
            {
                LOG.warn("Unable to find query " + info.getETLDescriptor().getSourceQuery() + " in schema " + info.getETLDescriptor().getSourceSchema() + " in " + info.getContainer().getPath());
            }
            else
            {
                FieldKey modifiedFieldKey = FieldKey.fromParts("modified");
                Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tableInfo, Collections.singleton(modifiedFieldKey));
                if (columns.isEmpty())
                {
                    LOG.warn("Could not find Modified column on query " + info.getETLDescriptor().getSourceQuery() + " in schema " + info.getETLDescriptor().getSourceSchema() + " in " + info.getContainer().getPath());
                }
                else
                {
                    SimpleFilter filter = new SimpleFilter();
                    Date mostRecentRun = getMostRecentRun(info);
                    if (mostRecentRun != null)
                    {
                        filter.addCondition(modifiedFieldKey, mostRecentRun, CompareType.GTE);
                    }
                    long updatedRows = new TableSelector(tableInfo, columns.values(), filter, null).getRowCount();

                    if (updatedRows > 0)
                    {
                        ViewBackgroundInfo backgroundInfo = new ViewBackgroundInfo(info.getContainer(), info.getUser(), null);
                        TransformJob job = new TransformJob(backgroundInfo, info.getETLDescriptor());
                        try
                        {
                            PipelineService.get().setStatus(job, PipelineJob.WAITING_STATUS, null, true);
                        }
                        catch (Exception e)
                        {
                            LOG.error("Unable to queue ETL job", e);
                            return;
                        }

                        TransformRun run = new TransformRun();
                        run.setStartTime(new Date());
                        run.setTransformId(info.getETLDescriptor().getTransformId());
                        run.setTransformVersion(info.getETLDescriptor().getTransformVersion());
                        run.setContainer(info.getContainer());

                        PipelineStatusFile statusFile = PipelineService.get().getStatusFile(job.getLogFile());
                        run.setJobId(statusFile.getRowId());

                        try
                        {
                            run = Table.insert(info.getUser(), DataIntegrationDbSchema.getTransformRunTableInfo(), run);
                        }
                        catch (SQLException e)
                        {
                            throw new JobExecutionException(e);
                        }

                        job.setRunId(run.getRowId());

                        try
                        {
                            PipelineService.get().setStatus(job, PipelineJob.WAITING_STATUS, null, true);

                            PipelineService.get().queueJob(job);
                        }
                        catch (Exception e)
                        {
                            LOG.error("Unable to queue ETL job", e);
                        }
                    }
                }
            }
        }
    }

    public Date getMostRecentRun(ETLUpdateCheckerInfo info)
    {
        SQLFragment sql = new SQLFragment("SELECT MAX(StartTime) FROM ");
        sql.append(DataIntegrationDbSchema.getTransformRunTableInfo(), "tr");
        sql.append(" WHERE Container = ? AND TransformId = ? AND TransformVersion = ?");
        sql.add(info.getContainer().getId());
        sql.add(info.getETLDescriptor().getTransformId());
        sql.add(info.getETLDescriptor().getTransformVersion());
        return new SqlSelector(DataIntegrationDbSchema.getSchema(), sql).getObject(Date.class);
    }
}
