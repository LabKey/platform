/*
 * Copyright (c) 2013-2018 LabKey Corporation
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
package org.labkey.api.assay;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.util.URIUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static java.util.Collections.unmodifiableMap;

/**
 * User: kevink
 * Date: 12/18/13
 */
public class AssayRunUploadContextImpl<ProviderType extends AssayProvider> implements AssayRunUploadContext<ProviderType>
{
    private static final String FILE_INPUT_NAME = "file";

    // Required fields
    private final ExpProtocol _protocol;
    private final ProviderType _provider;
    private final User _user;
    private final Container _container;

    // Optional fields
    private final Logger _logger;
    private final ViewContext _context;
    private final String _comments;
    private final String _name;
    private final Integer _workflowTask;
    private final String _targetStudy;
    private final Integer _reRunId;
    private final Map<String, Object> _rawRunProperties;
    private final Map<String, Object> _rawBatchProperties;
    private final List<Map<String, Object>> _rawData;
    private final Map<String, AssayPlateMetadataService.MetadataLayer> _rawPlateMetadata;
    private final Map<?, String> _inputDatas;
    private final Map<?, String> _inputMaterials;
    private final Map<?, String> _outputDatas;
    private final Map<?, String> _outputMaterials;
    private final boolean _allowCrossRunFileInputs;

    // Lazily created fields
    private Map<String, File> _uploadedData;
    private Map<DomainProperty, String> _runProperties;
    private Map<DomainProperty, String> _batchProperties;
    private Map<String, Object> _unresolvedRunProperties;

    // Mutable fields
    private TransformResult _transformResult;

    // async fields
    protected String _jobDescription;
    protected String _jobNotificationProvider;
    protected String _pipelineJobGUID;

    private AssayRunUploadContextImpl(Factory<ProviderType> factory)
    {
        _protocol = factory._protocol;
        _provider = factory._provider;
        _user = factory._user;
        _container = factory._container;
        _context = factory._context;
        _logger = factory._logger;

        _name = factory._name;
        _workflowTask = factory._workflowTask;
        _comments = factory._comments;

        _rawRunProperties = factory._rawRunProperties == null ? emptyMap() : unmodifiableMap(factory._rawRunProperties);
        _rawBatchProperties = factory._rawBatchProperties == null ? emptyMap() : unmodifiableMap(factory._rawBatchProperties);

        _inputDatas = factory._inputDatas == null ? emptyMap() : unmodifiableMap(factory._inputDatas);
        _inputMaterials = factory._inputMaterials == null ? emptyMap() : unmodifiableMap(factory._inputMaterials);
        _outputDatas = factory._outputDatas == null ? emptyMap() : unmodifiableMap(factory._outputDatas);
        _outputMaterials = factory._outputMaterials == null ? emptyMap() : unmodifiableMap(factory._outputMaterials);
        _allowCrossRunFileInputs = factory._allowCrossRunFileInputs;

        // TODO: Wrap the rawData in an unmodifiableList -- unfortunately, AbstractAssayTsvDataHandler.checkData mutates the list items in-place
        _rawData = factory._rawData;
        _rawPlateMetadata = factory._rawPlateMetadata;

        _uploadedData = factory._uploadedData;

        _reRunId = factory._reRunId;
        _targetStudy = factory._targetStudy;

        _jobDescription = factory._jobDescription;
        _jobNotificationProvider = factory._jobNotificationProvider;
    }

    public static class Factory<ProviderType extends AssayProvider> extends AssayRunUploadContext.Factory<ProviderType, Factory<ProviderType>>
    {
        public Factory(
                @NotNull ExpProtocol protocol,
                @NotNull ProviderType provider,
                @NotNull ViewContext context)
        {
            super(protocol, provider, context.getUser(), context.getContainer());
            setViewContext(context);
        }

        public Factory(
                @NotNull ExpProtocol protocol,
                @NotNull ProviderType provider,
                @NotNull User user,
                @NotNull Container container)
        {
            super(protocol, provider, user, container);
        }

        @Override
        public Factory self()
        {
            return this;
        }

        @Override
        public AssayRunUploadContext<ProviderType> create()
        {
            return new AssayRunUploadContextImpl<>(this);
        }
    }

    @Override
    @NotNull
    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    @Override
    public Map<DomainProperty, String> getRunProperties()
    {
        if (_runProperties == null)
        {
            Domain runDomain = _provider.getRunDomain(_protocol);
            _unresolvedRunProperties = new HashMap<>();
            _runProperties = propertiesFromRawValues(runDomain, _rawRunProperties, _unresolvedRunProperties);
        }
        return _runProperties;
    }

    @Override
    public Map<DomainProperty, String> getBatchProperties()
    {
        if (_batchProperties == null)
        {
            Domain batchDomain = _provider.getBatchDomain(_protocol);
            _batchProperties = propertiesFromRawValues(batchDomain, _rawBatchProperties, null);
        }
        return _batchProperties;
    }

    @Override
    public Map<String, Object> getUnresolvedRunProperties()
    {
        return _unresolvedRunProperties;
    }

