/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.StudySchema;

import java.util.Map;

/**
 * User: jgarms
 * Date: Jul 17, 2008
 * Time: 1:37:53 PM
 */
public class CohortDomainKind extends DomainKind
{
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    public boolean isDomainType(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return "Cohort".equals(lsid.getNamespacePrefix());
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        Container container = domain.getContainer();

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT o.ObjectId FROM " + StudySchema.getInstance().getTableInfoCohort() + " c, exp.object o WHERE c.LSID = o.ObjectURI AND c.container = ?");
        sql.add(container.getId());
        return sql;
    }
    
    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }

    public ActionURL urlShowData(Domain domain)
    {
        throw new UnsupportedOperationException("Not implemented");
    }

    public ActionURL urlEditDefinition(Domain domain)
    {
        // This isn't really the edit url, but instead the destination after editing
        return new ActionURL(CohortController.ManageCohortsAction.class, domain.getContainer());
    }

    // return the "system" properties for this domain
    public DomainProperty[] getDomainProperties(String domainURI)
    {
        return new DomainProperty[0];
    }
}
