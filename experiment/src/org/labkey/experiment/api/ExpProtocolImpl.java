/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.*;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;

import java.sql.SQLException;
import java.util.*;

public class ExpProtocolImpl extends ExpIdentifiableEntityImpl<Protocol> implements ExpProtocol
{
    public ExpProtocolImpl(Protocol protocol)
    {
        super(protocol);
    }

    public URLHelper detailsURL()
    {
        return null;
    }

    public ApplicationType getApplicationType()
    {
        try
        {
            return ApplicationType.valueOf(_object.getApplicationType());
        }
        catch (IllegalArgumentException iae)
        {
            return null;
        }
    }

    public ProtocolImplementation getImplementation()
    {
        String implName = (String) getProperty(ExperimentProperty.PROTOCOLIMPLEMENTATION.getPropertyDescriptor());
        return ExperimentService.get().getProtocolImplementation(implName);
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public Map<String, ObjectProperty> getObjectProperties()
    {
        return _object.retrieveObjectProperties();
    }

    public void setObjectProperties(Map<String, ObjectProperty> props)
    {
        _object.storeObjectProperties(props);
    }

    public Integer getMaxInputMaterialPerInstance()
    {
        return _object.getMaxInputMaterialPerInstance();
    }

    public String getProtocolDescription()
    {
        return _object.getProtocolDescription();
    }

    public void setProtocolDescription(String description)
    {
        _object.setProtocolDescription(description);
    }

    public void setMaxInputMaterialPerInstance(Integer maxMaterials)
    {
        _object.setMaxInputMaterialPerInstance(maxMaterials);
    }

    public void setMaxInputDataPerInstance(Integer i)
    {
        _object.setMaxInputDataPerInstance(i);
    }

    public List<ExpProtocolAction> getSteps()
    {
        return Arrays.<ExpProtocolAction>asList(ExpProtocolActionImpl.fromProtocolActions(ExperimentServiceImpl.get().getProtocolActions(getRowId())));
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public String getDescription()
    {
        return _object.getProtocolDescription();
    }

    public void setApplicationType(ApplicationType type)
    {
        _object.setApplicationType(type.toString());
    }

    public void setDescription(String description)
    {
        _object.setProtocolDescription(description);
    }

    public void save(User user)
    {
        _object = ExperimentServiceImpl.get().saveProtocol(user, _object);
    }

    public void delete(User user)
    {
        try
        {
            ExperimentServiceImpl.get().deleteProtocolByRowIds(getContainer(), user, getRowId());
        }
        catch (ExperimentException e)
        {
            throw new UnexpectedException(e);
        }
    }

    public ExpProtocolAction addStep(User user, ExpProtocol childProtocol, int actionSequence)
    {
        try
        {
            ProtocolAction action = new ProtocolAction();
            action.setParentProtocolId(getRowId());
            action.setChildProtocolId(childProtocol.getRowId());
            action.setSequence(actionSequence);
            action = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolAction(), action);
            return new ExpProtocolActionImpl(action);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpProtocol[] getParentProtocols()
    {
        return ExperimentServiceImpl.get().getParentProtocols(getRowId());        
    }

    public void setContainer(Container container)
    {
        _object.setContainer(container);
    }

    public Map<String, ProtocolParameter> getProtocolParameters()
    {
        return _object.retrieveProtocolParameters();
    }

    public void setProtocolParameters(Collection<ProtocolParameter> params)
    {
        _object.storeProtocolParameters(params);
    }

    public String getInstrument()
    {
        return _object.getInstrument();
    }

    public String getSoftware()
    {
        return _object.getSoftware();
    }

    public String getContact()
    {
        return _object.getContact();
    }

    public List<ExpProtocol> getChildProtocols()
    {
        return ExperimentServiceImpl.get().getChildProtocols(_object.getRowId());

    }

    public List<ExpExperimentImpl> getBatches()
    {
        try
        {
            Filter filter = new SimpleFilter("BatchProtocolId", getRowId());
            return Arrays.asList(ExpExperimentImpl.fromExperiments(Table.select(ExperimentServiceImpl.get().getTinfoExperiment(), Table.ALL_COLUMNS, filter, null, Experiment.class)));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public static ExpProtocolImpl[] fromProtocols(Protocol[] protocols)
    {
        ExpProtocolImpl[] result = new ExpProtocolImpl[protocols.length];
        for (int i = 0; i < protocols.length; i++)
        {
            result[i] = new ExpProtocolImpl(protocols[i]);
        }
        return result;
    }

    public Integer getMaxInputDataPerInstance()
    {
        return _object.getMaxInputDataPerInstance();
    }

    public Integer getOutputDataPerInstance()
    {
        return _object.getOutputDataPerInstance();
    }

    public Integer getOutputMaterialPerInstance()
    {
        return _object.getOutputMaterialPerInstance();
    }

    public String getOutputDataType()
    {
        return _object.getOutputDataType();
    }

    public String getOutputMaterialType()
    {
        return _object.getOutputMaterialType();
    }

    public ExpRun[] getExpRuns()
    {
        try
        {
            SQLFragment sql = new SQLFragment(" SELECT ER.* "
                        + " FROM exp.ExperimentRun ER "
                        + " WHERE ER.ProtocolLSID = ?");
            sql.add(getLSID());

            ExperimentRun[] runs = Table.executeQuery(
                ExperimentService.get().getSchema(),
                sql.getSQL(),
                sql.getParams().toArray(new Object[sql.getParams().size()]),
                ExperimentRun.class);

            return ExpRunImpl.fromRuns(runs);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

}
