/*
 *  Copyright (c) 2012-2019 LabKey Corporation
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

package org.labkey.assay.actions;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AssayFilePropertyWriter;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.DefaultAssayRunCreator;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static org.labkey.api.assay.AssayDataCollector.PRIMARY_FILE;
import static org.labkey.api.assay.AssayFileWriter.createFile;

@ActionNames("importRun")
@RequiresPermission(InsertPermission.class)
@ApiVersion(12.3)
public class ImportRunApiAction extends MutatingApiAction<ImportRunApiAction.ImportRunApiForm>
{
    static Logger LOG = LogManager.getLogger(ImportRunApiAction.class);

    @Override
    public ApiResponse execute(ImportRunApiForm form, BindException errors) throws Exception
    {
        ExpProtocol protocol;
        AssayProvider provider;

        Long batchId;
        String name;
        Long workflowTaskId;
        String comments;
        CaseInsensitiveHashMap<Object> runProperties = null;
        CaseInsensitiveHashMap<Object> batchProperties = null;
        String targetStudy;
        Long reRunId;
        AssayRunUploadContext.ReImportOption reImportOption = null;
        String runFilePath;
        String moduleName;
        List<Map<String, Object>> rawData = null;
        String jobDescription;
        String jobNotificationProvider;
        boolean forceAsync;
        boolean allowCrossRunFileInputs;
        boolean allowLookupByAlternateKey;
        String auditUserComment;
        Map<Object, String> outputData = new HashMap<>();
        String auditDetailsJsonStr;

        // 'json' form field -- allows for multipart forms
        JSONObject json = form.getJson();
        if (json == null)
        {
            // normal json
            json = form.getJsonObject();
        }

        if (json != null)
        {
            Pair<ExpProtocol, AssayProvider> pp = BaseProtocolAPIAction.getProtocolProvider(json, getContainer());
            protocol = pp.first;
            provider = pp.second;

            batchId = json.optLong(AssayJSONConverter.BATCH_ID);
            name = json.optString(ExperimentJSONConverter.NAME, null);
            workflowTaskId = json.optLong(ExperimentJSONConverter.WORKFLOW_TASK);
            if (workflowTaskId == 0)
                workflowTaskId = null;
            comments = json.optString(ExperimentJSONConverter.COMMENT, null);
            forceAsync = json.optBoolean("forceAsync");
            jobDescription = json.optString("jobDescription", null);
            jobNotificationProvider = json.optString("jobNotificationProvider", null);
            allowCrossRunFileInputs = json.optBoolean("allowCrossRunFileInputs");
            allowLookupByAlternateKey = json.optBoolean("allowLookupByAlternateKey");

            JSONObject runPropertiesJson = json.optJSONObject(AssayJSONConverter.RUN_PROPERTIES);
            if (runPropertiesJson != null)
                runProperties = new CaseInsensitiveHashMap<>(runPropertiesJson.toMap());

            JSONObject batchPropertiesJson = json.optJSONObject(AssayJSONConverter.BATCH_PROPERTIES);
            if (batchPropertiesJson != null)
                batchProperties = new CaseInsensitiveHashMap<>(batchPropertiesJson.toMap());

            // CONSIDER: Should we also look at the batch and run properties for the targetStudy?
            targetStudy = json.optString("targetStudy", null);
            reRunId = json.has("reRunId") ? json.optLong("reRunId") : null;
            if (json.has("reImportOption"))
                reImportOption = json.getEnum(AssayRunUploadContext.ReImportOption.class, "reImportOption");
            runFilePath = json.optString("runFilePath", null);
            moduleName = json.optString("module", null);
            auditUserComment  = json.optString("auditUserComment", null);
            auditDetailsJsonStr = json.optString("auditUserComment", null);
            JSONArray dataRows = json.optJSONArray(AssayJSONConverter.DATA_ROWS);
            if (dataRows != null)
                rawData = JsonUtil.toMapList(dataRows);
        }
        else
        {
            Pair<ExpProtocol, AssayProvider> pp = BaseProtocolAPIAction.getProtocolProvider(form.getAssayId(), getContainer());
            protocol = pp.first;
            provider = pp.second;

            batchId = form.getBatchId();
            name = form.getName();
            workflowTaskId = form.getWorkflowTask();
            comments = form.getComment();
            runProperties = new CaseInsensitiveHashMap<>(form.getProperties());
            batchProperties = new CaseInsensitiveHashMap<>(form.getBatchProperties());
            targetStudy = form.getTargetStudy();
            reRunId = form.getReRunId();
            reImportOption = form.getReImportOption();
            runFilePath = form.getRunFilePath();
            moduleName = form.getModule();
            JSONArray dataRows = form.getDataRows();
            if (dataRows != null)
                rawData = JsonUtil.toMapList(dataRows);

            forceAsync = form.isForceAsync();
            jobDescription = form.getJobDescription();
            jobNotificationProvider = form.getJobNotificationProvider();
            allowCrossRunFileInputs = form.isAllowCrossRunFileInputs();
            allowLookupByAlternateKey = form.isAllowLookupByAlternateKey();
            auditUserComment = form.getAuditUserComment();
            auditDetailsJsonStr = form.getAuditDetails();
        }

        if (reImportOption == null)
        {
            if (provider != null && protocol != null && provider.isPlateMetadataEnabled(protocol))
                reImportOption = AssayRunUploadContext.ReImportOption.MERGE_DATA;
            else
                reImportOption = AssayRunUploadContext.ReImportOption.REPLACE;
        }

        // Import the file at runFilePath if it is available, otherwise AssayRunUploadContextImpl.getUploadedData() will use the multi-part form POSTed file
        File file = null;
        if (runFilePath != null && !runFilePath.isEmpty())
        {
            // Resolve file under module resources
            if (moduleName != null && !moduleName.isEmpty())
            {
                Module m = ModuleLoader.getInstance().getModule(moduleName);
                if (m == null)
                    throw new NotFoundException("Could not find module " + moduleName);

                Resource r = m.getModuleResource(runFilePath);
                if (r == null || !r.exists())
                    throw new NotFoundException("Could not find runFilePath \"" + runFilePath + "\". Note, this path should be relative to the module's resource directory.");

                file = ((FileResource)r).getFile();
            }
            else
            {
                try
                {
                    // Resolve file under pipeline root
                    PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
                    if (root == null)
                        throw new NotFoundException("Pipeline root not configured");

                    if (!root.hasPermission(getContainer(), getUser(), ReadPermission.class))
                        throw new UnauthorizedException();

                    // Attempt absolute path first, then relative path from pipeline root
                    File f = new File(runFilePath);
                    if (!root.isUnderRoot(f))
                        f = root.resolvePath(runFilePath);

                    if (!NetworkDrive.exists(f) || !root.isUnderRoot(f))
                        throw new NotFoundException("File not found: " + runFilePath);

                    file = f;
                }
                catch (InvalidPathException e)
                {
                    LOG.info("Invalid path: " + runFilePath, e);
                    throw new NotFoundException("File not found: " + runFilePath);
                }
            }
        }

        if (file != null && rawData != null)
            throw new ExperimentException("Either file or " + AssayJSONConverter.DATA_ROWS + " is allowed, but not both");

        AssayRunUploadContext.Factory<?, ?> factory = provider.createRunUploadFactory(protocol, getViewContext())
                .setName(name)
                .setWorkflowTaskId(workflowTaskId)
                .setComments(comments)
                .setTargetStudy(targetStudy)
                .setReRunId(reRunId)
                .setReImportOption(reImportOption)
                .setLogger(LOG)
                .setAuditUserComment(auditUserComment)
                .setJobDescription(jobDescription)
                .setJobNotificationProvider(jobNotificationProvider)
                .setAllowCrossRunFileInputs(allowCrossRunFileInputs)
                .setAllowLookupByAlternateKey(allowLookupByAlternateKey);

        if (file != null)
        {
            factory.setRawData(null);
            factory.setUploadedData(Collections.singletonMap(PRIMARY_FILE, FileSystemLike.wrapFile(file)));
        }
        else if (rawData != null && !rawData.isEmpty())
        {
            boolean saveDataAsFile = form.isSaveDataAsFile();
            boolean saveMatchingColumnDataOnly = form.isSaveMatchingColumnDataOnly();

            if (saveDataAsFile)
            {
                // try to write out a tmp file containing the imported data so it can be used for transforms or for previewing
                // the original (untransformed) data within, say, a sample management application.
                FileLike dir = AssayFileWriter.ensureUploadDirectory(getContainer());
                // NOTE: We use a 'tmp' file extension so that DataLoaderService will sniff the file type by parsing the file's header.
                var fileObject = createFile(protocol, dir, "tmp");

                List<String> columns = provider.getResultsDomain(protocol).getProperties().stream().map(DomainProperty::getName).collect(Collectors.toList());

                try (TSVMapWriter tsvWriter = saveMatchingColumnDataOnly ? new TSVMapWriter(columns, rawData) : new TSVMapWriter(columns, rawData, true))
                {
                    tsvWriter.setAdditionalQuotedChars(","); // Issue 52272: ensure values with commas in column headers and data are quoted
                    tsvWriter.write(fileObject.toNioPathForWrite().toFile());
                    factory.setRawData(null);
                    factory.setUploadedData(Collections.singletonMap(PRIMARY_FILE, fileObject));
                }
                catch (Exception e)
                {
                    logger.warn("Unable to create temporary file for raw data. Creating result data using the data map.", e);
                    saveDataAsFile = false;
                }
            }

            if (!saveDataAsFile)
            {
                factory.setRawData(MapDataIterator.of(rawData));
                factory.setUploadedData(emptyMap());

                // Create an ExpData for the results if none exists in the outputData map
                DefaultAssayRunCreator.generateResultData(getUser(), getContainer(), provider, rawData, outputData, null);
            }
        }

        boolean success = false;
        AssayFilePropertyWriter<? extends AssayProvider> filePropertyWriter = new AssayFilePropertyWriter<>();

        try (DbScope.Transaction transaction = ExperimentService.get().getSchema().getScope().ensureTransaction(ExperimentService.get().getProtocolImportLock()))
        {
            Map<TransactionAuditProvider.TransactionDetail, Object> transactionDetails = getTransactionAuditDetails();
            if (!StringUtils.isEmpty(auditDetailsJsonStr))
                TransactionAuditProvider.TransactionDetail.addAuditDetails(transactionDetails, auditDetailsJsonStr);
            TransactionAuditProvider.TransactionAuditEvent auditEvent = AbstractQueryUpdateService.createTransactionAuditEvent(getContainer(), reRunId == null ? QueryService.AuditAction.UPDATE : QueryService.AuditAction.INSERT, transactionDetails);
            AbstractQueryUpdateService.addTransactionAuditEvent(transaction, getUser(), auditEvent);
            var auditTransactionEvent = transaction.getAuditEvent();
            Long auditTransactionId = auditTransactionEvent == null ? null : auditTransactionEvent.getRowId();

            // Bind file property values and persist files to the file system.
            {
                Map<String, MultipartFile> fileMap = getFileMap();
                bindAndPersistFilePropertyValues(AssayJSONConverter.BATCH_PROPERTIES, batchProperties, fileMap, filePropertyWriter, auditTransactionEvent, "Assay batch property file uploaded.");
                bindAndPersistFilePropertyValues(AssayJSONConverter.RUN_PROPERTIES, runProperties, fileMap, filePropertyWriter, auditTransactionEvent, "Assay run property file uploaded.");
            }

            AssayRunUploadContext<? extends AssayProvider> uploadContext = factory.setOutputDatas(outputData)
                    .setRunProperties(runProperties)
                    .setBatchProperties(batchProperties)
                    .setTransactionAuditId(auditTransactionId)
                    .setUploadedFiles(filePropertyWriter.getUploadedFiles())
                    .create();

            Pair<ExpExperiment, ExpRun> result = provider.getRunCreator().saveExperimentRun(uploadContext, batchId, forceAsync, getTransactionAuditDetails());
            ExpRun run = result.second;

            transaction.commit();
            success = true;

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("successurl", getUploadWizardCompleteURL(protocol, run));
            resp.put("assayId", protocol.getRowId());
            resp.put("batchId", result.first.getRowId());
            resp.put("auditTransactionId", auditTransactionId);
            // Run id may be null if the import is performed in a background job
            if (run != null)
                resp.put("runId", run.getRowId());

            String asyncJobGUID = uploadContext.getPipelineJobGUID();
            if (!StringUtils.isEmpty(asyncJobGUID))
            {
                auditEvent.addDetail(TransactionAuditProvider.TransactionDetail.ImportOptions, "backgroundImport");
                resp.put("jobId", PipelineService.get().getJobId(getUser(), getContainer(), asyncJobGUID));
            }

            return resp;
        }
        catch (ExperimentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }
        finally
        {
            if (!success)
                filePropertyWriter.cleanupPostedFiles(getContainer(), protocol.getName(), logger);
        }

        return null;
    }

    private void bindAndPersistFilePropertyValues(
        String propertyName,
        CaseInsensitiveHashMap<Object> properties,
        Map<String, MultipartFile> fileMap,
        AssayFilePropertyWriter<? extends AssayProvider> fileWriter,
        TransactionAuditProvider.TransactionAuditEvent auditTransactionEvent,
        String auditComment
    ) throws ExperimentException, ValidationException
    {
        if (properties == null || fileMap == null || fileMap.isEmpty())
            return;

        var filePropertyMap = bindFilePropertyValues(propertyName, properties, fileMap);
        if (filePropertyMap.isEmpty())
            return;

        var fileProperties = fileWriter.savePostedFiles(getContainer(), getUser(), filePropertyMap, auditTransactionEvent, auditComment);
        for (var entry : fileProperties.entrySet())
            properties.put(entry.getKey(), entry.getValue().toNioPathForRead().toString());
    }

    private CaseInsensitiveHashMap<MultipartFile> bindFilePropertyValues(
        String propertyName,
        @NotNull CaseInsensitiveHashMap<Object> properties,
        @NotNull Map<String, MultipartFile> fileMap
    ) throws ValidationException
    {
        String propertyPrefix = propertyName + "[";
        String propertySuffix = "]";

        CaseInsensitiveHashMap<MultipartFile> filePropertyMap = new CaseInsensitiveHashMap<>();

        for (Map.Entry<String, MultipartFile> entry : fileMap.entrySet())
        {
            if (!Strings.CI.startsWith(entry.getKey(), propertyPrefix) || !Strings.CI.endsWith(entry.getKey(), propertySuffix))
                continue;

            String key = entry.getKey().substring(propertyPrefix.length(), entry.getKey().length() - propertySuffix.length());
            key = ImportRunApiForm.parsePropertiesKey(key.trim());
            if (key == null || key.isEmpty())
                continue;

            if (properties.containsKey(key) || filePropertyMap.containsKey(key))
                throw new ValidationException(String.format("Multiple values for %s['%s'] is not supported.", propertyName, key));

            filePropertyMap.put(key, entry.getValue());
        }

        return filePropertyMap;
    }

    protected ActionURL getUploadWizardCompleteURL(ExpProtocol protocol, ExpRun run)
    {
        if (run == null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getShowUploadJobsURL(getContainer(), protocol, null);
        }

        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(getContainer(), protocol, run.getRowId());
    }

    protected static class ImportRunApiForm extends SimpleApiJsonForm implements HasBindParameters
    {
        private Integer _assayId;
        private Long _batchId;
        private String _comment;
        private JSONObject _json;
        private String _name;
        private Long _workflowTask;
        private Long _reRunId;
        private AssayRunUploadContext.ReImportOption _reImportOption;
        private String _targetStudy;
        private Map<String, Object> _properties = new HashMap<>();
        private Map<String, Object> _batchProperties = new HashMap<>();
        private JSONArray _dataRows;
        private String _runFilePath;
        private String _module;
        private boolean _saveDataAsFile;
        private boolean _saveMatchingColumnDataOnly = true;

        private String _jobDescription;
        private String _jobNotificationProvider;
        private boolean _forceAsync;
        private boolean _allowCrossRunFileInputs;
        private boolean _allowLookupByAlternateKey = true;
        private String _auditUserComment = null;
        private String _auditDetails = null;

        public JSONObject getJson()
        {
            return _json;
        }

        public void setJson(JSONObject json)
        {
            _json = json;
        }

        public Integer getAssayId()
        {
            return _assayId;
        }

        public void setAssayId(Integer assayId)
        {
            _assayId = assayId;
        }

        public Long getBatchId()
        {
            return _batchId;
        }

        public void setBatchId(Long batchId)
        {
            _batchId = batchId;
        }

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }

        public Long getWorkflowTask()
        {
            return _workflowTask;
        }

        public void setWorkflowTask(Long workflowTask)
        {
            _workflowTask = workflowTask;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public String getTargetStudy()
        {
            return _targetStudy;
        }

        public void setTargetStudy(String targetStudy)
        {
            _targetStudy = targetStudy;
        }

        public Long getReRunId()
        {
            return _reRunId;
        }

        public void setReRunId(Long reRunId)
        {
            _reRunId = reRunId;
        }

        public AssayRunUploadContext.ReImportOption getReImportOption()
        {
            return _reImportOption;
        }

        public void setReImportOption(AssayRunUploadContext.ReImportOption reImportOption)
        {
            _reImportOption = reImportOption;
        }

        public Map<String, Object> getProperties()
        {
            return _properties;
        }

        public void setProperties(Map<String, Object> properties)
        {
            _properties = properties;
        }

        public Map<String, Object> getBatchProperties()
        {
            return _batchProperties;
        }

        public void setBatchProperties(Map<String, Object> properties)
        {
            _batchProperties = properties;
        }

        public JSONArray getDataRows()
        {
            return _dataRows;
        }

        public void setDataRows(JSONArray dataRows)
        {
            _dataRows = dataRows;
        }

        public String getRunFilePath()
        {
            return _runFilePath;
        }

        public void setRunFilePath(String runFilePath)
        {
            _runFilePath = runFilePath;
        }

        public String getModule()
        {
            return _module;
        }

        public void setModule(String module)
        {
            _module = module;
        }

        public boolean isSaveDataAsFile()
        {
            return _saveDataAsFile;
        }

        public void setSaveDataAsFile(boolean saveDataAsFile)
        {
            _saveDataAsFile = saveDataAsFile;
        }

        public boolean isSaveMatchingColumnDataOnly()
        {
            return _saveMatchingColumnDataOnly;
        }

        public void setSaveMatchingColumnDataOnly(boolean saveMatchingColumnDataOnly)
        {
            _saveMatchingColumnDataOnly = saveMatchingColumnDataOnly;
        }

        public String getJobDescription()
        {
            return _jobDescription;
        }

        public void setJobDescription(String jobDescription)
        {
            _jobDescription = jobDescription;
        }

        public String getJobNotificationProvider()
        {
            return _jobNotificationProvider;
        }

        public void setJobNotificationProvider(String jobNotificationProvider)
        {
            _jobNotificationProvider = jobNotificationProvider;
        }

        public boolean isForceAsync()
        {
            return _forceAsync;
        }

        public void setForceAsync(boolean forceAsync)
        {
            _forceAsync = forceAsync;
        }

        public boolean isAllowCrossRunFileInputs()
        {
            return _allowCrossRunFileInputs;
        }

        public void setAllowCrossRunFileInputs(boolean allowCrossRunFileInputs)
        {
            _allowCrossRunFileInputs = allowCrossRunFileInputs;
        }

        public boolean isAllowLookupByAlternateKey()
        {
            return _allowLookupByAlternateKey;
        }

        public void setAllowLookupByAlternateKey(boolean allowLookupByAlternateKey)
        {
            _allowLookupByAlternateKey = allowLookupByAlternateKey;
        }

        public String getAuditUserComment()
        {
            return _auditUserComment;
        }

        public void setAuditUserComment(String auditUserComment)
        {
            _auditUserComment = auditUserComment;
        }

        public String getAuditDetails()
        {
            return _auditDetails;
        }

        public void setAuditDetails(String auditDetails)
        {
            _auditDetails = auditDetails;
        }

        @Override
        public @NotNull BindException bindParameters(PropertyValues m)
        {
            MutablePropertyValues propertyValues = new MutablePropertyValues();
            for (PropertyValue pv : m.getPropertyValues())
            {
                String name = pv.getName();
                propertyValues.add(name, pv.getValue());

                if (name.endsWith("]"))
                {
                    if (name.startsWith("properties["))
                    {
                        String key = parsePropertiesKey(name.substring("properties[".length(), name.length() - 1));
                        if (key != null)
                        {
                            getProperties().put(key, pv.getValue());
                            propertyValues.removePropertyValue(name);
                        }
                    }
                    else if (name.startsWith("batchProperties["))
                    {
                        String key = parsePropertiesKey(name.substring("batchProperties[".length(), name.length()-1));
                        if (key != null)
                        {
                            getBatchProperties().put(key, pv.getValue());
                            propertyValues.removePropertyValue(name);
                        }
                    }
                }
            }
            return springBindParameters(this, "form", propertyValues);
        }

        private static String parsePropertiesKey(String key)
        {
            if (key == null || key.isEmpty())
                return null;

            // Issue 52119: account for leading/trailing single quotes and decode double quotes and %
            if (key.startsWith("'") && key.endsWith("'"))
                key = key.substring(1, key.length()-1);
            key = PageFlowUtil.decodeQuoteEncodedFormDataKey(key);

            return key;
        }
    }
}
