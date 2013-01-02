/*
 * Copyright (c) 2007-2012 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyType;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DomainPropertyImpl implements DomainProperty
{
    DomainImpl _domain;
    PropertyDescriptor _pdOld;
    PropertyDescriptor _pd;
    boolean _deleted;
    List<PropertyValidatorImpl> _validators;
    List<ConditionalFormat> _formats;


    public DomainPropertyImpl(DomainImpl type, PropertyDescriptor pd)
    {
        this(type, pd, null);
    }

    public DomainPropertyImpl(DomainImpl type, PropertyDescriptor pd, List<ConditionalFormat> formats)
    {
        _domain = type;
        _pd = pd.clone();
        _formats = formats;
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

    public String getConceptURI()
    {
        return _pd.getConceptURI();
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

    @Override
    public boolean isMeasure()
    {
        return _pd.isMeasure();
    }

    @Override
    public boolean isDimension()
    {
        return _pd.isDimension();
    }

    @Override
    public boolean isProtected()
    {
        return _pd.isProtected();
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

    public void setConceptURI(String conceptURI)
    {
        edit().setConceptURI(conceptURI);
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

    @Override
    public void setMeasure(boolean isMeasure)
    {
        // UNDONE: isMeasure() has side-effect due to calling isNumeric()->getSqlTypeInt() which relies on rangeURI which might not be set yet.
        if (!isEdited() && isMeasure == isMeasure())
            return;
        edit().setMeasure(isMeasure);
    }

    @Override
    public void setDimension(boolean isDimension)
    {
        // UNDONE: isDimension() has side-effect due to calling isNumeric()->getSqlTypeInt() which relies on rangeURI which might not be set yet.
        if (!isEdited() && isDimension == isDimension())
            return;
        edit().setDimension(isDimension);
    }

    @Override
    public void setProtected(boolean isProtected)
    {
        // UNDONE: isProtected() has side-effect due to calling isNumeric()->getSqlTypeInt() which relies on rangeURI which might not be set yet.
        if (!isEdited() && isProtected == isProtected())
            return;
        edit().setProtected(isProtected);
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
        return _pd.getImportAliasSet();
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

    private boolean isEdited()
    {
        return null != _pdOld;
    }

    private PropertyDescriptor edit()
    {
        if (_pdOld == null)
        {
            _pdOld = _pd;
            _pd = _pdOld.clone();
        }
        return _pd;
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


    public PropertyDescriptor getPropertyDescriptor()
    {
        return _pd;
    }

    @Override
    public List<ConditionalFormat> getConditionalFormats()
    {
        return ensureConditionalFormats();
    }

    public boolean isNew()
    {
        return _pd.getPropertyId() == 0;
    }

    public boolean isDirty()
    {
        if (_pdOld != null) return true;

        for (PropertyValidatorImpl v : ensureValidators())
        {
            if (v.isDirty() || v.isNew())
                return true;
        }
        return false;
    }

    public void delete(User user)
    {
        for (IPropertyValidator validator : getValidators())
            DomainPropertyManager.get().removePropertyValidator(user, this, validator);
        DomainPropertyManager.get().deleteConditionalFormats(getPropertyId());

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
        {
            _pd = OntologyManager.updatePropertyDescriptor(user, _domain._dd, _pdOld, _pd, sortOrder);

            boolean hasProvisioner = null != getDomain().getDomainKind() && null != getDomain().getDomainKind().getStorageSchemaName();

            if (hasProvisioner)
            {
                boolean mvAdded = !_pdOld.isMvEnabled() && _pd.isMvEnabled();
                boolean mvDropped = _pdOld.isMvEnabled() && !_pd.isMvEnabled();
                boolean propRenamed = !_pdOld.getName().equals(_pd.getName());

                if (mvDropped)
                    StorageProvisioner.dropMvIndicator(this);
                else if (mvAdded)
                    StorageProvisioner.addMvIndicator(this);

                if (propRenamed)
                {
                    Map<DomainProperty, String> renames = new HashMap<DomainProperty, String>();
                    renames.put(this, _pdOld.getName());
                    StorageProvisioner.renameProperties(this.getDomain(), renames);
                }

            }
        }
        else
            OntologyManager.ensurePropertyDomain(_pd, _domain._dd, sortOrder);

        _pdOld = null;

        for (PropertyValidatorImpl validator : ensureValidators())
        {
            if (validator.isDeleted())
                DomainPropertyManager.get().removePropertyValidator(user, this, validator);
            else
                DomainPropertyManager.get().savePropertyValidator(user, this, validator);
        }

        DomainPropertyManager.get().saveConditionalFormats(user, getPropertyDescriptor(), ensureConditionalFormats());
    }

    @NotNull
    public List<PropertyValidatorImpl> getValidators()
    {
        return Collections.unmodifiableList(ensureValidators());
    }

    public void addValidator(IPropertyValidator validator)
    {
        if (validator != null)
        {
            PropertyValidator impl = new PropertyValidator();
            impl.copy(validator);
            ensureValidators().add(new PropertyValidatorImpl(impl));
        }
    }

    public void removeValidator(IPropertyValidator validator)
    {
        int idx = ensureValidators().indexOf(validator);
        if (idx != -1)
        {
            PropertyValidatorImpl impl = ensureValidators().get(idx);
            impl.delete();
        }
    }

    public void removeValidator(int validatorId)
    {
        if (validatorId == 0) return;

        for (PropertyValidatorImpl imp : ensureValidators())
        {
            if (imp.getRowId() == validatorId)
            {
                imp.delete();
                break;
            }
        }
    }

    @Override
    public void setConditionalFormats(List<ConditionalFormat> formats)
    {
        _formats = formats;
    }

    private List<PropertyValidatorImpl> ensureValidators()
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

    private List<ConditionalFormat> ensureConditionalFormats()
    {
        if (_formats == null)
        {
            _formats = new ArrayList<ConditionalFormat>();
            _formats.addAll(DomainPropertyManager.get().getConditionalFormats(this));
        }
        return _formats;
    }

    public PropertyDescriptor getOldProperty()
    {
        return _pdOld;
    }

    @Override
    public FacetingBehaviorType getFacetingBehavior()
    {
        return _pd.getFacetingBehaviorType();
    }

    @Override
    public void setFacetingBehavior(FacetingBehaviorType type)
    {
        _pd.setFacetingBehaviorType(type);
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

    @Override
    public String toString()
    {
        return super.toString() + _pd.getPropertyURI();
    }
}
