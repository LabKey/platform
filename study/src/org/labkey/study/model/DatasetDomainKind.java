/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 4, 2007
 * Time: 1:01:43 PM
 */
public abstract class DatasetDomainKind extends AbstractDomainKind
{
    public final static String LSID_PREFIX = "StudyDataset";

    final static String DATE = "date";
    final static String PARTICIPANTID = "participantid";
    final static String LSID = "lsid";
    final static String SEQUENCENUM = "sequencenum";
    final static String SOURCELSID = "sourcelsid";
    final static String _KEY = "_key";
    final static String QCSTATE = "qcstate";
    final static String PARTICIPANTSEQUENCEKEY = "participantsequencekey";

    /*
     * the columns common to all datasets
     */
    private final static Set<PropertyStorageSpec> BASE_PROPERTIES;
    private final static Set<PropertyStorageSpec.Index> PROPERTY_INDICES;
    protected final static PropertyStorageSpec DATE_PROPERTY = new PropertyStorageSpec(DATE, Types.TIMESTAMP);

    static
    {
        PropertyStorageSpec[] props =
        {
            new PropertyStorageSpec(PARTICIPANTID, Types.VARCHAR, 32),
            new PropertyStorageSpec(LSID, Types.VARCHAR, 200, true, false),
            new PropertyStorageSpec(SEQUENCENUM, Types.NUMERIC),
            new PropertyStorageSpec(SOURCELSID, Types.VARCHAR, 200),
            new PropertyStorageSpec(_KEY, Types.VARCHAR, 200),
            new PropertyStorageSpec(QCSTATE, Types.INTEGER),
            new PropertyStorageSpec(PARTICIPANTSEQUENCEKEY, Types.VARCHAR, 200),
        };

        BASE_PROPERTIES = new HashSet<PropertyStorageSpec>(Arrays.asList(props));
        BASE_PROPERTIES.addAll(AbstractDomainKind.BASE_PROPERTIES);

        PropertyStorageSpec.Index[] indices = {
          new PropertyStorageSpec.Index(true, LSID),
          new PropertyStorageSpec.Index(false, QCSTATE),
          new PropertyStorageSpec.Index(false, PARTICIPANTSEQUENCEKEY),
          new PropertyStorageSpec.Index(true, PARTICIPANTID, SEQUENCENUM, _KEY)
        };

        PROPERTY_INDICES = new HashSet<PropertyStorageSpec.Index>(Arrays.asList(indices));
    }


    protected DatasetDomainKind()
    {
    }


    abstract public String getKindName();

    
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }


    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        DataSetDefinition def = getDatasetDefinition(domain.getTypeURI());
        if (null == def)
            return new SQLFragment("NULL");
        TableInfo ti = def.getStorageTableInfo();
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT ObjectId FROM ").append(ti.getSelectName()).append(" SD JOIN exp.Object O ON SD.Lsid=O.ObjectURI WHERE O.container=?");
        sql.add(def.getContainer());
        return sql;
    }


    // Lsid.toString() encodes incorrectly TODO: fix
    @Override
    public String generateDomainURI(String schemaName, String name, Container container, User user)
    {
        // UNDONE can't use id, because it won't match OntologyManager.importTypes()!
        //String objectid = name == null ? "" : name + "-" + id;
        String objectid = name == null ? "" : name;
        return (new Lsid(LSID_PREFIX, "Folder-" + container.getRowId(), objectid)).toString();
    }

    public static String generateDomainURI(String name, Container container)
    {
        String objectid = name == null ? "" : name;
        return (new Lsid(LSID_PREFIX, "Folder-" + container.getRowId(), objectid)).toString();
    }


    public Pair<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }


    public ActionURL urlShowData(Domain domain)
    {
        DataSet def = getDatasetDefinition(domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.DatasetReportAction.class, domain.getContainer());
        url.addParameter("datasetId", "" + def.getDataSetId());
        return url;
    }


    public ActionURL urlEditDefinition(Domain domain)
    {
        DataSet def = getDatasetDefinition(domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.EditTypeAction.class, domain.getContainer());
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



    // consider move into StudyManager
    static Cache domainCache = CacheManager.getCache(1000, CacheManager.DAY, "Domain->Dataset map"); 
    CacheLoader loader = new CacheLoader()
    {
        @Override
        public Object load(Object key, Object argument)
        {
            String domainURI = (String)key;
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT container, datasetid FROM study.Dataset WHERE TypeURI=?");
            sql.add(domainURI);
            ResultSet rs = null;
            try
            {
                rs = Table.executeQuery(StudySchema.getInstance().getSchema(),sql);
                if (!rs.next())
                    return null;
                else
                    return new Pair(rs.getString(1), rs.getInt(2));
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
            finally
            {
                ResultSetUtil.close(rs);
            }
        }
    };


    DataSetDefinition getDatasetDefinition(String domainURI)
    {
        Pair<String,Integer> p = (Pair<String,Integer>)domainCache.get(domainURI, null, loader);
        if (null == p)
            return null;

        Container c = ContainerManager.getForId(p.first);
        Study study = StudyManager.getInstance().getStudy(c);
        return StudyManager.getInstance().getDataSetDefinition(study, p.second);
    }


    public abstract Set<String> getReservedPropertyNames(Domain domain);

    
    @Override
    public Set<PropertyStorageSpec> getBaseProperties()
    {
        Set<PropertyStorageSpec> specs = new HashSet<PropertyStorageSpec>(BASE_PROPERTIES);
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
}
