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
package org.labkey.api.exp.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ColumnRenderPropertiesImpl;
import org.labkey.api.data.ConditionalFormat;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PHI;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.gwt.client.DefaultScaleType;
import org.labkey.api.gwt.client.FacetingBehaviorType;
import org.labkey.api.gwt.client.LockedPropertyType;
import org.labkey.api.gwt.client.model.GWTConditionalFormat;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.model.GWTPropertyValidator;
import org.labkey.api.gwt.client.model.PropertyValidatorType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyValidationError;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JdbcUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.ConditionalFormatFilterType;
import org.labkey.data.xml.ConditionalFormatType;
import org.labkey.experiment.api.SampleTypeDomainKind;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.labkey.api.dataiterator.DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior;

/**
 * User: jgarms
 * Date: Aug 12, 2008
 * Time: 3:44:30 PM
 */
public class DomainUtil
{
    private DomainUtil()
    {
    }

    public static String getFormattedDefaultValue(User user, DomainProperty property, Object defaultValue)
    {
        return getFormattedDefaultValue(user, property, defaultValue, false);
    }

    public static String getFormattedDefaultValue(User user, DomainProperty property, Object defaultValue, boolean validateOnly)
    {
        if (defaultValue == null || (defaultValue instanceof String && StringUtils.isBlank((String)defaultValue)))
            return "[none]";
        if (defaultValue instanceof Date)
        {
            Date defaultDate = (Date) defaultValue;
            if (property.getFormat() != null)
                return DateUtil.formatDateTime(defaultDate, property.getFormat());
            else
                return DateUtil.formatDate(property.getContainer(), defaultDate);
        }
        else if (AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME.equalsIgnoreCase(property.getName()))
        {
            // ParticipantVisitResolver default value may be stored as a simple string, or it may be JSON encoded. If JSON encoded, it may have
            // addition nested properties containing ThawList list settings.
            try
            {
                Map<String, String> decodedVals = new ObjectMapper().readValue(defaultValue.toString(), Map.class);
                String stringValue = decodedVals.get("stringValue");
                if (stringValue != null)
                    return stringValue;
                // Fall through below to return defaultValue.toString();
            }
            catch (Exception e)
            {
                LogManager.getLogger(DomainUtil.class).debug("Failed to parse JSON for default value. It may predate JSON encoding for thaw list.", e);
                // And then fall through below to return defaultValue.toString();
            }
        }
        else if (property.getLookup() != null)
        {
            Container lookupContainer = property.getLookup().getContainer();
            if (lookupContainer == null)
                lookupContainer = property.getContainer();
            UserSchema schema = QueryService.get().getUserSchema(user, lookupContainer, property.getLookup().getSchemaKey());
            if (schema != null)
            {
                TableInfo table = schema.getTable(property.getLookup().getQueryName());
                if (table != null)
                {
                    List<String> pks = table.getPkColumnNames();
                    String pkCol = pks.get(0);
                    if ((pkCol.equalsIgnoreCase("container") || pkCol.equalsIgnoreCase("containerid")) && pks.size() == 2)
                        pkCol = pks.get(1);
                    if (pkCol != null)
                    {
                        ColumnInfo pkColumnInfo = table.getColumn(pkCol);
                        if (!pkColumnInfo.getClass().equals(defaultValue.getClass()))
                            defaultValue = ConvertUtils.convert(defaultValue.toString(), pkColumnInfo.getJavaClass());

                        if (!validateOnly)
                        {
                            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(pkCol), defaultValue);
                            Object value = new TableSelector(table, Collections.singleton(table.getTitleColumn()), filter, null).getObject(Object.class);

                            if (value != null)
                                return value.toString();
                        }
                    }
                }
            }
        }
        return defaultValue.toString();
    }

    @Nullable
    public static GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(User user, String typeURI, Container domainContainer, boolean skipPKCol /*skip querying for tableInfo for sample type and dataclass name previews*/)
    {
        DomainDescriptor dd = OntologyManager.getDomainDescriptor(typeURI, domainContainer);
        if (null == dd)
            return null;
        Domain domain = PropertyService.get().getDomain(dd.getDomainId());
        return getDomainDescriptor(domainContainer, user, domain, skipPKCol);
    }

    @Nullable
    public static GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(User user, String typeURI, Container domainContainer)
    {
        return getDomainDescriptor(user, typeURI, domainContainer, false);
    }

    @NotNull
    public static GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(User user, @NotNull Domain domain)
    {
        return getDomainDescriptor(domain.getContainer(), user, domain);
    }

    @NotNull
    public static GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(Container container, User user, @NotNull Domain domain)
    {
        return getDomainDescriptor(container, user, domain, false);
    }

    @NotNull
    public static GWTDomain<GWTPropertyDescriptor> getDomainDescriptor(Container container, User user, @NotNull Domain domain, boolean skipPKCols)
    {
        GWTDomain<GWTPropertyDescriptor> d = getDomain(domain);

        ArrayList<GWTPropertyDescriptor> list = new ArrayList<>();

        List<? extends DomainProperty> properties = domain.getProperties();

        Map<DomainProperty, Object> defaultValues = DefaultValueService.get().getDefaultValues(container, domain);

        DomainKind domainKind = domain.getDomainKind();
        if (domainKind == null)
        {
            throw new IllegalStateException("Could not find a DomainKind for " + domain.getTypeURI());
        }

        Set<String> mandatoryProperties = new CaseInsensitiveHashSet(domainKind.getMandatoryPropertyNames(domain));

        Map<String, Object> pkColMap = new HashMap<>();
        TableInfo tableInfo = null;
        if (!skipPKCols)
        {
            //get PK columns
            tableInfo = domainKind.getTableInfo(user, container, domain, null);

            if (null != tableInfo && null != tableInfo.getPkColumns())
            {
                pkColMap = tableInfo.getPkColumns().stream().collect(Collectors.toMap(ColumnInfo :: getColumnName, ColumnInfo :: isKeyField));
            }
        }

        for (DomainProperty prop : properties)
        {
            GWTPropertyDescriptor p = getPropertyDescriptor(prop);

            Object defaultValue = defaultValues.get(prop);
            String formattedDefaultValue = getFormattedDefaultValue(user, prop, defaultValue);
            p.setDefaultDisplayValue(formattedDefaultValue);
            p.setDefaultValue(ConvertUtils.convert(defaultValue));

            //set property as PK
            if (pkColMap.containsKey(p.getName()))
            {
                p.setIsPrimaryKey(true);
            }

            //partially lock mandatory properties (ex. for issues, specimen domains)
            if (mandatoryProperties.contains(p.getName()))
            {
                p.setLockType(LockedPropertyType.PartiallyLocked.name());
            }

            //fully lock shared columns or columns not in the same container (ex. for dataset domain)
            if (!p.getContainer().equalsIgnoreCase(container.getId()))
            {
                p.setLockType(LockedPropertyType.FullyLocked.name());
            }

            list.add(p);
        }

        d.setFields(list);

        // Handle reserved property names
        Set<String> reservedProperties = domainKind.getReservedPropertyNames(domain, user);
        d.setReservedFieldNames(new CaseInsensitiveHashSet(reservedProperties));
        d.setReservedFieldNamePrefixes(domainKind.getReservedPropertyNamePrefixes());
        d.setMandatoryFieldNames(new CaseInsensitiveHashSet(mandatoryProperties));
        d.setExcludeFromExportFieldNames(new CaseInsensitiveHashSet(domainKind.getAdditionalProtectedPropertyNames(domain)));
        d.setProvisioned(domain.isProvisioned());
        d.setDefaultValueOptions(domainKind.getDefaultValueOptions(domain), domainKind.getDefaultDefaultType(domain));

        d.setSchemaName(domainKind.getMetaDataSchemaName());
        d.setQueryName(domainKind.getMetaDataTableName());
        d.setDomainKindName(domainKind.getKindName());
        if (null != domain.getTemplateInfo())
        {
            TemplateInfo t = domain.getTemplateInfo();
            d.setTemplateDescription(t.getModuleName() + ": " + t.getTemplateGroupName() + "#" + t.getTableName());
        }

        // if not set via domain kind, provide the public schemaName and queryName for the domain
        if (d.getSchemaName() == null && d.getQueryName() == null && tableInfo != null)
        {
            d.setSchemaName(tableInfo.getPublicSchemaName());
            d.setQueryName(tableInfo.getPublicName());
        }

        return d;
    }

    private static GWTDomain<GWTPropertyDescriptor> getDomain(Domain dd)
    {
        GWTDomain<GWTPropertyDescriptor> gwtDomain = new GWTDomain<>();

        gwtDomain.set_Ts(JdbcUtil.rowVersionToString(dd.get_Ts()));
        gwtDomain.setDomainId(dd.getTypeId());
        gwtDomain.setDomainURI(dd.getTypeURI());
        gwtDomain.setName(dd.getName());
        gwtDomain.setDescription(dd.getDescription());
        gwtDomain.setContainer(dd.getContainer().getId());
        gwtDomain.setProvisioned(dd.isProvisioned());
        DomainKind<?> kind = dd.getDomainKind();
        if (kind != null)
        {
            gwtDomain.setAllowAttachmentProperties(kind.allowAttachmentProperties());
            gwtDomain.setAllowFileLinkProperties(kind.allowFileLinkProperties());
            gwtDomain.setAllowFlagProperties(kind.allowFlagProperties());
            gwtDomain.setAllowTextChoiceProperties(kind.allowTextChoiceProperties());
            gwtDomain.setAllowSampleSubjectProperties(kind.allowSampleSubjectProperties());
            gwtDomain.setAllowTimepointProperties(kind.allowTimepointProperties());
            gwtDomain.setShowDefaultValueSettings(kind.showDefaultValueSettings());
            gwtDomain.setInstructions(kind.getDomainEditorInstructions());
        }
        return gwtDomain;
    }

    public static GWTDomain<GWTPropertyDescriptor> getTemplateDomainForDomainKind(DomainKind kind)
    {
        GWTDomain<GWTPropertyDescriptor> gwtDomain = new GWTDomain<>();
        gwtDomain.setDomainKindName(kind.getKindName());
        gwtDomain.setAllowAttachmentProperties(kind.allowAttachmentProperties());
        gwtDomain.setAllowFileLinkProperties(kind.allowFileLinkProperties());
        gwtDomain.setAllowFlagProperties(kind.allowFlagProperties());
        gwtDomain.setAllowTextChoiceProperties(kind.allowTextChoiceProperties());
        gwtDomain.setAllowSampleSubjectProperties(kind.allowSampleSubjectProperties());
        gwtDomain.setAllowTimepointProperties(kind.allowTimepointProperties());
        gwtDomain.setShowDefaultValueSettings(kind.showDefaultValueSettings());
        gwtDomain.setInstructions(kind.getDomainEditorInstructions());
        gwtDomain.setDefaultValueOptions(kind.getDefaultValueOptions(null), kind.getDefaultDefaultType(null));
        return gwtDomain;
    }

    public static GWTPropertyDescriptor getPropertyDescriptor(DomainProperty prop)
    {
        return getPropertyDescriptor(prop.getPropertyDescriptor());
    }

    public static GWTPropertyDescriptor getPropertyDescriptor(PropertyDescriptor prop)
    {
        GWTPropertyDescriptor gwtProp = new GWTPropertyDescriptor();

        gwtProp.setPropertyId(prop.getPropertyId());
        gwtProp.setDescription(prop.getDescription());
        gwtProp.setFormat(prop.getFormat());
        gwtProp.setLabel(prop.getLabel());
        gwtProp.setConceptURI(prop.getConceptURI());
        gwtProp.setName(prop.getName());
        gwtProp.setPropertyURI(prop.getPropertyURI());
        gwtProp.setContainer(prop.getContainer().getId());
        gwtProp.setRangeURI(prop.getPropertyType() != null ? prop.getPropertyType().getTypeUri() : prop.getRangeURI());
        gwtProp.setRequired(prop.isRequired());
        gwtProp.setHidden(prop.isHidden());
        gwtProp.setShownInInsertView(prop.isShownInInsertView());
        gwtProp.setShownInUpdateView(prop.isShownInUpdateView());
        gwtProp.setShownInDetailsView(prop.isShownInDetailsView());
        gwtProp.setDimension(prop.isDimension());
        gwtProp.setMeasure(prop.isMeasure());
        gwtProp.setRecommendedVariable(prop.isRecommendedVariable());
        gwtProp.setDefaultScale(prop.getDefaultScale().name());
        gwtProp.setMvEnabled(prop.isMvEnabled());
        gwtProp.setFacetingBehaviorType(prop.getFacetingBehaviorType().name());
        gwtProp.setPHI(prop.getPHI().name());
        gwtProp.setExcludeFromShifting(prop.isExcludeFromShifting());
        gwtProp.setDefaultValueType(prop.getDefaultValueTypeEnum());
        gwtProp.setImportAliases(prop.getImportAliases());
        StringExpression url = prop.getURL();
        gwtProp.setURL(url == null ? null : url.toString());
        gwtProp.setScale(prop.getScale());
        gwtProp.setRedactedText(prop.getRedactedText());
        gwtProp.setPrincipalConceptCode(prop.getPrincipalConceptCode());
        gwtProp.setSourceOntology(prop.getSourceOntology());
        gwtProp.setConceptSubtree(prop.getConceptSubtree());
        gwtProp.setConceptImportColumn(prop.getConceptImportColumn());
        gwtProp.setConceptLabelColumn(prop.getConceptLabelColumn());
        gwtProp.setDerivationDataScope(prop.getDerivationDataScope());
        gwtProp.setScannable(prop.isScannable());

        List<GWTPropertyValidator> validators = new ArrayList<>();
        for (IPropertyValidator pv : prop.getValidators())
        {
            GWTPropertyValidator gpv = new GWTPropertyValidator();
            Lsid lsid = new Lsid(pv.getTypeURI());

            gpv.setName(pv.getName());
            gpv.setDescription(pv.getDescription());
            gpv.setExpression(pv.getExpressionValue());
            gpv.setRowId(pv.getRowId());
            gpv.setType(PropertyValidatorType.getType(lsid.getObjectId()));
            gpv.setErrorMessage(pv.getErrorMessage());

            Map<String, String> properties = new HashMap<>(pv.getProperties());
            // add in the TextChoice validValues here so that the client side code doesn't have to do the same
            // parsing of the validator expression (i.e. sorting, trimming, removing duplicates, etc.)
            if (PropertyValidatorType.TextChoice.equals(gpv.getType()))
                properties.put("validValues", StringUtils.join(PropertyService.get().getTextChoiceValidatorOptions(pv), "|"));
            gpv.setProperties(properties);

            validators.add(gpv);
        }
        gwtProp.setPropertyValidators(validators);

        List<GWTConditionalFormat> formats = new ArrayList<>();
        for (ConditionalFormat format : prop.getConditionalFormats())
        {
            formats.add(new GWTConditionalFormat(format));
        }
        gwtProp.setConditionalFormats(formats);

        if (prop.getLookup() != null)
        {
            gwtProp.setLookupContainer(prop.getLookup().getContainer() == null ? null : prop.getLookup().getContainer().getPath());
            gwtProp.setLookupQuery(prop.getLookup().getQueryName());
            var key = prop.getLookup().getSchemaKey();
            if (null != key)
               gwtProp.setLookupSchema(key.toString());
        }
        return gwtProp;
    }

    public static GWTPropertyDescriptor getPropertyDescriptor(ColumnType columnXml)
    {
        GWTPropertyDescriptor gwtProp = new GWTPropertyDescriptor();
        gwtProp.setName(columnXml.getColumnName());

        if (columnXml.isSetColumnTitle())
            gwtProp.setLabel(columnXml.getColumnTitle());
        if (columnXml.isSetPropertyURI())
            gwtProp.setPropertyURI(columnXml.getPropertyURI());
        if (columnXml.isSetRangeURI())
        {
            PropertyType pt = PropertyType.getFromURI(columnXml.getRangeURI(),null,null);
            if (null != pt)
                gwtProp.setRangeURI(pt.getTypeUri());
            else
                gwtProp.setRangeURI(columnXml.getRangeURI());
        }
        if (columnXml.isSetIsHidden())
            gwtProp.setHidden(columnXml.getIsHidden());
        if (columnXml.isSetFk())
        {
            gwtProp.setLookupContainer(columnXml.getFk().getFkFolderPath());
            gwtProp.setLookupSchema(columnXml.getFk().getFkDbSchema());
            gwtProp.setLookupQuery(columnXml.getFk().getFkTable());
        }

        // issue 25278: don't allow conceptURI lookups for attachment columns
        if (columnXml.isSetConceptURI() && !PropertyType.ATTACHMENT.getTypeUri().equals(gwtProp.getRangeURI()))
            gwtProp.setConceptURI(columnXml.getConceptURI());

        // Display properties
        if (columnXml.isSetDescription())
            gwtProp.setDescription(columnXml.getDescription());
        // TODO: Include null value behavior
        if (columnXml.isSetUrl())
            gwtProp.setURL(columnXml.getUrl().getStringValue());
        if (columnXml.isSetShownInInsertView())
            gwtProp.setShownInInsertView(columnXml.getShownInInsertView());
        if (columnXml.isSetShownInUpdateView())
            gwtProp.setShownInUpdateView(columnXml.getShownInUpdateView());
        if (columnXml.isSetShownInDetailsView())
            gwtProp.setShownInDetailsView(columnXml.getShownInDetailsView());

        // Format properties
        if (columnXml.isSetFormatString())
            gwtProp.setFormat(columnXml.getFormatString());
        if (columnXml.isSetConditionalFormats())
        {
            List<GWTConditionalFormat> formats = new ArrayList<>();
            for (ConditionalFormatType formatType : columnXml.getConditionalFormats().getConditionalFormatArray())
            {
                GWTConditionalFormat gwtFormat = new GWTConditionalFormat();
                gwtFormat.setBold(formatType.getBold());
                gwtFormat.setItalic(formatType.getItalics());
                gwtFormat.setStrikethrough(formatType.getStrikethrough());
                gwtFormat.setTextColor(formatType.getTextColor());
                gwtFormat.setBackgroundColor(formatType.getBackgroundColor());
                for (ConditionalFormatFilterType filterType : formatType.getFilters().getFilterArray())
                    gwtFormat.setFilter("format.column%7E" + filterType.getOperator().toString() + "=" + filterType.getValue());

                formats.add(gwtFormat);
            }
            gwtProp.setConditionalFormats(formats);
        }

        // Validator properties
        if (columnXml.isSetNullable())
            gwtProp.setRequired(!columnXml.getNullable());
        // TODO gwtProp.setPropertyValidators();

        // Reporting properties
        if (columnXml.isSetMeasure())
            gwtProp.setMeasure(columnXml.getMeasure());
        if (columnXml.isSetDimension())
            gwtProp.setDimension(columnXml.getDimension());
        if (columnXml.isSetRecommendedVariable())
            gwtProp.setRecommendedVariable(columnXml.getRecommendedVariable());
        if (columnXml.isSetDefaultScale())
            gwtProp.setDefaultScale(columnXml.getDefaultScale().toString());
        if (columnXml.isSetFacetingBehavior())
            gwtProp.setFacetingBehaviorType(columnXml.getFacetingBehavior().toString());

        // Advanced properties
        if (columnXml.isSetIsMvEnabled())
            gwtProp.setMvEnabled(columnXml.getIsMvEnabled());
        // TODO gwtProp.setDefaultValueType();
        if (columnXml.isSetDefaultValue())
            gwtProp.setDefaultValue(columnXml.getDefaultValue());
        if (columnXml.isSetImportAliases())
            gwtProp.setImportAliases(StringUtils.join(columnXml.getImportAliases().getImportAliasArray(), ","));
        if (columnXml.isSetProtected())  // column is removed from LabKey but need to support old archives, see spec #28920
        {
            if (columnXml.getProtected())
                gwtProp.setPHI(PHI.Limited.toString());  // always convert protected to limited PHI; may be overridden by getPhi(), though
        }
        if (columnXml.isSetPhi())
            gwtProp.setPHI(columnXml.getPhi().toString());
        if (columnXml.isSetExcludeFromShifting())
            gwtProp.setExcludeFromShifting(columnXml.getExcludeFromShifting());
        if (columnXml.isSetScale())
            gwtProp.setScale(columnXml.getScale());
        if (columnXml.isSetScannable())
            gwtProp.setScannable(columnXml.getScannable());

        return gwtProp;
    }


    public static Domain createDomain(DomainTemplate template, Container container, User user, @Nullable String domainName) throws ValidationException
    {
        return createDomain(template.getDomainKind(), template.getDomain(), template.getOptions(), container, user, domainName, template.getTemplateInfo());
    }


    public static Domain createDomain(
            String kindName, GWTDomain domain, Map<String, Object> arguments,
            Container container, User user, @Nullable String domainName,
            @Nullable TemplateInfo templateInfo
    ) throws ValidationException
    {
        // Create a copy of the GWTDomain to ensure the template's Domain is not modified
        domain = new GWTDomain(domain);
        if (domainName != null)
        {
            domain.setName(domainName);
        }

        DomainKind kind = PropertyService.get().getDomainKindByName(kindName);
        if (kind == null)
            throw new IllegalArgumentException("No domain kind matches name '" + kindName + "'");

        if (!kind.canCreateDefinition(user, container))
            throw new UnauthorizedException("You don't have permission to create a new domain");

        ValidationException ve = DomainUtil.validateProperties(null, domain, null, null, user);
        if (ve.hasErrors())
        {
            throw new ValidationException(ve);
        }
        ObjectMapper mapper = new ObjectMapper();

        arguments = kind.processArguments(container, user, arguments);
        Object options = mapper.convertValue(arguments, kind.getTypeClass());
        Domain created = kind.createDomain(domain, options, container, user, templateInfo);

        if (created == null)
            throw new RuntimeException("Failed to created domain for kind '" + kind.getKindName() + "' using domain name '" + domainName + "'");
        return created;
    }

    @NotNull
    public static ValidationException updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update, Container container, User user)
    {
        return updateDomainDescriptor(orig, update, container, user, false);
    }

    /** @return Errors encountered during the save attempt */
    @NotNull
    public static ValidationException updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> orig, GWTDomain<? extends GWTPropertyDescriptor> update, Container container, User user, boolean updateDomainName)
    {
        assert orig.getDomainURI().equals(update.getDomainURI());

        Domain d = PropertyService.get().getDomain(container, update.getDomainURI());
        if (null == d)
        {
            ValidationException validationException = new ValidationException();
            validationException.addError(new SimpleValidationError("Domain not found: " + update.getDomainURI()));
            return validationException;
        }

        DomainKind<?> kind = d.getDomainKind();
        ValidationException validationException = validateProperties(d, update, kind, orig, user);

        if (validationException.hasErrors())
        {
            return validationException;
        }

        if (!kind.canEditDefinition(user, d))
        {
            validationException.addError(new SimpleValidationError("Unauthorized"));
            return validationException;
        }

        if (updateDomainName)
            d.setName(update.getName());

        // NOTE that DomainImpl.save() does an optimistic concurrency check, but we still need to check here.
        // This code is diff'ing two GWTDomains and applying those changes to Domain d.  We need to make sure we're
        // applying the diff's the matching Domain version.
        String currentTs = JdbcUtil.rowVersionToString(d.get_Ts());
        if (!StringUtils.equalsIgnoreCase(currentTs,orig.get_Ts()))
        {
            validationException.addError(new SimpleValidationError("The domain has been edited by another user, you may need to refresh and try again."));
            return validationException;
        }

        //error if mandatory field name is not the same as orig or has been removed in updated domain
        String missingMandatoryField = getMissingMandatoryField(update.getFields(), orig.getFields());
        if (StringUtils.isNotEmpty(missingMandatoryField))
        {
            validationException.addError(new SimpleValidationError("Mandatory field '" + missingMandatoryField + "' not found, it may have been removed or renamed. Unable to update domain."));
            return validationException;
        }

        // validate names
        // look for swapped names

        // first delete properties
        Set<Integer> s = new HashSet<>();
        for (GWTPropertyDescriptor pd : orig.getFields())
            s.add(pd.getPropertyId());
        for (GWTPropertyDescriptor pd : update.getFields())
        {
            s.remove(pd.getPropertyId());
        }

        //replace update's locked fields with the orig for the same propertyId regardless of what we get from client
        replaceLockedFields(getLockedFields(orig.getFields()), (List<GWTPropertyDescriptor>) update.getFields());

        int deletedCount = 0;
        for (int id : s)
        {
            if (id <= 0)
                continue;
            DomainProperty p = d.getProperty(id);
            if (null == p)
                continue;
            p.delete();
            deletedCount++;
        }
        // If we're deleting all fields, set flag to potentially delete all data first.
        // "All fields" is defined to be the original property-driven field count, minus any that are known to be non-deleteable through the domain editor
        // Namely, that's the List key field.
        if (deletedCount > 0 && deletedCount == (orig.getFields().size() - kind.getAdditionalProtectedProperties(d).size()))
            d.setShouldDeleteAllData(true);

        Map<DomainProperty, Object> defaultValues = new HashMap<>();
        Map<DomainProperty, List<Map<String, Object>>> textChoiceValueUpdates = new HashMap<>();

        // and now update properties
        for (GWTPropertyDescriptor pd : update.getFields())
        {
            if (pd.getPropertyId() <= 0)
                continue;
            GWTPropertyDescriptor old = null;
            for (GWTPropertyDescriptor t : orig.getFields())
            {
                if (t.getPropertyId() == pd.getPropertyId())
                {
                    old = t;
                    break;
                }
            }
            // UNDONE: DomainProperty does not support all PropertyDescriptor fields
            DomainProperty p = d.getProperty(pd.getPropertyId());
            if (p == null)
            {
                String errorMsg = "Column " + pd.getName() + " not found (id: " + pd.getPropertyId() + "), it was probably deleted. Please reload the designer and attempt the edit again.";
                validationException.addError(new PropertyValidationError(errorMsg, pd.getName(), pd.getPropertyId()));
                return validationException;
            }

            defaultValues.put(p, pd.getDefaultValue());

            if (old == null)
                continue;
            List<Map<String, Object>> propTextChoiceValueUpdates = updatePropertyValidators(p, old, pd);
            if (propTextChoiceValueUpdates != null)
                textChoiceValueUpdates.put(p, propTextChoiceValueUpdates);
            if (old.equals(pd))
                continue;

            _copyProperties(p, pd, validationException);
        }

        // Need to ensure that any new properties are given a unique PropertyURI.  See #8329
        Set<String> propertyUrisInUse = new CaseInsensitiveHashSet();

        for (GWTPropertyDescriptor pd : update.getFields())
            if (!StringUtils.isEmpty(pd.getPropertyURI()))
                propertyUrisInUse.add(pd.getPropertyURI());

        // now add properties
        for (GWTPropertyDescriptor pd : update.getFields())
        {
            addProperty(d, pd, defaultValues, propertyUrisInUse, validationException);
        }

        // TODO: update indices -- drop and re-add?

        try
        {
            if (validationException.getErrors().isEmpty())
            {
                // Reorder the properties based on what we got from GWT
                Map<String, DomainProperty> dps = new HashMap<>();
                for (DomainProperty dp : d.getProperties())
                {
                    dps.put(dp.getPropertyURI(), dp);
                }
                int index = 0;
                for (GWTPropertyDescriptor pd : update.getFields())
                {
                    DomainProperty dp = dps.get(pd.getPropertyURI());
                    d.setPropertyIndex(dp, index++);
                }

                d.save(user);
                // Rebucket the hash map with the real property ids
                defaultValues = new HashMap<>(defaultValues);
                try
                {
                    DefaultValueService.get().setDefaultValues(d.getContainer(), defaultValues);
                }
                catch (ExperimentException e)
                {
                    validationException.addError(new SimpleValidationError(e.getMessage() == null ? e.toString() : e.getMessage()));
                }

                // Text Choice validator updates to existing / in use values
                for (Map.Entry<DomainProperty, List<Map<String, Object>>> entry : textChoiceValueUpdates.entrySet())
                {
                    for (Map<String, Object> valueUpdate : entry.getValue())
                        updateTextChoiceValueRows(d, user, entry.getKey().getName(), valueUpdate, validationException);
                }
            }
        }
        catch (IllegalStateException | ChangePropertyDescriptorException x)
        {
            validationException.addError(new SimpleValidationError(x.getMessage() == null ? x.toString() : x.getMessage()));
        }

        return validationException;
    }

    private static String getMissingMandatoryField(List<? extends GWTPropertyDescriptor> updatedFields,
                                                   List<? extends GWTPropertyDescriptor> origFields)
    {
        Map<String, ? extends GWTPropertyDescriptor> updatedFieldsMap = updatedFields.stream().collect(Collectors.toMap(GWTPropertyDescriptor::getName, e -> e));
        Set<String> updatedFieldNames = new CaseInsensitiveHashSet(updatedFieldsMap.keySet());

        Map<String, ? extends GWTPropertyDescriptor> origFieldsMap = origFields.stream().filter(e -> e.getLockType().equalsIgnoreCase(LockedPropertyType.PartiallyLocked.name())).collect(Collectors.toMap(GWTPropertyDescriptor::getName, e -> e));
        Set<String> origMandatoryFieldNames = new CaseInsensitiveHashSet(origFieldsMap.keySet());

        for (String mandatoryField : origMandatoryFieldNames)
        {
            if (!updatedFieldNames.contains(mandatoryField))
            {
                return mandatoryField;
            }
        }
        return null;
    }

    private static Set<GWTPropertyDescriptor> getLockedFields(List<? extends GWTPropertyDescriptor> origFields)
    {
        Set<GWTPropertyDescriptor> locked = new HashSet<>();
        for (GWTPropertyDescriptor pd : origFields)
        {
            //if a column is fully locked
            if (pd.getLockType().equalsIgnoreCase(LockedPropertyType.FullyLocked.name()))
            {
                locked.add(pd);
            }
        }
        return locked;
    }

    private static void replaceLockedFields(Set<GWTPropertyDescriptor> lockedFields, List<GWTPropertyDescriptor> updateFields)
    {
        Map<Integer, GWTPropertyDescriptor> origLockedFieldMap = getLockedPropertyIdMap(lockedFields);
        ListIterator<GWTPropertyDescriptor> updateFieldsIterator = updateFields.listIterator(); //using an iterator to avoid ConcurrentModificationException

        //replace locked field that came from the client
        while (updateFieldsIterator.hasNext())
        {
            GWTPropertyDescriptor pd = updateFieldsIterator.next();
            int propertyId = pd.getPropertyId();
            if (origLockedFieldMap.containsKey(propertyId))
            {
                updateFieldsIterator.set(origLockedFieldMap.get(propertyId));
            }
        }
    }

    private static Map<Integer, GWTPropertyDescriptor> getLockedPropertyIdMap(Set<? extends GWTPropertyDescriptor> lockedFields)
    {
        Map<Integer, GWTPropertyDescriptor> lockedFieldMap = new HashMap<>();
        for (GWTPropertyDescriptor lockedField : lockedFields)
        {
            lockedFieldMap.put(lockedField.getPropertyId(), lockedField);
        }
        return lockedFieldMap;
    }

    public static DomainProperty addProperty(Domain domain, GWTPropertyDescriptor pd, Map<DomainProperty, Object> defaultValues, Set<String> propertyUrisInUse, ValidationException errors)
    {
        if (pd.getPropertyId() > 0)
            return null;

        if (StringUtils.isEmpty(pd.getPropertyURI()))
        {
            String newPropertyURI = createUniquePropertyURI(domain.getTypeURI() + "#" + Lsid.encodePart(pd.getName()), propertyUrisInUse);
            assert !propertyUrisInUse.contains(newPropertyURI) : "Attempting to assign an existing PropertyURI to a new property";
            pd.setPropertyURI(newPropertyURI);
            propertyUrisInUse.add(newPropertyURI);
        }

        // UNDONE: DomainProperty does not support all PropertyDescriptor fields
        DomainProperty p = domain.addProperty();
        defaultValues.put(p, pd.getDefaultValue());
        _copyProperties(p, pd, errors);
        updatePropertyValidators(p, null, pd);

        return p;
    }

    private static String createUniquePropertyURI(String base, Set<String> propertyUrisInUse)
    {
        String candidateURI = base;
        int i = 0;

        while (propertyUrisInUse.contains(candidateURI))
        {
            i++;
            candidateURI = base + i;
        }

        return candidateURI;
    }

    private static void _copyProperties(DomainProperty to, GWTPropertyDescriptor from, ValidationException errors)
    {
        // avoid problems with setters that depend on rangeURI being set
        to.setRangeURI(from.getRangeURI());

        try
        {
            BeanUtils.copyProperties(to, from);
        }
        catch (IllegalAccessException | InvocationTargetException e)
        {
            throw new RuntimeException(e);
        }

        if (to.getPropertyDescriptor().getPropertyType() == null)
        {
            String msg = "Unrecognized type '" + from.getRangeURI() + "' for property '" + from.getName() + "'";
            if (errors == null)
                throw new IllegalArgumentException(msg);
            errors.addError(new PropertyValidationError(msg, from.getName(), from.getPropertyId()));
            return;
        }

        if (from.getLookupQuery() != null)
        {
            String containerId = from.getLookupContainer();
            Container c = null;
            containerId = StringUtils.trimToNull(containerId);
            if (containerId != null)
            {
                if (GUID.isGUID(containerId))
                    c = ContainerManager.getForId(containerId);
                if (null == c)
                    c = ContainerManager.getForPath(containerId);
                if (c == null)
                {
                    String msg = "Lookup for "+ from.getName() + " target Container not found: " + containerId;
                    if (errors == null)
                        throw new RuntimeException(msg);
                    errors.addError(new PropertyValidationError(msg, from.getName(), from.getPropertyId()));
                }
            }
            Lookup lu = new Lookup(c, from.getLookupSchema(), from.getLookupQuery());
            to.setLookup(lu);
        }
        else
        {
            to.setLookup(null);
        }

        List<ConditionalFormat> formats = new ArrayList<>();
        for (GWTConditionalFormat format : from.getConditionalFormats())
        {
            ConditionalFormat fmt = new ConditionalFormat(format);
            String msg = fmt.validateFormat(to.getPropertyDescriptor());
            if (msg != null)
            {
                if (errors == null)
                    throw new RuntimeException(msg);
                errors.addError(new PropertyValidationError(msg, from.getName(), from.getPropertyId()));
                return;
            }
            formats.add(fmt);
        }
        to.setConditionalFormats(formats);

        // If the incoming DomainProperty specifies its dimension/measure state, respect that value.  Otherwise we need
        // to infer the correct value. This is necessary for code paths like the dataset creation wizard which does not
        // (and should not, for simplicity reasons) provide the user with the option to specify dimension/measure status
        // at the time that the property descriptors are created.
        if (from.isSetDimension())
            to.setDimension(from.isDimension());
        else
            to.setDimension(ColumnRenderPropertiesImpl.inferIsDimension(from.getName(), from.getLookupQuery() != null, from.isHidden()));
        if (from.isSetMeasure())
            to.setMeasure(from.isMeasure());
        else
        {
            Type type = Type.getTypeByXsdType(from.getRangeURI());
            to.setMeasure(ColumnRenderPropertiesImpl.inferIsMeasure(from.getName(), from.getLabel(), type != null && type.isNumeric(),
                                                                false, from.getLookupQuery() != null, from.isHidden()));
        }

        if (from.isRecommendedVariable())
            to.setRecommendedVariable(from.isRecommendedVariable());

        if (from.getDefaultScale() != null)
            to.setDefaultScale(DefaultScaleType.valueOf(from.getDefaultScale()));

        if (from.getFacetingBehaviorType() != null)
        {
            FacetingBehaviorType type = FacetingBehaviorType.valueOf(from.getFacetingBehaviorType());
            to.setFacetingBehavior(type);
        }

        if (from.getPHI() != null)
        {
            PHI type = PHI.valueOf(from.getPHI());
            to.setPhi(type);
        }

        if (from.isExcludeFromShifting())
            to.setExcludeFromShifting(from.isExcludeFromShifting());

        if (from.getScale() != null)
            to.setScale(from.getScale());

        if (from.getDefaultValueType() != null)
            to.setDefaultValueType(from.getDefaultValueType().toString());

        if (from.isScannable())
            to.setScannable(from.isScannable());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> updatePropertyValidators(DomainProperty dp, GWTPropertyDescriptor oldPd, GWTPropertyDescriptor newPd)
    {
        Map<Integer, GWTPropertyValidator> newProps = new HashMap<>();
        List<Map<String, Object>> valueUpdates = new ArrayList<>();

        for (GWTPropertyValidator v : newPd.getPropertyValidators())
        {
            if (v.getRowId() != 0)
                newProps.put(v.getRowId(), v);
            else
            {
                Lsid lsid = DefaultPropertyValidator.createValidatorURI(v.getType());
                IPropertyValidator pv = PropertyService.get().createValidator(lsid.toString());

                _copyValidator(pv, v);
                dp.addValidator(pv);
            }

            if (v.getExtraProperties() != null && v.getExtraProperties().containsKey("valueUpdates"))
            {
                if (v.getExtraProperties().get("valueUpdates").size() > 0)
                    valueUpdates.add(v.getExtraProperties().get("valueUpdates"));
            }
        }

        if (oldPd != null)
        {
            List<GWTPropertyValidator> deleted = new ArrayList<>();
            for (GWTPropertyValidator v : oldPd.getPropertyValidators())
            {
                GWTPropertyValidator prop = newProps.get(v.getRowId());
                if (v.equals(prop))
                    newProps.remove(v.getRowId());
                else if (prop == null)
                    deleted.add(v);
            }

            // update any new or changed
            for (IPropertyValidator pv : dp.getValidators())
                _copyValidator(pv, newProps.get(pv.getRowId()));

            // deal with removed validators
            for (GWTPropertyValidator gpv : deleted)
                dp.removeValidator(gpv.getRowId());

            return valueUpdates;
        }

        return null;
    }

    private static void updateTextChoiceValueRows(Domain domain, User user, String propName, Map<String, Object> valueUpdates, ValidationException errors)
    {
        if (domain != null && domain.getDomainKind() != null)
        {
            // using ContainerFilter.EVERYTHING to account for /Shared domains
            TableInfo domainTable = domain.getDomainKind().getTableInfo(user, domain.getContainer(), domain, ContainerFilter.EVERYTHING);
            if (domainTable != null && domainTable.getUpdateService() != null)
            {
                // we need to make all of the row updates for this domain property at one time to prevent the
                // double mapping if one choice value was changed from a -> b and another from b -> c
                // (not sure why someone would do that though)
                List<Map<String, Object>> rows = new ArrayList<>();

                for (Map.Entry<String, Object> entry : valueUpdates.entrySet())
                {
                    // query for the row PKs of domain rows that have the original text choice value
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(propName), entry.getKey());
                    // filter out aliquots for sample type domain
                    if (domain.getDomainKind() instanceof SampleTypeDomainKind)
                        filter.addCondition(FieldKey.fromParts("IsAliquot"), false);
                    List<ColumnInfo> columns = new ArrayList<>(domainTable.getPkColumns());
                    if (domainTable.getContainerFieldKey() != null)
                        columns.add(domainTable.getColumn(domainTable.getContainerFieldKey()));
                    List<Map<String, Object>> valueRows = new TableSelector(domainTable, columns, filter, null).getMapCollection().stream().toList();

                    // put the updated property value into the row map as well
                    for (Map<String, Object> valueRow : valueRows)
                    {
                        valueRow.put(propName, entry.getValue());

                        // remove extra "_row" value (from ResultSetDataIterator) if it exists
                        valueRow.remove("_row");
                    }

                    rows.addAll(valueRows);
                }

                try
                {
                    // use update rows against each distinct row container to map the text choice value to the updated value,
                    // using each row container so that the audit events end up in the right container
                    if (domainTable.getContainerFieldKey() != null)
                    {
                        String containerFieldName = domainTable.getContainerFieldKey().getName();
                        Set<String> rowContainers = rows.stream().map((row) -> (String) row.get(containerFieldName)).collect(Collectors.toSet());
                        for (String rowContainer : rowContainers)
                        {
                            List<Map<String, Object>> containerRows = rows.stream().filter((row) -> row.get(containerFieldName).equals(rowContainer)).collect(Collectors.toList());
                            domainTable.getUpdateService().updateRows(user, ContainerManager.getForId(rowContainer), containerRows, containerRows, Map.of(AuditBehavior, AuditBehaviorType.DETAILED), null);
                        }
                    }
                    else
                        domainTable.getUpdateService().updateRows(user, domain.getContainer(), rows, rows, Map.of(AuditBehavior, AuditBehaviorType.DETAILED), null);
                }
                catch (Exception e)
                {
                    errors.addError(new PropertyValidationError(e.getMessage(), propName));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void _copyValidator(IPropertyValidator pv, GWTPropertyValidator gpv)
    {
        if (pv != null && gpv != null)
        {
            pv.setName(gpv.getName());
            pv.setDescription(gpv.getDescription());
            pv.setExpressionValue(gpv.getExpression());
            pv.setErrorMessage(gpv.getErrorMessage());

            for (Map.Entry<String, String> entry : gpv.getProperties().entrySet())
            {
                pv.setProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private static String getDomainErrorMessage(@Nullable GWTDomain domain, String message)
    {
        if (domain != null && domain.getName() != null)
        {
            return domain.getName() + " -- " + message;
        }

        return message;
    }

    /**
     * Validate domain property descriptors for things like duplicate names, missing names, and check against required fields.
     * @param domain The updated domain to validate
     * @return List of errors strings found during the validation
     */
    public static ValidationException validateProperties(@Nullable Domain domain, @NotNull GWTDomain updates, @Nullable DomainKind domainKind, @Nullable GWTDomain orig, @Nullable User user)
    {
        Set<String> reservedNames = (null != domain && null != domainKind) ? new CaseInsensitiveHashSet(domainKind.getReservedPropertyNames(domain, user))
                : new CaseInsensitiveHashSet(updates.getReservedFieldNames());
        Set<String> reservedPrefixes = (null != domain && null != domainKind) ? domainKind.getReservedPropertyNamePrefixes() : updates.getReservedFieldNamePrefixes();
        Map<String, Integer> namePropertyIdMap = new CaseInsensitiveHashMap<>();
        ValidationException exception = new ValidationException();
        Map<Integer, String> propertyIdNameMap = getOriginalFieldPropertyIdNameMap(orig);//key: orig property id, value : orig field name

        for (Object f : updates.getFields())
        {
            GWTPropertyDescriptor field = (GWTPropertyDescriptor)f;

            String name = field.getName();

            if (null == name || name.trim().length() == 0)
            {
                exception.addError(new SimpleValidationError(getDomainErrorMessage(updates,"Please provide a name for each field.")));
                continue;
            }

            if (!reservedPrefixes.isEmpty())
            {
                String lcName = name.toLowerCase();
                Optional<String> reservedPrefix = reservedPrefixes.stream().filter(prefix -> lcName.startsWith(prefix.toLowerCase())).findAny();
                if (reservedPrefix.isPresent())
                {
                    exception.addFieldError(name, getDomainErrorMessage(updates, "The prefix '" + reservedPrefix.get() + "' is reserved for system use."));
                    continue;
                }
            }

            if (reservedNames.contains(name))
            {
                //check if a new field is a reserved field or an existing field is updated to a reserved field
                String origFieldName = (null != propertyIdNameMap ? propertyIdNameMap.get(field.getPropertyId()) : null);
                if (field.getPropertyId() <= 0 || !name.equalsIgnoreCase(origFieldName))
                {
                    exception.addFieldError(name, getDomainErrorMessage(updates,("'" + name + "' is a reserved field name in '" + updates.getName() + "'.")));
                }
                continue;
            }

            if (namePropertyIdMap.containsKey(name))
            {
                String errorMsg = getDomainErrorMessage(updates,"The field name '" + name + "' is already taken. Please provide a unique name for each field.");
                PropertyValidationError propertyValidationError = new PropertyValidationError(errorMsg, name, field.getPropertyId());
                exception.addError(propertyValidationError);
                continue;
            }

            if (!namePropertyIdMap.containsKey(name))
            {
                namePropertyIdMap.put(name, field.getPropertyId());
            }
        }

        return exception;
    }

    @Nullable
    private static Map<Integer, String> getOriginalFieldPropertyIdNameMap(@Nullable GWTDomain orig)
    {
        if (null != orig)
        {
            Map<Integer, String> propertyIdMap = new HashMap<>();

            for (Object f : orig.getFields())
            {
                GWTPropertyDescriptor origField = (GWTPropertyDescriptor) f;
                propertyIdMap.put(origField.getPropertyId(), origField.getName());
            }
            return propertyIdMap;
        }
        return null;
    }
}
