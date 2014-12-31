/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

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
    private final String _targetStudy;
    private final Integer _reRunId;
    private final Map<String, String> _rawRunProperties;
    private final Map<String, String> _rawBatchProperties;
    private final Map<Object, String> _inputDatas;

    // Lazily created fields
    private Map<String, File> _uploadedData;
    private Map<DomainProperty, String> _runProperties;
    private Map<DomainProperty, String> _batchProperties;

    // Mutable fields
    private TransformResult _transformResult;

    private AssayRunUploadContextImpl(Factory<ProviderType> factory)
    {
        _protocol = factory._protocol;
        _provider = factory._provider;
        _user = factory._user;
        _container = factory._container;
        _context = factory._context;
        _logger = factory._logger;

        _name = factory._name;
        _comments = factory._comments;

        if (factory._rawRunProperties == null)
            _rawRunProperties = Collections.emptyMap();
        else
            _rawRunProperties = Collections.unmodifiableMap(factory._rawRunProperties);

        if (factory._rawBatchProperties == null)
            _rawBatchProperties = Collections.emptyMap();
        else
            _rawBatchProperties = Collections.unmodifiableMap(factory._rawBatchProperties);

        if (factory._inputDatas == null)
            _inputDatas = Collections.emptyMap();
        else
            _inputDatas = Collections.unmodifiableMap(factory._inputDatas);

        _uploadedData = factory._uploadedData;

        _reRunId = factory._reRunId;
        _targetStudy = factory._targetStudy;
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
        return _user;
    }

    @NotNull
    public Container getContainer()
    {
        return _container;
    }

    public HttpServletRequest getRequest()
    {
        return _context != null ? _context.getRequest() : null;
    }

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
    @NotNull
    public Map<String, File> getUploadedData() throws ExperimentException
    {
        if (_uploadedData == null && _context != null)
        {
            try
            {
                AssayDataCollector<AssayRunUploadContextImpl> collector = new FileUploadDataCollector(1, Collections.emptyMap(), FILE_INPUT_NAME);
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

    @NotNull
    @Override
    public Map<Object, String> getInputDatas()
    {
        return _inputDatas;
    }

    public ProviderType getProvider()
    {
        return _provider;
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

    @Override
    public Logger getLogger()
    {
        return _logger;
    }
}
