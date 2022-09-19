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

package org.labkey.experiment.api.property;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.data.ColumnRenderPropertiesImpl;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.StorageProvisioner;
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
import org.labkey.api.security.User;
import org.labkey.api.util.StringExpressionFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    @Override
    public int getPropertyId()
    {
        return _pd.getPropertyId();
    }

    @Override
    public Container getContainer()
    {
        return _pd.getContainer();
    }

    @Override
    public String getPropertyURI()
    {
        return _pd.getPropertyURI();
    }

    @Override
    public String getName()
    {
        return _pd.getName();
    }

    @Override
    public String getDescription()
    {
        return _pd.getDescription();
    }

    @Override
    public String getFormat()
    {
        return _pd.getFormat();
    }

    @Override
    public String getLabel()
    {
        return _pd.getLabel();
    }

    @Override
    public String getConceptURI()
    {
        return _pd.getConceptURI();
    }

    @Override
    public Domain getDomain()
    {
        return _domain;
    }

    @Override
    public IPropertyType getType()
    {
        return PropertyService.get().getType(getContainer(), _pd.getRangeURI());
    }

    @Override
    public boolean isRequired()
    {
        return _pd.isRequired();
    }

    @Override
    public boolean isHidden()
    {
        return _pd.isHidden();
    }

    @Override
    public boolean isDeleted()
    {
        return _deleted;
    }

    @Override
    public boolean isShownInInsertView()
    {
        return _pd.isShownInInsertView();
    }

    @Override
    public boolean isShownInDetailsView()
    {
        return _pd.isShownInDetailsView();
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
    public void setName(String name)
    {
        if (StringUtils.equals(name, getName()))
            return;
        edit().setName(name);
    }

    @Override
    public void setDescription(String description)
    {
        if (StringUtils.equals(description, getDescription()))
            return;
        edit().setDescription(description);
    }

    @Override
    public void setType(IPropertyType domain)
    {
        edit().setRangeURI(domain.getTypeURI());
    }

    @Override
    public void setPropertyURI(String uri)
    {
        if (StringUtils.equals(uri, getPropertyURI()))
            return;
        edit().setPropertyURI(uri);
    }

    @Override
    public void setRangeURI(String rangeURI)
    {
        if (StringUtils.equals(rangeURI, getRangeURI()))
            return;
        editSchema().setRangeURI(rangeURI);
    }

    @Override
    public String getRangeURI()
    {
        return _pd.getRangeURI();
    }

    @Override
    public void setFormat(String s)
    {
        if (StringUtils.equals(s, getFormat()))
            return;
        edit().setFormat(s);
    }

    @Override
    public void setLabel(String caption)
    {
        if (StringUtils.equals(caption, getLabel()))
            return;
        edit().setLabel(caption);
    }

    @Override
    public void setConceptURI(String conceptURI)
    {
        if (StringUtils.equals(conceptURI, getConceptURI()))
            return;
        edit().setConceptURI(conceptURI);
    }

    @Override
    public void setRequired(boolean required)
    {
        if (required == isRequired())
            return;
        edit().setRequired(required);
    }

    @Override
    public void setHidden(boolean hidden)
    {
        if (hidden == isHidden())
            return;
        edit().setHidden(hidden);
    }

    @Override
    public void setShownInDetailsView(boolean shown)
    {
        if (shown == isShownInDetailsView())
            return;
        edit().setShownInDetailsView(shown);
    }

    @Override
    public void setShownInInsertView(boolean shown)
    {
        if (shown == isShownInInsertView())
            return;
        edit().setShownInInsertView(shown);
    }

    @Override
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
        if (!isEdited() && ((getRedactedText() != null && getRedactedText().equals(redactedText))
                || (getRedactedText() == null && redactedText == null)))
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

    @Override
    public void setMvEnabled(boolean mv)
    {
        if (mv == isMvEnabled())
            return;
        edit().setMvEnabled(mv);
    }

    @Override
    public void setScale(int scale)
    {
        if (scale == getScale())
            return;
        edit().setScale(scale);
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public void setImportAliases(String aliases)
    {
        if (StringUtils.equals(aliases, getImportAliases()))
            return;
        edit().setImportAliases(aliases);
    }

    /** Need the string version of this method because it's called by reflection and must match by name */
    public String getImportAliases()
    {
        return _pd.getImportAliases();
    }

    @Override
    public void setImportAliasSet(Set<String> aliases)
    {
        String current = getImportAliases();
        String newAliases = ColumnRenderPropertiesImpl.convertToString(aliases);
        if (StringUtils.equals(current, newAliases))
            return;
        edit().setImportAliasesSet(aliases);
    }

    @Override
    public Set<String> getImportAliasSet()
    {
        return _pd.getImportAliasSet();
    }

    @Override
    public void setURL(String url)
    {
        if (StringUtils.equals(getURL(), url))
            return;

        if (null == url)
            edit().setURL(null);
        else
            edit().setURL(StringExpressionFactory.createURL(url));
    }

    @Override
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

    @Override
    public PropertyType getPropertyType()
    {
        return _pd.getPropertyType();
    }

    @Override
    public JdbcType getJdbcType()
    {
        return _pd.getPropertyType().getJdbcType();
    }

    @Override
    public int getScale()
    {
        return _pd.getScale();
    }

    @Override
    public String getInputType()
    {
        return _pd.getPropertyType().getInputType();
    }

    @Override
    public DefaultValueType getDefaultValueTypeEnum()
    {
        return _pd.getDefaultValueTypeEnum();
    }

    @Override
    public void setDefaultValueTypeEnum(DefaultValueType defaultValueType)
    {
        _pd.setDefaultValueTypeEnum(defaultValueType);
    }

    public String getDefaultValueType()
    {
        return _pd.getDefaultValueType();
    }

    @Override
    public void setDefaultValueType(String defaultValueTypeName)
    {
        if (getDefaultValueType() != null && getDefaultValueType().equals(defaultValueTypeName))
            return;

        edit().setDefaultValueType(defaultValueTypeName);
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

    @Override
    public Lookup getLookup()
    {
        return _pd.getLookup();
    }

    @Override
    public void setLookup(Lookup lookup)
    {
        Lookup current = getLookup();

        if (current == lookup)
            return;

        // current will return null if the schema or query is null so check
        // for this case in the passed in lookup
        if (current == null)
            if (lookup.getQueryName() == null || lookup.getSchemaKey() == null)
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
        edit().setLookupSchema(Objects.toString(lookup.getSchemaKey(),null));
    }

    @Override
    public void setScannable(boolean scannable)
    {
        if (scannable != isScannable())
            edit().setScannable(scannable);
    }

    @Override
    public boolean isScannable()
    {
        return _pd.isScannable();
    }

    @Override
    public void setPrincipalConceptCode(String code)
    {
        if (!StringUtils.equals(code, getPrincipalConceptCode()))
            edit().setPrincipalConceptCode(code);
    }

    @Override
    public String getPrincipalConceptCode()
    {
        return _pd.getPrincipalConceptCode();
    }

    @Override
    public String getSourceOntology()
    {
        return _pd.getSourceOntology();
    }

    @Override
    public void setSourceOntology(String sourceOntology)
    {
        if (!StringUtils.equals(sourceOntology, getSourceOntology()))
            edit().setSourceOntology(sourceOntology);
    }

    @Override
    public String getConceptSubtree()
    {
        return _pd.getConceptSubtree();
    }

    @Override
    public void setConceptSubtree(String path)
    {
        if (!StringUtils.equals(path, getConceptSubtree()))
            edit().setConceptSubtree(path);
    }

    @Override
    public String getConceptImportColumn()
    {
        return _pd.getConceptImportColumn();
    }

    @Override
    public void setConceptImportColumn(String conceptImportColumn)
    {
        if (!StringUtils.equals(conceptImportColumn, getConceptImportColumn()))
            edit().setConceptImportColumn(conceptImportColumn);
    }

    @Override
    public String getConceptLabelColumn()
    {
        return _pd.getConceptLabelColumn();
    }

    @Override
    public void setConceptLabelColumn(String conceptLabelColumn)
    {
        if (!StringUtils.equals(conceptLabelColumn, getConceptLabelColumn()))
            edit().setConceptLabelColumn(conceptLabelColumn);
    }

    @Override
    public void setDerivationDataScope(String scope)
    {
        if (!StringUtils.equals(scope, getDerivationDataScope()))
            edit().setDerivationDataScope(scope);
    }

    @Override
    public String getDerivationDataScope()
    {
        return _pd.getDerivationDataScope();
    }

    @Override
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

    public boolean isSwap()
    {
        return isNew() && _pdOld != null && _pd.getPropertyURI() != null
                && _pdOld.getPropertyURI() != null && !_pd.getPropertyURI().equals(_pdOld.getPropertyURI());
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
        DomainPropertyManager.get().removeValidatorsForPropertyDescriptor(getContainer(), getPropertyId());
        DomainPropertyManager.get().deleteConditionalFormats(getPropertyId());

        DomainKind<?> kind = getDomain().getDomainKind();
        if (null != kind)
            kind.deletePropertyDescriptor(getDomain(), user, _pd);
        OntologyManager.removePropertyDescriptorFromDomain(this);
    }

    public void save(User user, DomainDescriptor dd, int sortOrder) throws ChangePropertyDescriptorException
    {
        if (isSwap())
        {
            _pd = OntologyManager.insertOrUpdatePropertyDescriptor(_pd, dd, sortOrder);
            OntologyManager.removePropertyDescriptorFromDomain(new DomainPropertyImpl((DomainImpl) getDomain(), _pdOld));
        }
        else if (isNew())
        {
            _pd = OntologyManager.insertOrUpdatePropertyDescriptor(_pd, dd, sortOrder);
        }
        else if (_pdOld != null)
        {
            PropertyType oldType = _pdOld.getPropertyType();
            PropertyType newType = _pd.getPropertyType();
            boolean changedType = false;
            if (oldType.getJdbcType() != newType.getJdbcType())
            {
                if (newType.getJdbcType().isText() ||
                        (oldType.getJdbcType().isInteger() && newType.getJdbcType().isNumeric()))
                {
                    changedType = true;
                    if (newType.getJdbcType().isText())
                    {
                        // Remove any previously set formatting string as it won't apply to a text field
                        _pd.setFormat(null);
                    }
                }
                else
                {
                    throw new ChangePropertyDescriptorException("Cannot convert an instance of " + oldType.getJdbcType() + " to " + newType.getJdbcType() + ".");
                }
            }

            // Issue 44711: Prevent attachment and file field types from being converted to a different type
            if (PropertyType.FILE_LINK.getInputType().equalsIgnoreCase(oldType.getInputType()) && oldType != newType)
                throw new ChangePropertyDescriptorException("Cannot convert an instance of " + oldType.name() + " to " + newType.name() + ".");

            OntologyManager.validatePropertyDescriptor(_pd);
            Table.update(user, OntologyManager.getTinfoPropertyDescriptor(), _pd, _pdOld.getPropertyId());
            OntologyManager.ensurePropertyDomain(_pd, dd, sortOrder);

            boolean hasProvisioner = null != getDomain().getDomainKind() && null != getDomain().getDomainKind().getStorageSchemaName() && dd.getStorageTableName() != null;

            if (hasProvisioner)
            {
                boolean mvAdded = !_pdOld.isMvEnabled() && _pd.isMvEnabled();
                boolean mvDropped = _pdOld.isMvEnabled() && !_pd.isMvEnabled();
                boolean propRenamed = !_pdOld.getName().equals(_pd.getName());
                boolean propResized = _pd.isStringType() && _pdOld.getScale() != _pd.getScale();

                // Drop first, so rename doesn't have to worry about it
                if (mvDropped)
                    ((StorageProvisionerImpl)StorageProvisioner.get()).dropMvIndicator(this, _pdOld);

                if (propRenamed)
                    StorageProvisionerImpl.get().renameProperty(this.getDomain(), this, _pdOld, mvDropped);

                if (changedType)
                {
                    StorageProvisionerImpl.get().changePropertyType(this.getDomain(), this);
                    if (_pdOld.getJdbcType() == JdbcType.BOOLEAN && _pd.getJdbcType().isText())
                    {
                        updateBooleanValue(_domain.getDomainKind().getStorageSchemaName() + "." + _domain.getStorageTableName(),
                                _pd.getStorageColumnName(), _pdOld.getFormat(), null);
                    }
                }
                else if (propResized)
                    StorageProvisionerImpl.get().resizeProperty(this.getDomain(), this, _pdOld.getScale());

                if (mvAdded)
                    StorageProvisionerImpl.get().addMvIndicator(this);
            }
            else if (changedType)
            {
                if (oldType.getJdbcType().isDateOrTime() && newType.getJdbcType().isText())
                {
                    new SqlExecutor(OntologyManager.getExpSchema()).execute(
                            new SQLFragment("UPDATE ").
                                    append(OntologyManager.getTinfoObjectProperty().getSelectName()).
                                    append(" SET StringValue = DateTimeValue, DateTimeValue = NULL WHERE PropertyId = ?").
                                    add(_pdOld.getPropertyId()));
                }
                else if (!oldType.getJdbcType().isText() && newType.getJdbcType().isText())
                {
                    new SqlExecutor(OntologyManager.getExpSchema()).execute(
                            new SQLFragment("UPDATE ").
                                    append(OntologyManager.getTinfoObjectProperty().getSelectName()).
                                    append(" SET StringValue = FloatValue, FloatValue = NULL WHERE PropertyId = ?").
                                    add(_pdOld.getPropertyId()));
                }
                else //noinspection StatementWithEmptyBody
                    if (oldType.getJdbcType().isInteger() && newType.getJdbcType().isReal())
                {
                    // Since exp.ObjectProperty stores these types in the same column, there's nothing for us to do
                }
                else
                {
                    throw new ChangePropertyDescriptorException("Cannot convert from " + oldType.getJdbcType() + " to " + newType.getJdbcType() + " for non-provisioned table");
                }
            }

            if (changedType && _pdOld.getJdbcType() == JdbcType.BOOLEAN && _pd.getJdbcType().isText())
            {
                updateBooleanValue(OntologyManager.getTinfoObjectProperty().getSelectName(), "StringValue", _pdOld.getFormat(), new SQLFragment("PropertyId = ?", _pdOld.getPropertyId()));
            }
        }
        else
        {
            OntologyManager.ensurePropertyDomain(_pd, _domain._dd, sortOrder);
        }

        _pdOld = null;
        _schemaChanged = false;
        _schemaImport = false;

        for (PropertyValidatorImpl validator : ensureValidators())
        {
            if (validator.isDeleted())
                DomainPropertyManager.get().removePropertyValidator(this, validator);
            else
                DomainPropertyManager.get().savePropertyValidator(user, this, validator);
        }

        DomainPropertyManager.get().saveConditionalFormats(user, getPropertyDescriptor(), ensureConditionalFormats());
    }

    /**
     * Format values in columns that were just converted from booleans to strings with the DB's default type conversion.
     * Postgres will now have 'true' and 'false', and SQLServer will have '0' and '1'. Use the format string to use the
     * preferred format, and standardize on 'true' and 'false' in the absence of an explicitly configured format.
     */
    private void updateBooleanValue(String schemaTable, String column, String formatString, @Nullable SQLFragment whereClause)
    {
        column = OntologyManager.getExpSchema().getSqlDialect().makeLegalIdentifier(column);
        BooleanFormat f = BooleanFormat.getInstance(formatString);
        String trueValue = StringUtils.trimToNull(f.format(true));
        String falseValue = StringUtils.trimToNull(f.format(false));
        String nullValue = StringUtils.trimToNull(f.format(null));
        SQLFragment sql = new SQLFragment("UPDATE ").append(schemaTable).append(" SET ").
                append(column).append(" = CASE WHEN ").
                append(column).append(" IN ('1', 'true') THEN ? WHEN ").
                append(column).append(" IN ('0', 'false') THEN ? ELSE ? END");
        sql.add(trueValue);
        sql.add(falseValue);
        sql.add(nullValue);
        if (whereClause != null)
        {
            sql.append(" WHERE ");
            sql.append(whereClause);
        }
        new SqlExecutor(OntologyManager.getExpSchema()).execute(sql);
    }

    @Override
    @NotNull
    public List<PropertyValidatorImpl> getValidators()
    {
        return Collections.unmodifiableList(ensureValidators());
    }

    @Override
    public void addValidator(IPropertyValidator validator)
    {
        if (validator != null)
        {
            if (0 != validator.getPropertyId() && getPropertyId() != validator.getPropertyId())
                throw new IllegalStateException();

            // Ensure validator is a valid kind (ex. urn:lsid:labkey.com:PropertyValidator:length is no longer valid)
            if ( null != PropertyService.get().getValidatorKind(validator.getTypeURI()) )
            {
                PropertyValidator impl = new PropertyValidator();
                impl.copy(validator);
                impl.setPropertyId(getPropertyId());
                ensureValidators().add(new PropertyValidatorImpl(impl));
            }
        }
    }

    @Override
    public void removeValidator(IPropertyValidator validator)
    {
        int idx = ensureValidators().indexOf(validator);
        if (idx != -1)
        {
            PropertyValidatorImpl impl = ensureValidators().get(idx);
            impl.delete();
        }
    }

    @Override
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
        setScannable(propSrc.isScannable());

        setPrincipalConceptCode(propSrc.getPrincipalConceptCode());
        setSourceOntology(propSrc.getSourceOntology());
        setConceptSubtree(propSrc.getConceptSubtree());
        setConceptImportColumn(propSrc.getConceptImportColumn());
        setConceptLabelColumn(propSrc.getConceptLabelColumn());

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
        public void testUpdateDomainPropertyFromDescriptor()
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
                assertTrue(StringUtils.equals(l.getSchemaKey().toString(), _pd.getLookupSchema()));
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
