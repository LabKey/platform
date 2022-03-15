package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobNotificationProvider;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.query.AbstractQueryUpdateService.createTransactionAuditEvent;

public class QueryImportPipelineJob extends PipelineJob
{
    public static final String QUERY_IMPORT_PIPELINE_PROVIDER = "QueryImport";
    public static final String QUERY_IMPORT_PIPELINE_PROVIDER_PARAM = "pipelineProvider";
    public static final String QUERY_IMPORT_PIPELINE_DESCRIPTION_PARAM = "pipelineDescription";
    public static final String QUERY_IMPORT_NOTIFICATION_PROVIDER_PARAM = "pipelineNotificationProvider";

    private QueryImportAsyncContextBuilder _importContextBuilder;

    private long _transactionAuditId;

    protected QueryImportPipelineJob()
    {}

    public QueryImportPipelineJob(@Nullable String provider, ViewBackgroundInfo info, PipeRoot root, QueryImportAsyncContextBuilder importContextBuilder)
    {
        super(StringUtils.isEmpty(provider) ? QUERY_IMPORT_PIPELINE_PROVIDER : provider, info, root);
        _importContextBuilder = importContextBuilder;
        setLogFile(findUniqueLogFile(importContextBuilder.getPrimaryFile(), importContextBuilder.getJobDescription()));
    }

    public static class QueryImportAsyncContextBuilder
    {
        File _primaryFile;
        boolean _hasColumnHeaders;
        String _fileContentType;

        String _schemaName;
        String _queryName;
        Map<String, String> _renamedColumns;

        QueryUpdateService.InsertOption _insertOption= QueryUpdateService.InsertOption.INSERT;
        AuditBehaviorType _auditBehaviorType = null;

        boolean _importLookupByAlternateKey = false;
        boolean _importIdentity = false;
        boolean _hasLineageColumns = false;

        String _jobDescription;

        String _jobNotificationProvider;

        public QueryImportAsyncContextBuilder()
        {

        }

        public File getPrimaryFile()
        {
            return _primaryFile;
        }

        public boolean isHasColumnHeaders()
        {
            return _hasColumnHeaders;
        }

        public String getFileContentType()
        {
            return _fileContentType;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public Map<String, String> getRenamedColumns()
        {
            return _renamedColumns;
        }

        public QueryUpdateService.InsertOption getInsertOption()
        {
            return _insertOption;
        }

        public AuditBehaviorType getAuditBehaviorType()
        {
            return _auditBehaviorType;
        }

        public boolean isImportLookupByAlternateKey()
        {
            return _importLookupByAlternateKey;
        }

        public boolean isImportIdentity()
        {
            return _importIdentity;
        }

        public boolean isHasLineageColumns()
        {
            return _hasLineageColumns;
        }

        public String getJobDescription()
        {
            return StringUtils.isEmpty(_jobDescription) ? (_queryName + " - " + _primaryFile.getName()) : _jobDescription;
        }

        public String getJobNotificationProvider()
        {
            return _jobNotificationProvider;
        }

        public void setJobNotificationProvider(String jobNotificationProvider)
        {
            _jobNotificationProvider = jobNotificationProvider;
        }

        public QueryImportAsyncContextBuilder setPrimaryFile(File primaryFile)
        {
            _primaryFile = primaryFile;
            return this;
        }

        public QueryImportAsyncContextBuilder setHasColumnHeaders(boolean hasColumnHeaders)
        {
            _hasColumnHeaders = hasColumnHeaders;
            return this;
        }

        public QueryImportAsyncContextBuilder setFileContentType(String fileContentType)
        {
            _fileContentType = fileContentType;
            return this;
        }

        public QueryImportAsyncContextBuilder setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
            return this;
        }

        public QueryImportAsyncContextBuilder setQueryName(String queryName)
        {
            _queryName = queryName;
            return this;
        }

        public QueryImportAsyncContextBuilder setRenamedColumns(Map<String, String> renamedColumns)
        {
            _renamedColumns = renamedColumns;
            return this;
        }

        public QueryImportAsyncContextBuilder setInsertOption(QueryUpdateService.InsertOption insertOption)
        {
            _insertOption = insertOption;
            return this;
        }

        public QueryImportAsyncContextBuilder setAuditBehaviorType(AuditBehaviorType auditBehaviorType)
        {
            _auditBehaviorType = auditBehaviorType;
            return this;
        }

