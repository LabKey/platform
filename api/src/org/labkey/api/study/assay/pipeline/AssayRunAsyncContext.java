/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.study.assay.pipeline;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayRunUploadContext;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.ActionURL;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Context about an assay run import/upload that can be serialized out as part of a pipeline job and used
 * asynchronously to run the transform and/or validation scripts, and actually create the run.
 * User: jeckels
 * Date: Feb 13, 2012
 */
public class AssayRunAsyncContext<ProviderType extends AssayProvider> implements AssayRunUploadContext<ProviderType>, Serializable
{
    public static final String UNIT_TESTING_PROTOCOL_NAME = "LABKEYTESTINGPROTOCOL";
    // Store the RowIds, etc of objects so that we can safely serialize and deserialize them
    private int _userId;
    private int _protocolId;
    private String _targetStudy;
    private String _containerId;
    private String _runName;
    private String _runComments;
    private ActionURL _actionURL;
    private Map<String, File> _uploadedData;
    /** propertyId -> value */
    private Map<Integer, String> _runPropertiesById;
    /** propertyId -> value */
    private Map<Integer, String> _batchPropertiesById;
    /** RowIds for all the domains associated with properties we need to remember */
    private Set<Integer> _domainIds = new HashSet<>();
    private Integer _reRunId;

    private File _originalFileLocation;

    // Cached values that aren't serializable
    private transient User _user;
    private transient ExpProtocol _protocol;
    private transient ProviderType _provider;
    private transient Container _container;
    private transient Map<DomainProperty, String> _runProperties;
    private transient Map<DomainProperty, String> _batchProperties;
    private transient Set<Domain> _domains;
    private transient TransformResult _transformResult;
    private transient Logger _logger;

    public AssayRunAsyncContext(AssayRunUploadContext<ProviderType> originalContext) throws IOException, ExperimentException
    {
        // Cache the values, and remember their ids so that we can refetch the objects if needed
        _user = originalContext.getUser();
        _userId = _user.getUserId();
        _protocol = originalContext.getProtocol();
        _protocolId = _protocol.getRowId();
        if(!_protocol.getName().equals(UNIT_TESTING_PROTOCOL_NAME))
            _provider = (ProviderType) AssayService.get().getProvider(_protocol);
        _targetStudy = originalContext.getTargetStudy();
        _runName = originalContext.getName();
        _runComments = originalContext.getComments();
        _container = originalContext.getContainer();
        if(_container != null)
            _containerId = _container.getId();
        _actionURL = originalContext.getActionURL();
        _uploadedData = originalContext.getUploadedData();
        _reRunId = originalContext.getReRunId();
        _originalFileLocation = originalContext.getOriginalFileLocation();

        _batchProperties = originalContext.getBatchProperties();
        _batchPropertiesById = convertPropertiesToIds(_batchProperties);
        _runProperties = originalContext.getRunProperties();
        _runPropertiesById = convertPropertiesToIds(_runProperties);
    }

    /** Convert to a map that can be serialized - DomainProperty can't be */
    protected Map<Integer, String> convertPropertiesToIds(Map<DomainProperty, String> properties)
    {
        Map<Integer, String> result = new HashMap<>();
        for (Map.Entry<DomainProperty, String> entry : properties.entrySet())
        {
            result.put(entry.getKey().getPropertyId(), entry.getValue());
            if (_domains == null)
            {
                _domains = new HashSet<>();
            }
            // Remember the domains that contributed properties. We can't get a DomainProperty directly, so we have
            // to get the domain first and then ask it for the properties
            _domains.add(entry.getKey().getDomain());
            _domainIds.add(entry.getKey().getDomain().getTypeId());
        }
        return result;
    }

    /** Convert from a serialized map by looking up the DomainProperties */
    protected Map<DomainProperty, String> convertPropertiesFromIds(Map<Integer, String> properties)
    {
        Map<DomainProperty, String> result = new HashMap<>();

        for (Map.Entry<Integer, String> entry : properties.entrySet())
        {
            result.put(findProperty(entry.getKey().intValue()), entry.getValue());
        }

        return result;
    }

    /** Look through all the domains for a property with the right ID */
    private DomainProperty findProperty(int propertyId)
    {
        for (Domain domain : getDomains())
        {
            for (DomainProperty domainProperty : domain.getProperties())
            {
                if (domainProperty.getPropertyId() == propertyId)
                {
                    return domainProperty;
                }
            }
        }
        throw new IllegalStateException("Could not find property: " + propertyId);
    }

