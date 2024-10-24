/*
 * Copyright (c) 2018-2019 LabKey Corporation
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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.LockedPropertyType;
import org.labkey.api.gwt.client.ui.PropertyType;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.IntegerProperty;
import org.labkey.api.gwt.client.util.StringProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * User: matthewb
 * Date: Apr 24, 2007
 * Time: 1:28:42 PM
 *
 * see org.labkey.api.exp.PropertyDescriptor
 */
public class GWTPropertyDescriptor implements IsSerializable
{
    private IntegerProperty propertyId = new IntegerProperty(0);
    private StringProperty propertyURI = new StringProperty();
    private StringProperty container = new StringProperty();
    private StringProperty name = new StringProperty();
    private StringProperty description = new StringProperty();
    private StringProperty rangeURI = new StringProperty("http://www.w3.org/2001/XMLSchema#string");
    private StringProperty conceptURI = new StringProperty();
    private StringProperty label = new StringProperty();
    private StringProperty format = new StringProperty();
    private BooleanProperty required = new BooleanProperty(false);
    private BooleanProperty hidden = new BooleanProperty(false);
    private StringProperty lookupContainer = new StringProperty();
    private StringProperty lookupSchema = new StringProperty();
    private StringProperty lookupQuery = new StringProperty();
    private BooleanProperty lookupIsValid = new BooleanProperty(true);
    private String defaultValueType = null;
    private StringProperty defaultValue = new StringProperty();
    private StringProperty defaultDisplayValue = new StringProperty("[none]");
    private BooleanProperty mvEnabled = new BooleanProperty(false);
    private StringProperty importAliases = new StringProperty();
    private StringProperty url = new StringProperty();
    private BooleanProperty shownInInsertView = new BooleanProperty(true);
    private BooleanProperty shownInUpdateView = new BooleanProperty(true);
    private BooleanProperty shownInDetailsView = new BooleanProperty(true);
    private BooleanProperty measure = new BooleanProperty();
    private BooleanProperty dimension = new BooleanProperty();
    private BooleanProperty recommendedVariable = new BooleanProperty(false);
    private StringProperty defaultScale = new StringProperty(DefaultScaleType.LINEAR.name());
    private StringProperty facetingBehaviorType = new StringProperty();
    private StringProperty phi = new StringProperty("NotPHI"); // Must match PHI.NotPHI and tableInfo.xsd enum PHIType.NotPHI
    private BooleanProperty isExcludeFromShifting = new BooleanProperty();
    private BooleanProperty isPreventReordering = new BooleanProperty();
    private BooleanProperty isDisableEditing = new BooleanProperty();
    private IntegerProperty scale = new IntegerProperty(4000);
    private StringProperty principalConceptCode = new StringProperty();
    private StringProperty sourceOntology = new StringProperty();
    private StringProperty conceptSubtree = new StringProperty();
    private StringProperty conceptImportColumn = new StringProperty();
    private StringProperty conceptLabelColumn = new StringProperty();
    private StringProperty redactedText = new StringProperty();
    private StringProperty derivationDataScope = new StringProperty();
    private BooleanProperty isPrimaryKey = new BooleanProperty(false);
    private StringProperty lockType = new StringProperty(LockedPropertyType.NotLocked.name());
    private BooleanProperty scannable = new BooleanProperty(false);
    private StringProperty valueExpression = new StringProperty();

    // for controlling the property editor (not persisted or user settable)
//    private boolean isEditable = true;
    private boolean isTypeEditable = true;
//    private boolean isNameEditable = true;

    private List<GWTPropertyValidator> validators = new ArrayList<GWTPropertyValidator>();
    private List<GWTConditionalFormat> conditionalFormats = new ArrayList<GWTConditionalFormat>();

    public GWTPropertyDescriptor()
    {
    }

    public GWTPropertyDescriptor(String name, String rangeURI)
    {
        setName(name);
        setRangeURI(rangeURI);
    }

