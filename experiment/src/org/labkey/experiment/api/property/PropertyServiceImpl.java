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

package org.labkey.experiment.api.property;

import org.labkey.api.exp.property.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.Pair;
import org.labkey.api.gwt.client.DefaultValueType;
import org.fhcrc.cpas.exp.xml.*;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.ConversionException;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.sql.SQLException;

public class PropertyServiceImpl implements PropertyService.Interface
{
    List<DomainKind> _domainTypes = new ArrayList<DomainKind>();
    Map<String, ValidatorKind> _validatorTypes = new HashMap<String, ValidatorKind>();


    public IPropertyType getType(Container container, String typeURI)
    {
        Domain domain = getDomain(container, typeURI);
        if (domain != null)
        {
            return domain;
        }
        return new PrimitiveType(PropertyType.getFromURI(null, typeURI));
    }

    public Domain getDomain(Container container, String domainURI)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, container);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    public Domain getDomain(int domainId)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainId);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    public Domain createDomain(Container container, String typeURI, String name)
    {
        return new DomainImpl(container, typeURI, name);
    }

    public DomainKind getDomainKind(String typeURI)
    {
        for (DomainKind type : _domainTypes)
        {
            if (type.isDomainType(typeURI))
                return type;
        }
        return null;
    }

    public void registerDomainKind(DomainKind type)
    {
        _domainTypes.add(type);
    }

    public Domain[] getDomains(Container container)
    {
        try
        {
            DomainDescriptor[] dds = OntologyManager.getDomainDescriptors(container);
            Domain[] ret = new Domain[dds.length];
            for (int i = 0; i < dds.length; i ++)
            {
                ret[i] = new DomainImpl(dds[i]);
            }
            return ret;
        }
        catch(SQLException e)
        {
            return new Domain[0];
        }
    }

    public void registerValidatorKind(ValidatorKind validatorKind)
    {
        if (_validatorTypes.containsKey(validatorKind.getTypeURI()))
            throw new IllegalArgumentException("Validator type : " + validatorKind.getTypeURI() + " is already registered");

        _validatorTypes.put(validatorKind.getTypeURI(), validatorKind);
    }

    public ValidatorKind getValidatorKind(String typeURI)
    {
        return _validatorTypes.get(typeURI);
    }

    public IPropertyValidator createValidator(String typeURI)
    {
        ValidatorKind kind = getValidatorKind(typeURI);
        if (kind != null)
            return kind.createInstance();
        return null;
    }

    public IPropertyValidator[] getPropertyValidators(PropertyDescriptor desc)
    {
        List<IPropertyValidator> validators = new ArrayList<IPropertyValidator>();

        for (PropertyValidator v : DomainPropertyManager.get().getValidators(desc))
        {
            validators.add(new PropertyValidatorImpl(v));            
        }
        return validators.toArray(new IPropertyValidator[validators.size()]);
    }

    public void deleteValidatorsForPropertyDescriptor(int descriptorId) throws SQLException
    {
        DomainPropertyManager.get().removeValidatorsForPropertyDescriptor(descriptorId);
    }

    public void deleteValidatorsForContainer(Container c) throws SQLException
    {
        DomainPropertyManager.get().deleteAllValidators(c);
    }

    public Pair<Domain, Map<DomainProperty, Object>> createDomain(Container c, DomainDescriptorType xDomain)
    {
        try
        {
            return createDomain(c, null, xDomain);
        }
        catch (XarFormatException e)
        {
            // shouldn't happen: XarFormatExceptions only thrown when resolving lsids with non-null XarContext
            throw new UnexpectedException(e);
        }
    }

    public Pair<Domain, Map<DomainProperty, Object>> createDomain(Container c, XarContext context, DomainDescriptorType xDomain) throws XarFormatException
    {
        String lsid = xDomain.getDomainURI();
        if (context != null)
            lsid = LsidUtils.resolveLsidFromTemplate(lsid, context, "Domain");
        Domain domain = createDomain(c, lsid, xDomain.getName());
        domain.setDescription(xDomain.getDescription());
        Map<DomainProperty, Object> defaultValues = new HashMap<DomainProperty, Object>();

        if (xDomain.getPropertyDescriptorArray() != null)
        {
            for (PropertyDescriptorType xProp : xDomain.getPropertyDescriptorArray())
            {
                loadPropertyDescriptor(domain, context, xProp, defaultValues);
            }
        }

        return new Pair<Domain, Map<DomainProperty, Object>>(domain, defaultValues);
    }

    private static DomainProperty loadPropertyDescriptor(Domain domain, XarContext context, PropertyDescriptorType xProp, Map<DomainProperty, Object> defaultValues)
        throws XarFormatException
    {
        DomainProperty prop = domain.addProperty();
        prop.setDescription(xProp.getDescription());
        prop.setFormat(xProp.getFormat());
        prop.setLabel(xProp.getLabel());
        prop.setName(xProp.getName());
        prop.setRangeURI(xProp.getRangeURI());
        String propertyURI = xProp.getPropertyURI();
        if (context != null  && propertyURI != null && propertyURI.indexOf("${") != -1)
        {
            propertyURI = LsidUtils.resolveLsidFromTemplate(propertyURI, context);
        }
        prop.setPropertyURI(propertyURI);
        if (xProp.isSetRequired())
        {
            prop.setRequired(xProp.getRequired());
        }
        prop.getPropertyDescriptor().setConceptURI(xProp.getConceptURI());
        if (xProp.isSetOntologyURI())
        {
            String uri = xProp.getOntologyURI().trim();
            if (context != null && uri.indexOf("${") != -1)
            {
                uri = LsidUtils.resolveLsidFromTemplate(xProp.getOntologyURI(), context);
            }
            prop.getPropertyDescriptor().setOntologyURI(uri);
        }
        prop.getPropertyDescriptor().setSearchTerms(xProp.getSearchTerms());
        prop.getPropertyDescriptor().setSemanticType(xProp.getSemanticType());
        
        if (xProp.isSetQcEnabled())
            prop.getPropertyDescriptor().setQcEnabled(xProp.getQcEnabled());

        if (xProp.isSetHidden())
            prop.getPropertyDescriptor().setHidden(xProp.getHidden());

        if (xProp.isSetDefaultType())
        {
            DefaultType.Enum xDefaultType = xProp.getDefaultType();
            DefaultValueType defaultType;
            if (DefaultType.EDITABLE_DEFAULT.equals(xDefaultType))
            {
                defaultType = DefaultValueType.FIXED_EDITABLE;
            }
            else if (DefaultType.FIXED_VALUE.equals(xDefaultType))
            {
                defaultType = DefaultValueType.FIXED_NON_EDITABLE;
            }
            else if (DefaultType.LAST_ENTERED.equals(xDefaultType))
            {
                defaultType = DefaultValueType.LAST_ENTERED;
            }
            else
            {
                throw new XarFormatException("Unsupported default type: " + xDefaultType);
            }
            prop.getPropertyDescriptor().setDefaultValueTypeEnum(defaultType);
        }

        if (xProp.isSetDefaultValue())
        {
            String defaultValue = xProp.getDefaultValue();
            PropertyType type = prop.getPropertyDescriptor().getPropertyType();
            if (defaultValue != null && defaultValue.length() > 0)
            {
                try
                {
                    Object converted = ConvertUtils.convert(defaultValue, type.getJavaType());
                    defaultValues.put(prop, converted);
                }
                catch (ConversionException e)
                {
                    throw new XarFormatException("Could not convert default value '" + defaultValue + "' for property "
                            + prop.getName() + " to " + ColumnInfo.getFriendlyTypeName(type.getJavaType()) + ".");
                }
            }
        }

        if (xProp.getPropertyValidatorArray() != null)
        {
            for (PropertyValidatorType xValidator : xProp.getPropertyValidatorArray())
            {
                PropertyValidatorImpl validator = new PropertyValidatorImpl(new PropertyValidator());
                validator.setContainer(prop.getContainer().getId());
                validator.setName(xValidator.getName());
                validator.setTypeURI(xValidator.getTypeURI());
                if (xValidator.isSetDescription())
                {
                    validator.setDescription(xValidator.getDescription());
                }
                if (xValidator.isSetErrorMessage())
                {
                    validator.setErrorMessage(xValidator.getErrorMessage());
                }
                if (xValidator.isSetExpression())
                {
                    validator.setExpressionValue(xValidator.getExpression());
                }
                if (xValidator.getPropertyArray() != null)
                {
                    for (PropertyValidatorPropertyType xValidatorProperty : xValidator.getPropertyArray())
                    {
                        validator.setProperty(xValidatorProperty.getName(), xValidatorProperty.getValue());
                    }
                }
                prop.addValidator(validator);
            }
        }

        return prop;
    }

}
