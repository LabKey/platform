/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.labkey.api.data.Table;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.URLHelper;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExpProtocolImpl extends ExpIdentifiableBaseImpl<Protocol> implements ExpProtocol
{
    static private final Logger _log = Logger.getLogger(ExpProtocolImpl.class);
    public ExpProtocolImpl(Protocol protocol)
    {
        super(protocol);
    }

    public URLHelper detailsURL()
    {
        return null;
    }

    public User getCreatedBy()
    {
        return UserManager.getUser(_object.getCreatedBy());
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

    public Map<String, ObjectProperty> retrieveObjectProperties()
    {
        return _object.retrieveObjectProperties();
    }

    public void storeObjectProperties(Map<String, ObjectProperty> props)
    {
        _object.storeObjectProperties(props);
    }

    public Integer getMaxInputMaterialPerInstance()
    {
        return _object.getMaxInputDataPerInstance();
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

    public ExpProtocolAction[] getSteps()
    {
        try
        {
            List<ExpProtocolAction> ret = new ArrayList();
            for (ProtocolAction action : ExperimentServiceImpl.get().getProtocolActions(getRowId()))
            {
                ret.add(new ExpProtocolActionImpl(action));
            }
            return ret.toArray(new ExpProtocolAction[0]);
        }
        catch (SQLException e)
        {
            _log.error("Error", e);
            return new ExpProtocolAction[0];
        }
    }

    public String getContainerId()
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

    public ExpProtocolAction addStep(User user, ExpProtocol childProtocol, int actionSequence) throws Exception
    {
        ProtocolAction action = new ProtocolAction();
        action.setParentProtocolId(getRowId());
        action.setChildProtocolId(childProtocol.getRowId());
        action.setSequence(actionSequence);
        action = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolAction(), action);
        return new ExpProtocolActionImpl(action);
    }

    public ExpProtocol[] getParentProtocols()
    {
        return ExperimentServiceImpl.get().getParentProtocols(getRowId());        
    }

    public void setContainerId(String containerId)
    {
        _object.setContainer(containerId);
    }

    public Map<String, ProtocolParameter> retrieveProtocolParameters() throws SQLException
    {
        return _object.retrieveProtocolParameters();
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

    public static ExpProtocolImpl[] fromProtocols(Protocol[] protocols)
    {
        ExpProtocolImpl[] result = new ExpProtocolImpl[protocols.length];
        for (int i = 0; i < protocols.length; i++)
        {
            result[i] = new ExpProtocolImpl(protocols[i]);
        }
        return result;
    }
}
