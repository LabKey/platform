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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.PHIType;
import org.labkey.api.gwt.client.ui.PropertyType;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.IPropertyWrapper;
import org.labkey.api.gwt.client.util.IntegerProperty;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.util.StringUtils;

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
    private StringProperty ontologyURI = new StringProperty();
    private StringProperty name = new StringProperty();
    private StringProperty description = new StringProperty();
    private StringProperty rangeURI = new StringProperty("http://www.w3.org/2001/XMLSchema#string");
    private StringProperty conceptURI = new StringProperty();
    private StringProperty label = new StringProperty();
    private StringProperty searchTerms = new StringProperty();
    private StringProperty semanticType = new StringProperty();
    private StringProperty format = new StringProperty();
    private BooleanProperty required = new BooleanProperty(false);
    private BooleanProperty hidden = new BooleanProperty(false);
    private StringProperty lookupContainer = new StringProperty();
    private StringProperty lookupSchema = new StringProperty();
    private StringProperty lookupQuery = new StringProperty();
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
    private StringProperty phi = new StringProperty(PHIType.NotPHI.name());
    private BooleanProperty isExcludeFromShifting = new BooleanProperty();
    private BooleanProperty isPreventReordering = new BooleanProperty();
    private BooleanProperty isDisableEditing = new BooleanProperty();
    private IntegerProperty scale = new IntegerProperty(4000);
    private StringProperty redactedText = new StringProperty();

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
        setOntologyURI(s.getOntologyURI());
        setName(s.getName());
        setDescription(s.getDescription());
        setRangeURI(s.getRangeURI());
        setConceptURI(s.getConceptURI());
        setLabel(s.getLabel());
        setSearchTerms(s.getSearchTerms());
        setSemanticType(s.getSemanticType());
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

    public String getOntologyURI()
    {
        return ontologyURI.getString();
    }

    public void setOntologyURI(String ontologyURI)
    {
        this.ontologyURI.set(ontologyURI);
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

    public String getSearchTerms()
    {
        return searchTerms.getString();
    }

    public void setSearchTerms(String searchTerms)
    {
        this.searchTerms.set(searchTerms);
    }

    public String getLabel()
    {
        return label.getString();
    }

    public void setLabel(String label)
    {
        this.label.set(label);
    }

    public String getSemanticType()
    {
        return semanticType.getString();
    }

    public void setSemanticType(String semanticType)
    {
        this.semanticType.set(semanticType);
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

    public String getRedactedText()
    {
        return redactedText.getString();
    }

    public void setRedactedText(String redactedText)
    {
        this.redactedText.set(redactedText);
    }

    public String debugString()
    {
        return getName() + " " + getLabel() + " " + getRangeURI() + " " + isRequired() + " " + getDescription();
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
        if (getOntologyURI() != null ? !getOntologyURI().equals(that.getOntologyURI()) : that.getOntologyURI() != null) return false;
        if (getPropertyURI() != null ? !getPropertyURI().equals(that.getPropertyURI()) : that.getPropertyURI() != null) return false;
        if (getRangeURI() != null ? !getRangeURI().equals(that.getRangeURI()) : that.getRangeURI() != null) return false;
        if (getSearchTerms() != null ? !getSearchTerms().equals(that.getSearchTerms()) : that.getSearchTerms() != null) return false;
        if (getSemanticType() != null ? !getSemanticType().equals(that.getSemanticType()) : that.getSemanticType() != null) return false;
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
        if (!StringUtils.equals(getDefaultScale(), that.getDefaultScale())) return false;
        if (!StringUtils.equals(getFacetingBehaviorType(), that.getFacetingBehaviorType())) return false;
        if (getPHI() != null ? !getPHI().equals(that.getPHI()) : that.getPHI() != null) return false;
        if (isExcludeFromShifting() != that.isExcludeFromShifting()) return false;

        if (!getPropertyValidators().equals(that.getPropertyValidators())) return false;
        if (!getConditionalFormats().equals(that.getConditionalFormats()))
        {
            return false;
        }
        if(!getScale().equals(that.getScale())) return false;
        if (getRedactedText() != null ? !getRedactedText().equals(that.getRedactedText()) : that.getRedactedText() != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = (propertyId.getInteger() != null ? propertyId.getInteger().hashCode() : 0);
        result = 31 * result + (propertyURI.getString() != null ? propertyURI.getString().hashCode() : 0);
        result = 31 * result + (ontologyURI.getString() != null ? ontologyURI.getString().hashCode() : 0);
        result = 31 * result + (name.getString() != null ? name.getString().hashCode() : 0);
        result = 31 * result + (description.getString() != null ? description.getString().hashCode() : 0);
        result = 31 * result + (container.getString() != null ? container.getString().hashCode() : 0);
        result = 31 * result + (rangeURI.getString() != null ? rangeURI.getString().hashCode() : 0);
        result = 31 * result + (conceptURI.getString() != null ? conceptURI.getString().hashCode() : 0);
        result = 31 * result + (label.getString() != null ? label.getString().hashCode() : 0);
        result = 31 * result + (searchTerms.getString() != null ? searchTerms.getString().hashCode() : 0);
        result = 31 * result + (semanticType.getString() != null ? semanticType.getString().hashCode() : 0);
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
        result = 31 * result + (redactedText.getString() != null ? redactedText.getString().hashCode() : 0);

        for (GWTPropertyValidator gwtPropertyValidator : getPropertyValidators())
        {
            result = 31 * result + gwtPropertyValidator.hashCode();
        }
        return result;
    }

    public IPropertyWrapper bindProperty(String prop)
    {
        if ("propertyId".equals(prop)) return propertyId;
        if ("propertyURI".equals(prop)) return propertyURI;
        if ("ontologyURI".equals(prop)) return ontologyURI;
        if ("name".equals(prop)) return name;
        if ("description".equals(prop)) return description;
        if ("container".equals(prop)) return container;
        if ("rangeURI".equals(prop)) return rangeURI;
        if ("conceptURI".equals(prop)) return conceptURI;
        if ("label".equals(prop)) return label;
        if ("searchTerms".equals(prop)) return searchTerms;
        if ("semanticType".equals(prop)) return semanticType;
        if ("format".equals(prop)) return format;
        if ("required".equals(prop)) return required;
        if ("hidden".equals(prop)) return hidden;
        if ("lookupContainer".equals(prop)) return lookupContainer;
        if ("lookupSchema".equals(prop)) return lookupSchema;
        if ("lookupQuery".equals(prop)) return lookupQuery;
        if ("mvEnabled".equals(prop)) return mvEnabled;
        if ("url".equals(prop)) return url;
        if ("dimension".equals(prop)) return dimension;
        if ("measure".equals(prop)) return measure;
        if ("recommendedVariable".equals(prop)) return recommendedVariable;
        if ("defaultScale".equals(prop)) return defaultScale;
        if ("importAliases".equals(prop)) return importAliases;
        if ("shownInInsertView".equals(prop)) return shownInInsertView;
        if ("shownInUpdateView".equals(prop)) return shownInUpdateView;
        if ("shownInDetailsView".equals(prop)) return shownInDetailsView;
        if ("defaultValueType".equals(prop)) throw new IllegalStateException("defaultValueType cannot be bound.");
        if ("defaultValue".equals(prop)) throw new IllegalStateException("defaultValue cannot be bound.");
        if ("defaultDisplayValue".equals(prop)) throw new IllegalStateException("defaultDisplayValue cannot be bound.");
        if ("facetingBehaviorType".equals(prop)) return facetingBehaviorType;
        if ("phi".equals(prop)) return phi;
        if ("excludeFromShifting".equals(prop)) return isExcludeFromShifting;
        if ("scale".equals(prop)) return scale;
        if ("redactedText".equals(prop)) return redactedText;

        return null;
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
