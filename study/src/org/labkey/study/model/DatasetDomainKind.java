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

package org.labkey.study.model;

import org.apache.commons.beanutils.BeanUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.di.DataIntegrationService;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Dataset.KeyManagementType;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.StudyPublishManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.query.DatasetFactory;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import static org.labkey.study.model.DatasetDomainKindProperties.TIME_KEY_FIELD_KEY;

/**
 * User: matthewb
 * Date: May 4, 2007
 * Time: 1:01:43 PM
 */
public abstract class DatasetDomainKind extends AbstractDomainKind<DatasetDomainKindProperties>
{
    public final static String LSID_PREFIX = "StudyDataset";

    public final static String CONTAINER = "container";
    public final static String DATE = "date";
    public final static String PARTICIPANTID = "participantid";
    public final static String LSID = "lsid";
    public final static String DSROWID = "dsrowid";
    public final static String SEQUENCENUM = "sequencenum";
    public final static String SOURCELSID = "sourcelsid";
    public final static String _KEY = "_key";
    public final static String QCSTATE = "qcstate";
    public final static String PARTICIPANTSEQUENCENUM = "participantsequencenum";

    public static final String CREATED = "created";
    public static final String MODIFIED = "modified";
    public static final String CREATED_BY = "createdBy";
    public static final String MODIFIED_BY = "modifiedBy";


    /*
     * the columns common to all datasets
     */
    private final static Set<PropertyStorageSpec> BASE_PROPERTIES;
    private final static Set<PropertyStorageSpec> DATASPACE_BASE_PROPERTIES;
    protected final static Set<PropertyStorageSpec.Index> PROPERTY_INDICES;
    private final static Set<PropertyStorageSpec.Index> DATASPACE_PROPERTY_INDICES;