        public QueryImportAsyncContextBuilder setImportLookupByAlternateKey(boolean importLookupByAlternateKey)
        {
            _importLookupByAlternateKey = importLookupByAlternateKey;
            return this;
        }

        public QueryImportAsyncContextBuilder setImportIdentity(boolean importIdentity)
        {
            _importIdentity = importIdentity;
            return this;
        }

        public QueryImportAsyncContextBuilder setHasLineageColumns(boolean hasLineageColumns)
        {
            _hasLineageColumns = hasLineageColumns;
            return this;
        }

        public QueryImportAsyncContextBuilder setJobDescription(String jobDescription)
        {
            _jobDescription = jobDescription;
            return this;
        }

    }

    @Override
    public URLHelper getStatusHref()
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), _importContextBuilder.getSchemaName());
        TableInfo target = schema.getTable(_importContextBuilder.getQueryName(), true);

        if (null != target)
        {
            URLHelper url;
            PipelineJobNotificationProvider notificationProvider = getNotificationProvider();
            if (notificationProvider != null)
            {
                url = notificationProvider.getPipelineStatusHref(this);
                if (url != null)
                    return url;
            }

            url =  target.getGridURL(getContainer());
            if (_transactionAuditId > 0)
                url.addParameter("transactionAuditId", String.valueOf(_transactionAuditId));

            return url;
        }

        return null;
    }

    @Override
    public String getDescription()
    {
        return _importContextBuilder.getJobDescription();
    }

    @Override
    public void run()
    {
        BatchValidationException ve = new BatchValidationException();

        PipelineJobNotificationProvider notificationProvider = getNotificationProvider();
        DataLoader loader = null;

        try
        {
            setStatus(TaskStatus.running);
            getLogger().info("Starting import " + getDescription());

            if (notificationProvider != null)
                notificationProvider.onJobStart(this);

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), _importContextBuilder.getSchemaName());
            TableInfo target = schema.getTable(_importContextBuilder.getQueryName(), true);

            if (null == target)
                throw new PipelineValidationException("Table not found: " + _importContextBuilder.getQueryName());

            QueryUpdateService updateService = target.getUpdateService();

            loader = DataLoader.get().createLoader(_importContextBuilder.getPrimaryFile(), _importContextBuilder.getFileContentType(), _importContextBuilder.isHasColumnHeaders(), null, null);

            AbstractQueryImportAction.configureLoader(loader, target, _importContextBuilder.getRenamedColumns(), _importContextBuilder.isHasLineageColumns());

            TransactionAuditProvider.TransactionAuditEvent auditEvent = null;

            if (_importContextBuilder.getAuditBehaviorType() != null && _importContextBuilder.getAuditBehaviorType() != AuditBehaviorType.NONE)
                auditEvent = createTransactionAuditEvent(getContainer(), QueryService.AuditAction.INSERT);

            int importedCount = AbstractQueryImportAction.importData(loader, target, updateService, _importContextBuilder.getInsertOption(), _importContextBuilder.isImportLookupByAlternateKey(),
                _importContextBuilder.isImportIdentity(), ve, _importContextBuilder.getAuditBehaviorType(), auditEvent, getInfo().getUser(), getInfo().getContainer());

            if (ve.hasErrors())
                throw ve;

            if (auditEvent != null)
                _transactionAuditId = auditEvent.getRowId();

            setStatus(TaskStatus.complete);
            getLogger().info("Done importing " + getDescription() + ". " + importedCount + " row(s) imported.");

            if (notificationProvider != null)
            {
                Map<String, Object> results = new HashMap<>();
                results.put("rowCount", importedCount);
                if (_transactionAuditId > 0)
                    results.put("transactionAuditId", _transactionAuditId);

                notificationProvider.onJobSuccess(this, results);
            }
        }
        catch (Exception e)
        {
            error("Import failed: " + e.getMessage());
            setStatus(TaskStatus.error);

            if (notificationProvider != null)
                notificationProvider.onJobError(this, e.getMessage());
        }
        finally
        {
            if (loader != null)
            {
                loader.close();
            }
        }

    }

    @Override
    protected String getJobNotificationProvider()
    {
        return _importContextBuilder._jobNotificationProvider;
    }

    private PipelineJobNotificationProvider getNotificationProvider()
    {
        return PipelineService.get().getPipelineJobNotificationProvider(getJobNotificationProvider(), this);
    }

    public QueryImportAsyncContextBuilder getImportContextBuilder()
    {
        return _importContextBuilder;
    }

    public long getTransactionAuditId()
    {
        return _transactionAuditId;
    }

}
