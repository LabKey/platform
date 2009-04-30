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

package org.labkey.study.assay;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.view.ActionURL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: brittp
 * Date: June 25, 2007
 * Time: 1:01:43 PM
 */
public class AssayDomainKind extends DomainKind
{
    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    public boolean isDomainType(String domainURI)
    {
        // TODO: Enable the correct code below, BUT need to fix up sqlObjectIdsInDomain() first
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_PREFIX);
    }


    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        ExpProtocol protocol = findProtocol(domain);
        if (protocol != null)
        {
            SQLFragment sql = new SQLFragment();
            sql.append("SELECT o.ObjectId FROM " + ExperimentService.get().getTinfoExperimentRun() + " r, exp.object o WHERE r.LSID = o.ObjectURI AND r.ProtocolLSID = ?");
            sql.add(protocol.getLSID());
            return sql;
        }
        return new SQLFragment("NULL");
    }

    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containers)
    {
        return null;
    }

    private ExpProtocol findProtocol(Domain domain)
    {
        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(domain.getContainer());
        for (ExpProtocol protocol : protocols)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null)
            {
                for (Pair<Domain, Map<DomainProperty, Object>> protocolDomain : provider.getDomains(protocol))
                {
                    if (protocolDomain.getKey().getTypeURI().equals(domain.getTypeURI()))
                    {
                        return protocol;
                    }
                }
            }
        }
        return null;
    }


    public ActionURL urlShowData(Domain domain)
    {
        ExpProtocol protocol = findProtocol(domain);
        if (protocol != null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(domain.getContainer(), protocol);
        }
        return null;
    }


    public ActionURL urlEditDefinition(Domain domain)
    {
        ExpProtocol protocol = findProtocol(domain);
        if (protocol != null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(domain.getContainer(), protocol, false);
        }
        return null;
    }

    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, ACL.PERM_INSERT);
    }

    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return Collections.emptySet();
    }
}
