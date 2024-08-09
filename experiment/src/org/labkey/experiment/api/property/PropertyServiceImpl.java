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

import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ser.FilterProvider;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fhcrc.cpas.exp.xml.DefaultType;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyValidatorPropertyType;
import org.fhcrc.cpas.exp.xml.PropertyValidatorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Handler;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.XarFormatException;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.ConceptURIVocabularyDomainProvider;
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
import org.labkey.api.gwt.client.assay.model.GWTPropertyDescriptorMixin;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.ontology.OntologyService;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.usageMetrics.UsageMetricsProvider;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.NotFoundException;
import org.labkey.experiment.api.VocabularyDomainKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.exp.property.DefaultPropertyValidator.createValidatorURI;

public class PropertyServiceImpl implements PropertyService, UsageMetricsProvider
{
    private final List<DomainKind> _domainTypes = new CopyOnWriteArrayList<>();
    private final Map<String, ValidatorKind> _validatorTypes = new ConcurrentHashMap<>();
    private final Map<String, ConceptURIVocabularyDomainProvider> _conceptUriVocabularyProvider = new ConcurrentHashMap<>();

    public static PropertyServiceImpl get()
    {
        return (PropertyServiceImpl) PropertyService.get();
    }
    private static final Logger LOG = LogManager.getLogger(PropertyServiceImpl.class);

    @Override
    public IPropertyType getType(Container container, String typeURI)
    {
        Domain domain = getDomain(container, typeURI);
        if (domain != null)
        {
            return domain;
        }
        return new PrimitiveType(PropertyType.getFromURI(null, typeURI));
    }

