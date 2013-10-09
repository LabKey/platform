/*
 *  Copyright (c) 2012-2013 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiVersion;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.AssayJSONConverter;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.study.assay.AssayDataCollector;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.FileUploadDataCollector;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * User: kevink
 * Date: 8/26/12
 */
@ActionNames("importRun")
@RequiresPermissionClass(InsertPermission.class)
@ApiVersion(12.3)
public class ImportRunApiAction<ProviderType extends AssayProvider> extends MutatingApiAction<ImportRunApiAction.ImportRunApiForm>
{
    private static final String FILE_INPUT_NAME = "file";

    @Override
    public ApiResponse execute(ImportRunApiForm form, BindException errors) throws Exception
    {
        ExpProtocol protocol;
        AssayProvider provider;
        AssayRunUploadContext<ProviderType> uploadContext;
        Integer batchId;

        JSONObject json = form.getJson();
        if (json != null)
        {
            Pair<ExpProtocol, AssayProvider> pp = AbstractAssayAPIAction.getProtocolProvider(json, getViewContext().getContainer());
            protocol = pp.first;
            provider = pp.second;

            batchId = json.optInt(AssayJSONConverter.BATCH_ID);
            String name = json.optString(ExperimentJSONConverter.NAME);
            String comments = json.optString(ExperimentJSONConverter.COMMENT);
            Map<String, String> runProperties = (Map)json.optJSONObject(ExperimentJSONConverter.PROPERTIES);
            Map<String, String> batchProperties = (Map)json.optJSONObject("batchProperties");
            String targetStudy = json.optString("targetStudy");
            Integer reRunId = json.containsKey("reRunId") ? json.optInt("reRunId") : null;

            uploadContext = new ImportRunApiUploadContext<>(
                    protocol,
                    (ProviderType)provider,
                    getViewContext(),
                    name,
                    comments,
                    runProperties,
                    batchProperties,
                    targetStudy,
                    reRunId);
        }
        else
        {
            Pair<ExpProtocol, AssayProvider> pp = AbstractAssayAPIAction.getProtocolProvider(form.getAssayId(), getViewContext().getContainer());
            protocol = pp.first;
            provider = pp.second;

            batchId = form.getBatchId();

            uploadContext = new ImportRunApiUploadContext<>(
                    protocol,
                    (ProviderType)provider,
                    getViewContext(),
                    form.getName(),
                    form.getComment(),
                    form.getProperties(),
                    form.getBatchProperties(),
                    form.getTargetStudy(),
                    form.getReRunId());
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
            resp.put("runId", run.getRowId());

            return resp;
        }
//        catch (ValidationException ve)
//        {
//            for (ValidationError error : ve.getErrors())
//                errors.addError(new LabkeyError(error.getMessage()));
//        }
        catch (ExperimentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }

        return null;
    }

