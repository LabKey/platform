/*
 * Copyright (c) 2008-2014 LabKey Corporation
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
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.property.AbstractDomainKind;
import org.labkey.api.exp.property.Domain;

import java.util.Set;

/**
 * User: jgarms
 * Date: Jul 30, 2008
 * Time: 2:31:04 PM
 */
public abstract class BaseStudyDomainKind extends AbstractDomainKind
{
    public String getKindName()
    {
        return getDomainInfo().getDomainPrefix();
    }

    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return getDomainInfo().getDomainPrefix().equals(lsid.getNamespacePrefix()) ? Priority.MEDIUM : null;
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        Container container = domain.getContainer();

        SQLFragment sql = new SQLFragment();
        sql.append("SELECT o.ObjectId FROM ");
        sql.append(getTableInfo(), "c");
        sql.append(", ");
        sql.append(OntologyManager.getTinfoObject(), "o");
        sql.append(" WHERE c.LSID = o.ObjectURI AND c.container = ?");
        sql.add(container.getId());
        return sql;
    }

    protected abstract ExtensibleStudyEntity.DomainInfo getDomainInfo();

    protected abstract TableInfo getTableInfo();

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        TableInfo table = getTableInfo();
        return table.getColumnNameSet();
    }
}
