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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssayUrls;
import org.labkey.api.assay.DefaultAssayRunCreator;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.PlateMetadataDataHandler;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.springframework.validation.BindException;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.labkey.api.assay.AssayDataCollector.PRIMARY_FILE;
import static org.labkey.api.assay.AssayFileWriter.createFile;

/**
 * User: kevink
 * Date: 8/26/12
 */
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

        Integer batchId;
        String name;
        Integer workflowTask;
        String comments;
        Map<String, Object> runProperties;
        Map<String, Object> batchProperties;
        String targetStudy;
        Integer reRunId;
        String runFilePath;
        String moduleName;
        List<Map<String, Object>> rawData = null;
        String jobDescription;
        String jobNotificationProvider;
        boolean forceAsync;
        boolean allowCrossRunFileInputs;

        // TODO: support additional input/output data/materials
        Map<Object, String> inputData = new HashMap<>();
        Map<Object, String> outputData = new HashMap<>();
        Map<Object, String> inputMaterial = new HashMap<>();
        Map<Object, String> outputMaterial = new HashMap<>();


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

            batchId = json.optInt(AssayJSONConverter.BATCH_ID);
            name = json.optString(ExperimentJSONConverter.NAME, null);
            workflowTask = json.optInt(ExperimentJSONConverter.WORKFLOW_TASK);
            if (workflowTask == 0)
                workflowTask = null;
            comments = json.optString(ExperimentJSONConverter.COMMENT, null);
            forceAsync = json.optBoolean("forceAsync");
            jobDescription = json.optString("jobDescription", null);
            jobNotificationProvider = json.optString("jobNotificationProvider", null);
            allowCrossRunFileInputs = json.optBoolean("allowCrossRunFileInputs");

            runProperties = json.optJSONObject(ExperimentJSONConverter.PROPERTIES);
            if (runProperties != null)
                runProperties = new CaseInsensitiveHashMap<>(runProperties);

            batchProperties = json.optJSONObject("batchProperties");
            if (batchProperties != null)
                batchProperties = new CaseInsensitiveHashMap<>(batchProperties);

            // CONSIDER: Should we also look at the batch and run properties for the targetStudy?
            targetStudy = json.optString("targetStudy", null);
            reRunId = json.containsKey("reRunId") ? json.optInt("reRunId") : null;
            runFilePath = json.optString("runFilePath", null);
            moduleName = json.optString("module", null);
            JSONArray dataRows = json.optJSONArray(AssayJSONConverter.DATA_ROWS);
            if (dataRows != null)
                rawData = dataRows.toMapList();
        }
        else
        {
            Pair<ExpProtocol, AssayProvider> pp = BaseProtocolAPIAction.getProtocolProvider(form.getAssayId(), getContainer());
            protocol = pp.first;
            provider = pp.second;

            batchId = form.getBatchId();
            name = form.getName();
            workflowTask = form.getWorkflowTask();
            comments = form.getComment();
            runProperties = form.getProperties();
            batchProperties = form.getBatchProperties();
            targetStudy = form.getTargetStudy();
            reRunId = form.getReRunId();
            runFilePath = form.getRunFilePath();
            moduleName = form.getModule();
            JSONArray dataRows = form.getDataRows();
            if (dataRows != null)
                rawData = dataRows.toMapList();

            forceAsync = form.isForceAsync();
            jobDescription = form.getJobDescription();
            jobNotificationProvider = form.getJobNotificationProvider();
            allowCrossRunFileInputs = form.isAllowCrossRunFileInputs();
        }

        // Import the file at runFilePath if it is available, otherwise AssayRunUploadContextImpl.getUploadedData() will use the multi-part form POSTed file
        File file = null;
        if (runFilePath != null && runFilePath.length() > 0)
        {
            // Resolve file under module resources
            if (moduleName != null && moduleName.length() > 0)
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

                if (f == null || !NetworkDrive.exists(f) || !root.isUnderRoot(f))
                    throw new NotFoundException("File not found: " + runFilePath);

                file = f;
            }
        }

        AssayRunUploadContext.Factory<? extends AssayProvider, ? extends AssayRunUploadContext.Factory> factory
                = provider.createRunUploadFactory(protocol, getViewContext());
        factory.setName(name)
                .setWorkflowTask(workflowTask)
                .setComments(comments)
                .setRunProperties(runProperties)
                .setBatchProperties(batchProperties)
                .setTargetStudy(targetStudy)
                .setReRunId(reRunId)
                .setLogger(LOG)
                .setJobDescription(jobDescription)
                .setJobNotificationProvider(jobNotificationProvider)
                .setAllowCrossRunFileInputs(allowCrossRunFileInputs);

        if (file != null && rawData != null)
            throw new ExperimentException("Either file or " + AssayJSONConverter.DATA_ROWS + " is allowed, but not both");

        if (file != null)
        {
            factory.setRawData(null);
            factory.setUploadedData(Collections.singletonMap(PRIMARY_FILE, file));
        }
        else if (rawData != null && !rawData.isEmpty())
        {
            boolean saveDataAsFile = form.isSaveDataAsFile();

            if (saveDataAsFile)
            {
                // try to write out a tmp file containing the imported data so it can be used for transforms or for previewing
                // the original (untransformed) data within, say, a sample management application.
                File dir = AssayFileWriter.ensureUploadDirectory(getContainer());
                // NOTE: We use a 'tmp' file extension so that DataLoaderService will sniff the file type by parsing the file's header.
                file = createFile(protocol, dir, "tmp");
                try (TSVMapWriter tsvWriter = new TSVMapWriter(rawData))
                {
                    tsvWriter.write(file);
                    factory.setRawData(null);
                    factory.setUploadedData(Collections.singletonMap(PRIMARY_FILE, file));
                }
                catch (Exception e)
                {
                    logger.warn("Unable to create temporary file for raw data. Creating result data using the data map.", e);
                    saveDataAsFile = false;
                }
            }

            if (!saveDataAsFile)
            {
                factory.setRawData(rawData);
                factory.setUploadedData(Collections.emptyMap());

                // Create an ExpData for the results if none exists in the outputData map
                DefaultAssayRunCreator.generateResultData(getUser(), getContainer(), provider, rawData, outputData, null);
            }
        }

        if (form.getPlateMetadata() != null)
        {
            AssayPlateMetadataService svc = AssayPlateMetadataService.getService(PlateMetadataDataHandler.DATA_TYPE);
            if (svc != null)
            {
                ExpData plateData = DefaultAssayRunCreator.createData(getContainer(), "Plate Metadata", PlateMetadataDataHandler.DATA_TYPE, null);
                plateData.save(getUser());
                outputData.put(plateData, ExpDataRunInput.DEFAULT_ROLE);

                factory.setRawPlateMetadata(svc.parsePlateMetadata(form.getPlateMetadata()));
            }
        }

        factory.setInputDatas(inputData)
                .setOutputDatas(outputData)
                .setInputMaterials(inputMaterial)
                .setOutputMaterials(outputMaterial);

        AssayRunUploadContext<? extends AssayProvider> uploadContext = factory.create();

        try
        {
            Pair<ExpExperiment, ExpRun> result = provider.getRunCreator().saveExperimentRun(uploadContext, batchId, forceAsync);
            ExpRun run = result.second;

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("successurl", getUploadWizardCompleteURL(protocol, run));
            resp.put("assayId", protocol.getRowId());
            resp.put("batchId", result.first.getRowId());
            // Run id may be null if the import is performed in a background job
            if (run != null)
                resp.put("runId", run.getRowId());

            String asyncJobGUID = uploadContext.getPipelineJobGUID();
            if (!StringUtils.isEmpty(asyncJobGUID))
                resp.put("jobId", PipelineService.get().getJobId(getUser(), getContainer(), asyncJobGUID));

            return resp;
        }
        catch (ExperimentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }

        return null;
    }

    protected ActionURL getUploadWizardCompleteURL(ExpProtocol protocol, ExpRun run)
    {
        if (run == null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getShowUploadJobsURL(getContainer(), protocol, null);
        }

        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(getContainer(), protocol, run.getRowId());
    }

    protected static class ImportRunApiForm extends SimpleApiJsonForm
    {
        private Integer _assayId;
        private Integer _batchId;
        private String _comment;
        private JSONObject _json;
        private String _name;
        private Integer _workflowTask;
        private Integer _reRunId;
        private String _targetStudy;
        private Map<String, Object> _properties = new HashMap<>();
        private Map<String, Object> _batchProperties = new HashMap<>();
        private JSONArray _dataRows;
        private String _runFilePath;
        private String _module;
        private boolean _saveDataAsFile;
        private JSONObject _plateMetadata;

        private String _jobDescription;
        private String _jobNotificationProvider;
        private boolean _forceAsync;
        private boolean _allowCrossRunFileInputs;

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

        public Integer getBatchId()
        {
            return _batchId;
        }

        public void setBatchId(Integer batchId)
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

        public Integer getWorkflowTask()
        {
            return _workflowTask;
        }

        public void setWorkflowTask(Integer workflowTask)
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

        public Integer getReRunId()
        {
            return _reRunId;
        }

        public void setReRunId(Integer reRunId)
        {
            _reRunId = reRunId;
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

        public JSONObject getPlateMetadata()
        {
            return _plateMetadata;
        }

        public void setPlateMetadata(JSONObject plateMetadata)
        {
            _plateMetadata = plateMetadata;
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
    }

}
