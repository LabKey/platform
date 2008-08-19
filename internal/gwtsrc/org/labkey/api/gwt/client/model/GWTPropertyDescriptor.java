/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
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
    private StringProperty rangeURI = new StringProperty();
    private StringProperty conceptURI = new StringProperty();
    private StringProperty label = new StringProperty();
    private StringProperty searchTerms = new StringProperty();
    private StringProperty semanticType = new StringProperty();
    private StringProperty format = new StringProperty();
    private BooleanProperty required = new BooleanProperty(false);
    private StringProperty lookupContainer = new StringProperty();
    private StringProperty lookupSchema = new StringProperty();
    private StringProperty lookupQuery = new StringProperty();

    /**
     * @gwt.typeArgs <org.labkey.api.gwt.client.model.GWTPropertyValidator>
     */
    private List validators = new ArrayList();

    // not really part of the property descriptor, but this was easier than
    // having a side list of editable/non-editable properties
    private boolean isEditable = true;

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
        setLookupContainer(s.getLookupContainer());
        setLookupSchema(s.getLookupSchema());
        setLookupQuery(s.getLookupQuery());

        for (Iterator it = s.getPropertyValidators().iterator(); it.hasNext();)
        {
            GWTPropertyValidator v = new GWTPropertyValidator((GWTPropertyValidator)it.next());
            validators.add(v);
        }
        this.isEditable = s.isEditable;
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

        if (getPropertyValidators().size() != that.getPropertyValidators().size()) return false;
        GWTPropertyValidator[] cur = (GWTPropertyValidator[])getPropertyValidators().toArray(new GWTPropertyValidator[0]);
        GWTPropertyValidator[] prev = (GWTPropertyValidator[])that.getPropertyValidators().toArray(new GWTPropertyValidator[0]);

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
        result = 31 * result + (lookupContainer.getString() != null ? lookupContainer.getString().hashCode() : 0);
        result = 31 * result + (lookupSchema.getString() != null ? lookupSchema.getString().hashCode() : 0);
        result = 31 * result + (lookupQuery.getString() != null ? lookupQuery.getString().hashCode() : 0);

        for (Iterator it = getPropertyValidators().iterator(); it.hasNext();)
        {
            result = 31 * result + it.next().hashCode();
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
        if ("lookupContainer".equals(prop)) return lookupContainer;
        if ("lookupSchema".equals(prop)) return lookupSchema;
        if ("lookupQuery".equals(prop)) return lookupQuery;
        return null;
    }

    public boolean isEditable()
    {
        return isEditable;
    }

    public void setEditable(boolean editable)
    {
        isEditable = editable;
    }

    public List getPropertyValidators()
    {
        return validators;
    }

    public void setPropertyValidators(List validators)
    {
        this.validators = validators;
    }
}