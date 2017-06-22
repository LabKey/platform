/*
 * Copyright (c) 2011-2017 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.qc.DefaultTransformResult;
import org.labkey.api.qc.TransformResult;
import org.labkey.api.security.User;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.ActionURL;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Information about an assay run that has already been imported into the database.
 * User: jeckels
 * Date: Oct 7, 2011
 */
public class AssayRunDatabaseContext<ProviderType extends AssayProvider> implements AssayRunUploadContext<ProviderType>
{
    protected final ExpRun _run;
    protected final User _user;
    protected final ExpProtocol _protocol;
    protected final ProviderType _provider;
    private final HttpServletRequest _request;
    private TransformResult _transformResult;

    public AssayRunDatabaseContext(ExpRun run, User user, HttpServletRequest request)
    {
        _run = run;
        _user = user;
        _protocol = _run.getProtocol();
        _provider = (ProviderType)AssayService.get().getProvider(_protocol);
        _request = request;
    }

    @NotNull
    @Override
    public ExpProtocol getProtocol()
    {
        return _protocol;
    }

    @Override
    public Map<DomainProperty, String> getRunProperties() throws ExperimentException
    {
        return getProperties(_provider.getRunDomain(_protocol), _run.getObjectProperties());
    }

    @Override
    public Map<DomainProperty, String> getBatchProperties()
    {
        ExpExperiment batch = AssayService.get().findBatch(_run);
        if (batch == null)
        {
            return Collections.emptyMap();
        }
        return getProperties(_provider.getBatchDomain(_protocol), batch.getObjectProperties());
    }

    protected Map<DomainProperty, String> getProperties(@Nullable Domain domain, Map<String, ObjectProperty> props)
    {
        if (domain == null)
        {
            return Collections.emptyMap();
        }
        Map<DomainProperty, String> result = new HashMap<>();
        for (DomainProperty dp : domain.getProperties())
        {
            ObjectProperty op = props.get(dp.getPropertyURI());
            if (op != null)
            {
                result.put(dp, ConvertUtils.convert(op.value()));
            }
            else
            {
                result.put(dp, null);
            }
        }
        return result;
    }

    @Override
    public String getComments()
    {
        return _run.getComments();
    }

    @Override
    public String getName()
    {
        return _run.getName();
    }

    @Override
    public User getUser()
    {
        return _user;
    }

    @NotNull
    @Override
    public Container getContainer()
    {
        return _run.getContainer();
    }

    @Override
    public HttpServletRequest getRequest()
    {
        return _request;
    }

    @Override
    public ActionURL getActionURL()
    {
        return null;
    }

    @NotNull
    @Override
    public Map<String, File> getUploadedData() throws ExperimentException
    {
        Map<String, File> result = new HashMap<>();
        for (ExpData data : _run.getOutputDatas(_provider.getDataType()))
        {
            File f = data.getFile();
            if (f == null || !NetworkDrive.exists(f))
            {
                throw new ExperimentException("Data file " + data.getName() + " is no longer available on the server's file system");
            }
            result.put(f.getName(), f);
        }
        return result;
    }

    @Nullable
    @Override
    public File getOriginalFileLocation()
    {
        for (ExpData data : _run.getOutputDatas(_provider.getDataType()))
        {
            File f = data.getFile();
            if (f != null)
            {
                return f.getParentFile();
            }
        }
        return null;
    }

    @NotNull
    @Override
    public Map<Object, String> getInputDatas()
    {
        // CONSIDER: get the run's input datas
        return Collections.emptyMap();
    }

    @Override
    public ProviderType getProvider()
    {
        return _provider;
    }

    @Override
    public String getTargetStudy()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public TransformResult getTransformResult()
    {
        return _transformResult == null ? DefaultTransformResult.createEmptyResult() :_transformResult;
    }

    @Override
    public void setTransformResult(TransformResult result)
    {
        _transformResult = result;
    }

    @Override
    public Integer getReRunId()
    {
        return null;
    }

    @Override
    public void uploadComplete(ExpRun run) throws ExperimentException
    {
        // no-op
    }

    @Override
    public Logger getLogger()
    {
        return null;
    }
}
