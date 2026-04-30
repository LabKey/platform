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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.LockedPropertyType;
import org.labkey.api.gwt.client.ui.PropertyType;

import java.util.ArrayList;
import java.util.List;

/**
 * See {@link org.labkey.api.exp.PropertyDescriptor}
 */
@EqualsAndHashCode
public class GWTPropertyDescriptor
{
    private int propertyId = 0;
    private String propertyURI;
    private String container;
    private String name;
    private String description;
    private String rangeURI = "http://www.w3.org/2001/XMLSchema#string";
    private String conceptURI;
    private String label;
    private String format;
    private boolean required = false;
    private boolean hidden = false;
    private String lookupContainer;
    private String lookupSchema;
    private String lookupQuery;
    private boolean lookupIsValid = true;
    private String defaultValueType = null;
    private String defaultValue;
    private String defaultDisplayValue = "[none]";
    private boolean mvEnabled = false;
    private String importAliases;
    private String url;
    private String urlTarget;
    private boolean shownInInsertView = true;
    private boolean shownInUpdateView = true;
    private boolean shownInDetailsView = true;
    private Boolean measure;
    private Boolean dimension;
    private boolean recommendedVariable = false;
    private String defaultScale = DefaultScaleType.LINEAR.name();
    private String facetingBehaviorType;
    private String phi = "NotPHI"; // Must match PHI.NotPHI and tableInfo.xsd enum PHIType.NotPHI
    private Boolean isExcludeFromShifting;
    private boolean isPreventReordering = false;
    private boolean isDisableEditing = false;
    private Integer scale = 4000;
    private String principalConceptCode;
    private String sourceOntology;
    private String conceptSubtree;
    private String conceptImportColumn;
    private String conceptLabelColumn;
    private String redactedText;
    private String derivationDataScope;
    private boolean isPrimaryKey = false;
    private String lockType = LockedPropertyType.NotLocked.name();
    private boolean scannable = false;
    private String valueExpression;

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
        setURLTarget(s.getURLTarget());
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
        return container;
    }

    public void setContainer(String container)
    {
        this.container = container;
    }

    public String getLookupContainer()
    {
        return lookupContainer;
    }

    public void setLookupContainer(String lookupContainer)
    {
        this.lookupContainer = lookupContainer;
    }

    public String getLookupSchema()
    {
        return lookupSchema;
    }

    public void setLookupSchema(String lookupSchema)
    {
        this.lookupSchema = lookupSchema;
    }

    public String getLookupQuery()
    {
        return lookupQuery;
    }

    public void setLookupQuery(String lookupQuery)
    {
        this.lookupQuery = lookupQuery;
    }

    public boolean getLookupIsValid()
    {
        return lookupIsValid;
    }

    public void setLookupIsValid(boolean lookupIsValid)
    {
        this.lookupIsValid = lookupIsValid;
    }

    public int getPropertyId()
    {
        return propertyId;
    }

    public void setPropertyId(int rowId)
    {
        this.propertyId = rowId;
    }

    public String getPropertyURI()
    {
        return propertyURI;
    }

    public void setPropertyURI(String propertyURI)
    {
        this.propertyURI = propertyURI;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getRangeURI()
    {
        return rangeURI;
    }

    public void setRangeURI(String dataTypeURI)
    {
        this.rangeURI = dataTypeURI;
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
        return conceptURI;
    }

    public void setConceptURI(String conceptURI)
    {
        this.conceptURI = conceptURI;
    }

    public String getLabel()
    {
        return label;
    }

    public void setLabel(String label)
    {
        this.label = label;
    }

    public String getFormat()
    {
        return format;
    }

    public void setFormat(String format)
    {
        this.format = format;
    }

    public boolean isRequired()
    {
        return required;
    }

    public void setRequired(boolean required)
    {
        this.required = required;
    }

    public boolean isHidden()
    {
        return hidden;
    }

    public void setHidden(boolean hidden)
    {
        this.hidden = hidden;
    }

    public boolean isShownInInsertView()
    {
        return shownInInsertView;
    }

    public void setShownInInsertView(boolean shown)
    {
        this.shownInInsertView = shown;
    }

    public boolean isShownInUpdateView()
    {
        return shownInUpdateView;
    }

    public void setShownInUpdateView(boolean shown)
    {
        this.shownInUpdateView = shown;
    }

    public boolean isShownInDetailsView()
    {
        return shownInDetailsView;
    }

    public void setShownInDetailsView(boolean shown)
    {
        this.shownInDetailsView = shown;
    }

    public boolean isSetMeasure()
    {
        return measure != null;
    }

    public boolean isMeasure()
    {
        return measure != null && measure;
    }

    public void setMeasure(boolean isMeasure)
    {
        this.measure = isMeasure;
    }

    public boolean isSetDimension()
    {
        return dimension != null;
    }

    public boolean isDimension()
    {
        return dimension != null && dimension;
    }

    public void setDimension(boolean isDimension)
    {
        this.dimension = isDimension;
    }

    public boolean isRecommendedVariable()
    {
        return recommendedVariable;
    }

    public void setRecommendedVariable(boolean isRecommendedVariable)
    {
        this.recommendedVariable = isRecommendedVariable;
    }

    public String getDefaultScale()
    {
        return defaultScale;
    }

    public void setDefaultScale(String defaultScale)
    {
        this.defaultScale = defaultScale;
    }

    public boolean getMvEnabled()
    {
        return mvEnabled;
    }

    public void setMvEnabled(boolean mvEnabled)
    {
        this.mvEnabled = mvEnabled;
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
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue)
    {
        this.defaultValue = defaultValue;
    }

    public String getDefaultDisplayValue()
    {
        return defaultDisplayValue;
    }

    public void setDefaultDisplayValue(String defaultDisplayValue)
    {
        this.defaultDisplayValue = defaultDisplayValue;
    }

    public String getFacetingBehaviorType()
    {
        return facetingBehaviorType;
    }

    public void setFacetingBehaviorType(String facetingBehavior)
    {
        this.facetingBehaviorType = facetingBehavior;
    }

    public String getPHI()
    {
        return phi;
    }

    public void setPHI(String phi)
    {
        this.phi = phi;
    }

    public boolean isSetExcludeFromShifting()
    {
        return isExcludeFromShifting != null;
    }

    public boolean isExcludeFromShifting()
    {
        return isExcludeFromShifting != null && isExcludeFromShifting;
    }

    public void setExcludeFromShifting(boolean isExcludeFromShifting)
    {
        this.isExcludeFromShifting = isExcludeFromShifting;
    }

    public boolean getPreventReordering()
    {
        return isPreventReordering;
    }

    public void setPreventReordering(boolean preventReordering)
    {
        this.isPreventReordering = preventReordering;
    }

    public boolean getDisableEditing()
    {
        return isDisableEditing;
    }

    public void setDisableEditing(boolean disableEditing)
    {
        this.isDisableEditing = disableEditing;
    }

    public Integer getScale()
    {
        return scale;
    }

    public void setScale(Integer value)
    {
        this.scale = value;
    }

    public boolean isScannable()
    {
        return scannable;
    }

    public void setScannable(boolean scannable)
    {
        this.scannable = scannable;
    }

    public String getPrincipalConceptCode() { return principalConceptCode; }

    public void setPrincipalConceptCode(String code) { this.principalConceptCode = code; }

    public String getSourceOntology()
    {
        return sourceOntology;
    }

    public void setSourceOntology(String sourceOntology)
    {
        this.sourceOntology = sourceOntology;
    }

    public String getConceptSubtree()
    {
        return conceptSubtree;
    }

    public void setConceptSubtree(String path)
    {
        this.conceptSubtree = path;
    }

    public String getConceptImportColumn()
    {
        return conceptImportColumn;
    }

    public void setConceptImportColumn(String conceptImportColumn)
    {
        this.conceptImportColumn = conceptImportColumn;
    }

    public String getConceptLabelColumn()
    {
        return conceptLabelColumn;
    }

    public void setConceptLabelColumn(String conceptLabelColumn)
    {
        this.conceptLabelColumn = conceptLabelColumn;
    }

    public String getRedactedText()
    {
        return redactedText;
    }

    public void setRedactedText(String redactedText)
    {
        this.redactedText = redactedText;
    }

    public String getDerivationDataScope()
    {
        return derivationDataScope;
    }

    public void setDerivationDataScope(String derivationDataScope)
    {
        this.derivationDataScope = derivationDataScope;
    }

    public String getValueExpression()
    {
        return valueExpression;
    }

    public void setValueExpression(String valueExpression)
    {
        this.valueExpression = valueExpression;
    }

    public boolean getIsPrimaryKey()
    {
        return isPrimaryKey;
    }

    /** This method is for informational purpose only so that the client can identify column as a PK column.
     * Setting PK on a column via this method will not get preserved in the domain's table.
     */
    public void setIsPrimaryKey(boolean isPrimaryKey)
    {
        this.isPrimaryKey = isPrimaryKey;
    }

    public String getLockType()
    {
        return lockType;
    }

    /** This method is for informational purpose only so that the client can identify column's locked type.
     * Setting lock type on a column via this method will not get preserved in the domain's table.
     */
    public void setLockType(String lockType)
    {
        this.lockType = lockType;
    }

    public String debugString()
    {
        return getName() + " " + getLabel() + " " + getRangeURI() + " " + isRequired() + " " + getDescription();
    }

    public String getImportAliases()
    {
        return importAliases;
    }

    public void setImportAliases(String importAliases)
    {
        this.importAliases = importAliases;
    }

    public String getURL()
    {
        return url;
    }

    public void setURL(String url)
    {
        this.url = url;
    }

    public String getURLTarget()
    {
        return urlTarget;
    }

    public void setURLTarget(String urlTarget)
    {
        this.urlTarget = urlTarget;
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
        return name + ": " + rangeURI;
    }

    public boolean isFileType()
    {
        return "http://cpas.fhcrc.org/exp/xml#fileLink".equals(getRangeURI()) ||
               "http://www.labkey.org/exp/xml#attachment".equals(getRangeURI());
    }
}
