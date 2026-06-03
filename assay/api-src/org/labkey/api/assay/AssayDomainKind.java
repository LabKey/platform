/*
 * Copyright (c) 2010-2026 LabKey Corporation
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

package org.labkey.api.assay;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.TemplateInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.BaseAbstractDomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.permissions.AssayReadPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.writer.ContainerUser;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class AssayDomainKind extends BaseAbstractDomainKind
{
    private final String _namespacePrefix;
    private final Priority _priority;
    private static final Set<String> RESERVED_NAMES;
    static {
        Set<String> s = DomainUtil.getNamesAndLabels(List.of(
            "RowId",
            "Container",
            "LSID",
            "Owner",
            "CreatedBy",
            "Created",
            "ModifiedBy",
            "Modified"
        ));
        // make this an unmodifiable set because many domains build from this set but shouldn't alter the base set
        RESERVED_NAMES = Collections.unmodifiableSet(s);
    }

    protected AssayDomainKind(String namespacePrefix)
    {
        this(namespacePrefix, Priority.MEDIUM);
    }

    protected AssayDomainKind(String namespacePrefix, Priority priority)
    {
        _namespacePrefix = namespacePrefix;
        _priority = priority;
    }

    @Override
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

    @Override
    public DefaultValueType[] getDefaultValueOptions(Domain domain)
    {
        ExpProtocol protocol = findProtocol(domain);

        // In the case of a new assay there may not be a protocol yet. This value will be set in getAssayTemplate in AssayService
        if (protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);

            if (provider != null)
            {
                if (provider.allowDefaultValues(domain))
                {
                    return provider.getDefaultValueOptions(domain);
                }
            }
        }

        return new DefaultValueType[0];
    }

    @Override
    public DefaultValueType getDefaultDefaultType(Domain domain)
    {
        ExpProtocol protocol = findProtocol(domain);

        // In the case of a new assay there may not be a protocol yet. This value will be set in getAssayTemplate in AssayService
        if (protocol != null)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);

            if (provider != null)
            {
                return provider.getDefaultValueDefault(domain);
            }
        }

        return null;
    }

    @Override
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

    protected @Nullable ExpProtocol findProtocol(Domain domain)
    {
        Pair<AssayProvider, ExpProtocol> pair = findProviderAndProtocol(domain);
        if (pair == null)
            return null;

        return pair.second;
    }

    @Nullable
    protected Pair<AssayProvider, ExpProtocol> findProviderAndProtocol(Domain domain)
    {
        List<ExpProtocol> protocols = AssayService.get().getAssayProtocols(domain.getContainer());
        for (ExpProtocol protocol : protocols)
        {
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null)
            {
                for (Domain protocolDomain : provider.getDomains(protocol))
                {
                    if (protocolDomain.getTypeURI().equals(domain.getTypeURI()))
                    {
                        return Pair.of(provider, protocol);
                    }
                }
            }
        }

        return null;
    }

    @Override
    @Nullable
    public ActionURL urlShowData(Domain domain, ContainerUser containerUser)
    {
        ExpProtocol protocol = findProtocol(domain);
        if (protocol == null)
            return null;

        return PageFlowUtil.urlProvider(AssayUrls.class).getAssayRunsURL(containerUser.getContainer(), protocol);
    }

    @Override
    @Nullable
    public ActionURL urlEditDefinition(Domain domain, ContainerUser containerUser)
    {
        ExpProtocol protocol = findProtocol(domain);
        if (protocol == null)
            return null;

        return PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(containerUser.getContainer(), protocol, false, null);
    }

    @Override
    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, DesignAssayPermission.class);
    }

    @Override
    public boolean canCreateDefinition(User user, Container container)
    {
        return container.hasPermission(user, DesignAssayPermission.class);
    }

    @Override
    public Domain createDomain(GWTDomain domain, JSONObject arguments, Container container, User user, @Nullable TemplateInfo templateInfo, boolean forUpdate)
    {
        DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(domain.getDomainURI(), domain.getName(), container);
        dd = dd.edit().setDescription(domain.getDescription()).build();
        OntologyManager.ensureDomainDescriptor(dd);

        return PropertyService.get().getDomain(container, dd.getDomainURI(), forUpdate);
    }

    protected static Set<String> getAssayReservedPropertyNames()
    {
        return RESERVED_NAMES;
    }

    @Override
    public boolean showDefaultValueSettings()
    {
        return true;
    }

    @Override
    public TableInfo getTableInfo(User user, Container container, Domain domain, @Nullable ContainerFilter cf)
    {
        if (domain == null)
            return null;

        Pair<AssayProvider, ExpProtocol> providerAndProtocol = findProviderAndProtocol(domain);
        if (providerAndProtocol == null)
            return null;

        AssayProvider provider = providerAndProtocol.first;
        ExpProtocol protocol = providerAndProtocol.second;

        AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
        for (var tableName : schema.getTableNames())
        {
            // First, fetch a table that is more likely to be cached
            var table = schema.getTable(tableName);
            if (table != null)
            {
                var tableDomain = table.getDomain();
                if (tableDomain != null && tableDomain.getTypeURI().equals(domain.getTypeURI()))
                {
                    // Now that we have a matching table fetch the more expensive table with extra metadata
                    return schema.getTable(tableName, cf, true, true);
                }
            }
        }

        return null;
    }

    @Override
    public boolean hasPermission(@NotNull UserPrincipal user, @NotNull Class<? extends Permission> perm, @NotNull UserSchema userSchema)
    {
        if (perm == ReadPermission.class)
        {
            Set<Role> roles = null;
            if (userSchema instanceof UserSchema.HasContextualRoles schemaWithRoles)
                roles = schemaWithRoles.getContextualRoles();
            return userSchema.getContainer().hasPermission(user, AssayReadPermission.class, roles);
        }

        return super.hasPermission(user, perm, userSchema);
    }
}
