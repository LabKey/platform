/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PHI;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.DefaultPropertyValidator;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyType;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DomainPropertyImpl implements DomainProperty
{
    private final DomainImpl _domain;

    PropertyDescriptor _pd;
    PropertyDescriptor _pdOld;
    boolean _deleted;

    private boolean _schemaChanged;
    private boolean _schemaImport;
    private List<PropertyValidatorImpl> _validators;
    private List<ConditionalFormat> _formats;
    private String _defaultValue;


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
    public boolean isRecommendedVariable()
    {
        return _pd.isRecommendedVariable();
    }

    @Override
    public DefaultScaleType getDefaultScale()
    {
        return _pd.getDefaultScale();
    }

    @Override
    public PHI getPHI()
    {
        return _pd.getPHI();
    }

    @Override
    public String getRedactedText() { return _pd.getRedactedText(); }

    @Override
    public boolean isExcludeFromShifting()
    {
        return _pd.isExcludeFromShifting();
    }

    public boolean isMvEnabled()
    {
        return _pd.isMvEnabled();
    }

    @Override
    public boolean isMvEnabledForDrop()
    {
        if (null != _pdOld)
            return _pdOld.isMvEnabled();    // if we need to drop/recreate we care about the old one
        return _pd.isMvEnabled();
    }

    public void delete()
    {
        _deleted = true;
    }

    @Override
    public void setSchemaImport(boolean isSchemaImport)
    {
        // if this flag is set True then the column is dropped and recreated by its Domain if there is a type change
        _schemaImport = isSchemaImport;
    }

    public void setName(String name)
    {
        edit().setName(name);
    }

    public void setDescription(String description)
    {
        if (StringUtils.equals(description, getDescription()))
            return;
        edit().setDescription(description);
    }

    public void setType(IPropertyType domain)
    {
        edit().setRangeURI(domain.getTypeURI());
    }

    public void setPropertyURI(String uri)
    {
        if (StringUtils.equals(uri, getPropertyURI()))
            return;
        edit().setPropertyURI(uri);
    }

    public void setRangeURI(String rangeURI)
    {
        if (StringUtils.equals(rangeURI, getRangeURI()))
            return;
        editSchema().setRangeURI(rangeURI);
    }

    public String getRangeURI()
    {
        return _pd.getRangeURI();
    }

    public void setFormat(String s)
    {
        if (StringUtils.equals(s, getFormat()))
            return;
        edit().setFormat(s);
    }

    public void setLabel(String caption)
    {
        if (StringUtils.equals(caption, getLabel()))
            return;
        edit().setLabel(caption);
    }

    public void setConceptURI(String conceptURI)
    {
        if (StringUtils.equals(conceptURI, getConceptURI()))
            return;
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
    public void setRecommendedVariable(boolean isRecommendedVariable)
    {
        if (!isEdited() && isRecommendedVariable == isRecommendedVariable())
            return;
        edit().setRecommendedVariable(isRecommendedVariable);
    }

    @Override
    public void setDefaultScale(DefaultScaleType defaultScale)
    {
        if (!isEdited() && getDefaultScale() == defaultScale)
            return;

        edit().setDefaultScale(defaultScale);
    }

    @Override
    public void setPhi(PHI phi)
    {
        if (!isEdited() && getPHI() == phi)
            return;
        edit().setPHI(phi);
    }

    @Override
    public void setRedactedText(String redactedText)
    {
        if (!isEdited() && getRedactedText() == redactedText)
            return;
        edit().setRedactedText(redactedText);
    }

    @Override
    public void setExcludeFromShifting(boolean isExcludeFromShifting)
    {
        // UNDONE: isExcludeFromShifting() has side-effect due to calling isNumeric()->getSqlTypeInt() which relies on rangeURI which might not be set yet.
        if (!isEdited() && isExcludeFromShifting == isExcludeFromShifting())
            return;
        edit().setExcludeFromShifting(isExcludeFromShifting);
    }

    public void setMvEnabled(boolean mv)
    {
        if (mv == isMvEnabled())
            return;
        edit().setMvEnabled(mv);
    }

    public void setScale(int scale)
    {
        if (scale == getScale())
            return;
        edit().setScale(scale);
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
        String current = getImportAliases();
        String newAliases = ColumnRenderProperties.convertToString(aliases);
        if (StringUtils.equals(current, newAliases))
            return;
        edit().setImportAliasesSet(aliases);
    }

    public Set<String> getImportAliasSet()
    {
        return _pd.getImportAliasSet();
    }

    public void setURL(String url)
    {
        if (StringUtils.equals(getURL(), url))
            return;

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

    private PropertyDescriptor editSchema()
    {
        PropertyDescriptor pd = edit();
        _schemaChanged = true;
        _pd.clearPropertyType();
        return pd;
    }

    public boolean isRecreateRequired()
    {
        return _schemaChanged && _schemaImport;
    }

    public void markAsNew()
    {
        assert isRecreateRequired() && !isNew();
        _pd.setPropertyId(0);
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

    public PropertyType getPropertyType()
    {
        return _pd.getPropertyType();
    }

    public int getSqlType()
    {
        return _pd.getPropertyType().getSqlType();
    }

    public int getScale()
    {
        return _pd.getScale();
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

    @Override
    public void setDefaultValue(String value)
    {
        _defaultValue = value;
    }

    public String getDefaultValue()
    {
        return _defaultValue;
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
        Lookup current = getLookup();

        if (current == lookup)
            return;

        // current will return null if the schema or query is null so check
        // for this case in the passed in lookup
        if (current == null)
            if (lookup.getQueryName() == null || lookup.getSchemaName() == null)
                return;

        if (current != null && current.equals(lookup))
            return;

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
        OntologyManager.removePropertyDescriptorFromDomain(this);
    }

    public void save(User user, DomainDescriptor dd, int sortOrder) throws ChangePropertyDescriptorException
    {
        if (isNew())
            _pd = OntologyManager.insertOrUpdatePropertyDescriptor(_pd, dd, sortOrder);
        else if (_pdOld != null)
        {
            _pd = OntologyManager.updatePropertyDescriptor(user, _domain._dd, _pdOld, _pd, sortOrder);

            boolean hasProvisioner = null != getDomain().getDomainKind() && null != getDomain().getDomainKind().getStorageSchemaName() && dd.getStorageTableName() != null;

            if (hasProvisioner)
            {
                boolean mvAdded = !_pdOld.isMvEnabled() && _pd.isMvEnabled();
                boolean mvDropped = _pdOld.isMvEnabled() && !_pd.isMvEnabled();
                boolean propRenamed = !_pdOld.getName().equals(_pd.getName());
                boolean propResized = _pd.isStringType() && _pdOld.getScale() != _pd.getScale();

                // Drop first, so rename doesn't have to worry about it
                if (mvDropped)
                    StorageProvisioner.dropMvIndicator(this, _pdOld);

                if (propRenamed)
                    StorageProvisioner.renameProperty(this.getDomain(), this, _pdOld, mvDropped);

                if (propResized)
                    StorageProvisioner.resizeProperty(this.getDomain(), this, _pdOld.getScale());

                if (mvAdded)
                    StorageProvisioner.addMvIndicator(this);
            }
        }
        else
            OntologyManager.ensurePropertyDomain(_pd, _domain._dd, sortOrder);

        _pdOld = null;
        _schemaChanged = false;
        _schemaImport = false;

        ensureValidatorForType();
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
    public void copyFrom(DomainProperty propSrc, Container targetContainer)
    {
        setDescription(propSrc.getDescription());
        setFormat(propSrc.getFormat());
        setLabel(propSrc.getLabel());
        setName(propSrc.getName());
        setDescription(propSrc.getDescription());
        setConceptURI(propSrc.getConceptURI());
        setType(propSrc.getType());
        setDimension(propSrc.isDimension());
        setMeasure(propSrc.isMeasure());
        setRecommendedVariable(propSrc.isRecommendedVariable());
        setDefaultScale(propSrc.getDefaultScale());
        setRequired(propSrc.isRequired());
        setExcludeFromShifting(propSrc.isExcludeFromShifting());
        setFacetingBehavior(propSrc.getFacetingBehavior());
        setImportAliasSet(propSrc.getImportAliasSet());
        setPhi(propSrc.getPHI());
        setURL(propSrc.getURL());
        setHidden(propSrc.isHidden());
        setShownInDetailsView(propSrc.isShownInDetailsView());
        setShownInInsertView(propSrc.isShownInInsertView());
        setShownInUpdateView(propSrc.isShownInUpdateView());
        setMvEnabled(propSrc.isMvEnabled());
        setDefaultValueTypeEnum(propSrc.getDefaultValueTypeEnum());
        setScale(propSrc.getScale());

        // check to see if we're moving a lookup column to another container:
        Lookup lookup = propSrc.getLookup();
        if (lookup != null && !getContainer().equals(targetContainer))
        {
            // we need to update the lookup properties if the lookup container is either the source or the destination container
            if (lookup.getContainer() == null)
                lookup.setContainer(propSrc.getContainer());
            else if (lookup.getContainer().equals(targetContainer))
                lookup.setContainer(null);
        }
        setLookup(lookup);
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
            _validators = new ArrayList<>();
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
            _formats = new ArrayList<>();
            _formats.addAll(DomainPropertyManager.get().getConditionalFormats(this));
        }
        return _formats;
    }

    private void ensureValidatorForType()
    {
        PropertyDescriptor pd = getPropertyDescriptor();
        Lsid lsidValidator = DefaultPropertyValidator.createValidatorURI(PropertyValidatorType.Length);
        IPropertyValidator pvLength = PropertyService.get().createValidator(lsidValidator.toString());
        if (null != pvLength)
        {
            if (PropertyType.STRING.equals(pd.getPropertyType()) && pd.getScale() > 0)
            {
                for (PropertyValidatorImpl validator : ensureValidators())
                {
                    if (validator.getType() == pvLength.getType())
                        return;        // Type validator already present
                }

                pvLength.setName("Text Length");
                pvLength.setExpressionValue("~lte=" + pd.getScale());
                addValidator(pvLength);
            }
            else
            {
                for (PropertyValidatorImpl validator : ensureValidators())
                {
                    if (validator.getType() == pvLength.getType())
                    {
                        removeValidator(validator);
                        return;
                    }
                }
            }
        }
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
        if (getFacetingBehavior() == type)
            return;

        edit().setFacetingBehaviorType(type);
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

    public static class TestCase extends Assert
    {
        private PropertyDescriptor _pd;
        private DomainPropertyImpl _dp;

        @Test
        public void testUpdateDomainPropertyFromDescriptor() throws Exception
        {
            Container c = ContainerManager.ensureContainer("/_DomainPropertyImplTest");
            String domainURI = new Lsid("Junit", "DD", "Domain1").toString();
            Domain d = PropertyService.get().createDomain(c, domainURI, "Domain1");

            resetProperties(d, domainURI, c);

            // verify no change
            OntologyManager.updateDomainPropertyFromDescriptor(_dp, _pd);
            assertFalse(_dp.isDirty());
            assertFalse(_dp._schemaChanged);

            // change a property
            _pd.setPHI(PHI.Restricted);
            OntologyManager.updateDomainPropertyFromDescriptor(_dp, _pd);
            assertTrue(_dp.isDirty());
            assertFalse(_dp._schemaChanged);
            assertTrue(_dp.getPHI() == _pd.getPHI());

            // Issue #18738 change the schema outside of a schema reload and verify that the column
            // change the schema but don't mark the property as "Schema Import"
            // this will allow whatever type changes the UI allows (text -> multiline, for example)
            resetProperties(d, domainURI, c);
            _pd.setRangeURI("http://www.w3.org/2001/XMLSchema#double");
            OntologyManager.updateDomainPropertyFromDescriptor(_dp, _pd);
            assertTrue(_dp.isDirty());
            assertTrue(_dp._schemaChanged);
            assertFalse(_dp.isRecreateRequired());
            assertTrue(StringUtils.equals(_dp.getRangeURI(), _pd.getRangeURI()));

            // setting schema import to true will enable the _schemaChanged flag to toggle
            // so it should be set true here
            resetProperties(d, domainURI, c);
            _dp.setSchemaImport(true);
            _pd.setRangeURI("http://www.w3.org/2001/XMLSchema#double");
            OntologyManager.updateDomainPropertyFromDescriptor(_dp, _pd);
            assertTrue(_dp.isDirty());
            assertTrue(_dp._schemaChanged);
            assertTrue(_dp.isRecreateRequired());
            assertTrue(StringUtils.equals(_dp.getRangeURI(), _pd.getRangeURI()));

            // verify no change when setting value to the same value as it was
            resetProperties(d, domainURI, c);
            _pd.setRangeURI("http://www.w3.org/2001/XMLSchema#int");
            _pd.setPHI(PHI.NotPHI);
            OntologyManager.updateDomainPropertyFromDescriptor(_dp, _pd);
            assertFalse(_dp.isDirty());
            assertFalse(_dp._schemaChanged);
            assertFalse(_dp.isRecreateRequired());

            // verify Lookup is set to null with null schema
            resetProperties(d, domainURI, c);
            verifyLookup(null, "lkSchema", null, true);

            // verify Lookup is set to null with null query
            resetProperties(d, domainURI, c);
            verifyLookup(null, null, "lkQuery",true);

            // verify Lookup is set to null with invalid container
            resetProperties(d, domainURI, c);
            verifyLookup("bogus", null, "lkQuery",true);

            // verify Lookup is set with valid schema and query
            resetProperties(d, domainURI, c);
            verifyLookup(null, "lkSchema", "lkQuery",true);

            // verify Lookup is set with valid container, schema and query
            resetProperties(d, domainURI, c);
            verifyLookup(c.getId(), "lkSchema1", "lkQuery2",true);

            // no cleanup as we never persisted anything
        }

        private void verifyLookup(String containerId, String schema, String query, Boolean expectedDirty)
        {
            _pd.setLookupContainer(containerId);
            _pd.setLookupQuery(query);
            _pd.setLookupSchema(schema);
            OntologyManager.updateDomainPropertyFromDescriptor(_dp, _pd);
            assertTrue(_dp.isDirty() == expectedDirty);
            assertFalse(_dp._schemaChanged);

            // verify the lookup object returned
            Lookup l = _dp.getLookup();

            if (l == null)
            {
                // lookup can be null if we specified a containerId that is invalid or
                // we specified a valid containerId (including null) but schema or query is null
                if (containerId != null && null == ContainerManager.getForId(containerId))
                    assertTrue(true);
                else if (query == null || schema == null)
                    assertTrue(true);
                else
                    assertTrue(false);
            }
            else
            {
                if (containerId != null)
                    assertTrue(StringUtils.equals(l.getContainer().getId(), _pd.getLookupContainer()));

                assertTrue(StringUtils.equals(l.getQueryName(), _pd.getLookupQuery()));
                assertTrue(StringUtils.equals(l.getSchemaName(),_pd.getLookupSchema()));
            }
        }

        private void resetProperties(Domain d, String domainUri, Container c)
        {
            _pd = getPropertyDescriptor(c, domainUri);
            _dp = (DomainPropertyImpl) d.addProperty();
            _pd.copyTo(_dp.getPropertyDescriptor());
        }


        private PropertyDescriptor getPropertyDescriptor(Container c, String domainURI)
        {
            PropertyDescriptor pd = new PropertyDescriptor();
            pd.setPropertyURI(domainURI + ":column");
            pd.setName("column");
            pd.setLabel("label");
            pd.setConceptURI(null);
            pd.setRangeURI("http://www.w3.org/2001/XMLSchema#int");
            pd.setContainer(c);
            pd.setDescription("description");
            pd.setURL(StringExpressionFactory.createURL((String)null));
            pd.setImportAliases(null);
            pd.setRequired(false);
            pd.setHidden(false);
            pd.setShownInInsertView(true);
            pd.setShownInUpdateView(true);
            pd.setShownInDetailsView(true);
            pd.setDimension(false);
            pd.setMeasure(true);
            pd.setRecommendedVariable(false);
            pd.setDefaultScale(DefaultScaleType.LINEAR);
            pd.setFormat(null);
            pd.setMvEnabled(false);
            pd.setLookupContainer(c.getId());
            pd.setLookupSchema("lkSchema");
            pd.setLookupQuery("lkQuery");
            pd.setFacetingBehaviorType(FacetingBehaviorType.AUTOMATIC);
            pd.setPHI(PHI.NotPHI);
            pd.setExcludeFromShifting(false);
            return pd;
        }
    }
}
