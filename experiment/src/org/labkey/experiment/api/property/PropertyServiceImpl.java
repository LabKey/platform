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

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.fhcrc.cpas.exp.xml.DefaultType;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyValidatorPropertyType;
import org.fhcrc.cpas.exp.xml.PropertyValidatorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.IPropertyType;
import org.labkey.api.exp.property.IPropertyValidator;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.ValidatorKind;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.NotFoundException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PropertyServiceImpl implements PropertyService
{
    private final List<DomainKind> _domainTypes = new CopyOnWriteArrayList<>();
    private final Map<String, ValidatorKind> _validatorTypes = new ConcurrentHashMap<>();


    public IPropertyType getType(Container container, String typeURI)
    {
        Domain domain = getDomain(container, typeURI);
        if (domain != null)
        {
            return domain;
        }
        return new PrimitiveType(PropertyType.getFromURI(null, typeURI));
    }

    @Nullable
    public Domain getDomain(Container container, String domainURI)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, container);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    @Nullable
    public Domain getDomain(int domainId)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainId);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    @NotNull
    public Domain createDomain(Container container, String typeURI, String name)
    {
        return new DomainImpl(container, typeURI, name);
    }

    @NotNull
    public Domain createDomain(Container container, String typeURI, String name, @Nullable TemplateInfo templateInfo)
    {
        return new DomainImpl(container, typeURI, name, templateInfo);
    }

    @Nullable
    public String getDomainURI(String schemaName, String queryName, Container container, User user)
    {
        if (schemaName == null || queryName == null)
            throw new IllegalArgumentException("schemaName and queryName required");

        UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
        if (schema == null)
            throw new NotFoundException("Schema '" + schemaName + "' does not exist.");

        return schema.getDomainURI(queryName);
    }

    public DomainKind getDomainKindByName(String name)
    {
        for (DomainKind type : _domainTypes)
        {
            if (type.getKindName().equalsIgnoreCase(name))
                return type;
        }
        return null;
    }

    public DomainKind getDomainKind(String typeURI)
    {
        return Handler.Priority.findBestHandler(_domainTypes, typeURI);
    }

    public void registerDomainKind(DomainKind type)
    {
        _domainTypes.add(type);
    }


    public List<DomainKind> getDomainKinds()
    {
        ArrayList<DomainKind> l = new ArrayList<>(_domainTypes);
        return Collections.unmodifiableList(l);
    }

    public List<? extends Domain> getDomains(Container container)
    {
        List<Domain> result = new ArrayList<>();
        for (DomainDescriptor dd : OntologyManager.getDomainDescriptors(container))
        {
            result.add(new DomainImpl(dd));
        }

        return Collections.unmodifiableList(result);
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

    @Override
    public @NotNull List<IPropertyValidator> getPropertyValidators(PropertyDescriptor desc)
    {
        List<IPropertyValidator> validators = new ArrayList<>();

        for (PropertyValidator v : DomainPropertyManager.get().getValidators(desc))
        {
            validators.add(new PropertyValidatorImpl(v));            
        }
        return validators;
    }

    @Override
    public void deleteValidatorsAndFormats(Container c, int descriptorId)
    {
        DomainPropertyManager.get().removeValidatorsForPropertyDescriptor(c, descriptorId);
        DomainPropertyManager.get().deleteConditionalFormats(descriptorId);
    }

    public void deleteValidatorsAndFormats(Container c)
    {
        DomainPropertyManager.get().deleteAllValidatorsAndFormats(c);
    }

    @Override
    public @NotNull List<ConditionalFormat> getConditionalFormats(PropertyDescriptor desc)
    {
        return DomainPropertyManager.get().getConditionalFormats(desc);
    }

    @Override
    public void saveConditionalFormats(User user, PropertyDescriptor pd, List<ConditionalFormat> formats)
    {
        DomainPropertyManager.get().saveConditionalFormats(user, pd, formats);
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
        Map<DomainProperty, Object> defaultValues = new HashMap<>();

        if (xDomain.getPropertyDescriptorArray() != null)
        {
            for (PropertyDescriptorType xProp : xDomain.getPropertyDescriptorArray())
            {
                loadPropertyDescriptor(domain, context, xProp, defaultValues);
            }
        }

        return new Pair<>(domain, defaultValues);
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
        // Deal with legacy property URIs that don't have % in the name part properly encoded
        propertyURI = Lsid.fixupPropertyURI(propertyURI);
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
        prop.setURL(xProp.getURL());
        Set<String> importAliases = new LinkedHashSet<>();
        if (xProp.isSetImportAliases())
        {
            importAliases.addAll(Arrays.asList(xProp.getImportAliases().getImportAliasArray()));
        }
        prop.setImportAliasSet(importAliases);
        if (xProp.isSetFK())
        {
            PropertyDescriptorType.FK xFK = xProp.getFK();
            Container c = null;
            if (xFK.isSetFolderPath())
            {
                c = ContainerManager.getForPath(xFK.getFolderPath());
            }
            Lookup lookup = new Lookup(c, xFK.getSchema(), xFK.getQuery());
            prop.setLookup(lookup);
        }
        
        if (xProp.isSetMvEnabled())
            prop.getPropertyDescriptor().setMvEnabled(xProp.getMvEnabled());

        if (xProp.isSetHidden())
            prop.getPropertyDescriptor().setHidden(xProp.getHidden());
        if (xProp.isSetShownInDetailsView())
            prop.getPropertyDescriptor().setShownInDetailsView(xProp.getShownInDetailsView());
        if (xProp.isSetShownInInsertView())
            prop.getPropertyDescriptor().setShownInInsertView(xProp.getShownInInsertView());
        if (xProp.isSetShownInUpdateView())
            prop.getPropertyDescriptor().setShownInUpdateView(xProp.getShownInUpdateView());

        if (xProp.isSetScale())
            prop.getPropertyDescriptor().setScale(xProp.getScale());

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

        List<ConditionalFormat> formats = ConditionalFormat.convertFromXML(xProp.getConditionalFormats());
        prop.setConditionalFormats(formats);

        return prop;
    }
}
