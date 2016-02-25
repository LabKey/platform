/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ExpSampleSetImpl extends ExpIdentifiableEntityImpl<MaterialSource> implements ExpSampleSet
{
    private Domain _domain;

    public ExpSampleSetImpl(MaterialSource ms)
    {
        super(ms);
    }

    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL(ExperimentController.ShowMaterialSourceAction.class, getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    public Container getContainer()
    {
        return _object.getContainer();
    }

    public int getRowId()
    {
        return _object.getRowId();
    }

    public String getMaterialLSIDPrefix()
    {
        return _object.getMaterialLSIDPrefix();
    }

    public String getDescription()
    {
        return _object.getDescription();
    }

    public boolean canImportMoreSamples()
    {
        return hasNameAsIdCol() || getIdCol1() != null;        
    }

    private DomainProperty getDomainProperty(String uri)
    {
        if (uri == null)
        {
            return null;
        }

        for (DomainProperty property : getType().getProperties())
        {
            if (uri.equals(property.getPropertyURI()))
            {
                return property;
            }
        }
        return null;
    }

    public List<DomainProperty> getIdCols()
    {
        List<DomainProperty> result = new ArrayList<>();
        if (hasNameAsIdCol())
            return result;

        result.add(getIdCol1());
        DomainProperty idCol2 = getIdCol2();
        if (idCol2 != null)
        {
            result.add(idCol2);
        }
        DomainProperty idCol3 = getIdCol3();
        if (idCol3 != null)
        {
            result.add(idCol3);
        }
        return result;
    }

    public boolean hasIdColumns()
    {
        return _object.getIdCol1() != null;
    }

    public boolean hasNameAsIdCol()
    {
        return ExpMaterialTable.Column.Name.name().equals(_object.getIdCol1());
    }

    @Nullable
    public DomainProperty getIdCol1()
    {
        if (hasNameAsIdCol())
            return null;

        DomainProperty result = getDomainProperty(_object.getIdCol1());
        if (result == null)
        {
            List<? extends DomainProperty> props = getType().getProperties();
            if (!props.isEmpty())
            {
                result = props.get(0);
            }
        }
        return result;
    }

    public DomainProperty getIdCol2()
    {
        return getDomainProperty(_object.getIdCol2());
    }

    public DomainProperty getIdCol3()
    {
        return getDomainProperty(_object.getIdCol3());
    }

    public DomainProperty getParentCol()
    {
        return getDomainProperty(_object.getParentCol());
    }

    public void setDescription(String s)
    {
        ensureUnlocked();
        _object.setDescription(s);
    }

    public void setMaterialLSIDPrefix(String s)
    {
        ensureUnlocked();
        _object.setMaterialLSIDPrefix(s);
    }

    public List<ExpMaterialImpl> getSamples()
    {
        return getSamples(getContainer());
    }

    public List<ExpMaterialImpl> getSamples(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), getLSID());
        Sort sort = new Sort("Name");
        return ExpMaterialImpl.fromMaterials(new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), filter, sort).getArrayList(Material.class));
    }

    @Deprecated
    public ExpMaterialImpl getSample(String name)
    {
        return getSample(getContainer(), name);
    }

    public ExpMaterialImpl getSample(Container c, String name)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("CpasType"), getLSID());
        filter.addCondition(FieldKey.fromParts("Name"), name);

        Material material = new TableSelector(ExperimentServiceImpl.get().getTinfoMaterial(), filter, null).getObject(Material.class);
        if (material == null)
            return null;
        return new ExpMaterialImpl(material);
    }

    @NotNull
    public Domain getType()
    {
        if (_domain == null)
        {
            _domain = PropertyService.get().getDomain(getContainer(), getLSID());
            if (_domain == null)
            {
                _domain = PropertyService.get().createDomain(getContainer(), getLSID(), getName());
                try
                {
                    _domain.save(null);
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new UnexpectedException(e);
                }
            }
        }
        return _domain;
    }

    public ExpProtocol[] getProtocols(User user)
    {
        TableInfo tinfoProtocol = ExperimentServiceImpl.get().getTinfoProtocol();
        ColumnInfo colLSID = tinfoProtocol.getColumn("LSID");
        ColumnInfo colSampleLSID = new PropertyColumn(ExperimentProperty.SampleSetLSID.getPropertyDescriptor(), colLSID, getContainer(), user, false);
        SimpleFilter filter = new SimpleFilter();
        filter.addCondition(colSampleLSID, getLSID());
        List<ColumnInfo> selectColumns = new ArrayList<>();
        selectColumns.addAll(tinfoProtocol.getColumns());
        selectColumns.add(colSampleLSID);
        Protocol[] protocols = new TableSelector(tinfoProtocol, selectColumns, filter, null).getArray(Protocol.class);
        ExpProtocol[] ret = new ExpProtocol[protocols.length];
        for (int i = 0; i < protocols.length; i ++)
        {
            ret[i] = new ExpProtocolImpl(protocols[i]);
        }
        return ret;
    }

    public void onSamplesChanged(User user, List<Material> materials) throws SQLException
    {
        ExpProtocol[] protocols = getProtocols(user);
        if (protocols.length == 0)
            return;
        List<ExpMaterialImpl> expMaterials = null;

        if (materials != null)
        {
            expMaterials = new ArrayList<>(materials.size());
            for (int i = 0; i < expMaterials.size(); i ++)
            {
                expMaterials.add(new ExpMaterialImpl(materials.get(i)));
            }
        }
        for (ExpProtocol protocol : protocols)
        {
            ProtocolImplementation impl = protocol.getImplementation();
            if (impl == null)
                continue;
            impl.onSamplesChanged(user, protocol, expMaterials);
        }
    }


    public void setContainer(Container container)
    {
        ensureUnlocked();
        _object.setContainer(container);
    }

    public void save(User user)
    {
        boolean isNew = _object.getRowId() == 0;
        save(user, ExperimentServiceImpl.get().getTinfoMaterialSource());
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

            ExpSampleSet activeSampleSet = ExperimentServiceImpl.get().lookupActiveSampleSet(getContainer());
            if (activeSampleSet == null)
            {
                ExperimentServiceImpl.get().setActiveSampleSet(getContainer(), this);
            }
        }

        // NOTE cacheMaterialSource() of course calls transactioncache.put(), which does not alter the shared cache! (BUG?)
        // Just call uncache(), and let normal cache loading do its thing
        ExperimentServiceImpl.get().uncacheMaterialSource(_object);
    }

    public void delete(User user)
    {
        try
        {
            ExperimentServiceImpl.get().deleteSampleSet(getRowId(), getContainer(), user);
        }
        catch (ExperimentException e)
        {
            throw new UnexpectedException(e);
        }
    }

    @Override
    public String toString()
    {
        return "SampleSet " + getName() + " in " + getContainer().getPath();
    }
}