    public GWTPropertyDescriptor(GWTPropertyDescriptor s)
    {
        setPropertyId(s.getPropertyId());
        setPropertyURI(s.getPropertyURI());
        setContainer(s.getContainer());
        setName(s.getName());
        setDescription(s.getDescription());
        setRangeURI(s.getRangeURI());
        setConceptURI(s.getConceptURI());
        setLabel(s.getLabel());
        setFormat(s.getFormat());
        setRequired(s.isRequired());
        setHidden(s.isHidden());
        setShownInDetailsView(s.isShownInDetailsView());
        setShownInInsertView(s.isShownInInsertView());
        setShownInUpdateView(s.isShownInUpdateView());
        setMvEnabled(s.getMvEnabled());
        setMeasure(s.isMeasure());
        setDimension(s.isDimension());
        setRecommendedVariable(s.isRecommendedVariable());
        setDefaultScale(s.getDefaultScale());
        setLookupContainer(s.getLookupContainer());
        setLookupSchema(s.getLookupSchema());
        setLookupQuery(s.getLookupQuery());
        setDefaultValueType(s.getDefaultValueType());
        setDefaultValue(s.getDefaultValue());
        setDefaultDisplayValue(s.getDefaultDisplayValue());
        setImportAliases(s.getImportAliases());
        setURL(s.getURL());
        setFacetingBehaviorType(s.getFacetingBehaviorType());
        setPHI(s.getPHI());
        setExcludeFromShifting(s.isExcludeFromShifting());
        setPreventReordering(s.getPreventReordering());
        setDisableEditing(s.getDisableEditing());
        setScale(s.getScale());
        setRedactedText(s.getRedactedText());
        setIsPrimaryKey(s.getIsPrimaryKey());
        setLockType(s.getLockType());
        setTypeEditable(s.isTypeEditable());
        setPrincipalConceptCode(s.getPrincipalConceptCode());
        setSourceOntology(s.getSourceOntology());
        setConceptSubtree(s.getConceptSubtree());
        setConceptImportColumn(s.getConceptImportColumn());
        setConceptLabelColumn(s.getConceptLabelColumn());
        setDerivationDataScope(s.getDerivationDataScope());
        setScannable(s.isScannable());
        setValueExpression(s.getValueExpression());

        for (GWTPropertyValidator v : s.getPropertyValidators())
        {
            validators.add(new GWTPropertyValidator(v));
        }

        for (GWTConditionalFormat f : s.getConditionalFormats())
        {
            conditionalFormats.add(new GWTConditionalFormat(f));
        }
    }

    public GWTPropertyDescriptor copy()
    {
        return new GWTPropertyDescriptor(this);
    }

    public String getContainer()
    {
        return container.getString();
    }

    public void setContainer(String container)
    {
        this.container.set(container);
    }

    public String getLookupContainer()
    {
        return lookupContainer.getString();
    }

    public void setLookupContainer(String lookupContainer)
    {
        this.lookupContainer.set(lookupContainer);
    }

    public String getLookupSchema()
    {
        return lookupSchema.getString();
    }

    public void setLookupSchema(String lookupSchema)
    {
        this.lookupSchema.set(lookupSchema);
    }

    public String getLookupQuery()
    {
        return lookupQuery.getString();
    }

    public void setLookupQuery(String lookupQuery)
    {
        this.lookupQuery.set(lookupQuery);
    }

    public boolean getLookupIsValid()
    {
        return lookupIsValid.getBoolean();
    }

    public void setLookupIsValid(boolean lookupIsValid)
    {
        this.lookupIsValid.set(lookupIsValid);
    }

    public int getPropertyId()
    {
        return propertyId.getInt();
    }

    public void setPropertyId(int rowId)
    {
        this.propertyId.setInt(rowId);
    }

    public String getPropertyURI()
    {
        return propertyURI.getString();
    }

    public void setPropertyURI(String propertyURI)
    {
        this.propertyURI.set(propertyURI);
    }

    public String getName()
    {
        return name.getString();
    }

    public void setName(String name)
    {
        this.name.set(name);
    }

    public String getDescription()
    {
        return description.getString();
    }

    public void setDescription(String description)
    {
        this.description.set(description);
    }

    public String getRangeURI()
    {
        return rangeURI.getString();
    }

    public void setRangeURI(String dataTypeURI)
    {
        this.rangeURI.set(dataTypeURI);
    }

    public void guessMeasureAndDimension()
    {
        boolean plottableType = PropertyType.xsdInt.getURI().equals(getRangeURI()) ||
                PropertyType.xsdDouble.getURI().equals(getRangeURI());
        boolean isMeasure = plottableType && getLookupQuery() == null && !isHidden();
        setMeasure(isMeasure);

        setDimension(getLookupQuery() != null && !isHidden());
    }


