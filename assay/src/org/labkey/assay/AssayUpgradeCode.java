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
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.ObjectFactory;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SchemaTableInfo;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.roles.SiteAdminRole;
import org.labkey.api.util.Pair;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateSetImpl;
import org.labkey.assay.plate.model.PlateSetLineage;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
                    continue;

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
     * <p>
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
        return prop != null ? PlateManager.get().getPlateByName(protocol.getContainer(), prop.getStringValue()) : null;
    }

    /**
     * Called from assay-24.000-24.001.sql
     * <p>
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
            SQLFragment sql = new SQLFragment("SELECT MAX(rowId) FROM ").append(AssayDbSchema.getInstance().getTableInfoPlateSet(), "");
            Integer maxRowId = new SqlSelector(AssayDbSchema.getInstance().getSchema(), sql).getObject(Integer.class);

            if (maxRowId != null)
            {
                // reset the DbSequence
                TableInfo plateSetTable = AssayDbSchema.getInstance().getTableInfoPlateSet();
                DbSequence sequence = DbSequenceManager.get(ContainerManager.getRoot(), plateSetTable.getDbSequenceName("RowId"));
                sequence.ensureMinimum(maxRowId);
            }

            _log.info("Start updating temporary plate set names with the configured name expression");
            List<PlateSetImpl> plateSets = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateSet()).getArrayList(PlateSetImpl.class);

            NameGenerator nameGenerator = new NameGenerator(PlateManager.get().getPlateSetNameExpression(), AssayDbSchema.getInstance().getTableInfoPlateSet(), false, null, null, null);
            NameGenerator.State state = nameGenerator.createState(false);
            for (PlateSetImpl plateSet : plateSets)
            {
                Map<String, Object> plateRow = ObjectFactory.Registry.getFactory(PlateSetImpl.class).toMap(plateSet, new ArrayListMap<>());
                String name = nameGenerator.generateName(state, plateRow);
                state.cleanUp();

                SQLFragment sql2 = new SQLFragment("UPDATE ").append(AssayDbSchema.getInstance().getTableInfoPlateSet(), "")
                        .append(" SET Name = ?")
                        .add(name)
                        .append(" WHERE RowId = ?")
                        .add(plateSet.getRowId());
                new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql2);
            }
            _log.info("Successfully updated " + plateSets.size() + " plate set names");
            tx.commit();
        }
    }

    /**
     * Called from assay-24.001-24.002.sql
     * <p>
     * Iterate over each plate and plate set to generate a Plate ID and PlateSet ID based on the
     * configured name expression for each.
     */
    public static void initializePlateAndPlateSetIDs(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            _log.info("Start initializing Plate IDs");

            try (Results rs = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlate()).getResults())
            {
                int platesUpgraded = 0;
                while (rs.next())
                {
                    Map<String, Object> row = rs.getRowMap();
                    // get the plate container
                    String containerId = String.valueOf(row.get("container"));
                    Container c = ContainerManager.getForId(containerId);
                    if (c != null)
                    {
                        row.put("name", null);

                        NameGenerator nameGenerator = new NameGenerator(PlateManager.get().getPlateNameExpression(), AssayDbSchema.getInstance().getTableInfoPlate(), false, c, null, null);
                        NameGenerator.State state = nameGenerator.createState(false);
                        String name = nameGenerator.generateName(state, row);
                        state.cleanUp();

                        SQLFragment sql = new SQLFragment("UPDATE ").append(AssayDbSchema.getInstance().getTableInfoPlate(), "")
                                .append(" SET PlateId = ?")
                                .add(name)
                                .append(" WHERE RowId = ?")
                                .add(row.get("rowId"));
                        new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);
                        platesUpgraded++;
                    }
                    else
                        _log.error("Container for Plate ID : " + row.get("rowId") + " could not be resolved.");
                }
                _log.info("Successfully updated " + platesUpgraded + " plate IDs");
            }

            _log.info("Start initializing PlateSet IDs");
            try (Results rs = new TableSelector(AssayDbSchema.getInstance().getTableInfoPlateSet()).getResults())
            {
                NameGenerator nameGenerator = new NameGenerator(PlateManager.get().getPlateSetNameExpression(), AssayDbSchema.getInstance().getTableInfoPlateSet(), false, null, null, null);
                NameGenerator.State state = nameGenerator.createState(false);
                int plateSetsUpgraded = 0;
                while (rs.next())
                {
                    Map<String, Object> row = rs.getRowMap();
                    // for plate sets, they should have a valid PlateSetId, but if the name was not generated (or mutated), regenerate a new
                    // plate set id
                    if (!String.valueOf(row.get("name")).startsWith("PLS-"))
                    {
                        row.put("name", null);
                        String name = nameGenerator.generateName(state, row);
                        state.cleanUp();

                        SQLFragment sql = new SQLFragment("UPDATE ").append(AssayDbSchema.getInstance().getTableInfoPlateSet(), "")
                                .append(" SET PlateSetId = ?")
                                .add(name)
                                .append(" WHERE RowId = ?")
                                .add(row.get("rowId"));
                        new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);
                        plateSetsUpgraded++;
                    }
                }
                _log.info("Successfully updated " + plateSetsUpgraded + " plate set IDs");
            }
            tx.commit();
        }
    }

    /**
     * Well metadata has transitioned to a provisioned architecture.
     */
    private static @Nullable Domain getPlateMetadataVocabDomain(Container container, User user)
    {
        DomainKind<?> vocabDomainKind = PropertyService.get().getDomainKindByName("Vocabulary");

        if (vocabDomainKind == null)
            return null;

        // the domain is scoped at the project level (project and subfolder scoping)
        Container domainContainer = PlateManager.get().getPlateMetadataDomainContainer(container);
        String domainURI = vocabDomainKind.generateDomainURI(null, "PlateMetadataDomain", domainContainer, user);
        return PropertyService.get().getDomain(container, domainURI);
    }

    /**
     * Called from assay-24.002-24.003.sql to delete the vocabulary domains associated with
     * plate metadata. This upgrade transitions to using a provisioned table approach. Since the plate features are
     * still under an experimental flag we won't worry about upgrading the domains.
     */
    public static void deletePlateVocabDomains(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            // just truncate the plate to custom property mappings
            Table.truncate(AssayDbSchema.getInstance().getTableInfoPlateProperty());
            List<Container> biologicsFolders = new ArrayList<>();

            for (Container container : ContainerManager.getAllChildren(ContainerManager.getRoot()))
            {
                if (container != null)
                {
                    Domain domain = getPlateMetadataVocabDomain(container, User.getAdminServiceUser());
                    if (domain != null)
                    {
                        // delete the plate metadata values
                        SQLFragment sql = new SQLFragment("SELECT Lsid FROM ")
                                .append(AssayDbSchema.getInstance().getTableInfoWell(), "")
                                .append(" WHERE Container = ?")
                                .add(container);
                        OntologyManager.deleteOntologyObjects(AssayDbSchema.getInstance().getSchema(), sql, container);

                        // delete the domain
                        domain.delete(User.getAdminServiceUser());
                    }

                    if (container.getProject() != null && "Biologics".equals(ContainerManager.getFolderTypeName(container.getProject())))
                    {
                        // ensure the plate metadata domain for the top level biologics projects
                        if (container.isProject())
                            PlateManager.get().ensurePlateMetadataDomain(container, User.getAdminServiceUser());
                        biologicsFolders.add(container);
                    }
                }
            }

            // for existing plates we also need to populate the new provisioned tables so that wells can be joined
            // to the metadata properly
            for (Container container : biologicsFolders)
            {
                TableInfo tinfo = PlateManager.get().getPlateMetadataTable(container, User.getAdminServiceUser());
                if (tinfo != null)
                {
                    SQLFragment sql = new SQLFragment("INSERT INTO ").append(tinfo, "")
                            .append(" (Lsid) SELECT Lsid FROM ").append(AssayDbSchema.getInstance().getTableInfoWell(), "")
                            .append(" WHERE Container = ?").add(container);

                    new SqlExecutor(AssayDbSchema.getInstance().getScope()).execute(sql);
                }
            }
            tx.commit();
        }
    }

    /**
     * Called from assay-24.005-24.006.sql
     */
    public static void populatePlateSetPaths(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            Map<Integer, String> plateSetPaths = new HashMap<>();
            Map<Integer, List<Integer>> plateSetsToHits = new HashMap<>();

            SQLFragment sql = new SQLFragment("SELECT Hit.RowId AS HitRowId, PlateSet.RowId AS PlateSetRowId")
                    .append(" FROM assay.PlateSet")
                    .append(" INNER JOIN assay.Plate ON Plate.PlateSet = PlateSet.RowId")
                    .append(" INNER JOIN assay.Well ON Well.PlateId = Plate.RowId")
                    .append(" INNER JOIN assay.Hit ON Hit.WellLsid = Well.Lsid")
                    .appendEOS();
            Collection<Map<String, Object>> rows = new SqlSelector(scope, sql).getMapCollection();

            for (Map<String, Object> row : rows)
            {
                Integer plateSetRowId = (Integer) row.get("PlateSetRowId");
                Integer hitRowId = (Integer) row.get("HitRowId");

                if (!plateSetsToHits.containsKey(plateSetRowId))
                {
                    PlateSetLineage lineage = PlateManager.get().getPlateSetLineage(
                        ContainerManager.getRoot(),
                        User.getAdminServiceUser(),
                        plateSetRowId,
                        ContainerFilter.EVERYTHING
                    );
                    String lineagePath = lineage.getSeedPath();

                    plateSetPaths.put(plateSetRowId, lineagePath);
                    plateSetsToHits.put(plateSetRowId, new ArrayList<>());
                }

                plateSetsToHits.get(plateSetRowId).add(hitRowId);
            }

            for (Map.Entry<Integer, List<Integer>> entry : plateSetsToHits.entrySet())
            {
                String plateSetPath = plateSetPaths.get(entry.getKey());

                SQLFragment updateSql = new SQLFragment("UPDATE assay.Hit")
                        .append(" SET PlateSetPath = ? ").add(plateSetPath)
                        .append(" WHERE RowId ").appendInClause(entry.getValue(), scope.getSqlDialect())
                        .appendEOS();

                new SqlExecutor(scope).execute(updateSql);
            }

            tx.commit();
        }
    }
}
