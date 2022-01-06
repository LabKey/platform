/*
 * Copyright (c) 2015-2018 LabKey Corporation
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
package org.labkey.experiment.api;

import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NameExpressionValidationResult;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.DataClassDomainKindProperties;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeDomainKindProperties;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.query.DataClassUserSchema;
import org.labkey.api.exp.query.ExpDataClassDataTable;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DesignDataClassPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.data.xml.domainTemplate.DataClassTemplateType;
import org.labkey.data.xml.domainTemplate.DomainTemplateType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DomainKind implementation for data classes, allowing customizable fields to be attached to the based set of columns.
 * User: kevink
 * Date: 9/15/15
 */
public class DataClassDomainKind extends AbstractDomainKind<DataClassDomainKindProperties>
{
    public static final String NAME = "DataClass";
    public static final String PROVISIONED_SCHEMA_NAME = "expdataclass";

    private static final Set<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> INDEXES;
    private static final Set<String> RESERVED_NAMES;
    private static final Set<PropertyStorageSpec.ForeignKey> FOREIGN_KEYS;

    static {
        BASE_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec("genId", JdbcType.INTEGER),
                new PropertyStorageSpec("lsid", JdbcType.VARCHAR, 300).setNullable(false),
                new PropertyStorageSpec("name", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("classid", JdbcType.INTEGER)
        )));


        RESERVED_NAMES = new CaseInsensitiveHashSet(BASE_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet()));
        RESERVED_NAMES.addAll(Arrays.asList(ExpDataClassDataTable.Column.values()).stream().map(ExpDataClassDataTable.Column::name).collect(Collectors.toList()));
        RESERVED_NAMES.add("Container");

        FOREIGN_KEYS = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                // NOTE: We join to exp.data using LSID instead of rowid for insert performance -- we will generate
                // the LSID once on the server and insert into exp.object, exp.data, and the provisioned table at the same time.
                new PropertyStorageSpec.ForeignKey("lsid", "exp", "Data", "LSID", null, false)
        )));

        INDEXES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(new PropertyStorageSpec.Index(true, "lsid"),
                new PropertyStorageSpec.Index(true, "name", "classid"))));
    }

    public DataClassDomainKind()
    {
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Class<DataClassDomainKindProperties> getTypeClass()
    {
        return DataClassDomainKindProperties.class;
    }

    @Override
    public Map<String, Object> processArguments(Container container, User user, Map<String, Object> arguments)
    {
        Map<String, Object> updatedArguments = new HashMap<>(arguments);

        // if "sampleSet" is the Name string, look it up and switch the argument map to use the RowId
        if (arguments.containsKey("sampleSet") && !StringUtils.isNumeric(arguments.get("sampleSet").toString()))
        {
            ExpSampleType sampleType = SampleTypeService.get().getSampleType(container, user, (String)arguments.get("sampleSet"));
            if (sampleType != null)
                updatedArguments.put("sampleSet", sampleType.getRowId());
        }

        return updatedArguments;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        return null;
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ExpDataClass dataClass = getDataClass(domain);
        if (dataClass == null)
            return null;

        return dataClass.detailsURL();
    }

    @Override
    public DefaultValueType[] getDefaultValueOptions(Domain domain)
    {
        return new DefaultValueType[0];
    }

    @Override
    public DefaultValueType getDefaultDefaultType(Domain domain)
    {
        return null;
    }

    private ExpDataClass getDataClass(Domain domain)
    {
        return ExperimentServiceImpl.get().getDataClass(domain.getTypeURI());
    }

    @Override
    public boolean allowAttachmentProperties()
    {
        return true;
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getCreateDataClassURL(container);
    }

    @Nullable
    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ExpDataClass dataClass = getDataClass(domain);
        if (dataClass == null)
            return null;

        return dataClass.urlEditDefinition(containerUser);
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return BASE_PROPERTIES;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return RESERVED_NAMES;
    }

    @Override
    public Set<String> getReservedPropertyNamePrefixes()
    {
        return LINEAGE_FIELD_NAME_PREFIXES;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        return INDEXES;
    }

    @Override
    public Set<PropertyStorageSpec.ForeignKey> getPropertyForeignKeys(Container container)
    {
        return FOREIGN_KEYS;
    }

    @Nullable
    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getKindName().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public String getStorageSchemaName()
    {
        return PROVISIONED_SCHEMA_NAME;
    }

    public DbSchema getSchema()
    {
        return DbSchema.get(PROVISIONED_SCHEMA_NAME, DbSchemaType.Provisioned);
    }

    @Override
    public DbScope getScope()
    {
        return getSchema().getScope();
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, DesignDataClassPermission.class);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignDataClassPermission.class);
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignDataClassPermission.class);
    }

    private @NotNull ValidationException getNamePatternValidationResult(String patten, List<? extends GWTPropertyDescriptor> properties, @Nullable Map<String, String> importAliases, Container container)
    {
        ValidationException errors = new ValidationException();
        NameExpressionValidationResult results = NameGenerator.getValidationMessages(patten, properties, importAliases, container);
        if (results.errors() != null && !results.errors().isEmpty())
            results.errors().forEach(error -> errors.addError(new SimpleValidationError(error)));
        return errors;
    }

    @Override
    public NameExpressionValidationResult validateNameExpressions(DataClassDomainKindProperties options, GWTDomain domainDesign, Container container)
    {
        if (StringUtils.isNotBlank(options.getNameExpression()))
        {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            List<String> previewNames = new ArrayList<>();

            NameExpressionValidationResult results = NameGenerator.getValidationMessages(options.getNameExpression(), domainDesign.getFields(), null, container);
            if (results.errors() != null && !results.errors().isEmpty())
                results.errors().forEach(error -> errors.add("Name Pattern error: " + error));
            if (results.warnings() != null && !results.warnings().isEmpty())
                results.warnings().forEach(error -> warnings.add("Name Pattern warning: " + error));
            if (results.previews() != null)
                previewNames.addAll(results.previews());

            return new NameExpressionValidationResult(errors, warnings, previewNames);
        }
        return null;
    }

    @Override
    public void validateOptions(Container container, User user, DataClassDomainKindProperties options, String name, Domain domain, GWTDomain updatedDomainDesign)
    {
        super.validateOptions(container, user, options, name, domain, updatedDomainDesign);
        if (StringUtils.isNotBlank(options.getNameExpression()))
        {
            ValidationException errors = getNamePatternValidationResult(options.getNameExpression(), updatedDomainDesign.getFields(), null, container);
            if (errors.hasErrors())
                throw new IllegalArgumentException(errors.getMessage());
        }

    }

    @Override
    public Domain createDomain(GWTDomain domain, DataClassDomainKindProperties options, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();
        List<GWTIndex> indices = (List<GWTIndex>)domain.getIndices();

        try
        {
            ExpDataClass dataClass = ExperimentService.get().createDataClass(container, user, name, options, properties, indices, templateInfo);
            return dataClass.getDomain();
        }
        catch (ExperimentException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public @NotNull ValidationException updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update,
                                                     @Nullable DataClassDomainKindProperties options, Container container, User user, boolean includeWarnings)
    {
        ExpDataClass dc = ExperimentService.get().getDataClass(original.getDomainURI());
        return ExperimentService.get().updateDataClass(container, user, dc, options, original, update);
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        ExpDataClass dc = getDataClass(domain);
        if (dc == null)
            throw new NotFoundException("DataClass not found: " + domain.getTypeURI());

        dc.delete(user);
    }

    @Override
    public TableInfo getTableInfo(User user, Container container, String name, @Nullable ContainerFilter cf)
    {
        UserSchema schema = new DataClassUserSchema(container, user);
        return schema.getTable(name, cf);
    }

    @Override
    public void invalidate(Domain domain)
    {
        super.invalidate(domain);

        ExpDataClass dc = getDataClass(domain);
        if (dc != null && dc.getDomain() != null && dc.getDomain().getStorageTableName() != null)
            ExperimentServiceImpl.get().indexDataClass(dc);
    }

    @Override
    public boolean matchesTemplateXML(String templateName, DomainTemplateType template, List<GWTPropertyDescriptor> properties)
    {
        return template instanceof DataClassTemplateType;
    }


    @Override
    public String getObjectUriColumnName()
    {
        return OBJECT_URI_COLUMN_NAME;
    }

    @Override
    public UpdateableTableInfo.ObjectUriType getObjectUriColumn()
    {
        return UpdateableTableInfo.ObjectUriType.schemaColumn;
    }

    @Nullable
    @Override
    public DataClassDomainKindProperties getDomainKindProperties(GWTDomain domain, Container container, User user)
    {
        ExpDataClass dc = domain != null ? ExperimentService.get().getDataClass(domain.getDomainURI()) : null;
        return new DataClassDomainKindProperties(dc);
    }
}