    public String getConceptURI()
    {
        return conceptURI.getString();
    }

    public void setConceptURI(String conceptURI)
    {
        this.conceptURI.set(conceptURI);
    }

    public String getLabel()
    {
        return label.getString();
    }

    public void setLabel(String label)
    {
        this.label.set(label);
    }

    public String getFormat()
    {
        return format.getString();
    }

    public void setFormat(String format)
    {
        this.format.set(format);
    }

    public boolean isRequired()
    {
        return required.getBool();
    }

    public void setRequired(boolean required)
    {
        this.required.setBool(required);
    }

    public boolean isHidden()
    {
        return hidden.getBool();
    }

    public void setHidden(boolean hidden)
    {
        this.hidden.setBool(hidden);
    }

    public boolean isShownInInsertView()
    {
        return shownInInsertView.getBool();
    }

    public void setShownInInsertView(boolean shown)
    {
        shownInInsertView.setBool(shown);
    }

    public boolean isShownInUpdateView()
    {
        return shownInUpdateView.getBool();
    }

    public void setShownInUpdateView(boolean shown)
    {
        shownInUpdateView.setBool(shown);
    }

    public boolean isShownInDetailsView()
    {
        return shownInDetailsView.getBool();
    }

    public void setShownInDetailsView(boolean shown)
    {
        shownInDetailsView.setBool(shown);
    }

    public boolean isSetMeasure()
    {
        return measure.getBoolean() != null;
    }

    public boolean isMeasure()
    {
        return measure.booleanValue();
    }

    public void setMeasure(boolean isMeasure)
    {
        measure.setBool(isMeasure);
    }

    public boolean isSetDimension()
    {
        return dimension.getBoolean() != null;
    }

    public boolean isDimension()
    {
        return dimension.booleanValue();
    }

    public void setDimension(boolean isDimension)
    {
        dimension.setBool(isDimension);
    }

    public boolean isRecommendedVariable()
    {
        return recommendedVariable.booleanValue();
    }

    public void setRecommendedVariable(boolean isRecommendedVariable)
    {
        recommendedVariable.setBool(isRecommendedVariable);
    }

    public String getDefaultScale()
    {
        return defaultScale.getString();
    }

    public void setDefaultScale(String defaultScale)
    {
        this.defaultScale.set(defaultScale);
    }

    public boolean getMvEnabled()
    {
        return mvEnabled.getBool();
    }

    public void setMvEnabled(boolean mvEnabled)
    {
        this.mvEnabled.setBool(mvEnabled);
    }

    public DefaultValueType getDefaultValueType()
    {
        return null==defaultValueType ? null : DefaultValueType.valueOf(defaultValueType);
    }

    public void setDefaultValueType(DefaultValueType defaultValueType)
    {
        this.defaultValueType = null==defaultValueType ? null : defaultValueType.name();
    }

    public String getDefaultValue()
    {
        return defaultValue.getString();
    }

    public void setDefaultValue(String defaultValue)
    {
        this.defaultValue.set(defaultValue);
    }

    public String getDefaultDisplayValue()
    {
        return defaultDisplayValue.toString();
    }

    public void setDefaultDisplayValue(String  defaultDisplayValue)
    {
        this.defaultDisplayValue.set(defaultDisplayValue);
    }

    public String getFacetingBehaviorType()
    {
        return facetingBehaviorType.getString();
    }

    public void setFacetingBehaviorType(String facetingBehavior)
    {
        this.facetingBehaviorType.set(facetingBehavior);
    }

    public String getPHI()
    {
        return phi.getString();
    }

    public void setPHI(String phi)
    {
        this.phi.set(phi);
    }

    public boolean isSetExcludeFromShifting()
    {
        return isExcludeFromShifting.getBoolean() != null;
    }

    public boolean isExcludeFromShifting()
    {
        return isExcludeFromShifting.booleanValue();
    }

    public void setExcludeFromShifting(boolean isExcludeFromShifting)
    {
        this.isExcludeFromShifting.setBool(isExcludeFromShifting);
    }

    public boolean getPreventReordering()
    {
        return isPreventReordering.booleanValue();
    }

    public void setPreventReordering(boolean preventReordering)
    {
        isPreventReordering.setBool(preventReordering);
    }

    public boolean getDisableEditing()
    {
        return isDisableEditing.booleanValue();
    }

    public void setDisableEditing(boolean disableEditing)
    {
        isDisableEditing.setBool(disableEditing);
    }

