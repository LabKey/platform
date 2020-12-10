package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.data.TableInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.springframework.util.StringUtils;

import javax.servlet.ServletException;
import java.io.File;
import java.util.Map;

import static org.labkey.api.query.AbstractQueryUpdateService.createTransactionAuditEvent;

public class QueryImportPipelineJob extends PipelineJob
{
    public static final String QUERY_IMPORT_PIPELINE_PROVIDER = "QueryImport";
    public static final String QUERY_IMPORT_PIPELINE_PROVIDER_PARAM = "pipelineProvider";
    public static final String QUERY_IMPORT_PIPELINE_DESCRIPTION_PARAM = "pipelineDescription";

    protected File _primaryFile;
    protected boolean _hasColumnHeaders;
    protected String _fileContentType;

    protected String _schemaName;
    protected String _queryNama;
    protected Map<String, String> _renamedColumns;

    protected QueryUpdateService.InsertOption _insertOption= QueryUpdateService.InsertOption.INSERT;
    protected AuditBehaviorType _auditBehaviorType = null;
    protected boolean _importLookupByAlternateKey = false;
    protected boolean _importIdentity = false;
    protected boolean _hasLineageColumns = false;

    protected String _jobDescription;

    protected QueryImportPipelineJob()
    {}

    public QueryImportPipelineJob(@Nullable String provider, ViewBackgroundInfo info, PipeRoot root, File primaryFile, boolean hasColumnHeaders, String fileContentType,
                                  String schemaName, String queryName, Map<String, String> renamedColumns,
                                  QueryUpdateService.InsertOption insertOption, AuditBehaviorType auditBehaviorType, boolean importLookupByAlternateKey,
                                  boolean importIdentity, boolean hasLineageColumns, String jobDescription)
    {
        super(StringUtils.isEmpty(provider) ? QUERY_IMPORT_PIPELINE_PROVIDER : provider, info, root);

        _primaryFile = primaryFile;
        _hasColumnHeaders = hasColumnHeaders;
        _fileContentType = fileContentType;
        _schemaName = schemaName;
        _queryNama = queryName;
        _renamedColumns = renamedColumns;
        _insertOption = insertOption;
        _auditBehaviorType = auditBehaviorType;
        _importLookupByAlternateKey = importLookupByAlternateKey;
        _importIdentity = importIdentity;
        _hasLineageColumns = hasLineageColumns;

        _jobDescription = StringUtils.isEmpty(jobDescription) ? (_schemaName + " - " + _queryNama + " - " + _primaryFile.getName()) : jobDescription;

        setLogFile(findUniqueLogFile(primaryFile, getDescription()));

    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return _jobDescription;
    }

    @Override
    public void run()
    {
        BatchValidationException ve = new BatchValidationException();

        try
        {
            setStatus(TaskStatus.running);
            getLogger().info("Starting importing " + getDescription());

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), _schemaName);
            TableInfo target = schema.getTable(_queryNama, true);

            if (null == target)
                throw new ServletException("TableInfo not found.");

            QueryUpdateService updateService = target.getUpdateService();

            DataLoader loader = DataLoader.get().createLoader(_primaryFile, _fileContentType, _hasColumnHeaders, null, null);

            AbstractQueryImportAction.configureLoaderImpl(loader, target, _renamedColumns, _hasLineageColumns);

            TransactionAuditProvider.TransactionAuditEvent auditEvent = null;

            if (_auditBehaviorType != null && _auditBehaviorType != AuditBehaviorType.NONE)
                auditEvent = createTransactionAuditEvent(getContainer(), QueryService.AuditAction.INSERT);

            int importedCount = AbstractQueryImportAction.importDataImpl(loader, target, updateService, _insertOption, _importLookupByAlternateKey,
                _importIdentity, ve, _auditBehaviorType, auditEvent, getInfo().getUser(), getInfo().getContainer());

            if (ve.hasErrors())
                throw ve;

            setStatus(TaskStatus.complete);
            getLogger().info("Done importing " + getDescription() + ". " + importedCount + " row(s) imported.");
        }
        catch (Exception e)
        {
            error("Import failed: " + e.getMessage());
            setStatus(TaskStatus.error);
        }

    }

}
