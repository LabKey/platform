/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.experiment.api.property;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.*;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.gwt.client.DefaultValueType;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DomainPropertyImpl implements DomainProperty
{
    DomainImpl _domain;
    boolean _new;
    PropertyDescriptor _pdOld;
    PropertyDescriptor _pd;
    boolean _deleted;
    List<PropertyValidatorImpl> _validators = new ArrayList<PropertyValidatorImpl>();


    public DomainPropertyImpl(DomainImpl type, PropertyDescriptor pd)
    {
        _domain = type;
        _pd = pd.clone();

        for (PropertyValidator validator : DomainPropertyManager.get().getValidators(this))
        {
            _validators.add(new PropertyValidatorImpl(validator));
        }
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
        ActionURL ret = getDomain().urlEditDefinition(false, false, false);
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

    public boolean isQcEnabled()
    {
        return _pd.isQcEnabled();
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

    public String getRangeURI()
    {
        return _pd.getRangeURI();
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

    public void setQcEnabled(boolean qc)
    {
        if (qc == isQcEnabled())
            return;
        edit().setQcEnabled(qc);
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

    public SQLFragment getQCValueSQL()
    {
        return PropertyForeignKey.getQCValueSQL();
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

    public DefaultValueType getDefaultValueTypeEnum()
    {
        return _pd.getDefaultValueTypeEnum();
    }

    public void setDefaultValueTypeEnum(DefaultValueType defaultValueType)
    {
        _pd.setDefaultValueTypeEnum(defaultValueType);
    }

    public String getDefaultValueType()
    {
        return _pd.getDefaultValueType();
    }

    public void setDefaultValueType(String defaultValueTypeName)
    {
        _pd.setDefaultValueType(defaultValueTypeName);
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

    public boolean isNew()
    {
        return _pd.getPropertyId() == 0;
    }

    public boolean isDirty()
    {
        if (_pdOld != null) return true;

        for (PropertyValidatorImpl v : _validators)
        {
            if (v.isDirty() || v.isNew())
                return true;
        }
        return false;
    }

    public void delete(User user) throws SQLException
    {
        for (IPropertyValidator validator : getValidators())
            DomainPropertyManager.get().removePropertyValidator(user, this, validator);

        DomainKind kind = getDomain().getDomainKind();
        if (null != kind)
            kind.deletePropertyDescriptor(getDomain(), user, _pd);
        OntologyManager.deletePropertyDescriptor(_pd);
    }

    public void save(User user, DomainDescriptor dd) throws SQLException, ChangePropertyDescriptorException
    {
        if (isNew())
            _pd = OntologyManager.insertOrUpdatePropertyDescriptor(_pd, dd);
        else if (_pdOld != null)
            _pd = OntologyManager.updatePropertyDescriptor(user, _domain.getTypeURI(), _pdOld, _pd);

        _pdOld = null;

        for (PropertyValidatorImpl validator : _validators)
        {
            if (validator.isDeleted())
                DomainPropertyManager.get().removePropertyValidator(user, this, validator);
            else
                DomainPropertyManager.get().savePropertyValidator(user, this, validator);
        }
    }

    public IPropertyValidator[] getValidators()
    {
        return _validators.toArray(new PropertyValidatorImpl[0]);
    }

    public void addValidator(IPropertyValidator validator)
    {
        if (validator != null)
        {
            PropertyValidator impl = new PropertyValidator();
            impl.copy(validator);
            _validators.add(new PropertyValidatorImpl(impl));
        }
    }

    public void removeValidator(IPropertyValidator validator)
    {
        int idx = _validators.indexOf(validator);
        if (idx != -1)
        {
            PropertyValidatorImpl impl = _validators.get(idx);
            impl.delete();
        }
    }

    public void removeValidator(int validatorId)
    {
        if (validatorId == 0) return;

        for (PropertyValidatorImpl imp : _validators)
        {
            if (imp.getRowId() == validatorId)
            {
                imp.delete();
                break;
            }
        }
    }

    @Override
    public int hashCode()
    {
        return _pd.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (!(obj instanceof DomainPropertyImpl))
            return false;
        // once a domain property has been edited, it no longer equals any other domain property:
        if (_pdOld != null || ((DomainPropertyImpl) obj)._pdOld != null)
            return false;
        return (_pd.equals(((DomainPropertyImpl) obj)._pd));
    }
}