    public Integer getScale()
    {
        return this.scale.getInteger();
    }

    public void setScale(Integer value)
    {
        this.scale.set(value);
    }

    public boolean isScannable()
    {
        return scannable.getBoolean();
    }

    public void setScannable(boolean scannable)
    {
        this.scannable.setBool(scannable);
    }

    public String getPrincipalConceptCode() { return this.principalConceptCode.getString(); }

    public void setPrincipalConceptCode(String code) { this.principalConceptCode.set(code); }

    public String getSourceOntology()
    {
        return sourceOntology.getString();
    }

    public void setSourceOntology(String sourceOntology)
    {
        this.sourceOntology.set(sourceOntology);
    }

    public String getConceptSubtree()
    {
        return this.conceptSubtree.getString();
    }

    public void setConceptSubtree(String path)
    {
        this.conceptSubtree.set(path);
    }

    public String getConceptImportColumn()
    {
        return conceptImportColumn.getString();
    }

    public void setConceptImportColumn(String conceptImportColumn)
    {
        this.conceptImportColumn.set(conceptImportColumn);
    }

    public String getConceptLabelColumn()
    {
        return conceptLabelColumn.getString();
    }

    public void setConceptLabelColumn(String conceptLabelColumn)
    {
        this.conceptLabelColumn.set(conceptLabelColumn);
    }

    public String getRedactedText()
    {
        return redactedText.getString();
    }

    public void setRedactedText(String redactedText)
    {
        this.redactedText.set(redactedText);
    }

    public String getDerivationDataScope()
    {
        return derivationDataScope.getString();
    }

    public void setDerivationDataScope(String derivationDataScope)
    {
        this.derivationDataScope.set(derivationDataScope);
    }

    public String getValueExpression()
    {
        return valueExpression.getString();
    }

    public void setValueExpression(String valueExpression)
    {
        this.valueExpression.set(valueExpression);
    }

    public boolean getIsPrimaryKey()
    {
        return isPrimaryKey.booleanValue();
    }

    /** This method is for informational purpose only so that the client can identify column as a PK column.
     * Setting PK on a column via this method will not get preserved in the domain's table.
     */
    public void setIsPrimaryKey(boolean isPrimaryKey)
    {
        this.isPrimaryKey.setBool(isPrimaryKey);
    }

    public String getLockType()
    {
        return lockType.getString();
    }

    /** This method is for informational purpose only so that the client can identify column's locked type.
     * Setting lock type on a column via this method will not get preserved in the domain's table.
     */
    public void setLockType(String lockType)
    {
        this.lockType.set(lockType);
    }

    public String debugString()
    {
        return getName() + " " + getLabel() + " " + getRangeURI() + " " + isRequired() + " " + getDescription();
    }

    private boolean equals(String a, String b)
    {
        if (null == a || null == b)
            return a==b;
        return a.equals(b);
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null) return false;
        if (!(o instanceof GWTPropertyDescriptor))
            return false;
        
        GWTPropertyDescriptor that = (GWTPropertyDescriptor) o;

