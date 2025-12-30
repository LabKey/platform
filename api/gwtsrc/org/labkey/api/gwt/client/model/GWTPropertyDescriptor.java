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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
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
 * See {@link org.labkey.api.exp.PropertyDescriptor}
 */
@EqualsAndHashCode
public class GWTPropertyDescriptor implements IsSerializable
{
    private final IntegerProperty propertyId = new IntegerProperty(0);
    private final StringProperty propertyURI = new StringProperty();
    private final StringProperty container = new StringProperty();
    private final StringProperty name = new StringProperty();
    private final StringProperty description = new StringProperty();
    private final StringProperty rangeURI = new StringProperty("http://www.w3.org/2001/XMLSchema#string");
    private final StringProperty conceptURI = new StringProperty();
    private final StringProperty label = new StringProperty();
    private final StringProperty format = new StringProperty();
    private final BooleanProperty required = new BooleanProperty(false);
    private final BooleanProperty hidden = new BooleanProperty(false);
    private final StringProperty lookupContainer = new StringProperty();
    private final StringProperty lookupSchema = new StringProperty();
    private final StringProperty lookupQuery = new StringProperty();
    private final BooleanProperty lookupIsValid = new BooleanProperty(true);
    private String defaultValueType = null;
    private final StringProperty defaultValue = new StringProperty();
    private final StringProperty defaultDisplayValue = new StringProperty("[none]");
    private final BooleanProperty mvEnabled = new BooleanProperty(false);
    private final StringProperty importAliases = new StringProperty();
    private final StringProperty url = new StringProperty();
    private final StringProperty urlTargetWindow = new StringProperty();
    private final BooleanProperty shownInInsertView = new BooleanProperty(true);
    private final BooleanProperty shownInUpdateView = new BooleanProperty(true);
    private final BooleanProperty shownInDetailsView = new BooleanProperty(true);
    private final BooleanProperty measure = new BooleanProperty();
    private final BooleanProperty dimension = new BooleanProperty();
    private final BooleanProperty recommendedVariable = new BooleanProperty(false);
    private final StringProperty defaultScale = new StringProperty(DefaultScaleType.LINEAR.name());
    private final StringProperty facetingBehaviorType = new StringProperty();
    private final StringProperty phi = new StringProperty("NotPHI"); // Must match PHI.NotPHI and tableInfo.xsd enum PHIType.NotPHI
    private final BooleanProperty isExcludeFromShifting = new BooleanProperty();
    private final BooleanProperty isPreventReordering = new BooleanProperty();
    private final BooleanProperty isDisableEditing = new BooleanProperty();
    private final IntegerProperty scale = new IntegerProperty(4000);
    private final StringProperty principalConceptCode = new StringProperty();
    private final StringProperty sourceOntology = new StringProperty();
    private final StringProperty conceptSubtree = new StringProperty();
    private final StringProperty conceptImportColumn = new StringProperty();
    private final StringProperty conceptLabelColumn = new StringProperty();
    private final StringProperty redactedText = new StringProperty();
    private final StringProperty derivationDataScope = new StringProperty();
    private final BooleanProperty isPrimaryKey = new BooleanProperty(false);
    private final StringProperty lockType = new StringProperty(LockedPropertyType.NotLocked.name());
    private final BooleanProperty scannable = new BooleanProperty(false);
    private final StringProperty valueExpression = new StringProperty();

    @Getter @Setter private List<GWTConditionalFormat> conditionalFormats = new ArrayList<>();
    @Getter @Setter private List<GWTFilterCriteria> filterCriteria = new ArrayList<>();
    @Getter @Setter private List<GWTPropertyValidator> propertyValidators = new ArrayList<>();

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
        this(s, false);
    }

    public GWTPropertyDescriptor(GWTPropertyDescriptor s, boolean isNew)
    {
        if (!isNew)
        {
            setPropertyId(s.getPropertyId());
            setPropertyURI(s.getPropertyURI());
        }

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
        setLookupIsValid(s.getLookupIsValid());
        setLookupSchema(s.getLookupSchema());
        setLookupQuery(s.getLookupQuery());
        setDefaultValueType(s.getDefaultValueType());
        setDefaultValue(s.getDefaultValue());
        setDefaultDisplayValue(s.getDefaultDisplayValue());
        setImportAliases(s.getImportAliases());
        setURL(s.getURL());
        setURLTargetWindow(s.getURLTargetWindow());
        setFacetingBehaviorType(s.getFacetingBehaviorType());
        setPHI(s.getPHI());
        setExcludeFromShifting(s.isExcludeFromShifting());
        setPreventReordering(s.getPreventReordering());
        setDisableEditing(s.getDisableEditing());
        setScale(s.getScale());
        setRedactedText(s.getRedactedText());
        setIsPrimaryKey(s.getIsPrimaryKey());
        setLockType(s.getLockType());
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
            GWTPropertyValidator gpv = new GWTPropertyValidator(v);
            if (isNew)
            {
                gpv.setRowId(0);
                gpv.setNew(true);
            }
            propertyValidators.add(gpv);
        }

        for (GWTConditionalFormat f : s.getConditionalFormats())
        {
            conditionalFormats.add(new GWTConditionalFormat(f));
        }

        for (GWTFilterCriteria fc : s.getFilterCriteria())
        {
            filterCriteria.add(new GWTFilterCriteria(fc));
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

    public String getURLTargetWindow()
    {
        return urlTargetWindow.toString();
    }

    public void setURLTargetWindow(String urlTargetWindow)
    {
        this.urlTargetWindow.set(urlTargetWindow);
    }

    public String getLookupDescription()
    {
        if (StringUtils.isEmpty(getLookupSchema()) || StringUtils.isEmpty(getLookupQuery()))
            return "(none)";

        return getLookupSchema() + "." + getLookupQuery();
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
}