    private Map<DomainProperty, String> propertiesFromRawValues(Domain domain, Map<String, Object> rawProperties, Map<String, Object> unresolvedProperties)
    {
        Map<DomainProperty, String> properties = new HashMap<>();
        if (rawProperties != null)
        {
            for (DomainProperty prop : domain.getProperties())
            {
                Object value;
                if (rawProperties.containsKey(prop.getName()))
                    value = rawProperties.get(prop.getName());
                else
                    value = rawProperties.get(prop.getPropertyURI());
                properties.put(prop, Objects.toString(value, null));
            }

            addVocabularyAndUnresolvedRunProperties(properties, rawProperties, unresolvedProperties);
        }

        return unmodifiableMap(properties);
    }

    private void addVocabularyAndUnresolvedRunProperties(Map<DomainProperty, String> properties, Map<String, Object> rawProperties, Map<String, Object> unresolvedProperties)
    {
        // 1. Properties belonging to a VocabularyDomain will be added to the run properties.
        // 2. This is the only implementation of AssayRunUploadContext for adding these properties as importRuns Api uses this implementation.
        // 3. Provenance object input property will be added to the unresolved run properties.

        for (Map.Entry<String, Object> property : rawProperties.entrySet())
        {
            if (URIUtil.hasURICharacters(property.getKey()))
            {
                PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(property.getKey(), _container);

                if (null == pd)
                {
                    throw new NotFoundException("Property URI is not valid - " + property.getKey());
                }
                List<Domain> domains = OntologyManager.getDomainsForPropertyDescriptor(_container, pd);
                List<Domain> vocabularyDomains = domains.stream().filter(d -> d.getDomainKind().getKindName().equalsIgnoreCase(ExperimentJSONConverter.VOCABULARY_DOMAIN)).collect(Collectors.toList());

                if (vocabularyDomains.isEmpty())
                {
                    throw new NotFoundException("No Vocabularies found for this property - " + property.getKey());
                }

                DomainProperty dp = vocabularyDomains.get(0).getPropertyByURI(property.getKey());
                if (!properties.containsKey(dp))
                {
                    properties.put(dp, property.getValue().toString());
                }
            }
            else if (null != unresolvedProperties)
            {
                unresolvedProperties.put(property.getKey(),property.getValue());
            }

        }
    }

    @Override
    public String getComments()
    {
        return _comments;
    }

    @Override
    public String getName()
    {
        return _name;
    }

    @Override
    public Integer getWorkflowTask()
    {
        return _workflowTask;
    }

    @Override
    public User getUser()
    {
        return _user;
    }

    @Override
    @NotNull
    public Container getContainer()
    {
        return _container;
    }

    @Override
    public HttpServletRequest getRequest()
    {
        return _context != null ? _context.getRequest() : null;
    }

    @Override
    public ActionURL getActionURL()
    {
        return _context != null ? _context.getActionURL() : null;
    }

    /**
     * Map of file name to uploaded file that will be imported by the assay's DataHandler.
     * The uploaded file is expected to be POSTed as a form-data parameter named '<code>file</code>'.
     * The file will be added as an output ExpData to the imported assay run.
     *
     * @return A singleton map with key {@link AssayDataCollector#PRIMARY_FILE} and value of the uploaded file.
     * @throws ExperimentException
     */
    @Override
    @NotNull
    public Map<String, File> getUploadedData() throws ExperimentException
    {
        if (_uploadedData == null && _context != null)
        {
            try
            {
                AssayDataCollector<AssayRunUploadContextImpl<?>> collector = new FileUploadDataCollector<>(1, emptyMap(), FILE_INPUT_NAME);
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

    @Nullable
    @Override
    public List<Map<String, Object>> getRawData()
    {
        return _rawData;
    }

    @Override
    public @Nullable Map<String, AssayPlateMetadataService.MetadataLayer> getRawPlateMetadata()
    {
        return _rawPlateMetadata;
    }

    @NotNull
    @Override
    public Map<? extends Object, String> getInputDatas()
    {
        return _inputDatas;
    }

    @NotNull
    @Override
    public Map<? extends Object, String> getOutputDatas()
    {
        return _outputDatas;
    }

    @NotNull
    @Override
    public Map<? extends Object, String> getInputMaterials()
    {
        return _inputMaterials;
    }

    @NotNull
    @Override
    public Map<? extends Object, String> getOutputMaterials()
    {
        return _outputMaterials;
    }

    @Override
    public boolean isAllowCrossRunFileInputs()
    {
        return _allowCrossRunFileInputs;
    }

    @Override
    public ProviderType getProvider()
    {
        return _provider;
    }

    @Override
    public Integer getReRunId()
    {
        return _reRunId;
    }

    @Override
    public String getTargetStudy()
    {
        return _targetStudy;
    }

    @Override
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
    public void uploadComplete(ExpRun run)
    {
        // no-op
    }

    @Override
    public String getJobDescription()
    {
        return _jobDescription;
    }

    @Override
    public String getJobNotificationProvider()
    {
        return _jobNotificationProvider;
    }

    @Override
    public String getPipelineJobGUID()
    {
        return _pipelineJobGUID;
    }

    @Override
    public void setPipelineJobGUID(String jobGUID)
    {
        _pipelineJobGUID = jobGUID;
    }

    @Override
    public Logger getLogger()
    {
        return _logger;
    }

}