        if (getPropertyId() != that.getPropertyId()) return false;
        if (isRequired() != that.isRequired()) return false;
        if (isHidden() != that.isHidden()) return false;
        if (getMvEnabled() != that.getMvEnabled()) return false;
        if (getContainer() != null ? !getContainer().equals(that.getContainer()) : that.getContainer() != null) return false;
        if (getConceptURI() != null ? !getConceptURI().equals(that.getConceptURI()) : that.getConceptURI() != null) return false;
        if (getDescription() != null ? !getDescription().equals(that.getDescription()) : that.getDescription() != null) return false;
        if (getFormat() != null ? !getFormat().equals(that.getFormat()) : that.getFormat() != null) return false;
        if (getLabel() != null ? !getLabel().equals(that.getLabel()) : that.getLabel() != null) return false;
        if (getLookupContainer() != null ? !getLookupContainer().equals(that.getLookupContainer()) : that.getLookupContainer() != null)
            return false;
        if (getLookupQuery() != null ? !getLookupQuery().equals(that.getLookupQuery()) : that.getLookupQuery() != null) return false;
        if (getLookupSchema() != null ? !getLookupSchema().equals(that.getLookupSchema()) : that.getLookupSchema() != null) return false;
        if (getName() != null ? !getName().equals(that.getName()) : that.getName() != null) return false;
        if (getPropertyURI() != null ? !getPropertyURI().equals(that.getPropertyURI()) : that.getPropertyURI() != null) return false;
        if (getRangeURI() != null ? !getRangeURI().equals(that.getRangeURI()) : that.getRangeURI() != null) return false;
        if (getDefaultValueType() != null ? !getDefaultValueType().equals(that.getDefaultValueType()) : that.getDefaultValueType() != null) return false;
        if (getDefaultValue() != null ? !getDefaultValue().equals(that.getDefaultValue()) : that.getDefaultValue() != null) return false;
        if (getDefaultDisplayValue() != null ? !getDefaultDisplayValue().equals(that.getDefaultDisplayValue()) : that.getDefaultDisplayValue() != null) return false;
        if (getImportAliases() != null ? !getImportAliases().equals(that.getImportAliases()) : that.getImportAliases() != null) return false;
        if (getURL() != null ? !getURL().equals(that.getURL()) : that.getURL() != null) return false;
        if (isShownInDetailsView() != that.isShownInDetailsView()) return false;
        if (isShownInInsertView() != that.isShownInInsertView()) return false;
        if (isShownInUpdateView() != that.isShownInUpdateView()) return false;
        if (isMeasure() != that.isMeasure()) return false;
        if (isDimension() != that.isDimension()) return false;
        if (isRecommendedVariable() != that.isRecommendedVariable()) return false;
        if (!equals(getDefaultScale(), that.getDefaultScale())) return false;
        if (!equals(getFacetingBehaviorType(), that.getFacetingBehaviorType())) return false;
        if (getPHI() != null ? !getPHI().equals(that.getPHI()) : that.getPHI() != null) return false;
        if (isExcludeFromShifting() != that.isExcludeFromShifting()) return false;

        if (!getPropertyValidators().equals(that.getPropertyValidators())) return false;
        if (!getConditionalFormats().equals(that.getConditionalFormats()))
        {
            return false;
        }
        if (!getScale().equals(that.getScale())) return false;

        if (!equals(getPrincipalConceptCode(),that.getPrincipalConceptCode())) return false;
        if (!equals(getSourceOntology(),that.getSourceOntology())) return false;
        if (!equals(getConceptSubtree(), that.getConceptSubtree())) return false;
        if (!equals(getConceptImportColumn(),that.getConceptImportColumn())) return false;
        if (!equals(getConceptLabelColumn(),that.getConceptLabelColumn())) return false;
        if (!equals(getDerivationDataScope(),that.getDerivationDataScope())) return false;
        if (getRedactedText() != null ? !getRedactedText().equals(that.getRedactedText()) : that.getRedactedText() != null) return false;
        if (isScannable() != that.isScannable()) return false;
        if (!equals(getValueExpression(),that.getValueExpression())) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = (propertyId.getInteger() != null ? propertyId.getInteger().hashCode() : 0);
        result = 31 * result + (propertyURI.getString() != null ? propertyURI.getString().hashCode() : 0);
        result = 31 * result + (name.getString() != null ? name.getString().hashCode() : 0);
        result = 31 * result + (description.getString() != null ? description.getString().hashCode() : 0);
        result = 31 * result + (container.getString() != null ? container.getString().hashCode() : 0);
        result = 31 * result + (rangeURI.getString() != null ? rangeURI.getString().hashCode() : 0);
        result = 31 * result + (conceptURI.getString() != null ? conceptURI.getString().hashCode() : 0);
        result = 31 * result + (label.getString() != null ? label.getString().hashCode() : 0);
        result = 31 * result + (format.getString() != null ? format.getString().hashCode() : 0);
        result = 31 * result + (required.getBoolean() != null ? required.getBoolean().hashCode() : 0);
        result = 31 * result + (hidden.getBoolean() != null ? hidden.getBoolean().hashCode() : 0);
        result = 31 * result + (mvEnabled.getBoolean() != null ? mvEnabled.getBoolean().hashCode() : 0);
        result = 31 * result + (lookupContainer.getString() != null ? lookupContainer.getString().hashCode() : 0);
        result = 31 * result + (lookupSchema.getString() != null ? lookupSchema.getString().hashCode() : 0);
        result = 31 * result + (lookupQuery.getString() != null ? lookupQuery.getString().hashCode() : 0);
        result = 31 * result + (defaultValueType != null ? defaultValueType.hashCode() : 0);
        result = 31 * result + (defaultValue != null ? defaultValue.hashCode() : 0);
        result = 31 * result + (defaultDisplayValue != null ? defaultDisplayValue.hashCode() : 0);
        result = 31 * result + (url != null ? url.hashCode() : 0);
        result = 31 * result + (importAliases != null ? importAliases.hashCode() : 0);
        result = 31 * result + (shownInDetailsView.getBoolean() != null ? shownInDetailsView.getBoolean().hashCode() : 0);
        result = 31 * result + (shownInInsertView.getBoolean() != null ? shownInInsertView.getBoolean().hashCode() : 0);
        result = 31 * result + (shownInUpdateView.getBoolean() != null ? shownInUpdateView.getBoolean().hashCode() : 0);
        result = 31 * result + (dimension.getBoolean() != null ? dimension.getBoolean().hashCode() : 0);
        result = 31 * result + (measure.getBoolean() != null ? measure.getBoolean().hashCode() : 0);
        result = 31 * result + (recommendedVariable.getBoolean() != null ? recommendedVariable.getBoolean().hashCode() : 0);
        result = 31 * result + (defaultScale.getString() != null ? defaultScale.getString().hashCode() : 0);
        result = 31 * result + (facetingBehaviorType.getString() != null ? facetingBehaviorType.getString().hashCode() : 0);
        result = 31 * result + (phi.getString() != null ? phi.hashCode() : 0);
        result = 31 * result + (isExcludeFromShifting.getBoolean() != null ? isExcludeFromShifting.getBoolean().hashCode() : 0);
        result = 31 * result + (scale.getInteger() != null ? scale.getInteger().hashCode() : 0);
        result = 31 * result + sourceOntology.hashCode();
        result = 31 * result + conceptSubtree.hashCode();
        result = 31 * result + conceptImportColumn.hashCode();
        result = 31 * result + conceptLabelColumn.hashCode();
        result = 31 * result + principalConceptCode.hashCode();
        result = 31 * result + redactedText.hashCode();
        result = 31 * result + derivationDataScope.hashCode();
        result = 31 * result + (scannable.getBoolean() != null ? scannable.getBoolean().hashCode() : 0);
        result = 31 * result + valueExpression.hashCode();

