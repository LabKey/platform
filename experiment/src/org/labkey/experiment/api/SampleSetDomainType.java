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

package org.labkey.experiment.api;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SampleSetDomainType extends DomainKind
{
    public SampleSetDomainType()
    {
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
        return reserved;
    }
}
