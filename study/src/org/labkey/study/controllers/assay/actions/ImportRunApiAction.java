/*
 *  Copyright (c) 2012 LabKey Corporation
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
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
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
        JSONObject root = form.getRoot();
        if (root == null)
            throw new IllegalAccessException("Expected root parameter");

        Pair<ExpProtocol, AssayProvider> pp = AbstractAssayAPIAction.getProtocolProvider(root, getViewContext().getContainer());
        ExpProtocol protocol = pp.first;
        AssayProvider provider = pp.second;

        Integer batchId = null;
        if (root.has(AbstractAssayAPIAction.BATCH_ID))
            batchId = root.getInt(AbstractAssayAPIAction.BATCH_ID);

        String name = null;
        if (root.has(ExperimentJSONConverter.NAME))
            name = root.getString(ExperimentJSONConverter.NAME);

        String comments = null;
        if (root.has(ExperimentJSONConverter.COMMENT))
            comments = root.getString(ExperimentJSONConverter.COMMENT);

        try
        {
            AssayRunUploadContext<ProviderType> uploadContext = new ImportRunApiUploadContext<ProviderType>(
                    protocol,
                    (ProviderType)provider,
                    getViewContext(),
                    name,
                    comments);

            Pair<ExpExperiment, ExpRun> result = provider.getRunCreator().saveExperimentRun(uploadContext, batchId);
            ExpRun run = result.second;

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("successurl", getUploadWizardCompleteURL(protocol, run));

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
        JSONObject _root;

        public JSONObject getRoot()
        {
            return _root;
        }

        public void setRoot(JSONObject root)
        {
            _root = root;
        }
    }

    protected static class ImportRunApiUploadContext<ProviderType extends AssayProvider> implements AssayRunUploadContext<ProviderType>
    {
        private ExpProtocol _protocol;
        private ProviderType _provider;
        private ViewContext _context;
        private Map _uploadedData;
        private String _comments;
        private String _name;

        public ImportRunApiUploadContext(
                @NotNull ExpProtocol protocol, @NotNull ProviderType provider, ViewContext context,
                String name, String comments)
        {
            _protocol = protocol;
            _provider = provider;
            _context = context;

            _name = name;
            _comments = comments;
        }

        @NotNull
        public ExpProtocol getProtocol()
        {
            return _protocol;
        }

        public Map<DomainProperty, String> getRunProperties() throws ExperimentException
        {
            Map<DomainProperty, String> properties = new HashMap<DomainProperty, String>();
            for (DomainProperty prop : _provider.getRunDomain(_protocol).getProperties())
                ;//properties.put(prop, getSampleValue(prop));

            return properties;
        }

        public Map<DomainProperty, String> getBatchProperties()
        {
            Map<DomainProperty, String> properties = new HashMap<DomainProperty, String>();
            for (DomainProperty prop : _provider.getBatchDomain(_protocol).getProperties())
                ;//properties.put(prop, getSampleValue(prop));

            return properties;
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
                    AssayDataCollector<ImportRunApiUploadContext> collector = new FileUploadDataCollector(1, FILE_INPUT_NAME);
                    _uploadedData = collector.createData(this);
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
            return null;
        }

        public String getTargetStudy()
        {
            return null;
        }

        public TransformResult getTransformResult()
        {
            return null;
        }

        @Override
        public void setTransformResult(TransformResult result)
        {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public void uploadComplete(ExpRun run) throws ExperimentException
        {
            // no-op
        }
    }
}