        for (GWTPropertyValidator gwtPropertyValidator : getPropertyValidators())
        {
            result = 31 * result + gwtPropertyValidator.hashCode();
        }
        return result;
    }

    public List<GWTPropertyValidator> getPropertyValidators()
    {
        return validators;
    }

    public void setPropertyValidators(List<GWTPropertyValidator> validators)
    {
        this.validators = validators;
    }

    public List<GWTConditionalFormat> getConditionalFormats()
    {
        return conditionalFormats;
    }

    public void setConditionalFormats(List<GWTConditionalFormat> conditionalFormats)
    {
        this.conditionalFormats = conditionalFormats;
    }

    public String getImportAliases()
    {
        return importAliases.toString();
    }

    public void setImportAliases(String importAliases)
    {
        this.importAliases.set(importAliases);
    }

    public String getURL()
    {
        return url.toString();
    }

    public void setURL(String url)
    {
        this.url.set(url);
    }

    public String getLookupDescription()
    {
        if (getLookupQuery() != null && getLookupQuery().length() > 0 &&
                getLookupSchema() != null && getLookupSchema().length() > 0)
        {
            return getLookupSchema() + "." + getLookupQuery();
        }
        return "(none)";
    }

    @Override
    public String toString()
    {
        return name.getString() + ": " + rangeURI.getString();
    }

    public boolean isFileType()
    {
        return "http://cpas.fhcrc.org/exp/xml#fileLink".equals(getRangeURI()) ||
               "http://www.labkey.org/exp/xml#attachment".equals(getRangeURI());
    }


    // for communicating with the type editor, not persisted
//    public void setEditable(boolean b)
//    {
//        isEditable = b;
//    }
//
//    public boolean isEditable()
//    {
//        return isEditable;
//    }

    public void setTypeEditable(boolean b)
    {
        isTypeEditable = b;
    }

    public boolean isTypeEditable()
    {
        return isTypeEditable;
    }

//    public void setNameEditable(boolean b)
//    {
//        isNameEditable = b;
//    }
//
//    public boolean isNameEditable()
//    {
//        return isNameEditable;
//    }
}
