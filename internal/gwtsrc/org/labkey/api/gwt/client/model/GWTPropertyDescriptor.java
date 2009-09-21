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

package org.labkey.api.gwt.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import org.labkey.api.gwt.client.util.BooleanProperty;
import org.labkey.api.gwt.client.util.IPropertyWrapper;
import org.labkey.api.gwt.client.util.IntegerProperty;
import org.labkey.api.gwt.client.util.StringProperty;
import org.labkey.api.gwt.client.DefaultValueType;

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
    private DefaultValueType defaultValueType = null;
    private StringProperty defaultValue = new StringProperty();
    private StringProperty defaultDisplayValue = new StringProperty("[none]");
    private BooleanProperty mvEnabled = new BooleanProperty(false);
    private StringProperty importAliases = new StringProperty();
    private StringProperty url = new StringProperty();

    private List<GWTPropertyValidator> validators = new ArrayList<GWTPropertyValidator>();

    public GWTPropertyDescriptor()
    {
    }

    public GWTPropertyDescriptor(GWTPropertyDescriptor s)
    {
        setPropertyId(s.getPropertyId());
        setPropertyURI(s.getPropertyURI());
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
        setMvEnabled(s.getMvEnabled());
        setLookupContainer(s.getLookupContainer());
        setLookupSchema(s.getLookupSchema());
        setLookupQuery(s.getLookupQuery());
        setDefaultValueType(s.getDefaultValueType());
        setDefaultValue(s.getDefaultValue());
        setDefaultDisplayValue(s.getDefaultDisplayValue());
        setImportAliases(s.getImportAliases());
        setURL(s.getURL());

        for (GWTPropertyValidator v : s.getPropertyValidators())
        {
            validators.add(new GWTPropertyValidator(v));
        }
    }

    public GWTPropertyDescriptor copy()
    {
        return new GWTPropertyDescriptor(this);
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
        return defaultValueType;
    }

    public void setDefaultValueType(DefaultValueType defaultValueType)
    {
        this.defaultValueType = defaultValueType;
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

        if (getPropertyValidators().size() != that.getPropertyValidators().size()) return false;
        GWTPropertyValidator[] cur = getPropertyValidators().toArray(new GWTPropertyValidator[getPropertyValidators().size()]);
        GWTPropertyValidator[] prev = that.getPropertyValidators().toArray(new GWTPropertyValidator[that.getPropertyValidators().size()]);

        for (int i=0; i < cur.length; i++)
        {
            if (!cur[i].equals(prev[i])) return false;
        }
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
        if ("defaultValueType".equals(prop)) throw new IllegalStateException("defaultValueType cannot be bound.");
        if ("defaultValue".equals(prop)) throw new IllegalStateException("defaultValue cannot be bound.");
        if ("defaultDisplayValue".equals(prop)) throw new IllegalStateException("defaultDisplayValue cannot be bound.");
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
}