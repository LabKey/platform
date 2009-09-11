/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

import org.labkey.api.data.*;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.security.User;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.api.study.DataSet;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 4, 2007
 * Time: 1:01:43 PM
 */
public class DatasetDomainKind extends DomainKind
{
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }


    public boolean isDomainType(String domainURI)
    {
        try
        {
            Lsid lsid = new Lsid(domainURI);
            if ("StudyDataset".equalsIgnoreCase(lsid.getNamespacePrefix()))
                return true;
        }
        catch (Exception x)
        {
        /* */
        }

        // UNDONE: switch to LSID format to make this easier
        return getDatasetDefinition(null, domainURI) != null;
    }


    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        DataSet def = getDatasetDefinition(domain.getContainer(), domain.getTypeURI());
        if (null == def)
            return null;
        SQLFragment sql = new SQLFragment();
        sql.append("SELECT ObjectId FROM study.StudyData SD JOIN exp.Object O ON SD.Lsid=O.ObjectURI WHERE O.container=? AND SD.container=? AND SD.datasetid=?");
        sql.add(def.getContainer());
        sql.add(def.getContainer());
        sql.add(def.getDataSetId());
        return sql;
    }


    // Lsid.toString() encodes incorrectly TODO: fix
    public String generateDomainURI(Container container, String name)
    {
        // UNDONE can't use id, because it won't match OntologyManager.importTypes()!
        //String objectid = name == null ? "" : name + "-" + id;
        String objectid = name == null ? "" : name;
        return (new Lsid("StudyDatasets", "Folder-" + container.getRowId(), objectid)).toString();
    }


    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }


    public ActionURL urlShowData(Domain domain)
    {
        DataSet def = getDatasetDefinition(domain.getContainer(), domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.DatasetReportAction.class, domain.getContainer());
        url.addParameter("datasetId", "" + def.getDataSetId());
        return url;
    }


    public ActionURL urlEditDefinition(Domain domain)
    {
        DataSet def = getDatasetDefinition(domain.getContainer(), domain.getTypeURI());
        ActionURL url = new ActionURL(StudyController.EditTypeAction.class, domain.getContainer());
        url.addParameter("datasetId", "" + def.getDataSetId());
        return url;
    }


    DataSet getDatasetDefinition(Container c, String domainURI)
    {
        ResultSet rs = null;
        try
        {
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT container, datasetid FROM study.Dataset WHERE TypeURI=?");
            sql.add(domainURI);
            if (null != c)
            {
                sql.append(" AND Container=?");
                sql.add(c.getId());
            }

            rs = Table.executeQuery(StudySchema.getInstance().getSchema(),
                    "SELECT container, datasetid FROM study.Dataset WHERE TypeURI=?", new Object[] {domainURI});
            if (!rs.next())
                return null;
            String container = rs.getString(1);
            int id = rs.getInt(2);
            rs.close();
            rs = null;

            if (null == c)
            {
                c = ContainerManager.getForId(container);
                if (null == c)
                    return null;
            }
            Study study = StudyManager.getInstance().getStudy(c);
            return StudyManager.getInstance().getDataSetDefinition(study, id);
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

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        DataSet def = getDatasetDefinition(domain.getContainer(), domain.getTypeURI());
        return def.getDefaultFieldNames();
    }
}
