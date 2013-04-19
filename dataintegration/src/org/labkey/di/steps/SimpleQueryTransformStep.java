package org.labkey.di.steps;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TableInfo;
import org.labkey.api.etl.CopyConfig;
import org.labkey.api.etl.DataIteratorBuilder;
import org.labkey.api.etl.DataIteratorContext;
import org.labkey.api.etl.ResultSetDataIterator;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParseException;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.di.pipeline.TransformJobContext;
import org.labkey.di.pipeline.TransformTask;
import org.labkey.di.pipeline.TransformTaskFactory;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 2013-04-16
 * Time: 12:27 PM
 */
public class SimpleQueryTransformStep extends TransformTask
{
    final SimpleQueryTransformStepMeta _meta;
    final TransformJobContext _context;

    public SimpleQueryTransformStep(TransformTaskFactory f, PipelineJob job, SimpleQueryTransformStepMeta meta, TransformJobContext context)
    {
        super(f, job);
        _meta = meta;
        _context = context;
    }

    public void doWork() throws PipelineJobException
    {

        try
        {
            _context.getLogger().info("SimpleQueryTransformStep.doWork called");
// this works, but need to update tests
//            if (!executeCopy(_meta, _context.getContainer(), _context.getUser(), getJob().getLogger()))
//                getJob().setStatus("ERROR");
        }
        catch (Exception x)
        {
            getJob().getLogger().error(x);
        }
    }



    public static boolean executeCopy(CopyConfig meta, Container c, User u, Logger log) throws IOException, SQLException
    {
        QuerySchema sourceSchema = DefaultSchema.get(u, c, meta.getSourceSchema());
        if (null == sourceSchema || null == sourceSchema.getDbSchema())
        {
            log.error("ERROR: Source schema not found: " + meta.getSourceSchema());
            return false;
        }

        QuerySchema targetSchema = DefaultSchema.get(u, c, meta.getTargetSchema());
        if (null == targetSchema || null == targetSchema.getDbSchema())
        {
            log.error("ERROR: Target schema not found: " + meta.getTargetSchema());
            return false;
        }

        DbScope targetScope = targetSchema.getDbSchema().getScope();
        DbScope sourceScope = sourceSchema.getDbSchema().getScope();
        if (sourceScope.equals(targetScope))
            sourceScope = null;

        ResultSet rs = null;
        DataIteratorContext context = new DataIteratorContext();
        context.setForImport(true);
        context.setFailFast(true);
        try
        {
            targetScope.ensureTransaction();
            if (!sourceScope.equals(targetScope))
                sourceScope.ensureTransaction();

            long start = System.currentTimeMillis();
            log.info(DateUtil.toISO(start) + " Copying data from " + meta.getSourceSchema() + "." + meta.getSourceQuery() + " to " +
                    meta.getTargetSchema() + "." + meta.getTargetQuery());
            context.setForImport(true);
            context.setFailFast(true);

            DataIteratorBuilder source = selectFromSource(meta, c, u, context, log);
            if (null == source)
                return false;
            int count = appendToTarget(meta, c, u, context, source);

            targetScope.commitTransaction();
            if (null != sourceScope)
                sourceScope.commitTransaction();

            long finish = System.currentTimeMillis();
            log.info(DateUtil.toISO(finish) + " Copied " + count + " row" + (count != 1 ? "s" : "") + " in " + DateUtil.formatDuration(finish - start) + ".");
        }
        catch (Exception x)
        {
            log.error(x);
            return false;
        }
        finally
        {
            ResultSetUtil.close(rs);
            targetScope.closeConnection();
            if (null != sourceScope)
                sourceScope.closeConnection();
        }
        if (context.getErrors().hasErrors())
        {
            for (ValidationException v : context.getErrors().getRowErrors())
            {
                log.error(v.getMessage());
            }
            return false;
        }
        return true;
    }


    static DataIteratorBuilder selectFromSource(CopyConfig meta, Container c, User u, DataIteratorContext context, Logger log) throws SQLException
    {
        try
        {
            QuerySchema sourceSchema = DefaultSchema.get(u, c, meta.getSourceSchema());
            String sql = meta.getSourceQuery().startsWith("SELECT") ? meta.getSourceQuery() : "SELECT * FROM " + meta.getSourceQuery();
            ResultSet rs = QueryService.get().select(sourceSchema, sql);
            return new DataIteratorBuilder.Wrapper(ResultSetDataIterator.wrap(rs, context));
        }
        catch (QueryParseException x)
        {
            log.error(x.getMessage());
            return null;
        }
    }


    static int appendToTarget(CopyConfig meta, Container c, User u, DataIteratorContext context, DataIteratorBuilder source)
    {
        QuerySchema querySchema =  DefaultSchema.get(u, c, meta.getTargetSchema());
        if (null == querySchema)
        {
            context.getErrors().addRowError(new ValidationException("Could not create schema: " + meta.getTargetSchema()));
            return -1;
        }
        TableInfo targetTableInfo = querySchema.getTable(meta.getTargetQuery());
        if (null == targetTableInfo)
        {
            context.getErrors().addRowError(new ValidationException("Could find table: " +  meta.getTargetSchema() + "." + meta.getTargetQuery()));
            return -1;
        }
        try
        {
            QueryUpdateService qus = targetTableInfo.getUpdateService();
            if (null == qus)
            {
                context.getErrors().addRowError(new ValidationException("Can't import into table: " + meta.getTargetSchema() + "." + meta.getTargetQuery()));
                return -1;
            }
            return qus.importRows(u, c, source.getDataIterator(context), context.getErrors(), null);
        }
        catch (SQLException sqlx)
        {
            throw new RuntimeException(sqlx);
        }
    }
}
