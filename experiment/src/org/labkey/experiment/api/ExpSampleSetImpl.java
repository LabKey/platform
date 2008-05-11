package org.labkey.experiment.api;

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ProtocolImplementation;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.ExperimentProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ExpSampleSetImpl extends ExpIdentifiableBaseImpl<MaterialSource> implements ExpSampleSet
{
    static final private Logger _log = Logger.getLogger(ExpSampleSetImpl.class);

    public ExpSampleSetImpl(MaterialSource ms)
    {
        super(ms);
    }

    public URLHelper detailsURL()
    {
        ActionURL ret = new ActionURL("Experiment", "showMaterialSource", getContainer());
        ret.addParameter("rowId", Integer.toString(getRowId()));
        return ret;
    }

    public User getCreatedBy()
    {
        return UserManager.getUser(_object.getCreatedBy());
    }

    public String getContainerId()
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

    private PropertyDescriptor getIdCol(String uri)
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
        _object.setIdCol1(pds.size() > 1 ? pds.get(1).getPropertyURI() : null);
        _object.setIdCol1(pds.size() > 2 ? pds.get(2).getPropertyURI() : null);
    }

    public PropertyDescriptor getIdCol1()
    {
        PropertyDescriptor result = getIdCol(_object.getIdCol1());
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
        return getIdCol(_object.getIdCol2());
    }

    public PropertyDescriptor getIdCol3()
    {
        return getIdCol(_object.getIdCol3());
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

    public ExpMaterial[] getSamples()
    {
        try
        {
            return ExperimentServiceImpl.get().getMaterialsForSampleSet(getLSID(), getContainer());
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

    public ExpProtocol[] getProtocols()
    {
        try
        {
            TableInfo tinfoProtocol = ExperimentServiceImpl.get().getTinfoProtocol();
            ColumnInfo colLSID = tinfoProtocol.getColumn("LSID");
            ColumnInfo colSampleLSID = new PropertyColumn(ExperimentProperty.SampleSetLSID.getPropertyDescriptor(), colLSID, null);
            SQLFragment whereClause = colSampleLSID.getValueSql();
            whereClause.append(" = ");
            whereClause.appendStringLiteral(getLSID());
            SimpleFilter filter = new SimpleFilter();
            filter.addWhereClause(whereClause.getSQL(), whereClause.getParams().toArray());
            List<ColumnInfo> selectColumns = new ArrayList<ColumnInfo>();
            selectColumns.addAll(tinfoProtocol.getColumnsList());
            selectColumns.add(colSampleLSID);
            Protocol[] protocols = Table.select(tinfoProtocol,
                    selectColumns, filter, null, Protocol.class);
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

    public void onSamplesChanged(User user, List<Material> materials) throws Exception
    {
        ExpProtocol[] protocols = getProtocols();
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


    public void setContainerId(String containerId)
    {
        _object.setContainer(containerId);
    }
}