    protected ActionURL getUploadWizardCompleteURL(ExpProtocol protocol, ExpRun run)
    {
        Container c = getViewContext().getContainer();
        if (run == null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getShowUploadJobsURL(c, protocol, null);
        }
        else
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayResultsURL(c, protocol, run.getRowId());
            //return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(form.getContainer(), protocol);
        }
    }

    protected static class ImportRunApiForm extends SimpleApiJsonForm
    {
        private JSONObject _json;

        public JSONObject getJson()
        {
            return _json;
        }

        public void setJson(JSONObject json)
        {
            _json = json;
        }

        private Integer _assayId;
        private Integer _batchId;
        private String _name;
        private String _comment;
        private String _targetStudy;
        private Integer _reRunId;
        private Map<String, String> _properties = new HashMap<>();
        private Map<String, String> _batchProperties = new HashMap<>();

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

    protected static class ImportRunApiUploadContext<ProviderType extends AssayProvider> implements AssayRunUploadContext<ProviderType>
    {
        private ExpProtocol _protocol;
        private ProviderType _provider;
        private ViewContext _context;
        private Map<String, File> _uploadedData;
        private String _comments;
        private String _name;
        private String _targetStudy;
        private Integer _reRunId;

        private Map<String, String> _rawRunProperties;
        private Map<String, String> _rawBatchProperties;
        private Map<DomainProperty, String> _runProperties;
        private Map<DomainProperty, String> _batchProperties;

        private TransformResult _transformResult;

        public ImportRunApiUploadContext(
                @NotNull ExpProtocol protocol, @NotNull ProviderType provider, ViewContext context,
                String name, String comment,
                Map<String, String> runProperties,
                Map<String, String> batchProperties,
                String targetStudy,
                Integer reRunId)
        {
            _protocol = protocol;
            _provider = provider;
            _context = context;

            _name = name;
            _comments = comment;

            _rawRunProperties = runProperties;
            _rawBatchProperties = batchProperties;

            _reRunId = reRunId;
            _targetStudy = targetStudy;
        }

        @NotNull
        public ExpProtocol getProtocol()
        {
            return _protocol;
        }

        public Map<DomainProperty, String> getRunProperties() throws ExperimentException
        {
            if (_runProperties == null)
            {
                Map<DomainProperty, String> properties = new HashMap<>();
                if (_rawRunProperties != null)
                {
                    for (DomainProperty prop : _provider.getRunDomain(_protocol).getProperties())
                    {
                        String value;
                        if (_rawRunProperties.containsKey(prop.getName()))
                            value = _rawRunProperties.get(prop.getName());
                        else
                            value = _rawRunProperties.get(prop.getPropertyURI());
                        properties.put(prop, value);
                    }

                }
                _runProperties = properties;
            }
            return _runProperties;
        }

        public Map<DomainProperty, String> getBatchProperties()
        {
            if (_batchProperties == null)
            {
                Map<DomainProperty, String> properties = new HashMap<>();
                if (_rawBatchProperties != null)
                {
                    for (DomainProperty prop : _provider.getBatchDomain(_protocol).getProperties())
                    {
                        String value;
                        if (_rawBatchProperties.containsKey(prop.getName()))
                            value = _rawBatchProperties.get(prop.getName());
                        else
                            value = _rawBatchProperties.get(prop.getPropertyURI());
                        properties.put(prop, value);
                    }

                }
                _batchProperties = properties;
            }
            return _batchProperties;
        }

        public String getComments()
        {
            return _comments;
        }

        public String getName()
        {
            return _name;
        }

        public User getUser()
        {
            return _context.getUser();
        }

        public Container getContainer()
        {
            return _context.getContainer();
        }

        public HttpServletRequest getRequest()
        {
            return _context.getRequest();
        }

        public ActionURL getActionURL()
        {
            return _context.getActionURL();
        }

        @NotNull
        public Map<String, File> getUploadedData() throws ExperimentException
        {
            if (_uploadedData == null)
            {
                try
                {
                    AssayDataCollector<ImportRunApiUploadContext> collector = new FileUploadDataCollector(1, Collections.emptyMap(), FILE_INPUT_NAME);
                    Map<String, File> files = collector.createData(this);
                    // HACK: rekey the map using PRIMARY_FILE instead of FILE_INPUT_NAME
                    _uploadedData = Collections.singletonMap(AssayDataCollector.PRIMARY_FILE, files.get(FILE_INPUT_NAME));
                }
                catch (IOException e)
                {
                    throw new ExperimentException(e);
                }
            }
            return _uploadedData;
        }

        public ProviderType getProvider()
        {
            return _provider;
        }

        public Map<DomainProperty, Object> getDefaultValues(Domain domain, String scope) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public Map<DomainProperty, Object> getDefaultValues(Domain domain) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void saveDefaultValues(Map<DomainProperty, String> values, String scope) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void saveDefaultBatchValues() throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void saveDefaultRunValues() throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        public void clearDefaultValues(Domain domain) throws ExperimentException
        {
            throw new UnsupportedOperationException("Not Supported");
        }

        @Override
        public Integer getReRunId()
        {
            return _reRunId;
        }

        public String getTargetStudy()
        {
            return _targetStudy;
        }

        public TransformResult getTransformResult()
        {
            return _transformResult == null ? DefaultTransformResult.createEmptyResult() : _transformResult;
        }

        @Override
        public void setTransformResult(TransformResult result)
        {
            _transformResult = result;
        }
        
        @Override
        public void uploadComplete(ExpRun run) throws ExperimentException
        {
            // no-op
        }
    }
}