    @Override
    @Nullable
    public DomainImpl getDomain(Container container, String domainURI)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainURI, container);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    @Override
    @Nullable
    public Domain getDomain(int domainId)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(domainId);
        if (dd == null)
            return null;
        return new DomainImpl(dd);
    }

    @Override
    @NotNull
    public Domain createDomain(Container container, String typeURI, String name)
    {
        return new DomainImpl(container, typeURI, name);
    }

    @Override
    @NotNull
    public Domain createDomain(Container container, String typeURI, String name, @Nullable TemplateInfo templateInfo)
    {
        return new DomainImpl(container, typeURI, name, templateInfo);
    }

    @Override
    public @NotNull Domain ensureDomain(Container container, User user, String domainURI, String name)
    {
        try
        {
            // fast exists check
            Domain domain = PropertyService.get().getDomain(container, domainURI);
            if (null != domain)
                return domain;

            // check exists and save in a transaction
            domain = PropertyService.get().createDomain(container, domainURI, name);
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                ((DomainImpl) domain).saveIfNotExists(user);
            }

            // return created domain, will only be null in some sort of race condition
            domain = PropertyService.get().getDomain(domain.getTypeId());
            if (null == domain)
                throw new OptimisticConflictException("Domain deleted: " + domainURI, "", Table.ERROR_DELETED);
            return domain;
        }
        catch (ChangePropertyDescriptorException x)
        {
            // shouldn't happen on create
            throw UnexpectedException.wrap(x);
        }
    }

    @Override
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

    @Override
    public DomainKind getDomainKindByName(String name)
    {
        for (DomainKind type : _domainTypes)
        {
            if (type.getKindName().equalsIgnoreCase(name))
                return type;
        }
        return null;
    }

    @Override
    public DomainKind getDomainKind(String typeURI)
    {
        return Handler.Priority.findBestHandler(_domainTypes, typeURI);
    }

    @Override
    public void registerDomainKind(DomainKind type)
    {
        _domainTypes.add(type);
    }


    @Override
    public List<DomainKind> getDomainKinds()
    {
        ArrayList<DomainKind> l = new ArrayList<>(_domainTypes);
        return Collections.unmodifiableList(l);
    }

    @Override
    public List<DomainKind> getDomainKinds(Container container, User user, Set<String> domainKinds, boolean includeProjectAndShared)
    {
        List<? extends Domain> domains = getDomains(container, user, domainKinds, null, includeProjectAndShared);
        List<DomainKind> dks = new ArrayList<>();
        domains.forEach(d -> {
            if(null != d.getDomainKind())
                dks.add((d.getDomainKind()));
        });

        return dks;
    }

    @Override
    public List<? extends Domain> getDomains(Container container)
    {
        List<Domain> result = new ArrayList<>();
        for (DomainDescriptor dd : OntologyManager.getDomainDescriptors(container))
        {
            result.add(new DomainImpl(dd));
        }

        return Collections.unmodifiableList(result);
    }

    @Override
    public List<? extends Domain> getDomains(Container container, User user, boolean includeProjectAndShared)
    {
        List<Domain> result = new ArrayList<>();
        for (DomainDescriptor dd : OntologyManager.getDomainDescriptors(container, user, includeProjectAndShared))
        {
            result.add(new DomainImpl(dd));
        }

        return Collections.unmodifiableList(result);
    }

    public void ensureDomains(Container container, User user)
    {
        LOG.info("Initializing domain caches for folder: " + container.getPath());
        getDomains(container, user, true);
    }

    @Override
    public List<? extends Domain> getDomains(Container container, User user, @Nullable Set<String> domainKinds, @Nullable Set<String> domainNames, boolean includeProjectAndShared)
    {
        return getDomainsStream(container, user, domainKinds, domainNames, includeProjectAndShared).collect(Collectors.toList());
    }

    @Override
    public List<? extends Domain> getDomains(Container container, User user, @NotNull DomainKind<?> dk, boolean includeProjectAndShared)
    {
        // Domain.getDomainKind() can be slow. Instead just ask the passed-in dk if the domain matches or not.
        return getDomains(container, user, includeProjectAndShared)
                .stream()
                .filter(d -> dk.getPriority(d.getTypeURI()) != null)
                .collect(Collectors.toList());
    }

    @Override
    public Stream<? extends Domain> getDomainsStream(Container container, User user, @Nullable Set<String> domainKinds, @Nullable Set<String> domainNames, boolean includeProjectAndShared)
    {
        Stream<? extends Domain> stream = getDomains(container, user, includeProjectAndShared)
                .stream()
                .filter(d -> d.getDomainKind() != null);

        if (domainKinds != null && !domainKinds.isEmpty())
            stream = stream.filter(d -> domainKinds.contains(d.getDomainKind().getKindName()));

        if (domainNames != null && !domainNames.isEmpty())
            stream = stream.filter(d -> domainNames.contains(d.getName()));

        return stream;
    }

    @Override
    public void registerValidatorKind(ValidatorKind validatorKind)
    {
        if (_validatorTypes.containsKey(validatorKind.getTypeURI()))
            throw new IllegalArgumentException("Validator type : " + validatorKind.getTypeURI() + " is already registered");

        _validatorTypes.put(validatorKind.getTypeURI(), validatorKind);
    }

    @Override
    public ValidatorKind getValidatorKind(String typeURI)
    {
        return _validatorTypes.get(typeURI);
    }

    @Override
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

    @Override
    public void deleteValidatorsAndFormats(Container c)
    {
        DomainPropertyManager.get().deleteAllValidatorsAndFormats(c);
    }

    @Override
    public IPropertyValidator getValidatorForColumn(ColumnInfo col, org.labkey.api.gwt.client.model.PropertyValidatorType type)
    {
        List<? extends IPropertyValidator> validators = col.getValidators();
        if (!validators.isEmpty())
        {
            String typeURI = createValidatorURI(type).toString();

            for (IPropertyValidator validator : validators)
            {
                if (validator.getTypeURI().equals(typeURI))
                    return validator;
            }
        }
        return null;
    }

    @Override
    public List<String> getTextChoiceValidatorOptions(IPropertyValidator validator)
    {
        String expression = "";
        if (validator != null && validator.getExpressionValue() != null)
            expression = validator.getExpressionValue();

        // split expression, trim choices, and remove duplicates
        String[] choiceTokens = expression.split("\\|");
        Set<String> choiceSet = Arrays.stream(choiceTokens).map(String::trim).collect(Collectors.toSet());

        // remove empty strings and sort
        return choiceSet.stream().filter(choice -> !StringUtils.isEmpty(choice))
                .sorted()
                .collect(Collectors.toList());
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

    @Override
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

    @Override
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

    @Override
    public void configureObjectMapper(ObjectMapper om, @Nullable SimpleBeanPropertyFilter filter)
    {
        SimpleBeanPropertyFilter gwtDomainPropertiesFilter;
        if(null == filter)
        {
            gwtDomainPropertiesFilter = SimpleBeanPropertyFilter.serializeAll();
        }
        else
        {
            gwtDomainPropertiesFilter = filter;
        }

        FilterProvider gwtDomainFilterProvider = new SimpleFilterProvider()
                .addFilter("listDomainsActionFilter", gwtDomainPropertiesFilter);
        om.setFilterProvider(gwtDomainFilterProvider);
        om.addMixIn(GWTDomain.class, GWTDomainMixin.class);
        om.addMixIn(GWTPropertyDescriptor.class, GWTPropertyDescriptorMixin.class);
        om.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
    }

    private static void loadPropertyDescriptor(Domain domain, XarContext context, PropertyDescriptorType xProp, Map<DomainProperty, Object> defaultValues)
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
        }
        
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

        if (xProp.isSetShownInLookupView())
            prop.getPropertyDescriptor().setShownInLookupView(xProp.getShownInLookupView());

        if (xProp.isSetScale())
            prop.getPropertyDescriptor().setScale(xProp.getScale());

        if (xProp.isSetDerivationDataScope())
            prop.setDerivationDataScope(xProp.getDerivationDataScope().toString());

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

        if (null != xProp.xgetMeasure())
            prop.setMeasure(xProp.getMeasure());
        if (null != xProp.xgetDimension())
            prop.setDimension(xProp.getDimension());
        prop.setRecommendedVariable(xProp.getRecommendedVariable());

        OntologyService os = OntologyService.get();
        if (null != os)
            os.parseXml(xProp,prop);

        if (null != xProp.xgetScannable())
            prop.setScannable(xProp.getScannable());
    }

    @Override
    public Set<DomainProperty> findVocabularyProperties(Container container, Set<String> keyColumnNames)
    {
        Set<DomainProperty> vocabularyDomainProperties = new HashSet<>();

        if (null != container)
        {
            for (String key : keyColumnNames)
            {
                if (URIUtil.hasURICharacters(key))
                {
                    PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(key, container);

                    if (null != pd)
                    {
                        List<Domain> vocabDomains = OntologyManager.getDomainsForPropertyDescriptor(container, pd)
                                .stream()
                                .filter(d -> d.getDomainKind() instanceof VocabularyDomainKind)
                                .collect(Collectors.toList());

                        if (!vocabDomains.isEmpty())
                        {
                            DomainProperty dp = vocabDomains.get(0).getPropertyByURI(key);
                            vocabularyDomainProperties.add(dp);
                        }
                    }
                }
            }
        }

        return vocabularyDomainProperties;
    }

    @Override
    public Map<String, Object> getUsageMetrics()
    {
        DbSchema schema = ExperimentService.get().getSchema();
        String lengthFn = schema.getSqlDialect().getVarcharLengthFunction();

        return Map.of(
                "propertyValidators", Map.of(
                        "byType", new SqlSelector(schema,
                                new SQLFragment("SELECT typeuri, COUNT(*) AS count FROM exp.propertyvalidator GROUP BY typeuri")
                        ).getMapCollection().stream().reduce(new HashMap<>(), (x, m) -> {
                            x.put(m.get("typeuri").toString(), m.get("count"));
                            return x;
                        }),
                        "textChoiceValues", new SqlSelector(schema,
                                new SQLFragment("SELECT MIN(NumValues) AS min, MAX(NumValues) AS max, AVG(NumValues) AS avg FROM (")
                                        .append(String.format("SELECT (%s(expression) - %s(replace(expression, '|', '')) + 1) AS NumValues FROM exp.propertyvalidator WHERE typeuri = ?", lengthFn, lengthFn))
                                        .append(") AS X")
                                        .add("urn:lsid:labkey.com:PropertyValidator:textchoice")
                            ).getMapCollection().stream().reduce(new HashMap<>(), (x, m) -> {
                                x.put("min", m.get("min") != null ? m.get("min") : 0);
                                x.put("max", m.get("max") != null ? m.get("max") : 0);
                                x.put("avg", m.get("avg") != null ? m.get("avg") : 0);
                                return x;
                            })
                )
        );
    }

    @Override
    public void registerConceptUriVocabularyDomainProvider(String conceptUri, ConceptURIVocabularyDomainProvider provider)
    {
        _conceptUriVocabularyProvider.put(conceptUri, provider);
    }

    @Override
    public ConceptURIVocabularyDomainProvider getConceptUriVocabularyDomainProvider(String conceptUri)
    {
        return _conceptUriVocabularyProvider.get(conceptUri);
    }

    @Override
    @Nullable
    public Object getDomainPropertyValueFromRow(DomainProperty property, Map<String, Object> row)
    {
        if (property == null || row == null)
            return null;

        if (row.containsKey(property.getName()))
            return row.get(property.getName());
        else if (property.getLabel() != null && row.containsKey(property.getLabel()))
            return row.get(property.getLabel());
        else if (row.containsKey(property.getName().toLowerCase()))
            return row.get(property.getName().toLowerCase());
        else if (!property.getImportAliasSet().isEmpty())
        {
            for (String alias : property.getImportAliasSet())
            {
                if (row.containsKey(alias))
                    return row.get(alias);
            }
        }

        return null;
    }

    @Override
    public void replaceDomainPropertyValue(DomainProperty property, Map<String, Object> row, Object value)
    {
        if (property == null || row == null)
            return;

        if (row.containsKey(property.getName()))
            row.put(property.getName(), value);
        else if (property.getLabel() != null && row.containsKey(property.getLabel()))
            row.put(property.getLabel(), value);
        else if (row.containsKey(property.getName().toLowerCase()))
            row.put(property.getName().toLowerCase(), value);
        else if (!property.getImportAliasSet().isEmpty())
        {
            for (String alias : property.getImportAliasSet())
            {
                if (row.containsKey(alias))
                {
                    row.put(alias, value);
                    return;
                }
            }
        }
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testTextChoiceValidatorOptions()
        {
            PropertyServiceImpl service = new PropertyServiceImpl();
            TextChoiceValidator validator = new TextChoiceValidator();
            IPropertyValidator instance = validator.createInstance();

            // empty expression
            instance.setExpressionValue("");
            List<String> choices = service.getTextChoiceValidatorOptions(instance);
            Assert.assertEquals(new ArrayList<>(), choices);

            // filter empty option
            instance.setExpressionValue("a||b");
            choices = service.getTextChoiceValidatorOptions(instance);
            Assert.assertEquals(Arrays.asList("a", "b"), choices);

            // remove duplicates
            instance.setExpressionValue("A|A|a|a|A|A");
            choices = service.getTextChoiceValidatorOptions(instance);
            Assert.assertEquals(Arrays.asList("A", "a"), choices);

            // trim options
            instance.setExpressionValue(" a|b | c ");
            choices = service.getTextChoiceValidatorOptions(instance);
            Assert.assertEquals(Arrays.asList("a", "b", "c"), choices);

            // sort options
            instance.setExpressionValue("a|c|d|b");
            choices = service.getTextChoiceValidatorOptions(instance);
            Assert.assertEquals(Arrays.asList("a", "b", "c", "d"), choices);
        }

        @Test
        public void testTetDomainPropertyValueFromRow()
        {
            PropertyServiceImpl service = new PropertyServiceImpl();
            PropertyDescriptor pd = new PropertyDescriptor();
            pd.setName("MyName");
            pd.setLabel("MyLabel");
            pd.setImportAliases("Alias1,\"Alias 2\"");
            DomainPropertyImpl dp = new DomainPropertyImpl(null, pd);

            // null checks
            Assert.assertNull(service.getDomainPropertyValueFromRow(null, null));
            Assert.assertNull(service.getDomainPropertyValueFromRow(dp, null));
            Assert.assertNull(service.getDomainPropertyValueFromRow(null, Map.of("name", "MyName")));

            // no match in row
            Assert.assertNull(service.getDomainPropertyValueFromRow(dp, Map.of("Bogus", "test")));
            Assert.assertNull(service.getDomainPropertyValueFromRow(dp, Map.of("Name", "test")));
            Assert.assertNull(service.getDomainPropertyValueFromRow(dp, Map.of("MyNamE", "test")));
            Assert.assertNull(service.getDomainPropertyValueFromRow(dp, Map.of("Alias2", "test")));

            // has match in row
            Assert.assertEquals("test", service.getDomainPropertyValueFromRow(dp, Map.of("MyName", "test")));
            Assert.assertEquals("test", service.getDomainPropertyValueFromRow(dp, Map.of("myname", "test")));
            Assert.assertEquals("test", service.getDomainPropertyValueFromRow(dp, Map.of("MyLabel", "test")));
            Assert.assertEquals("test", service.getDomainPropertyValueFromRow(dp, Map.of("Alias1", "test")));
            Assert.assertEquals("test", service.getDomainPropertyValueFromRow(dp, Map.of("Alias 2", "test")));

            // check order of precedence
            Map<String, Object> row = new HashMap<>(Map.of("MyName", "name", "MyLabel", "label", "Alias1", "a1", "Alias 2", "a2"));
            Assert.assertEquals("name", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("MyName");
            Assert.assertEquals("label", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("MyLabel");
            Assert.assertEquals("a1", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("Alias1");
            Assert.assertEquals("a2", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("Alias 2");
            Assert.assertNull(service.getDomainPropertyValueFromRow(dp, row));
        }

        @Test
        public void replaceDomainPropertyValue()
        {
            PropertyServiceImpl service = new PropertyServiceImpl();
            PropertyDescriptor pd = new PropertyDescriptor();
            pd.setName("MyName");
            pd.setLabel("MyLabel");
            pd.setImportAliases("Alias1,\"Alias 2\"");
            DomainPropertyImpl dp = new DomainPropertyImpl(null, pd);
            Map<String, Object> row = new HashMap<>(Map.of("MyName", "name", "MyLabel", "label", "Alias1", "a1", "Alias 2", "a2"));

            Assert.assertEquals("name", service.getDomainPropertyValueFromRow(dp, row));
            service.replaceDomainPropertyValue(dp, row, "newName");
            Assert.assertEquals("newName", row.get("MyName"));
            Assert.assertEquals("newName", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("MyName");

            Assert.assertEquals("label", service.getDomainPropertyValueFromRow(dp, row));
            service.replaceDomainPropertyValue(dp, row, "newLabel");
            Assert.assertEquals("newLabel", row.get("MyLabel"));
            Assert.assertEquals("newLabel", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("MyLabel");

            Assert.assertEquals("a1", service.getDomainPropertyValueFromRow(dp, row));
            service.replaceDomainPropertyValue(dp, row, "newA1");
            Assert.assertEquals("newA1", row.get("Alias1"));
            Assert.assertEquals("newA1", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("Alias1");

            Assert.assertEquals("a2", service.getDomainPropertyValueFromRow(dp, row));
            service.replaceDomainPropertyValue(dp, row, "newA2");
            Assert.assertEquals("newA2", row.get("Alias 2"));
            Assert.assertEquals("newA2", service.getDomainPropertyValueFromRow(dp, row));
            row.remove("Alias 2");

            Assert.assertNull(service.getDomainPropertyValueFromRow(dp, row));
        }
    }
}
