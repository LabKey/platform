/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.experiment.api;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.experiment.controllers.exp.ExperimentController;
import org.springframework.web.servlet.mvc.Controller;

import java.util.List;

/**
 * User: kevink
 * Date: 9/21/15
 */
public class ExpDataClassImpl extends ExpIdentifiableEntityImpl<DataClass> implements ExpDataClass
{
    protected static final String NAMESPACE_PREFIX = "DataClass";

    public static final String GENID_SEQUENCE_NAME = "org.labkey.experiment.api.DataClass";

    private Domain _domain;

    public ExpDataClassImpl(DataClass dataClass)
    {
        super(dataClass);
    }

    @Override
    public String getDataLsidPrefix()
    {
        Lsid lsid = new Lsid("Data", String.valueOf(getRowId()) + "." + PageFlowUtil.encode(getName()), "");
        return lsid.toString();
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    @Override
    public String getDescription()
    {
        return _object.getDescription();
    }

    @Override
    public String getNameExpression()
    {
        return _object.getNameExpression();
    }

    @Nullable
    @Override
    public ExpSampleSet getSampleSet()
    {
        Integer sampleSetRowId = _object.getMaterialSourceId();
        if (sampleSetRowId != null)
            return ExperimentService.get().getSampleSet(sampleSetRowId);

        return null;
    }

    @Override
    public List<? extends ExpData> getDatas()
    {
        return ExperimentService.get().getExpDatas(this);
    }

    @Override
    public ExpData getData(Container c, String name)
    {
        return ExperimentService.get().getExpData(this, /*c, */ name);
    }

    @Override
    public Domain getDomain()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(getContainer(), getLSID());
        }
        return _domain;
    }

    @Override
    public void setDomain(Domain d)
    {
        _domain = d;
    }

    @Nullable
    @Override
    public ActionURL detailsURL()
    {
        return urlFor(ExperimentController.ShowDataClassAction.class, getContainer());
    }


    @Override
    public void save(User user)
    {
        boolean isNew = _object.getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoDataClass());
        if (isNew)
        {
            Domain domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (domain == null)
            {
                domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    domain.save(user);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new UnexpectedException(e);
                }
            }
        }
        ExperimentServiceImpl.get().indexDataClass(this);
    }

    @Override
    public void delete(User user)
    {
        try
        {
            ExperimentServiceImpl.get().deleteDataClass(getRowId(), getContainer(), user);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeValidationException(e);
        }
    }

    protected TableInfo getTinfo()
    {
        Domain d = getDomain();
        return StorageProvisioner.createTableInfo(d);
    }

    @Override
    public ActionURL urlShowDefinition(ContainerUser cu)
    {
        Domain d = getDomain();
        DomainKind kind = d.getDomainKind();
        return kind.urlEditDefinition(d, cu);
    }

    @Override
    public ActionURL urlEditDefinition(ContainerUser cu)
    {
        Domain d = getDomain();
        DomainKind kind = d.getDomainKind();
        return kind.urlEditDefinition(d, cu);
    }

    @Override
    public ActionURL urlShowData(Container c)
    {
        return detailsURL();
    }

    @Override
    public ActionURL urlShowData()
    {
        return urlShowData(getContainer());
    }

    @Override
    public ActionURL urlUpdate(User user, Container container, @Nullable URLHelper cancelUrl)
    {
        ActionURL url = QueryService.get().urlFor(user, container, QueryAction.updateQueryRow, ExpSchema.SCHEMA_NAME, ExpSchema.TableType.DataClasses.name());

        url.addParameter("rowId", getRowId());

        if (cancelUrl != null)
            url.addParameter(ActionURL.Param.cancelUrl, cancelUrl.getLocalURIString());

        return url;
    }

    @Override
    public ActionURL urlDetails()
    {
        return detailsURL();
    }

    @Override
    public ActionURL urlShowHistory()
    {
        //return urlFor(ExperimentController.HistoryDataClassAction.class, c);
        return null;
    }

    @Override
    public ActionURL urlFor(Class<? extends Controller> actionClass)
    {
        return urlFor(actionClass, getContainer());
    }

    @Override
    public ActionURL urlFor(Class<? extends Controller> actionClass, Container c)
    {
        ActionURL ret = new ActionURL(actionClass, c);
        ret.addParameter("rowId", getRowId());
        return ret;
    }

}
