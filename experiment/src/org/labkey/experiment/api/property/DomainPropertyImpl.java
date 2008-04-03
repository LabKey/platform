package org.labkey.experiment.api.property;

import org.labkey.api.exp.property.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.security.User;

public class DomainPropertyImpl implements DomainProperty
{
    DomainImpl _domain;
    boolean _new;
    PropertyDescriptor _pdOld;
    PropertyDescriptor _pd;
    boolean _deleted;


    public DomainPropertyImpl(DomainImpl type, PropertyDescriptor pd)
    {
        _domain = type;
        _pd = pd.clone();
    }

    public int getPropertyId()
    {
        return _pd.getPropertyId();
    }

    public Container getContainer()
    {
        return _pd.getContainer();
    }

    public String getPropertyURI()
    {
        return _pd.getPropertyURI();
    }

    public String getName()
    {
        return _pd.getName();
    }

    public String getDescription()
    {
        return _pd.getDescription();
    }

    public String getFormatString()
    {
        return _pd.getFormat();
    }

    public String getLabel()
    {
        return _pd.getLabel();
    }

    public ActionURL detailsURL()
    {
        ActionURL ret = getDomain().urlEditDefinition(false, false);
        ret.setAction("showProperty");
        ret.replaceParameter("propertyId", Integer.toString(getPropertyId()));
        return ret;
    }

    public Domain getDomain()
    {
        return _domain;
    }

    public IPropertyType getType()
    {
        return PropertyService.get().getType(getContainer(), _pd.getRangeURI());
    }

    public boolean isRequired()
    {
        return _pd.isRequired();
    }

    public void delete()
    {
        _deleted = true;
    }

    public void setName(String name)
    {
        edit().setName(name);
    }

    public void setDescription(String description)
    {
        edit().setDescription(description);
    }

    public void setType(IPropertyType domain)
    {
        edit().setRangeURI(domain.getTypeURI());
    }

    public void setPropertyURI(String uri)
    {
        edit().setPropertyURI(uri);
    }

    public void setRangeURI(String rangeURI)
    {
        edit().setRangeURI(rangeURI);
    }

    public void setFormat(String s)
    {
        edit().setFormat(s);
    }

    public void setLabel(String caption)
    {
        edit().setLabel(caption);
    }

    public void setRequired(boolean required)
    {
        if (required == isRequired())
            return;
        edit().setRequired(required);
    }

    private PropertyDescriptor edit()
    {
        if (_new)
            return _pd;
        if (_pdOld == null)
        {
            _pdOld = _pd;
            _pd = _pdOld.clone();
        }
        return _pd;
    }

    public SQLFragment getValueSQL()
    {
        return PropertyForeignKey.getValueSql(_pd.getPropertyType());
    }

    public int getSqlType()
    {
        return _pd.getPropertyType().getSqlType();
    }

    public int getScale()
    {
        return _pd.getPropertyType().getScale();
    }

    public String getInputType()
    {
        return _pd.getPropertyType().getInputType();
    }

    public Lookup getLookup()
    {
        Lookup ret = new Lookup();
        String containerId = _pd.getLookupContainer();
        ret.setQueryName(_pd.getLookupQuery());
        ret.setSchemaName(_pd.getLookupSchema());
        if (ret.getQueryName() == null || ret.getSchemaName() == null)
            return null;

        if (containerId != null)
        {
            Container container = ContainerManager.getForId(containerId);
            if (container == null)
            {
                return null;
            }
            ret.setContainer(container);
        }
        return ret;
    }

    public void setLookup(Lookup lookup)
    {
        if (lookup == null)
        {
            edit().setLookupContainer(null);
            edit().setLookupSchema(null);
            edit().setLookupQuery(null);
            return;
        }
        if (lookup.getContainer() == null)
        {
            edit().setLookupContainer(null);
        }
        else
        {
            edit().setLookupContainer(lookup.getContainer().getId());
        }
        edit().setLookupQuery(lookup.getQueryName());
        edit().setLookupSchema(lookup.getSchemaName());
    }

    public void initColumn(User user, ColumnInfo column)
    {
        PropertyForeignKey.initColumn(user, column, _pd);
    }

    public PropertyDescriptor getPropertyDescriptor()
    {
        return _pd;
    }

    public boolean isDataTypeChanged()
    {
        if (_deleted)
            return true;
        if (_new)
            return true;
        if (_pdOld == null)
            return false;
        return !_pdOld.getRangeURI().equals(_pd.getRangeURI());
    }
}
