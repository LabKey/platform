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
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
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

    public PropertyDescriptor[] getPropertiesForType()
    {
        return OntologyManager.getPropertiesForType(getLSID(), getContainer());
    }

    public String getDescription()
    {
        return _object.getDescription();
    }

    public boolean canImportMoreSamples()
    {
        return getIdCol1() != null;        
    }

    private PropertyDescriptor getPropertyDescriptor(String uri)
    {
        if (uri == null)
        {
            return null;
        }

        for (PropertyDescriptor pd : getPropertiesForType())
        {
            if (uri.equals(pd.getPropertyURI()))
            {
                return pd;
            }
        }
        return null;
    }

    public List<PropertyDescriptor> getIdCols()
    {
        List<PropertyDescriptor> result = new ArrayList<PropertyDescriptor>();
        result.add(getIdCol1());
        PropertyDescriptor idCol2 = getIdCol2();
        if (idCol2 != null)
        {
            result.add(idCol2);
        }
        PropertyDescriptor idCol3 = getIdCol3();
        if (idCol3 != null)
        {
            result.add(idCol3);
        }
        return result;
    }

    public void setIdCols(List<PropertyDescriptor> pds)
    {
        if (pds.size() > 3)
        {
            throw new IllegalArgumentException("A maximum of three id columns is supported, but tried to set " + pds.size() + " of them");
        }
        _object.setIdCol1(pds.size() > 0 ? pds.get(0).getPropertyURI() : null);
        _object.setIdCol2(pds.size() > 1 ? pds.get(1).getPropertyURI() : null);
        _object.setIdCol3(pds.size() > 2 ? pds.get(2).getPropertyURI() : null);
    }

    public boolean hasIdColumns()
    {
        return _object.getIdCol1() != null;
    }

    public PropertyDescriptor getIdCol1()
    {
        PropertyDescriptor result = getPropertyDescriptor(_object.getIdCol1());
        if (result == null)
        {
            PropertyDescriptor[] props = getPropertiesForType();
            if (props.length > 0)
            {
                result = props[0];
            }
        }
        return result;
    }

    public PropertyDescriptor getIdCol2()
    {
        return getPropertyDescriptor(_object.getIdCol2());
    }

    public PropertyDescriptor getIdCol3()
    {
        return getPropertyDescriptor(_object.getIdCol3());
    }

    public PropertyDescriptor getParentCol()
    {
        return getPropertyDescriptor(_object.getParentCol());
    }

    public void setDescription(String s)
    {
        _object.setDescription(s);
    }

    public void setMaterialLSIDPrefix(String s)
    {
        _object.setMaterialLSIDPrefix(s);
    }

    public void insert(User user)
    {
        try
        {
            ExperimentServiceImpl.get().insertMaterialSource(user, _object, null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public ExpMaterialImpl[] getSamples()
    {
        try
        {
            SimpleFilter filter = new SimpleFilter();
            filter.addCondition("Container", getContainer().getId());
            filter.addCondition("CpasType", getLSID());
            Sort sort = new Sort("Name");
            return ExpMaterialImpl.fromMaterials(Table.select(ExperimentServiceImpl.get().getTinfoMaterial(), Table.ALL_COLUMNS, filter, sort, Material.class));
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }

    public Domain getType()
    {
        return PropertyService.get().getDomain(getContainer(), getLSID());
    }

    public ExpProtocol[] getProtocols(User user)
    {
        try
        {
            TableInfo tinfoProtocol = ExperimentServiceImpl.get().getTinfoProtocol();
            ColumnInfo colLSID = tinfoProtocol.getColumn("LSID");
            ColumnInfo colSampleLSID = new PropertyColumn(ExperimentProperty.SampleSetLSID.getPropertyDescriptor(), colLSID, null, user);
            SimpleFilter filter = new SimpleFilter();
			filter.addCondition(colSampleLSID, getLSID());
            List<ColumnInfo> selectColumns = new ArrayList<ColumnInfo>();
            selectColumns.addAll(tinfoProtocol.getColumns());
            selectColumns.add(colSampleLSID);
            Protocol[] protocols = Table.select(tinfoProtocol, selectColumns, filter, null, Protocol.class);
            ExpProtocol[] ret = new ExpProtocol[protocols.length];
            for (int i = 0; i < protocols.length; i ++)
            {
                ret[i] = new ExpProtocolImpl(protocols[i]);
            }
            return ret;
        }
        catch (SQLException e)
        {
            return new ExpProtocol[0];
        }
    }

    public void onSamplesChanged(User user, List<Material> materials) throws SQLException
    {
        ExpProtocol[] protocols = getProtocols(user);
        if (protocols.length == 0)
            return;
        ExpMaterial[] expMaterials = null;

        if (materials != null)
        {
            expMaterials = new ExpMaterial[materials.size()];
            for (int i = 0; i < expMaterials.length; i ++)
            {
                expMaterials[i] = new ExpMaterialImpl(materials.get(i));
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
        _object.setContainer(container);
    }

    public void save(User user)
    {
        save(user, ExperimentServiceImpl.get().getTinfoMaterialSource());
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

    public static ExpSampleSetImpl[] fromMaterialSources(MaterialSource[] sources)
    {
        ExpSampleSetImpl[] ret = new ExpSampleSetImpl[sources.length];
        for (int i = 0; i < sources.length; i ++)
        {
            ret[i] = new ExpSampleSetImpl(sources[i]);
        }
        return ret;
    }

    @Override
    public String toString()
    {
        return "SampleSet " + getName() + " in " + getContainer().getPath();
    }
}