    static
    {
        DATASPACE_BASE_PROPERTIES = new HashSet<>(Arrays.asList(
            new PropertyStorageSpec(DSROWID, JdbcType.BIGINT, 0, PropertyStorageSpec.Special.PrimaryKeyNonClustered, false, true, null),
            new PropertyStorageSpec(CONTAINER, JdbcType.GUID).setNullable(false),
            new PropertyStorageSpec(PARTICIPANTID, JdbcType.VARCHAR, 32).setNullable(false),
            new PropertyStorageSpec(LSID, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(SEQUENCENUM, JdbcType.DECIMAL),
            new PropertyStorageSpec(SOURCELSID, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(_KEY, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(QCSTATE, JdbcType.INTEGER),
            new PropertyStorageSpec(PARTICIPANTSEQUENCENUM, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(CREATED, JdbcType.TIMESTAMP),
            new PropertyStorageSpec(MODIFIED, JdbcType.TIMESTAMP),
            new PropertyStorageSpec(CREATED_BY, JdbcType.INTEGER),
            new PropertyStorageSpec(MODIFIED_BY, JdbcType.INTEGER),
            new PropertyStorageSpec(DATE, JdbcType.TIMESTAMP),
            new PropertyStorageSpec(DataIntegrationService.Columns.TransformImportHash.getColumnName(), JdbcType.VARCHAR, 256)
        ));


        BASE_PROPERTIES = new HashSet<>(Arrays.asList(
            new PropertyStorageSpec(DSROWID, JdbcType.BIGINT, 0, PropertyStorageSpec.Special.PrimaryKeyNonClustered, false, true, null),
            new PropertyStorageSpec(PARTICIPANTID, JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(LSID, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(SEQUENCENUM, JdbcType.DECIMAL),
            new PropertyStorageSpec(SOURCELSID, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(_KEY, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(QCSTATE, JdbcType.INTEGER),
            new PropertyStorageSpec(PARTICIPANTSEQUENCENUM, JdbcType.VARCHAR, 200),
            new PropertyStorageSpec(CREATED, JdbcType.TIMESTAMP),
            new PropertyStorageSpec(MODIFIED, JdbcType.TIMESTAMP),
            new PropertyStorageSpec(CREATED_BY, JdbcType.INTEGER),
            new PropertyStorageSpec(MODIFIED_BY, JdbcType.INTEGER),
            new PropertyStorageSpec(DATE, JdbcType.TIMESTAMP),
            new PropertyStorageSpec(DataIntegrationService.Columns.TransformImportHash.getColumnName(), JdbcType.VARCHAR, 256)
        ));

        DATASPACE_PROPERTY_INDICES = new HashSet<>(Arrays.asList(
            new PropertyStorageSpec.Index(false, true, CONTAINER, PARTICIPANTID, DATE),
            new PropertyStorageSpec.Index(false, CONTAINER, QCSTATE),
            new PropertyStorageSpec.Index(true, CONTAINER, PARTICIPANTID, SEQUENCENUM, _KEY),
            new PropertyStorageSpec.Index(true, LSID),
            new PropertyStorageSpec.Index(false, DATE)
        ));

        PROPERTY_INDICES = new HashSet<>(Arrays.asList(
            new PropertyStorageSpec.Index(false, true, PARTICIPANTID, DATE),
            new PropertyStorageSpec.Index(false, QCSTATE),
            new PropertyStorageSpec.Index(true, PARTICIPANTID, SEQUENCENUM, _KEY),
            new PropertyStorageSpec.Index(true, LSID),
            new PropertyStorageSpec.Index(false, DATE)
        ));
    }


    protected DatasetDomainKind()
    {
    }

    @Override
    abstract public String getKindName();

    @Override
    public Class<DatasetDomainKindProperties> getTypeClass()
    {
        return DatasetDomainKindProperties.class;
    }

    @Override
    public String getTypeLabel(Domain domain)
    {
        DatasetDefinition def = getDatasetDefinition(domain.getTypeURI());
        if (null == def)
            return domain.getName();
        return def.getName();
    }

    @Override
    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        DatasetDefinition def = getDatasetDefinition(domain.getTypeURI());
        if (null == def)
            return new SQLFragment("NULL");
        TableInfo ti = def.getStorageTableInfo();
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT O.ObjectId FROM ").append(ti.getSelectName()).append(" SD JOIN exp.Object O ON SD.Lsid=O.ObjectURI WHERE O.container=?");
        sql.add(def.getContainer());
        return sql;
    }

    // Issue 16526:  nobody should call this overload of generateDomainURI for DatasetDomainKind.  Instead
    // use the overload below with a unique id (the dataset's entityId).  Assert is here to track down
    // any callers.
    // Lsid.toString() encodes incorrectly TODO: fix
    @Override
    public String generateDomainURI(String schemaName, String name, Container container, User user)
    {
        assert false;
        return null;
    }

    // Issue 16526: This specific generateDomainURI takes an id to uniquify the dataset.
    public static String generateDomainURI(String name, String id, Container container)
    {
        String objectid = name == null ? "" : name;
        if (null != objectid && null != id)
        {
            // normalize the object id
            objectid += "-" + id.toLowerCase();
        }
        return (new Lsid(LSID_PREFIX, "Folder-" + container.getRowId(), objectid)).toString();
    }

    @Override
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        Dataset def = getDatasetDefinition(domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.DatasetReportAction.class, containerUser.getContainer());
        url.addParameter("datasetId", "" + def.getDatasetId());
        return url;
    }

    @Override
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        Dataset def = getDatasetDefinition(domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.EditTypeAction.class, containerUser.getContainer());
        url.addParameter("datasetId", "" + def.getDatasetId());
        return url;
    }

    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        return new ActionURL(StudyController.DefineDatasetTypeAction.class, container);
    }

    @Override
    public boolean allowFileLinkProperties()
    {
        return true;
    }

    @Override
    public boolean showDefaultValueSettings()
    {
        return true;
    }

    DatasetDefinition getDatasetDefinition(String domainURI)
    {
        return StudyManager.getInstance().getDatasetDefinition(domainURI);
    }

    // Issue 43898: Add the study subject name column to reserved fields
    protected Set<String> getStudySubjectReservedName(Domain domain)
    {
        HashSet<String> fields = new HashSet<>();
        if (null != domain)
        {
            Study study = StudyManager.getInstance().getStudy(domain.getContainer());
            if (null != study)
            {
                String participantIdField = study.getSubjectColumnName();
                fields.add(participantIdField);
            }
        }
        return Collections.unmodifiableSet(fields);
    }

    @Override
    public abstract Set<String> getReservedPropertyNames(Domain domain, User user);

    @Override
    public Set<PropertyStorageSpec> getBaseProperties(Domain domain)
    {
        Set<PropertyStorageSpec> specs;
        Study study = StudyManager.getInstance().getStudy(domain.getContainer());

        if(study == null || study.isDataspaceStudy())
        {
            specs = new HashSet<>(DATASPACE_BASE_PROPERTIES);
        }
        else
        {
            specs = new HashSet<>(BASE_PROPERTIES);
        }
        specs.addAll(super.getBaseProperties(domain));
        return specs;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices(Domain domain)
    {
        Study study = StudyManager.getInstance().getStudy(domain.getContainer());

        if(study == null || study.isDataspaceStudy())
            return DATASPACE_PROPERTY_INDICES;

        return PROPERTY_INDICES;
    }

    @Override
    public DbScope getScope()
    {
        return StudySchema.getInstance().getSchema().getScope();
    }

    @Override
    public String getStorageSchemaName()
    {
        return StudySchema.getInstance().getDatasetSchemaName();
    }

    @Override
    public DbSchemaType getSchemaType()
    {
        return DbSchemaType.Provisioned;
    }

    @Override
    public void invalidate(Domain domain)
    {
        super.invalidate(domain);
        DatasetDefinition def = getDatasetDefinition(domain.getTypeURI());
        if (null != def)
        {
            StudyManager.getInstance().uncache(def);
        }
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    @Override
    public Map<String, Object> processArguments(Container container, User user, Map<String, Object> arguments)
    {
        Map<String, Object> updatedArguments = new HashMap<>(arguments);

        // For backwards compatibility, map "demographics" => "demographicData"
        if (arguments.containsKey("demographics"))
        {
            updatedArguments.put("demographicData", arguments.get("demographics"));
            updatedArguments.remove("demographics");
        }

        // For backwards compatibility, map "categoryId" and "categoryName" => "category"
        if (arguments.containsKey("categoryId"))
        {
            if (arguments.containsKey("categoryName"))
                throw new IllegalArgumentException("Category ID and category name cannot both be specified.");

            ViewCategory category = ViewCategoryManager.getInstance().getCategory(container, (Integer)arguments.get("categoryId"));
            if (category == null)
                throw new IllegalArgumentException("Unable to find a category with the ID : " + arguments.get("categoryId") + " in this folder.");

            updatedArguments.put("category", category.getLabel());
            updatedArguments.remove("categoryId");
        }
        else if (arguments.containsKey("categoryName"))
        {
            updatedArguments.put("category", arguments.get("categoryName"));
            updatedArguments.remove("categoryName");
        }

        return updatedArguments;
    }

    @Nullable
    @Override
    public DatasetDomainKindProperties getDomainKindProperties(GWTDomain domain, Container container, User user)
    {
        Dataset ds = domain != null ? getDatasetDefinition(domain.getDomainURI()) : null;
        return DatasetManager.get().getDatasetDomainKindProperties(container, ds != null ? ds.getDatasetId() : null);
    }

    @Override
    public Domain createDomain(GWTDomain domain, DatasetDomainKindProperties arguments, Container container, User user,
                               @Nullable TemplateInfo templateInfo)
    {
        arguments.setName(domain.getName());
        String name = arguments.getName();
        String description = arguments.getDescription() != null ? arguments.getDescription() : domain.getDescription();
        String label = (arguments.getLabel() == null || arguments.getLabel().length() == 0) ? arguments.getName() : arguments.getLabel();
        Integer cohortId = arguments.getCohortId();
        String tag = arguments.getTag();
        Integer datasetId = arguments.getDatasetId();
        String categoryName = arguments.getCategory();
        boolean demographics = arguments.isDemographicData();
        boolean isManagedField = arguments.isKeyPropertyManaged();
        String visitDatePropertyName = arguments.getVisitDatePropertyName();
        boolean useTimeKeyField = arguments.isUseTimeKeyField();
        boolean showByDefault = arguments.isShowByDefault();
        String dataSharing = arguments.getDataSharing();

        // general dataset validation
        validateDatasetProperties(arguments, container, user, domain, null);

        // create-case specific validation
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        TimepointType timepointType = study.getTimepointType();
        if (timepointType.isVisitBased() && getKindName().equals(DateDatasetDomainKind.KIND_NAME))
            throw new IllegalArgumentException("Visit based studies require a visit based dataset domain. Please specify a kind name of : " + VisitDatasetDomainKind.KIND_NAME + ".");
        else if (!timepointType.isVisitBased() && getKindName().equals(VisitDatasetDomainKind.KIND_NAME))
            throw new IllegalArgumentException("Date based studies require a date based dataset domain. Please specify a kind name of : " + DateDatasetDomainKind.KIND_NAME + ".");
        if (timepointType.isVisitBased() && useTimeKeyField)
            throw new IllegalArgumentException("Additional key property cannot be Time (from Date/Time) for visit based studies.");

        // Check for usage of Time as Key Field
        String keyPropertyName = arguments.getKeyPropertyName();
        if (useTimeKeyField)
            keyPropertyName = null;

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            KeyManagementType managementType = KeyManagementType.None;
            if (isManagedField)
            {
                String rangeUri = "";
                for (GWTPropertyDescriptor a : (List<GWTPropertyDescriptor>)domain.getFields())
                {
                    if (keyPropertyName.equalsIgnoreCase(a.getName()))
                    {
                        rangeUri = a.getRangeURI();
                        break;
                    }

                }

                PropertyDescriptor pd = new PropertyDescriptor();
                pd.setRangeURI(rangeUri);
                managementType = KeyManagementType.getManagementTypeFromProp(pd.getPropertyType());
            }

            Integer categoryId = null;
            ViewCategory category;
            if (categoryName != null)
            {
                category = ViewCategoryManager.getInstance().getCategory(container, categoryName);
                if (category != null)
                {
                    categoryId = category.getRowId();
                }
                else
                {
                    String[] parts = ViewCategoryManager.getInstance().decode(categoryName);
                    category = ViewCategoryManager.getInstance().ensureViewCategory(container, user, parts);
                    categoryId = category.getRowId();
                }
            }

            DatasetDefinition def = StudyPublishManager.getInstance().createDataset(user, new DatasetDefinition.Builder(name)
                    .setStudy(study)
                    .setKeyPropertyName(keyPropertyName)
                    .setDatasetId(datasetId)
                    .setDemographicData(demographics)
                    .setCategoryId(categoryId)
                    .setUseTimeKeyField(useTimeKeyField)
                    .setKeyManagementType(managementType)
                    .setShowByDefault(showByDefault)
                    .setLabel(label)
                    .setDescription(description)
                    .setCohortId(cohortId)
                    .setTag(tag)
                    .setVisitDatePropertyName(visitDatePropertyName)
                    .setDataSharing(dataSharing));

            if (def.getDomain() != null)
            {
                List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();
                List<GWTIndex> indices = (List<GWTIndex>)domain.getIndices();

                Domain newDomain = def.getDomain();
                if (newDomain != null)
                {
                    Set<String> reservedNames = getReservedPropertyNames(newDomain, user);
                    Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
                    Set<String> existingProperties = newDomain.getProperties().stream().map(o -> o.getName().toLowerCase()).collect(Collectors.toSet());
                    Map<DomainProperty, Object> defaultValues = new HashMap<>();
                    Set<String> propertyUris = new HashSet<>();

                    for (GWTPropertyDescriptor pd : properties)
                    {
                        if (lowerReservedNames.contains(pd.getName().toLowerCase()) || existingProperties.contains(pd.getName().toLowerCase()))
                        {
                            if (arguments.isStrictFieldValidation())
                                throw new ApiUsageException("Property: " + pd.getName() + " is reserved or exists in the current domain.");
                        }
                        else
                            DomainUtil.addProperty(newDomain, pd, defaultValues, propertyUris, null);
                    }

                    Set<PropertyStorageSpec.Index> propertyIndices = new HashSet<>();
                    for (GWTIndex index : indices)
                    {
                        PropertyStorageSpec.Index propIndex = new PropertyStorageSpec.Index(index.isUnique(), index.getColumnNames());
                        propertyIndices.add(propIndex);
                    }
                    newDomain.setPropertyIndices(propertyIndices);

                    newDomain.save(user);
                }
                else
                    throw new IllegalArgumentException("Failed to create domain for dataset : " + name + ".");
            }

            transaction.commit();
            return study.getDataset(def.getDatasetId()).getDomain();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    private void validateDatasetProperties(DatasetDomainKindProperties datasetProperties, Container container, User user, GWTDomain domain, DatasetDefinition def)
    {
        String name = datasetProperties.getName();
        String label = datasetProperties.getLabel();
        String keyPropertyName = datasetProperties.getKeyPropertyName();
        Integer datasetId = datasetProperties.getDatasetId();
        boolean isManagedField = datasetProperties.isKeyPropertyManaged();
        boolean useTimeKeyField = datasetProperties.isUseTimeKeyField();
        boolean isDemographicData = datasetProperties.isDemographicData();

        if (!container.hasPermission(user, AdminPermission.class))
            throw new IllegalArgumentException("You do not have permissions to edit dataset definitions in this container.");

        Study study = StudyService.get().getStudy(container);
        if (study == null)
            throw new IllegalArgumentException("A study does not exist for this container.");

        // Name related exceptions

        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("Dataset name cannot be empty.");

        if (name.length() > 200)
            throw new IllegalArgumentException("Dataset name must be under 200 characters.");

        // issue 17766: check if dataset or query exist with this name
        if ((!name.equals(domain.getName()) || def == null) && (null != StudyManager.getInstance().getDatasetDefinitionByName(study, name) || null != QueryService.get().getQueryDef(user, container, "study", name)))
            throw new IllegalArgumentException("A Dataset or Query already exists with the name \"" + name +"\".");

        // Label related exceptions

        if ((def == null || !def.getLabel().equals(label)) && null != StudyManager.getInstance().getDatasetDefinitionByLabel(study, label))
            throw new IllegalArgumentException("A Dataset already exists with the label \"" + label +"\".");

        // Additional key related exceptions

        if ("".equals(keyPropertyName))
            throw new IllegalArgumentException("Please select a field name for the additional key.");

        if (isManagedField && (keyPropertyName == null || keyPropertyName.length() == 0))
            throw new IllegalArgumentException("Additional Key Column name must be specified if field is managed.");

        if (useTimeKeyField && isManagedField)
            throw new IllegalArgumentException("Additional key cannot be a managed field if KeyPropertyName is Time (from Date/Time).");

        if (useTimeKeyField && !(keyPropertyName == null || keyPropertyName.equals(TIME_KEY_FIELD_KEY)))
            throw new IllegalArgumentException("KeyPropertyName should not be provided when using additional key of Time (from Date/Time).");

        if (isDemographicData && (isManagedField || keyPropertyName != null))
            throw new IllegalArgumentException("There cannot be an Additional Key Column if the dataset is Demographic Data.");

        if (!useTimeKeyField && null != keyPropertyName && null == domain.getFieldByName(keyPropertyName))
            throw new IllegalArgumentException("Additional Key Column name \"" + keyPropertyName +"\" must be the name of a column.");

        if (null != keyPropertyName && isManagedField)
        {
            String rangeURI = domain.getFieldByName(keyPropertyName).getRangeURI();
            if (!(rangeURI.endsWith("int") || rangeURI.endsWith("double") || rangeURI.endsWith("string")))
                throw new IllegalArgumentException("If Additional Key Column is managed, the column type must be numeric or text-based.");
        }

        // Other exception(s)

        if (null != datasetId && (null == def || def.getDatasetId() != datasetId) && null != study.getDataset(datasetId))
            throw new IllegalArgumentException("A Dataset already exists with the datasetId \"" + datasetId +"\".");

        if (!study.getShareVisitDefinitions() && null != datasetProperties.getDataSharing() && !datasetProperties.getDataSharing().equals("NONE"))
            throw new IllegalArgumentException("Illegal value set for data sharing option.");
    }

    private void checkCanUpdate(DatasetDefinition def, Container container, User user, DatasetDomainKindProperties datasetProperties,
                                GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update)
    {
        if (null == def)
            throw new IllegalArgumentException("Dataset not found.");

        if (!def.canUpdateDefinition(user))
            throw new IllegalArgumentException("Shared dataset can not be edited in this folder.");

        if (datasetProperties.getLabel() == null || datasetProperties.getLabel().length() == 0)
            throw new IllegalArgumentException("Dataset label cannot be empty.");

        if (null == PropertyService.get().getDomain(container, update.getDomainURI()))
            throw new IllegalArgumentException("Domain not found: " + update.getDomainURI() + ".");

        if (!def.getTypeURI().equals(original.getDomainURI()) || !def.getTypeURI().equals(update.getDomainURI()))
            throw new IllegalArgumentException("Illegal Argument");

        if (datasetProperties.isDemographicData() && !def.isDemographicData() && !StudyManager.getInstance().isDataUniquePerParticipant(def))
        {
            String noun = StudyService.get().getSubjectNounSingular(container);
            throw new IllegalArgumentException("This dataset currently contains more than one row of data per " + noun +
                    ". Demographic data includes one row of data per " + noun + ".");
        }
    }

    private ValidationException updateDomainDescriptor(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update,
                                                       Container container, User user)
    {
        ValidationException exception = new ValidationException();
        exception.addErrors(DomainUtil.updateDomainDescriptor(original, update, container, user));
        return exception;
    }

    private ValidationException updateDataset(DatasetDomainKindProperties datasetProperties, String domainURI, ValidationException exception,
                                              StudyImpl study, Container container, User user, DatasetDefinition def)
    {
        try
        {
            // Check for usage of Time as Key Field
            boolean useTimeKeyField = datasetProperties.isUseTimeKeyField();
            if (useTimeKeyField)
                datasetProperties.setKeyPropertyName(null);

            // Default is no key management
            KeyManagementType keyType = KeyManagementType.None;
            String keyPropertyName = datasetProperties.getKeyPropertyName();
            if (datasetProperties.getKeyPropertyName() != null)
            {
                Domain domain = PropertyService.get().getDomain(container, domainURI);
                for (DomainProperty dp : domain.getProperties())
                {
                    if (dp.getName().equalsIgnoreCase(datasetProperties.getKeyPropertyName()))
                    {
                        keyPropertyName = dp.getName();

                        // Be sure that the user really wants a managed key, not just that disabled select box still had a value
                        if (datasetProperties.isKeyPropertyManaged())
                            keyType = KeyManagementType.getManagementTypeFromProp(dp.getPropertyDescriptor().getPropertyType());

                        break;
                    }
                }
            }

            DatasetDefinition updated = def.createMutable();

            // Clear the category ID so that it gets regenerated based on the new string - see issue 19649
            updated.setCategoryId(null);

            BeanUtils.copyProperties(updated, datasetProperties);
            updated.setKeyPropertyName(keyPropertyName);
            updated.setKeyManagementType(keyType);
            updated.setUseTimeKeyField(useTimeKeyField);

            List<String> errors = new ArrayList<>();
            StudyManager.getInstance().updateDatasetDefinition(user, updated, errors);

            for (String errorMsg: errors)
                exception.addGlobalError(errorMsg);
            return exception;
        }
        catch (RuntimeSQLException e)
        {
            return exception.addGlobalError("Additional key column must have unique values.");
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ValidationException updateDomain(GWTDomain<? extends GWTPropertyDescriptor> original, GWTDomain<? extends GWTPropertyDescriptor> update,
                                            DatasetDomainKindProperties datasetProperties, Container container, User user, boolean includeWarnings)
    {
        assert original.getDomainURI().equals(update.getDomainURI());
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        DatasetDefinition def = null;

        if (datasetProperties != null)
        {
            def = study.getDataset(datasetProperties.getDatasetId());
            validateDatasetProperties(datasetProperties, container, user, update, def);
            checkCanUpdate(def, container, user, datasetProperties, original, update);
        }

        // Acquire lock before we actually start the transaction to avoid deadlocks when it's refreshed during the process
        Lock[] locks = def == null ? new Lock[0] : new Lock[] { def.getDomainLoadingLock() };
        try (DbScope.Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction(locks))
        {
            ValidationException exception = updateDomainDescriptor(original, update, container, user);

            if (!exception.hasErrors() && def != null)
                exception = updateDataset(datasetProperties, original.getDomainURI(), exception, study, container, user, def);

            if (!exception.hasErrors())
                transaction.commit();

            return exception;
        }
        finally
        {
            if (def != null)
                StudyManager.getInstance().uncache(def);
        }
    }

    @Override
    public void deleteDomain(User user, Domain domain)
    {
        DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(domain.getTypeURI());
        if (def == null)
            throw new NotFoundException("Dataset not found: " + domain.getTypeURI());

        StudyImpl study = StudyManager.getInstance().getStudy(domain.getContainer());
        if (study == null)
            throw new IllegalArgumentException("A study does not exist for this folder");

        try (DbScope.Transaction transaction = StudySchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            StudyManager.getInstance().deleteDataset(study, user, def, false);
            transaction.commit();
        }
    }

    @Override
    public boolean isDeleteAllDataOnFieldImport()
    {
        return true;
    }


    @Override
    public TableInfo getTableInfo(User user, Container container, String name, @Nullable ContainerFilter cf)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            return null;
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, user);
        DatasetDefinition dsd = schema.getDatasetDefinitionByName(name);
        if (null == dsd)
            return null;

        return DatasetFactory.createDataset(schema, cf, dsd);
    }

    @Override
    public void afterLoadTable(SchemaTableInfo ti, Domain domain)
    {
        // Grab the "standard" properties and apply them to this dataset table
        TableInfo template = DatasetDefinition.getTemplateTableInfo();

        for (PropertyStorageSpec pss : domain.getDomainKind().getBaseProperties(domain))
        {
            ColumnInfo c = ti.getColumn(pss.getName());
            ColumnInfo tCol = template.getColumn(pss.getName());
            // The column may be null if the dataset is being deleted in the background
            if (null != tCol && c != null)
            {
                ((BaseColumnInfo)c).setExtraAttributesFrom(tCol);

                // When copying a column, the hidden bit is not propagated, so we need to do it manually
                if (tCol.isHidden())
                    ((BaseColumnInfo)c).setHidden(true);
            }
        }
    }
}
