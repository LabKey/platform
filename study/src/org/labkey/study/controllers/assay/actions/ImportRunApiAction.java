/*
 *  Copyright (c) 2012-2015 LabKey Corporation
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

package org.labkey.study.controllers.assay.actions;

import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.springframework.validation.BindException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.labkey.api.study.assay.AssayDataCollector.PRIMARY_FILE;

/**
 * User: kevink
 * Date: 8/26/12
 */
@ActionNames("importRun")
@RequiresPermission(InsertPermission.class)
@ApiVersion(12.3)
public class ImportRunApiAction<ProviderType extends AssayProvider> extends MutatingApiAction<ImportRunApiAction.ImportRunApiForm>
{
    @Override
    public ApiResponse execute(ImportRunApiForm form, BindException errors) throws Exception
    {
        ExpProtocol protocol;
        AssayProvider provider;
        AssayRunUploadContext<ProviderType> uploadContext;
        Integer batchId;

        // 'json' form field -- allows for multipart forms
        JSONObject json = form.getJson();
        if (json == null)
        {
            // normal json
            json = form.getJsonObject();
        }

        if (json != null)
        {
            Pair<ExpProtocol, AssayProvider> pp = AbstractAssayAPIAction.getProtocolProvider(json, getContainer());
            protocol = pp.first;
            provider = pp.second;

            AssayRunUploadContext.Factory factory = provider.createRunUploadFactory(protocol, getViewContext());

            batchId = json.optInt(AssayJSONConverter.BATCH_ID);
            String name = json.optString(ExperimentJSONConverter.NAME);
            String comments = json.optString(ExperimentJSONConverter.COMMENT);
            Map<String, String> runProperties = (Map)json.optJSONObject(ExperimentJSONConverter.PROPERTIES);
            if (runProperties != null)
                runProperties = new CaseInsensitiveHashMap<>(runProperties);

            Map<String, String> batchProperties = (Map)json.optJSONObject("batchProperties");
            if (batchProperties != null)
                batchProperties = new CaseInsensitiveHashMap<>(batchProperties);

            // CONSIDER: Should we also look at the batch and run properties for the targetStudy?
            String targetStudy = json.optString("targetStudy");
            Integer reRunId = json.containsKey("reRunId") ? json.optInt("reRunId") : null;

            String moduleName = json.optString("module");
            if (moduleName != null)
            {
                Module m = ModuleLoader.getInstance().getModuleForSchemaName(moduleName);
                if (m == null)
                {
                    throw new NotFoundException("Could not find module " + moduleName);
                }

                String runFilePath = json.optString("runFilePath");
                if (runFilePath == null)
                {
                    throw new NotFoundException("\"runFilePath\" is required when importing from a module.");
                }

                Resource r = m.getModuleResource(runFilePath);

                if (r == null || !r.exists())
                {
                    throw new NotFoundException("Could not find runFilePath \"" + runFilePath + "\". Note, this path should be relative to the module's resource directory.");
                }

                factory.setUploadedData(Collections.singletonMap(PRIMARY_FILE, ((FileResource) r).getFile()));
            }

            factory.setName(name)
                   .setComments(comments)
                   .setRunProperties(runProperties)
                   .setBatchProperties(batchProperties)
                   .setTargetStudy(targetStudy)
                   .setReRunId(reRunId);

            uploadContext = factory.create();
        }
        else
        {
            Pair<ExpProtocol, AssayProvider> pp = AbstractAssayAPIAction.getProtocolProvider(form.getAssayId(), getContainer());
            protocol = pp.first;
            provider = pp.second;

            batchId = form.getBatchId();

            AssayRunUploadContext.Factory factory = provider.createRunUploadFactory(protocol, getViewContext());

            factory.setName(form.getName())
                    .setComments(form.getComment())
                    .setRunProperties(form.getProperties())
                    .setBatchProperties(form.getBatchProperties())
                    .setTargetStudy(form.getTargetStudy())
                    .setReRunId(form.getReRunId());

            uploadContext = factory.create();
        }

        try
        {

            Pair<ExpExperiment, ExpRun> result = provider.getRunCreator().saveExperimentRun(uploadContext, batchId);
            ExpRun run = result.second;

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("successurl", getUploadWizardCompleteURL(protocol, run));
            resp.put("assayId", protocol.getRowId());
            resp.put("batchId", result.first.getRowId());
            // Run id may be null if the import is performed in a background job
            if (run != null)
                resp.put("runId", run.getRowId());

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
        private Integer _reRunId;
        private String _targetStudy;
        private Map<String, String> _properties = new HashMap<>();
        private Map<String, String> _batchProperties = new HashMap<>();

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

        public Map<String, String> getProperties()
        {
            return _properties;
        }

        public void setProperties(Map<String, String> properties)
        {
            _properties = properties;
        }

        public Map<String, String> getBatchProperties()
        {
            return _batchProperties;
        }

        public void setBatchProperties(Map<String, String> properties)
        {
            _batchProperties = properties;
        }
    }

}
