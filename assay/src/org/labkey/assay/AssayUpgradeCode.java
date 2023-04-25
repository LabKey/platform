package org.labkey.assay;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.data.Table.CREATED_BY_COLUMN_NAME;
import static org.labkey.api.data.Table.CREATED_COLUMN_NAME;
import static org.labkey.api.data.Table.MODIFIED_BY_COLUMN_NAME;
import static org.labkey.api.data.Table.MODIFIED_COLUMN_NAME;

public class AssayUpgradeCode implements UpgradeCode
{
    private static final Logger _log = LogManager.getLogger(AssayUpgradeCode.class);

    // Invoked by assay-23.000-23.001.sql
    @SuppressWarnings({"UnusedDeclaration"})
    @DeferredUpgrade
    public static void addAssayDataCreatedColumns(final ModuleContext context)
    {
        if (context.isNewInstall())
            return;

        try (DbScope.Transaction transaction = ExperimentService.get().ensureTransaction())
        {
            Set<ExpProtocol> protocols = new HashSet<>();
            for (Container container : ContainerManager.getAllChildren(ContainerManager.getRoot()))
            {
                if (container != null)
                    protocols.addAll(AssayService.get().getAssayProtocols(container));
            }

            Map<Container, List<Pair<ExpProtocol, Domain>>> protocolsByContainer = new HashMap<>();
            for (ExpProtocol protocol : protocols)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider == null)
                    continue;;

                Domain resultsDomain = provider.getResultsDomain(protocol);
                if (null == resultsDomain || null == resultsDomain.getStorageTableName() || null == resultsDomain.getTypeURI())
                    continue;

                Lsid domainLsid = new Lsid(resultsDomain.getTypeURI());
                if (!ExpProtocol.ASSAY_DOMAIN_DATA.equals(domainLsid.getNamespacePrefix()))
                    continue;

                Container container = protocol.getContainer();
                protocolsByContainer.computeIfAbsent(container, s -> new ArrayList<>());
                protocolsByContainer.get(container).add(new Pair<>(protocol, resultsDomain));
            }

            User upgradeUser = new LimitedUser(UserManager.getGuestUser(), new int[0], Collections.singleton(RoleManager.getRole(SiteAdminRole.class)), false);
            for (Container container : protocolsByContainer.keySet())
            {
                List<Pair<ExpProtocol, Domain>> protocolDomains = protocolsByContainer.get(container);
                _log.info("Start adding result created/modified columns for " + protocolDomains.size() + " assay design(s) in " + container.getPath());

                for (Pair<ExpProtocol, Domain> protocolDomain : protocolDomains)
                    _addAssayResultColumns(protocolDomain.first, protocolDomain.second);

                _log.info("Finished adding result created/modified columns for " + protocolDomains.size() + " assay design(s) in " + container.getPath());
            }

            transaction.commit();
        }

    }

    private static void _addAssayResultColumns(ExpProtocol protocol, Domain resultsDomain)
    {
        AssayResultDomainKind kind = null;
        try
        {
            kind = (AssayResultDomainKind) resultsDomain.getDomainKind();
        }
        catch (IllegalArgumentException e)
        {
            // pass
        }
        if (null == kind || null == kind.getStorageSchemaName())
        {
            _log.warn("Unable to get result domain kind for " + protocol.getName());
            return;
        }

        DbSchema schema = kind.getSchema();
        SchemaTableInfo provisionedTable = schema.getTable(resultsDomain.getStorageTableName());
        if (provisionedTable == null)
            throw new IllegalStateException(protocol.getName() + " has no provisioned result table.");

        _ensureColumn(CREATED_COLUMN_NAME, resultsDomain, protocol, provisionedTable, kind);
        _ensureColumn(CREATED_BY_COLUMN_NAME, resultsDomain, protocol, provisionedTable, kind);
        _ensureColumn(MODIFIED_COLUMN_NAME, resultsDomain, protocol, provisionedTable, kind);
        _ensureColumn(MODIFIED_BY_COLUMN_NAME, resultsDomain, protocol, provisionedTable, kind);
    }

    private static void _ensureColumn(String colName, Domain domain, ExpProtocol protocol, SchemaTableInfo provisionedTable, AssayResultDomainKind kind)
    {

        ColumnInfo col = provisionedTable.getColumn(colName);
        if (col != null)
            _log.error("Column '" + colName + "' is already defined in result table for '" + protocol.getName() + "'.");

        PropertyStorageSpec colProp = kind.getBaseProperties(domain).stream().filter(p -> colName.equalsIgnoreCase(p.getName())).findFirst().orElseThrow();
        StorageProvisioner.get().addStorageProperties(domain, Arrays.asList(colProp), true);
        _log.info("Added '" + colName + "' column to '" + protocol.getName() + " provisioned result table.");

    }
}