    /** @return all the domains associated with properties that hold values */
    private Set<Domain> getDomains()
    {
        if (_domains == null)
        {
            _domains = new HashSet<>();
            for (Integer domainId : _domainIds)
            {
                Domain domain = PropertyService.get().getDomain(domainId);
                if (domain == null)
                {
                    throw new IllegalStateException("Could not find domain " + domainId);
                }
                _domains.add(domain);
            }
        }
        return _domains;
    }

    public void logProperties(Logger logger)
    {
        logger.info("----- Start Batch Properties -----");
        String valueText;

        for (Map.Entry<DomainProperty, String> entry : this._batchProperties.entrySet())
        {
            if(entry.getValue() == null)
                valueText = "[Blank]";
            else if(entry.getKey().getName().equals("TargetStudy"))
                valueText = ContainerManager.getForId(getTargetStudy()).getName();
            else
                valueText = entry.getValue();

            if(entry.getKey().getLabel() != null)
                logger.info("\t"+entry.getKey().getLabel()+": " + valueText);
            else
                logger.info("\t"+entry.getKey().getName()+": " + valueText);
        }
        logger.info("----- End Batch Properties -----");

        logger.info("----- Start Run Properties -----");
        try{
            logger.info("\tUploaded Files:");
            for(Map.Entry<String, File> entry : getUploadedData().entrySet())
            {
                logger.info("\t\t* " + entry.getValue().getName());
            }
        }
        catch(ExperimentException e)
        {
            logger.info("ERROR:  Experiment Exception getting file names.");
        }

        if(_runName == null)
            logger.info("\tAssay ID: [Blank]");
        else
            logger.info("\tAssay ID: " + _runName);

        if(_runComments == null)
            logger.info("\tRun Comments: [Blank]");
        else
            logger.info("\tRun Comments: " + _runComments);

        for (Map.Entry<DomainProperty, String> entry : this._runProperties.entrySet())
        {
            if(entry.getValue() == null)
                valueText = "[Blank]";
            else
                valueText = entry.getValue();

            if(entry.getKey().getLabel() != null)
                logger.info("\t"+entry.getKey().getLabel()+": " + valueText);
            else
                logger.info("\t"+entry.getKey().getName()+": " + valueText);
        }
        logger.info("----- End Run Properties -----");
    }

    @NotNull
    @Override
    public ExpProtocol getProtocol()
    {
        if (_protocol == null)
        {
            _protocol = ExperimentService.get().getExpProtocol(_protocolId);
        }
        return _protocol;
    }

    @Override
    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        if (_runProperties == null)
        {
            _runProperties = convertPropertiesFromIds(_runPropertiesById);
        }
        return _runProperties;
    }

    @Override
    public Map<DomainProperty, String> getBatchProperties()
    {
        if (_batchProperties == null)
        {
            _batchProperties = convertPropertiesFromIds(_batchPropertiesById);
        }
        return _batchProperties;
    }

    @Override
    public String getComments()
    {
        return _runComments;
    }

    @Override
    public String getName()
    {
        return _runName;
    }

    @Override
    public User getUser()
    {
        if (_user == null)
        {
            _user = UserManager.getUser(_userId);
        }
        return _user;
    }

    @NotNull
    @Override
    public Container getContainer()
    {
        if (_container == null)
        {
            _container = ContainerManager.getForId(_containerId);
        }
        return _container;
    }

    @Override
    public HttpServletRequest getRequest()
    {
        return null;
    }

    @Override
    public ActionURL getActionURL()
    {
        return _actionURL;
    }

    @NotNull
    @Override
    public Map<String, File> getUploadedData() throws ExperimentException
    {
        return _uploadedData;
    }

    @NotNull
    @Override
    public Map<Object, String> getInputDatas()
    {
        return Collections.emptyMap();
    }

    @Override
    public ProviderType getProvider()
    {
        if (_provider == null)
        {
            _provider = (ProviderType)AssayService.get().getProvider(getProtocol());
        }
        return _provider;
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
    public Integer getReRunId()
    {
        return _reRunId;
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

    @Nullable
    @Override
    public File getOriginalFileLocation()
    {
        return _originalFileLocation;
    }

    public void setLogger(Logger logger)
    {
        _logger = logger;
    }
}
