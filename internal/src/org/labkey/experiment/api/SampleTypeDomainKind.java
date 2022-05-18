/*
 * Copyright (c) 2019 LabKey Corporation
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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NameExpressionValidationResult;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UpdateableTableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExperimentUrls;
import org.labkey.api.exp.api.SampleTypeDomainKindProperties;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpSampleTypeTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.inventory.InventoryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DesignSampleTypePermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.Tuple3;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.data.xml.domainTemplate.DomainTemplateType;
import org.labkey.data.xml.domainTemplate.SampleSetTemplateType;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SampleTypeDomainKind extends AbstractDomainKind<SampleTypeDomainKindProperties>
{
    private static final Logger logger;
    public static final String NAME = "SampleSet";
    public static final String PROVISIONED_SCHEMA_NAME = "expsampleset";


    private static final Set<PropertyStorageSpec> BASE_PROPERTIES;
    private static final Set<PropertyStorageSpec.Index> INDEXES;
    private static final Set<String> RESERVED_NAMES;
    private static final Set<PropertyStorageSpec.ForeignKey> FOREIGN_KEYS;

    static {
        BASE_PROPERTIES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec("genId", JdbcType.INTEGER),
                new PropertyStorageSpec("lsid", JdbcType.VARCHAR, 300).setNullable(false),
                new PropertyStorageSpec("name", JdbcType.VARCHAR, 200),
                new PropertyStorageSpec("materialSourceId", JdbcType.INTEGER)
        )));

        RESERVED_NAMES = BASE_PROPERTIES.stream().map(PropertyStorageSpec::getName).collect(Collectors.toSet());
        RESERVED_NAMES.addAll(Arrays.stream(ExpSampleTypeTable.Column.values()).map(ExpSampleTypeTable.Column::name).collect(Collectors.toList()));
        RESERVED_NAMES.add("CpasType");
        RESERVED_NAMES.add("IsAliquot");
        RESERVED_NAMES.add("AliquotedFrom");
        RESERVED_NAMES.add("AliquotedFromLSID");
        RESERVED_NAMES.add("RootMaterialLSID");
        RESERVED_NAMES.add("RecomputeRollup");
        RESERVED_NAMES.add("AliquotTotalVolume");
        RESERVED_NAMES.add("AliquotCount");
        RESERVED_NAMES.add("AliquotVolume");
        RESERVED_NAMES.add("AliquotUnit");
        RESERVED_NAMES.add("Container");
        RESERVED_NAMES.add(ExpMaterialTable.Column.SampleState.name());
        RESERVED_NAMES.addAll(InventoryService.INVENTORY_STATUS_COLUMN_NAMES);

        FOREIGN_KEYS = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                // NOTE: We join to exp.material using LSID instead of rowid for insert performance -- we will generate
                // the LSID once on the server and insert into exp.object, exp.material, and the provisioned table at the same time.
                new PropertyStorageSpec.ForeignKey("lsid", "exp", "Material", "LSID", null, false)
        )));

        INDEXES = Collections.unmodifiableSet(Sets.newLinkedHashSet(Arrays.asList(
                new PropertyStorageSpec.Index(true, "lsid")
        )));

        logger = LogManager.getLogger(SampleTypeDomainKind.class);
    }

    public SampleTypeDomainKind()
    {
    }

    @Override
    public String getKindName()
    {
        return NAME;
    }

    @Override
    public Class<? extends SampleTypeDomainKindProperties> getTypeClass()
    {
        return SampleTypeDomainKindProperties.class;
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
        return DbSchema.get(PROVISIONED_SCHEMA_NAME, DbSchemaType.Provisioned).getScope();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        String prefix = lsid.getNamespacePrefix();
        if ("SampleSet".equals(prefix) || "SampleSource".equals(prefix) || "SampleType".equals(prefix))
            return Priority.MEDIUM;
        return null;
    }

    @Override
    public String generateDomainURI(String schemaName, String queryName, Container container, User user)
    {
        return ExperimentService.get().generateLSID(container, ExpSampleType.class, queryName);
    }

    private ExpSampleType getSampleType(Domain domain)
    {
        return SampleTypeService.get().getSampleType(domain.getTypeURI());
    }

    @Override
    public boolean allowFileLinkProperties()
    {
        return true;
    }

    @Override
    public boolean allowTimepointProperties()
    {
        return true;
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ExpSampleType st = getSampleType(domain);
        if (st == null)
            return null;

        return st.detailsURL();
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return PageFlowUtil.urlProvider(ExperimentUrls.class).getCreateSampleTypeURL(container);
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ExpSampleType st = getSampleType(domain);
        if (st == null)
            return null;

        return st.urlEditDefinition(containerUser);
    }

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        return BASE_PROPERTIES;
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain, User user)
    {
        Set<String> reserved = new CaseInsensitiveHashSet(RESERVED_NAMES);

        if (domain == null)
            return reserved;

        ExpSampleType st = getSampleType(domain);
        if (st == null)
            return reserved;

        try
        {
            Map<String, String> aliases = st.getImportAliasMap();
            reserved.addAll(aliases.keySet());
        }
        catch (IOException e)
        {
            logger.error(String.format("Failed to parse SampleType parent aliases for [%1$s]", st.getRowId()), e);
        }
        return reserved;
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


    @Override
    public String getTypeLabel(Domain domain)
    {
        ExpSampleType st = getSampleType(domain);
        if (null == st)
            return "Sample Type '" + domain.getName() + "'";
        return st.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        SQLFragment ret = new SQLFragment("SELECT exp.object.objectid FROM exp.object INNER JOIN exp.material ON exp.object.objecturi = exp.material.lsid WHERE exp.material.cpastype = ?");
        ret.add(domain.getTypeURI());
        return ret;
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        ExpSampleType st = getSampleType(domain);
        if (st == null)
            return false;

        return domain.getContainer().hasPermission(user, DesignSampleTypePermission.class);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, DesignSampleTypePermission.class);
    }

    @Override
    public boolean canDeleteDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignSampleTypePermission.class);
    }

    @Override
    public DefaultValueType getDefaultDefaultType(Domain domain)
    {
        return null;
    }

    @Override
    public DefaultValueType[] getDefaultValueOptions(Domain domain)
    {
        return new DefaultValueType[0];
    }

    @Override
    @NotNull
    public ValidationException updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, @NotNull GWTDomain<? extends GWTPropertyDescriptor> update,
                                            @Nullable SampleTypeDomainKindProperties options, Container container, User user, boolean includeWarnings)
    {
        return SampleTypeService.get().updateSampleType(original, update, options, container, user, includeWarnings);
    }

    @Override
    public NameExpressionValidationResult validateNameExpressions(SampleTypeDomainKindProperties options, GWTDomain domainDesign, Container container)
    {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> previewNames = new ArrayList<>();
        if (StringUtils.isNotBlank(options.getNameExpression()))
        {
            NameExpressionValidationResult results = NameGenerator.getValidationMessages(domainDesign.getName(), options.getNameExpression(), domainDesign.getFields(), options.getImportAliases(), container);
            if (results.errors() != null && !results.errors().isEmpty())
                results.errors().forEach(error -> errors.add("Name Pattern error: " + error));
            if (results.warnings() != null && !results.warnings().isEmpty())
                results.warnings().forEach(error -> warnings.add("Name Pattern warning: " + error));
            if (results.previews() != null)
                previewNames.addAll(results.previews());
            else
                previewNames.add(null);
        }
        else
            previewNames.add(null);

        if (StringUtils.isNotBlank(options.getAliquotNameExpression()))
        {
            NameExpressionValidationResult results = NameGenerator.getValidationMessages(domainDesign.getName(), options.getAliquotNameExpression(), domainDesign.getFields(), options.getImportAliases(), container);
            if (results.errors() != null && !results.errors().isEmpty())
                results.errors().forEach(error -> errors.add("Aliquot Name Pattern error: " + error));
            if (results.warnings() != null && !results.warnings().isEmpty())
                results.warnings().forEach(error -> warnings.add("Aliquot Name Pattern warning: " + error));
            if (results.previews() != null)
                previewNames.addAll(results.previews());
            else
                previewNames.add(null);
        }
        return new NameExpressionValidationResult(errors, warnings, previewNames);
    }

    private @NotNull ValidationException getNamePatternValidationResult(String currentSampleType, String patten, List<? extends GWTPropertyDescriptor> properties, @Nullable Map<String, String> importAliases, Container container)
    {
        ValidationException errors = new ValidationException();
        NameExpressionValidationResult results = NameGenerator.getValidationMessages(currentSampleType, patten, properties, importAliases, container);
        if (results.errors() != null && !results.errors().isEmpty())
            results.errors().forEach(error -> errors.addError(new SimpleValidationError(error)));
        return errors;
    }

    @Override
    public void validateOptions(Container container, User user, SampleTypeDomainKindProperties options, String name, Domain domain, GWTDomain updatedDomainDesign)
    {
        super.validateOptions(container, user, options, name, domain, updatedDomainDesign);

        // verify and NameExpression values
        TableInfo materialSourceTI = ExperimentService.get().getTinfoSampleType();

        boolean isUpdate = domain != null;
        if (!isUpdate)
        {
            if (name == null)
            {
                throw new IllegalArgumentException("You must supply a name for the sample type.");
            }
            else
            {
                ExpSampleType st = SampleTypeService.get().getSampleType(container, user, name);
                if (st != null)
                    throw new IllegalArgumentException("A Sample Type with that name already exists.");
            }
        }

        // verify the length of the Name
        int nameMax = materialSourceTI.getColumn("Name").getScale();
        if (name != null && name.length() >= nameMax)
            throw new IllegalArgumentException("Value for Name field may not exceed " + nameMax + " characters.");

        if (options == null)
        {
            return;
        }

        int nameExpMax = materialSourceTI.getColumn("NameExpression").getScale();
        if (StringUtils.isNotBlank(options.getNameExpression()) && options.getNameExpression().length() > nameExpMax)
            throw new IllegalArgumentException("Value for Name Expression field may not exceed " + nameExpMax + " characters.");

        int aliquotNameExpMax = materialSourceTI.getColumn("AliquotNameExpression").getScale();
        if (StringUtils.isNotBlank(options.getAliquotNameExpression()) && options.getAliquotNameExpression().length() > aliquotNameExpMax)
            throw new IllegalArgumentException("Value for Aliquot Naming Patten field may not exceed " + nameExpMax + " characters.");

        int labelColorMax = materialSourceTI.getColumn("LabelColor").getScale();
        if (StringUtils.isNotBlank(options.getLabelColor()) && options.getLabelColor().length() > labelColorMax)
            throw new IllegalArgumentException("Value for Label Color field may not exceed " + labelColorMax + " characters.");

        int metricUnitMax = materialSourceTI.getColumn("MetricUnit").getScale();
        if (StringUtils.isNotBlank(options.getMetricUnit()) && options.getMetricUnit().length() > metricUnitMax)
            throw new IllegalArgumentException("Value for Metric Unit field may not exceed " + metricUnitMax + " characters.");

        int categoryMax = materialSourceTI.getColumn("Category").getScale();
        if (StringUtils.isNotBlank(options.getCategory()) && options.getCategory().length() > categoryMax)
            throw new IllegalArgumentException("Value for Category field may not exceed " + categoryMax + " characters.");

        Map<String, String> aliasMap = options.getImportAliases();
        if (aliasMap == null || aliasMap.size() == 0)
            return;

        SampleTypeService ss = SampleTypeService.get();
        ExpSampleType sampleType = options.getRowId() >= 0 ? ss.getSampleType(options.getRowId()) : null;
        Domain stDomain = sampleType != null ? sampleType.getDomain() : null;
        Set<String> reservedNames = new CaseInsensitiveHashSet(this.getReservedPropertyNames(stDomain, user));
        Set<String> existingAliases = new CaseInsensitiveHashSet();
        Set<String> dupes = new CaseInsensitiveHashSet();

        try
        {
            if (sampleType != null)
                existingAliases = new CaseInsensitiveHashSet(sampleType.getImportAliasMap().keySet());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }


        final Set<String> finalExistingAliases = existingAliases;
        aliasMap.forEach((key, value) -> {
            String trimmedKey = StringUtils.trimToNull(key);
            String trimmedValue = StringUtils.trimToNull(value);
            if (trimmedKey == null)
                throw new IllegalArgumentException("Import alias heading cannot be blank");

            if (trimmedValue == null)
            {
                throw new IllegalArgumentException("You must specify a valid parent type for the import alias.");
            }

            if (reservedNames.contains(trimmedKey))
            {
                throw new IllegalArgumentException(String.format("Parent alias header is reserved: %1$s", trimmedKey));
            }

            if (updatedDomainDesign != null && !finalExistingAliases.contains(trimmedKey) && updatedDomainDesign.getFieldByName(trimmedKey) != null)
            {
                throw new IllegalArgumentException(String.format("An existing sample type property conflicts with parent alias header: %1$s", trimmedKey));
            }

            if (!dupes.add(trimmedKey))
            {
                throw new IllegalArgumentException(String.format("Duplicate parent alias header found: %1$s", trimmedKey));
            }

            //Check if parent alias has correct format MaterialInput/<name> or NEW_SAMPLE_TYPE_ALIAS_VALUE
            if (!ss.parentAliasHasCorrectFormat(trimmedValue))
                throw new IllegalArgumentException(String.format("Invalid parent alias header: %1$s", trimmedValue));
        });

        List<? extends GWTPropertyDescriptor> properties = updatedDomainDesign.getFields();

        String errorMsg = "";
        if (StringUtils.isNotBlank(options.getNameExpression()))
        {
            ValidationException errors = getNamePatternValidationResult(name, options.getNameExpression(), properties, aliasMap, container);
            if (errors.hasErrors())
                errorMsg += "Invalid Name Expression:" + errors.getMessage();
        }

        if (StringUtils.isNotBlank(options.getAliquotNameExpression()))
        {
            ValidationException errors = getNamePatternValidationResult(name, options.getAliquotNameExpression(), properties, aliasMap, container);
            if (errors.hasErrors())
                errorMsg += "Invalid Aliquot Name Expression:" + errors.getMessage();
        }

        if (StringUtils.isNotBlank(errorMsg))
            throw new IllegalArgumentException(errorMsg);
    }

    @Override
    public Domain createDomain(GWTDomain domain, @Nullable SampleTypeDomainKindProperties arguments, Container container, User user, @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        if (name == null)
            throw new IllegalArgumentException("SampleSet name required");

        String description = domain.getDescription();
        List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();
        List<GWTIndex> indices = (List<GWTIndex>)domain.getIndices();

        int idCol1 = -1;
        int idCol2 = -1;
        int idCol3 = -1;
        int parentCol = -1;
        String nameExpression = null;
        String aliquotNameExpression = null;
        String labelColor = null;
        String metricUnit = null;
        Container autoLinkTargetContainer = null;
        String autoLinkCategory = null;
        String category = null;
        Map<String, String> aliases = null;

        if (arguments != null)
        {
            //These are outdated but some clients still use them, or have existing sample types that do.
            List<Integer> idCols = (arguments.getIdCols() != null) ? arguments.getIdCols() : Collections.emptyList();
            idCol1 = idCols.size() > 0 ? idCols.get(0) : -1;
            idCol2 = idCols.size() > 1 ? idCols.get(1) : -1;
            idCol3 = idCols.size() > 2 ? idCols.get(2) : -1;
            parentCol = arguments.getParentCol() != null ? arguments.getParentCol() : -1;

            nameExpression = StringUtils.trimToNull(StringUtilsLabKey.replaceBadCharacters(arguments.getNameExpression()));
            aliquotNameExpression = StringUtils.trimToNull(StringUtilsLabKey.replaceBadCharacters(arguments.getAliquotNameExpression()));
            labelColor = StringUtils.trimToNull(arguments.getLabelColor());
            metricUnit = StringUtils.trimToNull(arguments.getMetricUnit());
            if (!StringUtils.isBlank(arguments.getAutoLinkTargetContainerId()))
                autoLinkTargetContainer = ContainerManager.getForId(arguments.getAutoLinkTargetContainerId());
            autoLinkCategory = StringUtils.trimToNull(arguments.getAutoLinkCategory());
            category = StringUtils.trimToNull(arguments.getCategory());
            aliases = arguments.getImportAliases();
        }
        ExpSampleType st;
        try
        {
            st = SampleTypeService.get().createSampleType(container, user, name, description, properties, indices, idCol1, idCol2, idCol3, parentCol, nameExpression, aliquotNameExpression, templateInfo, aliases, labelColor, metricUnit, autoLinkTargetContainer, autoLinkCategory, category);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeException(e);
        }
        return st.getDomain();
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        ExpSampleType st = SampleTypeService.get().getSampleType(domain.getTypeURI());
        if (st == null)
            throw new NotFoundException("Sample Type not found: " + domain);

        st.delete(user);
    }

    @Override
    public TableInfo getTableInfo(User user, Container container, String name, @Nullable ContainerFilter cf)
    {
        UserSchema schema = new SamplesSchema(user, container);
        return schema.getTable(name, cf);
    }

    @Override
    public void invalidate(Domain domain)
    {
        super.invalidate(domain);

        ExpSampleType st = SampleTypeService.get().getSampleType(domain.getTypeURI());
        if (st != null)
            SampleTypeService.get().indexSampleType(st);
    }

    @Override
    public boolean matchesTemplateXML(String templateName, DomainTemplateType template, List<GWTPropertyDescriptor> properties)
    {
        return template instanceof SampleSetTemplateType;
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

    @Override
    public SampleTypeDomainKindProperties getDomainKindProperties(GWTDomain domain, Container container, User user)
    {
        ExpSampleType sampleType = domain != null ? SampleTypeService.get().getSampleType(domain.getDomainURI()) : null;
        return new SampleTypeDomainKindProperties(sampleType);
    }

    @Override
    public boolean hasNullValues(Domain domain, DomainProperty prop)
    {
        SQLFragment allRowsSQL = new SQLFragment();
        SQLFragment nonBlankRowsSQL = new SQLFragment();

        if (getTotalAndNonBlankSql(domain, prop, allRowsSQL, nonBlankRowsSQL))
        {
            // Issue 43754: Don't include aliquot rows in the null value check (see ExpMaterialTableImpl.createColumn for IsAliquot)
            String table = domain.getStorageTableName();
            SQLFragment nonAliquotRowsSQL = new SQLFragment("SELECT * FROM exp.material WHERE LSID IN (")
                    .append("SELECT LSID FROM " + getStorageSchemaName() + "." + table)
                    .append(") AND RootMaterialLSID IS NULL");

            long totalRows = new SqlSelector(ExperimentService.get().getSchema(), nonAliquotRowsSQL).getRowCount();
            long nonBlankRows = new SqlSelector(ExperimentService.get().getSchema(), nonBlankRowsSQL).getRowCount();
            return totalRows != nonBlankRows;
        }
        else
        {
            return false;
        }
    }
}
