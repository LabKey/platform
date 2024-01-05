package org.labkey.assay;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.plate.AbstractPlateBasedAssayProvider;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateBasedAssayProvider;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.Pair;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetImpl;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Arrays;
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

            User upgradeUser = new LimitedUser(UserManager.getGuestUser(), SiteAdminRole.class);
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

    /**
     * Called from assay-23.002-23.003.sql
     *
     * Switch from storing the protocol plate template by name to the plate lsid.
     */
    @DeferredUpgrade
    public static void updateProtocolPlateTemplate(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            Set<ExpProtocol> protocols = new HashSet<>();
            for (Container container : ContainerManager.getAllChildren(ContainerManager.getRoot()))
            {
                if (container != null)
                    protocols.addAll(AssayService.get().getAssayProtocols(container));
            }

            for (ExpProtocol protocol : protocols)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null)
                {
                    if (provider instanceof PlateBasedAssayProvider plateProvider)
                    {
                        Plate plate = getPlate(protocol);
                        if (plate != null)
                        {
                            // get a mutable version of the protocol
                            ExpProtocol mutableProtocol = ExperimentService.get().getExpProtocol(protocol.getRowId());
                            _log.info("Adjusting plate template storage for assay: " + mutableProtocol.getName());
                            plateProvider.setPlate(mutableProtocol.getContainer(), mutableProtocol, plate);
                            mutableProtocol.save(User.getAdminServiceUser());
                        }
                    }
                }
            }
            tx.commit();
        }
    }

    @Nullable
    private static Plate getPlate(ExpProtocol protocol)
    {
        // resolve plate by the legacy deprecated plate name method
        ObjectProperty prop = protocol.getObjectProperties().get(protocol.getLSID() + AbstractPlateBasedAssayProvider.PLATE_TEMPLATE_SUFFIX);
        return prop != null ? PlateManager.get().getPlate(protocol.getContainer(), prop.getStringValue()) : null;
    }

    /**
     * Called from assay-24.000-24.001.sql
     *
     * The referenced upgrade script creates a new plate set for every plate in the system. We now
     * want to iterate over each plate set to set the name using the configured name expression.
     */
    public static void updatePlateSetNames(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            _log.info("Start updating temporary plate set names with the configured name expression");
            List<PlateSetImpl> plateSets = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateSet()).getArrayList(PlateSetImpl.class);

            NameGenerator nameGenerator = new NameGenerator(PlateManager.get().getPlateSetNameExpression(), AssayDbSchema.getInstance().getTableInfoPlateSet(), false, null, null, null);
            NameGenerator.State state = nameGenerator.createState(false);
            for (PlateSetImpl plateSet : plateSets)
            {
                Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateSetImpl.class).toMap(plateSet, new ArrayListMap<>());
                String name = nameGenerator.generateName(state, plateRow);
                state.cleanUp();

                SQLFragment sql = new SQLFragment("UPDATE ").append(AssayDbSchema.getInstance().getTableInfoPlateSet(), "")
                        .append(" SET Name = ?")
                        .add(name)
                        .append(" WHERE RowId = ?")
                        .add(plateSet.getRowId());
                new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);
            }
            _log.info("Successfully updated " + plateSets.size() + " plate set names");
            tx.commit();
        }
    }
}
