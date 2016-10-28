/*
 * Copyright (c) 2007-2016 LabKey Corporation
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
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.query.DatasetTableImpl;
import org.labkey.study.query.StudyQuerySchema;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
            new PropertyStorageSpec(CONTAINER, JdbcType.GUID),
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
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user,
        @Nullable TemplateInfo templateInfo)
    {
        return super.createDomain(domain, arguments, container, user, templateInfo);
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

        return new DatasetTableImpl(schema, dsd);
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
                c.setExtraAttributesFrom(tCol);

                // When copying a column, the hidden bit is not propagated, so we need to do it manually
                if (tCol.isHidden())
                    c.setHidden(true);
            }
        }
    }
}
