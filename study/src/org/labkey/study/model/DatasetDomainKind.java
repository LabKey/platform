/*
 * Copyright (c) 2007-2014 LabKey Corporation
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

import org.labkey.api.data.Container;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.query.DataSetTableImpl;
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

    final static String CONTAINER = "container";
    final static String DATE = "date";
    final static String PARTICIPANTID = "participantid";
    final static String LSID = "lsid";
    final static String SEQUENCENUM = "sequencenum";
    final static String SOURCELSID = "sourcelsid";
    final static String _KEY = "_key";
    final static String QCSTATE = "qcstate";
    final static String PARTICIPANTSEQUENCENUM = "participantsequencenum";

    public static final String CREATED = "created";
    public static final String MODIFIED = "modified";
    public static final String CREATED_BY = "createdBy";
    public static final String MODIFIED_BY = "modifiedBy";


    /*
     * the columns common to all datasets
     */
    private final static Set<PropertyStorageSpec> BASE_PROPERTIES;
    private final static Set<PropertyStorageSpec.Index> PROPERTY_INDICES;

    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(CONTAINER, JdbcType.GUID),
            new PropertyStorageSpec(PARTICIPANTID, JdbcType.VARCHAR, 32),
            new PropertyStorageSpec(LSID, JdbcType.VARCHAR, 200, PropertyStorageSpec.Special.PrimaryKey),
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
        };

        BASE_PROPERTIES = new HashSet<>(Arrays.asList(props));

        PropertyStorageSpec.Index[] indices = {
          new PropertyStorageSpec.Index(false, CONTAINER, QCSTATE),
          new PropertyStorageSpec.Index(false, CONTAINER, PARTICIPANTSEQUENCENUM),
          new PropertyStorageSpec.Index(true, CONTAINER, PARTICIPANTID, SEQUENCENUM, _KEY)
        };

        PROPERTY_INDICES = new HashSet<>(Arrays.asList(indices));
    }


    protected DatasetDomainKind()
    {
    }


    abstract public String getKindName();

    
    public String getTypeLabel(Domain domain)
    {
        DataSetDefinition def = getDatasetDefinition(domain.getTypeURI());
        if (null == def)
            return domain.getName();
        return def.getName();
    }


    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        DataSetDefinition def = getDatasetDefinition(domain.getTypeURI());
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
        DataSet def = getDatasetDefinition(domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.DatasetReportAction.class, containerUser.getContainer());
        url.addParameter("datasetId", "" + def.getDataSetId());
        return url;
    }


    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        DataSet def = getDatasetDefinition(domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.EditTypeAction.class, containerUser.getContainer());
        url.addParameter("datasetId", "" + def.getDataSetId());
        return url;
    }


    @Override
    public ActionURL urlCreateDefinition(String schemaName, String queryName, Container container, User user)
    {
        ActionURL createURL = new ActionURL(StudyController.DefineDatasetTypeAction.class, container);
        createURL.addParameter("autoDatasetId", "true");
        return createURL;
    }


    DataSetDefinition getDatasetDefinition(String domainURI)
    {
        return StudyManager.getInstance().getDatasetDefinition(domainURI);
    }


    public abstract Set<String> getReservedPropertyNames(Domain domain);

    
    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        Set<PropertyStorageSpec> specs = new HashSet<>(BASE_PROPERTIES);
        specs.addAll(super.getBaseProperties());
        return specs;
    }

    @Override
    public Set<PropertyStorageSpec.Index> getPropertyIndices()
    {
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
    public void invalidate(Domain domain)
    {
        super.invalidate(domain);
        StudyManager.getInstance().uncache(getDatasetDefinition(domain.getTypeURI()));
    }

    @Override
    public Domain createDomain(GWTDomain domain, Map<String, Object> arguments, Container container, User user)
    {
        return super.createDomain(domain, arguments, container, user);
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
        StudyQuerySchema schema = StudyQuerySchema.createSchema(study, user, true);
        DataSetDefinition dsd = schema.getDataSetDefinitionByName(name);

        return new DataSetTableImpl(schema, dsd);
    }
}
