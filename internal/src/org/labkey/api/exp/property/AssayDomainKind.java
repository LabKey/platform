/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.api.exp.property;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: June 25, 2007
 * Time: 1:01:43 PM
 */
public abstract class AssayDomainKind extends AbstractDomainKind
{
    private final String _namespacePrefix;
    private final Priority _priority;

    protected AssayDomainKind(String namespacePrefix)
    {
        this(namespacePrefix, Priority.MEDIUM);
    }

    protected AssayDomainKind(String namespacePrefix, Priority priority)
    {
        _namespacePrefix = namespacePrefix;
        _priority = priority;
    }

    public String getTypeLabel(Domain domain)
    {
        return domain.getName();
    }

    @Override
    public Priority getPriority(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(_namespacePrefix) ? _priority: null;
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


    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ExpProtocol protocol = findProtocol(domain);
        if (protocol != null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(containerUser.getContainer(), protocol);
        }
        return null;
    }


    @Nullable
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ExpProtocol protocol = findProtocol(domain);
        if (protocol != null)
        {
            return PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(containerUser.getContainer(), protocol, false, null);
        }
        return null;
    }

    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignAssayPermission.class);
    }

    protected Set<String> getAssayReservedPropertyNames()
    {
        Set<String> result = new HashSet<>();
        result.add("RowId");
        result.add("Row Id");
        result.add("Container");
        result.add("LSID");
        result.add("Owner");
        result.add("CreatedBy");
        result.add("Created");
        result.add("ModifiedBy");
        result.add("Modified");
        return result;
    }
}
