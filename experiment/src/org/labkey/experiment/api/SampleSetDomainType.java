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

package org.labkey.experiment.api;

import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.*;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.sql.SQLException;
import java.util.*;

public class SampleSetDomainType extends AbstractDomainKind
{
    public SampleSetDomainType()
    {
    }

    public String getKindName()
    {
        return "SampleSet";
    }
    
    public boolean isDomainType(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return "SampleSet".equals(lsid.getNamespacePrefix());
    }

//    public String generateDomainURI(Container container, String name)
//    {
//        return ExperimentServiceImpl.get().generateLSID(container, ExpSampleSet.class, name);
//    }

    private ExpSampleSet getSampleSet(Domain domain)
    {
        return ExperimentService.get().getSampleSet(domain.getTypeURI());
    }

    public ActionURL urlShowData(Domain domain)
    {
        ExpSampleSet ss = getSampleSet(domain);
        if (ss == null)
        {
            return null;
        }
        return (ActionURL) ss.detailsURL();
    }

    public ActionURL urlEditDefinition(Domain domain)
    {
        return urlShowData(domain);
    }

    public String getTypeLabel(Domain domain)
    {
        return "Sample Set '" + domain.getName() + "'";
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        SQLFragment ret = new SQLFragment("SELECT exp.object.objectid FROM exp.object INNER JOIN exp.material ON exp.object.objecturi = exp.material.lsid WHERE exp.material.cpastype = ?");
        ret.add(domain.getTypeURI());
        return ret;
    }

    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containerFilter)
    {
        SamplesSchema schema = new SamplesSchema(user, domain.getContainer());
        TableInfo table = schema.getSampleTable(ExperimentService.get().getSampleSet(domain.getTypeURI()));
        if (table == null)
            return null;
        return new Pair<TableInfo,ColumnInfo>(table, table.getColumn("LSID"));
    }

    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, UpdatePermission.class);
    }

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        ExpMaterialTable.Column[] columns = ExpMaterialTable.Column.values();
        Set<String> reserved = new HashSet<String>(columns.length);
        for (ExpMaterialTable.Column column : columns)
            reserved.add(column.name());
        reserved.add("CpasType");
        return reserved;
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, AdminPermission.class);
    }

    @Override
    public Domain createDomain(GWTDomain domain, JSONObject arguments, Container container, User user)
    {
        String name = domain.getName();
        String description = domain.getDescription();
        List<GWTPropertyDescriptor> properties = (List<GWTPropertyDescriptor>)domain.getFields();

        JSONArray idCols = arguments.containsKey("idCols") ? (JSONArray)arguments.get("idCols") : new JSONArray();
        int idCol1 = idCols.optInt(0, -1);
        int idCol2 = idCols.optInt(1, -1);
        int idCol3 = idCols.optInt(2, -1);
        int parentCol = arguments.optInt("parentCol", -1);

        ExpSampleSet ss;
        try
        {
            ss = ExperimentService.get().createSampleSet(container, user, name, description, properties, idCol1, idCol2, idCol3, parentCol);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        catch (ExperimentException e)
        {
            throw new RuntimeException(e);
        }
        return ss.getType();
    }

}
