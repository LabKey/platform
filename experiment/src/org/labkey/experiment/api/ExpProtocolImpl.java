/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentProtocolHandler;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.api.ExpDataProtocolInput;
import org.labkey.api.exp.api.ExpMaterialProtocolInput;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocolAction;
import org.labkey.api.exp.api.ExpProtocolInput;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.query.ExpProtocolTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryRowReference;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ExpProtocolImpl extends ExpIdentifiableEntityImpl<Protocol> implements ExpProtocol
{
    private transient List<ExpProtocolActionImpl> _actions;

    // For serialization
    protected ExpProtocolImpl() {}

    public ExpProtocolImpl(Protocol protocol)
    {
        super(protocol);
    }

    @Override
    public ActionURL detailsURL()
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getProtocolDetailsURL(this);
    }

    @Override
    public @Nullable QueryRowReference getQueryRowReference()
    {
        QueryRowReference ref = getCustomQueryRowReference();
        if (ref != null)
            return ref;

        return new QueryRowReference(getContainer(), ExpSchema.SCHEMA_EXP, ExpSchema.TableType.Protocols.name(), FieldKey.fromParts(ExpProtocolTable.Column.RowId.name()), getRowId());
    }

    /**
     * Return a protocol specific query row reference or null if the default should be used.
     */
    /*package*/ @Nullable QueryRowReference getCustomQueryRowReference()
    {
        ProtocolImplementation impl = getImplementation();
        if (impl != null)
        {
            QueryRowReference ref = impl.getQueryRowReference(this);
            if (ref != null)
                return ref;
        }

        ExperimentProtocolHandler handler = ExperimentService.get().getExperimentProtocolHandler(this);
        if (handler != null)
        {
            QueryRowReference ref = handler.getQueryRowReference(this);
            if (ref != null)
                return ref;
        }

        return null;
    }

    @Override
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

    @Override
    public @Nullable String getImplementationName()
    {
        return (String) getProperty(ExperimentProperty.PROTOCOLIMPLEMENTATION.getPropertyDescriptor());
    }

    @Override
    public @Nullable ProtocolImplementation getImplementation()
    {
        return ExperimentService.get().getProtocolImplementation(getImplementationName());
    }

    @Override
    public int getRowId()
    {
        return _object.getRowId();
    }

    @Override
    public Map<String, ObjectProperty> getObjectProperties()
    {
        return _object.retrieveObjectProperties();
    }

    @Override
    public void setObjectProperties(Map<String, ObjectProperty> props)
    {
        ensureUnlocked();
        _object.storeObjectProperties(props);
    }

    @Override
    public Integer getMaxInputMaterialPerInstance()
    {
        return _object.getMaxInputMaterialPerInstance();
    }

    @Override
    public String getProtocolDescription()
    {
        return _object.getProtocolDescription();
    }

    @Override
    public void setProtocolDescription(String description)
    {
        ensureUnlocked();
        _object.setProtocolDescription(description);
    }

    @Override
    public void setMaxInputMaterialPerInstance(Integer maxMaterials)
    {
        ensureUnlocked();
        _object.setMaxInputMaterialPerInstance(maxMaterials);
    }

    @Override
    public void setMaxInputDataPerInstance(Integer i)
    {
        ensureUnlocked();
        _object.setMaxInputDataPerInstance(i);
    }

    @Override
    public List<ExpProtocolActionImpl> getSteps()
    {
        if (_actions == null)
        {
            _actions = Collections.unmodifiableList(ExpProtocolActionImpl.fromProtocolActions(ExperimentServiceImpl.get().getProtocolActions(getRowId())));
        }
        return _actions;
    }

    @Override
    public Container getContainer()
    {
        return _object.getContainer();
    }

    @Override
    public String getDescription()
    {
        return _object.getProtocolDescription();
    }

    @Override
    public void setApplicationType(ApplicationType type)
    {
        ensureUnlocked();
        _object.setApplicationType(type.toString());
    }

    @Override
    public void setDescription(String description)
    {
        ensureUnlocked();
        _object.setProtocolDescription(description);
    }

    @Override
    public void save(User user)
    {
        _object = ExperimentServiceImpl.get().saveProtocol(user, _object);
    }

    @Override
    public void save(User user, boolean saveProperties, @Nullable Collection<? extends ExpProtocolInput> protocolInputsToDeleteOnUpdate)
    {
        _object = ExperimentServiceImpl.get().saveProtocol(user, _object, saveProperties, protocolInputsToDeleteOnUpdate);
    }

    @Override
    public void delete(User user)
    {
        delete(user, null);
    }

    public void delete(User user, @Nullable final String auditUserComment )
    {
        try
        {
            ExperimentServiceImpl.get().deleteProtocolByRowIds(getContainer(), user, auditUserComment, getRowId());
        }
        catch (ExperimentException e)
        {
            throw new RuntimeValidationException(e);
        }
    }

    @Override
    public ExpProtocolAction addStep(User user, ExpProtocol childProtocol, int actionSequence)
    {
        ProtocolAction action = new ProtocolAction();
        action.setParentProtocolId(getRowId());
        action.setChildProtocolId(childProtocol.getRowId());
        action.setSequence(actionSequence);
        action = Table.insert(user, ExperimentServiceImpl.get().getTinfoProtocolAction(), action);
        return new ExpProtocolActionImpl(action);
    }

    @Override
    public List<ExpProtocolImpl> getParentProtocols()
    {
        String sql = "SELECT P.* FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " P, " + ExperimentServiceImpl.get().getTinfoProtocolAction() + " PA "
                + " WHERE P.RowId = PA.ParentProtocolID AND PA.ChildProtocolId = ?" ;

        return fromProtocols(new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql, getRowId()).getArrayList(Protocol.class));
    }

    @Override
    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    @Override
    public Map<String, ProtocolParameter> getProtocolParameters()
    {
        return _object.retrieveProtocolParameters();
    }

    @Override
    public void setProtocolParameters(Collection<ProtocolParameter> params)
    {
        ensureUnlocked();
        _object.storeProtocolParameters(params);
    }

    @Override
    public String getInstrument()
    {
        return _object.getInstrument();
    }

    @Override
    public String getSoftware()
    {
        return _object.getSoftware();
    }

    @Override
    public String getContact()
    {
        return _object.getContact();
    }

    @Override
    public List<ExpProtocolImpl> getChildProtocols()
    {
        String sql = "SELECT P.* FROM " + ExperimentServiceImpl.get().getTinfoProtocol() + " P, " + ExperimentServiceImpl.get().getTinfoProtocolAction() + " PA "
                + " WHERE P.RowId = PA.ChildProtocolID AND PA.ParentProtocolId = ? ORDER BY PA.Sequence" ;

        return fromProtocols(new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql, _object.getRowId()).getArrayList(Protocol.class));
    }

    @Override
    public List<ExpExperimentImpl> getBatches()
    {
        Filter filter = new SimpleFilter(FieldKey.fromParts("BatchProtocolId"), getRowId());
        return ExpExperimentImpl.fromExperiments(new TableSelector(ExperimentServiceImpl.get().getTinfoExperiment(), filter, null).getArray(Experiment.class));
    }


    @Override
    public void setEntityId(String entityId)
    {
        _object.setEntityId(entityId);
    }

    @Override
    public String getEntityId()
    {
        return _object.entityId;
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

    @Override
    public List<? extends ExpMaterialProtocolInput> getMaterialProtocolInputs()
    {
        List<? extends ExpProtocolInput> allInputs = _object.retrieveProtocolInputs();
        return allInputs.stream()
                .filter(ExpProtocolInput::isInput)
                .filter(i -> ExpMaterialProtocolInput.class.isAssignableFrom(i.getClass()))
                .map(i -> (ExpMaterialProtocolInput)i)
                .collect(Collectors.toList());
    }

    @Override
    public List<? extends ExpDataProtocolInput> getDataProtocolInputs()
    {
        List<? extends ExpProtocolInput> allInputs = _object.retrieveProtocolInputs();
        return allInputs.stream()
                .filter(ExpProtocolInput::isInput)
                .filter(i -> ExpDataProtocolInput.class.isAssignableFrom(i.getClass()))
                .map(i -> (ExpDataProtocolInput)i)
                .collect(Collectors.toList());
    }

    @Override
    public List<? extends ExpMaterialProtocolInput> getMaterialProtocolOutputs()
    {
        List<? extends ExpProtocolInput> allInputs = _object.retrieveProtocolInputs();
        return allInputs.stream()
                .filter(i -> !i.isInput())
                .filter(i -> ExpMaterialProtocolInput.class.isAssignableFrom(i.getClass()))
                .map(i -> (ExpMaterialProtocolInput)i)
                .collect(Collectors.toList());
    }

    @Override
    public List<? extends ExpDataProtocolInput> getDataProtocolOutputs()
    {
        List<? extends ExpProtocolInput> allInputs = _object.retrieveProtocolInputs();
        return allInputs.stream()
                .filter(i -> !i.isInput())
                .filter(i -> ExpDataProtocolInput.class.isAssignableFrom(i.getClass()))
                .map(i -> (ExpDataProtocolInput)i)
                .collect(Collectors.toList());
    }

    @Override
    public void setProtocolInputs(Collection<? extends ExpProtocolInput> protocolInputs)
    {
        ensureUnlocked();
        _object.storeProtocolInputs(protocolInputs);
    }

    @Override
    public Integer getMaxInputDataPerInstance()
    {
        return _object.getMaxInputDataPerInstance();
    }

    @Override
    public Integer getOutputDataPerInstance()
    {
        return _object.getOutputDataPerInstance();
    }

    @Override
    public Integer getOutputMaterialPerInstance()
    {
        return _object.getOutputMaterialPerInstance();
    }

    @Override
    public String getOutputDataType()
    {
        return _object.getOutputDataType();
    }

    @Override
    public String getOutputMaterialType()
    {
        return _object.getOutputMaterialType();
    }

    @Override
    public List<ExpRunImpl> getExpRuns()
    {
        SQLFragment sql = new SQLFragment(" SELECT ER.* "
                    + " FROM exp.ExperimentRun ER "
                    + " WHERE ER.ProtocolLSID = ?");
        sql.add(getLSID());

        return ExpRunImpl.fromRuns(new SqlSelector(ExperimentService.get().getSchema(), sql).getArrayList(ExperimentRun.class));
    }

    @Override
    public Set<Container> getExpRunContainers()
    {
        SQLFragment sql = new SQLFragment(" SELECT DISTINCT Container "
                    + " FROM exp.ExperimentRun ER "
                    + " WHERE ER.ProtocolLSID = ?");
        sql.add(getLSID());

        final Set<Container> containers = new TreeSet<>();

        new SqlSelector(ExperimentService.get().getSchema(), sql).forEach(rs -> {
            String containerId = rs.getString("Container");
            Container container = ContainerManager.getForId(containerId);
            assert container != null : "All runs should have a valid container.  Couldn't find container for ID " + containerId;
            containers.add(container);
        });

        return containers;
    }

    @Override
    public Status getStatus()
    {
        return _object.getStatus();
    }

    @Override
    public void setStatus(Status status)
    {
        _object.setStatus(status);
    }
}
