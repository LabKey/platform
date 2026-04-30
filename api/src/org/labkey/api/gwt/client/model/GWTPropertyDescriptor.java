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

import java.util.ArrayList;
import java.util.List;

/**
 * See {@link org.labkey.api.exp.PropertyDescriptor}
 */
@EqualsAndHashCode
public class GWTPropertyDescriptor
{
    @Setter
    @Getter
    private int propertyId = 0;
    @Setter
    @Getter
    private String propertyURI;
    @Setter
    @Getter
    private String container;
    @Setter
    @Getter
    private String name;
    @Setter
    @Getter
    private String description;
    @Setter
    @Getter
    private String rangeURI = "http://www.w3.org/2001/XMLSchema#string";
    @Setter
    @Getter
    private String conceptURI;
    @Setter
    @Getter
    private String label;
    @Setter
    @Getter
    private String format;
    @Setter
    @Getter
    private boolean required = false;
    @Setter
    @Getter
    private boolean hidden = false;
    @Getter
    @Setter
    private String lookupContainer;
    @Setter
    @Getter
    private String lookupSchema;
    @Setter
    @Getter
    private String lookupQuery;
    @Setter
    private boolean lookupIsValid = true;
    private String defaultValueType = null;
    @Setter
    @Getter
    private String defaultValue;
    @Setter
    @Getter
    private String defaultDisplayValue = "[none]";
    @Setter
    private boolean mvEnabled = false;
    @Setter
    @Getter
    private String importAliases;
    private String url;
    private String urlTarget;
    @Setter
    @Getter
    private boolean shownInInsertView = true;
    @Setter
    @Getter
    private boolean shownInUpdateView = true;
    @Setter
    @Getter
    private boolean shownInDetailsView = true;
    private Boolean measure;
    private Boolean dimension;
    @Setter
    @Getter
    private boolean recommendedVariable = false;
    @Setter
    @Getter
    private String defaultScale = DefaultScaleType.LINEAR.name();
    @Setter
    @Getter
    private String facetingBehaviorType;
    private String phi = "NotPHI"; // Must match PHI.NotPHI and tableInfo.xsd enum PHIType.NotPHI
    private Boolean isExcludeFromShifting;
    @Setter
    private boolean isPreventReordering = false;
    @Setter
    private boolean isDisableEditing = false;
    @Setter
    @Getter
    private Integer scale = 4000;
    @Setter
    @Getter
    private String principalConceptCode;
    @Setter
    @Getter
    private String sourceOntology;
    @Setter
    @Getter
    private String conceptSubtree;
    @Setter
    @Getter
    private String conceptImportColumn;
    @Setter
    @Getter
    private String conceptLabelColumn;
    @Setter
    @Getter
    private String redactedText;
    @Setter
    @Getter
    private String derivationDataScope;
    private boolean isPrimaryKey = false;
    /**
     * -- SETTER --
     * This method is for informational purpose only so that the client can identify column's locked type.
     *  Setting lock type on a column via this method will not get preserved in the domain's table.
     */
    @Setter
    @Getter
    private String lockType = LockedPropertyType.NotLocked.name();
    @Setter
    @Getter
    private boolean scannable = false;
    @Setter
    @Getter
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

    public boolean getLookupIsValid()
    {
        return lookupIsValid;
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

    public boolean getMvEnabled()
    {
        return mvEnabled;
    }

    public DefaultValueType getDefaultValueType()
    {
        return null==defaultValueType ? null : DefaultValueType.valueOf(defaultValueType);
    }

    public void setDefaultValueType(DefaultValueType defaultValueType)
    {
        this.defaultValueType = null==defaultValueType ? null : defaultValueType.name();
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

    public boolean getDisableEditing()
    {
        return isDisableEditing;
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
