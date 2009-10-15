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

import org.labkey.api.data.*;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.*;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DomainPropertyImpl implements DomainProperty
{
    DomainImpl _domain;
    boolean _new;
    PropertyDescriptor _pdOld;
    PropertyDescriptor _pd;
    boolean _deleted;
    List<PropertyValidatorImpl> _validators;


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

    public String getFormat()
    {
        return _pd.getFormat();
    }

    public String getLabel()
    {
        return _pd.getLabel();
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

    public boolean isHidden()
    {
        return _pd.isHidden();
    }

    public boolean isShownInInsertView()
    {
        return _pd.isShownInInsertView();
    }

    public boolean isShownInDetailsView()
    {
        return _pd.isShownInDetailsView();
    }

    public boolean isShownInUpdateView()
    {
        return _pd.isShownInUpdateView();
    }

    public boolean isMvEnabled()
    {
        return _pd.isMvEnabled();
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

    public void setHidden(boolean hidden)
    {
        if (hidden == isHidden())
            return;
        edit().setHidden(hidden);
    }

    public void setShownInDetailsView(boolean shown)
    {
        if (shown == isShownInDetailsView())
            return;
        edit().setShownInDetailsView(shown);
    }

    public void setShownInInsertView(boolean shown)
    {
        if (shown == isShownInInsertView())
            return;
        edit().setShownInInsertView(shown);
    }

    public void setShownInUpdateView(boolean shown)
    {
        if (shown == isShownInUpdateView())
            return;
        edit().setShownInUpdateView(shown);
    }



    public void setMvEnabled(boolean mv)
    {
        if (mv == isMvEnabled())
            return;
        edit().setMvEnabled(mv);
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public void setImportAliases(String aliases)
    {
        edit().setImportAliases(aliases);
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public String getImportAliases()
    {
        return _pd.getImportAliases();
    }

    public void setImportAliasSet(Set<String> aliases)
    {
        edit().setImportAliasesSet(aliases);
    }

    public Set<String> getImportAliasSet()
    {
        return _pd.getImportAliasesSet();
    }

    public void setURL(String url)
    {
        if (null == url)
            edit().setURL(null);
        else
            edit().setURL(StringExpressionFactory.createURL(url));
    }

    public String getURL()
    {
        return _pd.getURL() == null ? null : _pd.getURL().toString();
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

    public SQLFragment getMvIndicatorSQL()
    {
        return PropertyForeignKey.getMvIndicatorSQL();
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
        column.setReadOnly(false);
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

        for (PropertyValidatorImpl v : _getValidators())
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

    public void save(User user, DomainDescriptor dd, int sortOrder) throws SQLException, ChangePropertyDescriptorException
    {
        if (isNew())
            _pd = OntologyManager.insertOrUpdatePropertyDescriptor(_pd, dd, sortOrder);
        else if (_pdOld != null)
            _pd = OntologyManager.updatePropertyDescriptor(user, _domain._dd, _pdOld, _pd, sortOrder);
        else
            OntologyManager.ensurePropertyDomain(_pd, _domain._dd, sortOrder);

        _pdOld = null;

        for (PropertyValidatorImpl validator : _getValidators())
        {
            if (validator.isDeleted())
                DomainPropertyManager.get().removePropertyValidator(user, this, validator);
            else
                DomainPropertyManager.get().savePropertyValidator(user, this, validator);
        }
    }

    public IPropertyValidator[] getValidators()
    {
        return _getValidators().toArray(new PropertyValidatorImpl[0]);
    }

    public void addValidator(IPropertyValidator validator)
    {
        if (validator != null)
        {
            PropertyValidator impl = new PropertyValidator();
            impl.copy(validator);
            _getValidators().add(new PropertyValidatorImpl(impl));
        }
    }

    public void removeValidator(IPropertyValidator validator)
    {
        int idx = _getValidators().indexOf(validator);
        if (idx != -1)
        {
            PropertyValidatorImpl impl = _getValidators().get(idx);
            impl.delete();
        }
    }

    public void removeValidator(int validatorId)
    {
        if (validatorId == 0) return;

        for (PropertyValidatorImpl imp : _getValidators())
        {
            if (imp.getRowId() == validatorId)
            {
                imp.delete();
                break;
            }
        }
    }

    private List<PropertyValidatorImpl> _getValidators()
    {
        if (_validators == null)
        {
            _validators = new ArrayList<PropertyValidatorImpl>();
            for (PropertyValidator validator : DomainPropertyManager.get().getValidators(this))
            {
                _validators.add(new PropertyValidatorImpl(validator));
            }
        }
        return _validators;
    }

    @Override
    public int hashCode()
    {
        return _pd.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj == this)
            return true;
        if (!(obj instanceof DomainPropertyImpl))
            return false;
        // once a domain property has been edited, it no longer equals any other domain property:
        if (_pdOld != null || ((DomainPropertyImpl) obj)._pdOld != null)
            return false;
        return (_pd.equals(((DomainPropertyImpl) obj)._pd));
    }
}
