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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTIndex;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.TimepointType;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.assay.AssayPublishManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.StudyQuerySchema;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: matthewb
 * Date: May 4, 2007
 * Time: 1:01:43 PM
 */
public abstract class DatasetDomainKind extends AbstractDomainKind
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
            new PropertyStorageSpec(DATE, JdbcType.TIMESTAMP)
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
            new PropertyStorageSpec(DATE, JdbcType.TIMESTAMP)
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


    abstract public String getKindName();

    
    public String getTypeLabel(Domain domain)
    {
        DatasetDefinition def = getDatasetDefinition(domain.getTypeURI());
        if (null == def)
            return domain.getName();
        return def.getName();
    }


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


    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        Dataset def = getDatasetDefinition(domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.DatasetReportAction.class, containerUser.getContainer());
        url.addParameter("datasetId", "" + def.getDatasetId());
        return url;
    }


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
        ActionURL createURL = new ActionURL(StudyController.DefineDatasetTypeAction.class, container);
        createURL.addParameter("autoDatasetId", "true");
        return createURL;
    }


    DatasetDefinition getDatasetDefinition(String domainURI)
    {
        return StudyManager.getInstance().getDatasetDefinition(domainURI);
    }


    public abstract Set<String> getReservedPropertyNames(Domain domain);

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
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user,
        @Nullable TemplateInfo templateInfo)
    {
        String name = domain.getName();
        Integer datasetId = arguments.containsKey("datasetId") ? (Integer)arguments.get("datasetId") : null;
        Integer categoryId = arguments.containsKey("categoryId") ? (Integer)arguments.get("categoryId") : null;
        String categoryName = arguments.containsKey("categoryName") ? (String)arguments.get("categoryName") : null;
        boolean demographics = arguments.containsKey("demographics") ? (Boolean)arguments.get("demographics") : false;
        String keyPropertyName = arguments.containsKey("keyPropertyName") ? (String)arguments.get("keyPropertyName") : null;
        boolean useTimeKeyField = arguments.containsKey("useTimeKeyField") ? (Boolean)arguments.get("useTimeKeyField") : false;
        boolean strictFieldValidation = arguments.containsKey("strictFieldValidation") ? (Boolean)arguments.get("strictFieldValidation") : true;

        if (name == null)
            throw new IllegalArgumentException("Dataset name must not be null");

        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (study == null)
            throw new IllegalArgumentException("A study does not exist for this folder");

        if (categoryId != null && categoryName != null)
            throw new IllegalArgumentException("Category ID and category name cannot both be specified");

        // make sure the domain matches the timepoint type
        TimepointType timepointType = study.getTimepointType();
        if (timepointType.isVisitBased() && getKindName().equals(DateDatasetDomainKind.KIND_NAME))
            throw new IllegalArgumentException("Visit based studies require a visit based dataset domain. Please specify a kind name of : " + VisitDatasetDomainKind.KIND_NAME);
        else if (!timepointType.isVisitBased() && getKindName().equals(VisitDatasetDomainKind.KIND_NAME))
            throw new IllegalArgumentException("Date based studies require a date based dataset domain. Please specify a kind name of : " + DateDatasetDomainKind.KIND_NAME);

        if (categoryName != null)
        {
            ViewCategory category = ViewCategoryManager.getInstance().getCategory(container, categoryName);
            if (category != null)
                categoryId = category.getRowId();
            else
                throw new IllegalArgumentException("Unable to find a category named : " + categoryName + " in this folder.");
        }
        else if (categoryId != null)
        {
            // validate the category ID
            if (ViewCategoryManager.getInstance().getCategory(container, categoryId) == null)
                throw new IllegalArgumentException("Unable to find a category with the ID : " + categoryId + " in this folder.");
        }

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            DatasetDefinition def = AssayPublishManager.getInstance().createAssayDataset(user, study, name, keyPropertyName, datasetId,
                    demographics, Dataset.TYPE_STANDARD, categoryId, null, useTimeKeyField);

            if (def.getDomain() != null)
            {
                List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();
                List<GWTIndex> indices = (List<GWTIndex>)domain.getIndices();

                Domain newDomain = def.getDomain();
                if (newDomain != null)
                {
                    Set<String> reservedNames = getReservedPropertyNames(newDomain);
                    Set<String> lowerReservedNames = reservedNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
                    Set<String> existingProperties = newDomain.getProperties().stream().map(o -> o.getName().toLowerCase()).collect(Collectors.toSet());
                    Map<DomainProperty, Object> defaultValues = new HashMap<>();
                    Set<String> propertyUris = new HashSet<>();

                    for (GWTPropertyDescriptor pd : properties)
                    {
                        if (lowerReservedNames.contains(pd.getName().toLowerCase()) || existingProperties.contains(pd.getName().toLowerCase()))
                        {
                            if (strictFieldValidation)
                                throw new IllegalArgumentException("Property: " + pd.getName() + " is reserved or exists in the current domain.");
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
                    throw new IllegalArgumentException("Failed to create domain for dataset : " + name);
            }
            transaction.commit();
            return study.getDataset(def.getDatasetId()).getDomain();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
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
    public TableInfo getTableInfo(User user, Container container, String name)
    {
        StudyImpl study = StudyManager.getInstance().getStudy(container);
        if (null == study)
            return null;
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, user, true);
        DatasetDefinition dsd = schema.getDatasetDefinitionByName(name);
        if (null == dsd)
            return null;

        return new DatasetTableImpl(schema, null, dsd);
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
