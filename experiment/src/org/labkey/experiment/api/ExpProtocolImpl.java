/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;

import java.sql.SQLException;
import java.sql.ResultSet;
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
        if (_object.getApplicationType() == null)
        {
            return null;
        }
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
        ensureUnlocked();
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
        ensureUnlocked();
        _object.setProtocolDescription(description);
    }

    public void setMaxInputMaterialPerInstance(Integer maxMaterials)
    {
        ensureUnlocked();
        _object.setMaxInputMaterialPerInstance(maxMaterials);
    }

    public void setMaxInputDataPerInstance(Integer i)
    {
        ensureUnlocked();
        _object.setMaxInputDataPerInstance(i);
    }

    public List<? extends ExpProtocolAction> getSteps()
    {
        return ExpProtocolActionImpl.fromProtocolActions(ExperimentServiceImpl.get().getProtocolActions(getRowId()));
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
        ensureUnlocked();
        _object.setApplicationType(type.toString());
    }

    public void setDescription(String description)
    {
        ensureUnlocked();
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
            throw new RuntimeValidationException(e);
        }
    }

    public ExpProtocolAction addStep(User user, ExpProtocol childProtocol, int actionSequence)
    {
        ProtocolAction action = new ProtocolAction();
        action.setParentProtocolId(getRowId());
        action.setChildProtocolId(childProtocol.getRowId());
        action.setSequence(actionSequence);
        action = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolAction(), action);
        return new ExpProtocolActionImpl(action);
    }

    public List<ExpProtocolImpl> getParentProtocols()
    {
        String sql = "SELECT P.* FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " P, " + ExperimentServiceImpl.get().getTinfoProtocolAction() + " PA "
                + " WHERE P.RowId = PA.ParentProtocolID AND PA.ChildProtocolId = ?" ;

        return fromProtocols(new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql, getRowId()).getArrayList(Protocol.class));
    }

    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    public Map<String, ProtocolParameter> getProtocolParameters()
    {
        return _object.retrieveProtocolParameters();
    }

    public void setProtocolParameters(Collection<ProtocolParameter> params)
    {
        ensureUnlocked();
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

    public List<ExpProtocolImpl> getChildProtocols()
    {
        String sql = "SELECT P.* FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " P, " + ExperimentServiceImpl.get().getTinfoProtocolAction() + " PA "
                + " WHERE P.RowId = PA.ChildProtocolID AND PA.ParentProtocolId = ? ORDER BY PA.Sequence" ;

        return fromProtocols(new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql, _object.getRowId()).getArrayList(Protocol.class));
    }

    public List<ExpExperimentImpl> getBatches()
    {
        Filter filter = new SimpleFilter(FieldKey.fromParts("BatchProtocolId"), getRowId());
        return ExpExperimentImpl.fromExperiments(new TableSelector(ExperimentServiceImpl.get().getTinfoExperiment(), filter, null).getArray(Experiment.class));
    }

    public static List<ExpProtocolImpl> fromProtocols(List<Protocol> protocols)
    {
        List<ExpProtocolImpl> result = new ArrayList<>(protocols.size());
        for (Protocol protocol : protocols)
        {
            result.add(new ExpProtocolImpl(protocol));
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

    public List<ExpRunImpl> getExpRuns()
    {
        SQLFragment sql = new SQLFragment(" SELECT ER.* "
                    + " FROM exp.ExperimentRun ER "
                    + " WHERE ER.ProtocolLSID = ?");
        sql.add(getLSID());

        return ExpRunImpl.fromRuns(new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(ExperimentRun.class));
    }

    public Set<Container> getExpRunContainers()
    {
        SQLFragment sql = new SQLFragment(" SELECT DISTINCT Container "
                    + " FROM exp.ExperimentRun ER "
                    + " WHERE ER.ProtocolLSID = ?");
        sql.add(getLSID());

        final Set<Container> containers = new TreeSet<>();

        new SqlSelector(ExperimentService.get().getSchema(), sql).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                String containerId = rs.getString("Container");
                Container container = ContainerManager.getForId(containerId);
                assert container != null : "All runs should have a valid container.  Couldn't find container for ID " + containerId;
                containers.add(container);
            }
        });

        return containers;
    }
}
