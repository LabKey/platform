package org.labkey.assay;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.plate.PlateDataStateManager;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequence;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.DeferredUpgrade;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.NameGeneratorState;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.UpgradeCode;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.assay.plate.PlateImpl;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.plate.PlateMetadataDomainKind;
import org.labkey.assay.plate.TsvPlateLayoutHandler;
import org.labkey.assay.plate.model.PlateSetLineage;
import org.labkey.assay.plate.query.PlateTable;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.assay.plate.AssayPlateMetadataService.HIT_SELECTION_CRITERIA_COLUMN_NAME;
import static org.labkey.assay.plate.PlateMetadataDomainKind.Column;

public class AssayUpgradeCode implements UpgradeCode
{
    private static final Logger _log = LogManager.getLogger(AssayUpgradeCode.class);

    /**
     * Called from assay-24.000-24.001.sql
     * <p>
     * The referenced upgrade script creates a new plate set for every plate in the system. We now
     * want to iterate over each plate set to set the name using the configured name expression.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static void updatePlateSetNames(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            TableInfo plateSetTable = AssayDbSchema.getInstance().getTableInfoPlateSet();

            // Set the DbSequence minimum
            {
                SQLFragment sql = new SQLFragment("SELECT MAX(rowId) FROM ").append(plateSetTable, "");
                Integer maxRowId = new SqlSelector(AssayDbSchema.getInstance().getSchema(), sql).getObject(Integer.class);

                if (maxRowId != null)
                {
                    DbSequence sequence = DbSequenceManager.get(ContainerManager.getRoot(), plateSetTable.getDbSequenceName("RowId"));
                    sequence.ensureMinimum(maxRowId);
                }
            }

            _log.info("Start updating temporary plate set names with the configured name expression");
            List<Integer> plateSetRowIds = new TableSelector(plateSetTable, Collections.singleton("RowId")).getArrayList(Integer.class);

            // This is a copy of PlateManager.PLATE_SET_NAME_EXPRESSION as set when this script was written.
            // Copied here to allow this script to assume that only the Plate Set "RowId" value is needed for the
            // generated name.
            String PLATE_SET_NAME_EXPRESSION = "PLS-${now:date('yyyyMMdd')}-${RowId}";
            NameGenerator nameGenerator = new NameGenerator(PLATE_SET_NAME_EXPRESSION, plateSetTable, false, null, null, null);
            NameGeneratorState state = nameGenerator.createState(false);

            for (Integer plateSetRowId : plateSetRowIds)
            {
                String name = nameGenerator.generateName(state, CaseInsensitiveHashMap.of("RowId", plateSetRowId));
                state.cleanUp();

                SQLFragment sql = new SQLFragment("UPDATE ").append(plateSetTable, "")
                        .append(" SET Name = ?")
                        .add(name)
                        .append(" WHERE RowId = ?")
                        .add(plateSetRowId);

                new SqlExecutor(AssayDbSchema.getInstance().getSchema()).execute(sql);
            }

            _log.info("Successfully updated " + plateSetRowIds.size() + " plate set names");
            tx.commit();
        }
    }

    /**
     * Called from assay-24.001-24.002.sql
     * <p>
     * Iterate over each plate and plate set to generate a Plate ID and PlateSet ID based on the
     * configured name expression for each.
     */
    @SuppressWarnings({"UnusedDeclaration"})
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
                        NameGeneratorState state = nameGenerator.createState(false);
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
                NameGeneratorState state = nameGenerator.createState(false);
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
    @SuppressWarnings({"UnusedDeclaration"})
    public static void deletePlateVocabDomains(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            // just truncate the plate to custom property mappings
            Table.truncate(AssayDbSchema.getInstance().getSchema().getTable("PlateProperty"));
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

                    if (isBiologicsFolder(container.getProject()))
                    {
                        // ensure the plate metadata domain for the top level biologics projects
                        if (container.isProject())
                            PlateManager.get().ensurePlateMetadataDomain(container, User.getAdminServiceUser(), false);
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
     * Populates
     */
    @SuppressWarnings({"UnusedDeclaration"})
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
                            ContainerFilter.getUnsafeEverythingFilter()
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

    /**
     * Called from assay-24.007-24.008.sql
     * This updates the well type to WellGroup.Type.SAMPLE for all wells in Biologics folders that have a value
     * set for Well.SampleId.
     */
    @DeferredUpgrade
    @SuppressWarnings({"UnusedDeclaration"})
    public static void populatePlateWellTypes(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();

        // Determine all containers that have a Plate where Samples are specified in wells
        SQLFragment sql = new SQLFragment("""
            SELECT DISTINCT P.Container
            FROM assay.Well AS W
            INNER JOIN assay.Plate AS P ON P.RowId = W.PlateId
            WHERE P.AssayType = ? AND W.SampleId IS NOT NULL
        """).add(TsvPlateLayoutHandler.TYPE);
        List<String> containerIds = new SqlSelector(scope, sql).getArrayList(String.class);

        for (String containerId : containerIds)
        {
            Container container = ContainerManager.getForId(containerId);
            if (container == null)
            {
                _log.error(String.format("Failed to populate plate well types. Unable to resolve container for entityId \"%s\".", containerId));
                continue;
            }

            if (!(isBiologicsFolder(container) || isBiologicsFolder(container.getProject())))
            {
                _log.info(String.format("Populating plate well types. Skipping \"%s\" plates in \"%s\".", TsvPlateLayoutHandler.TYPE, container.getPath()));
                continue;
            }

            _log.info(String.format("Populating plate well types in \"%s\".", container.getPath()));

            try (DbScope.Transaction tx = scope.ensureTransaction())
            {
                SQLFragment wellSql = new SQLFragment("""
                    SELECT W.RowId, W.PlateId
                    FROM assay.Well AS W
                    INNER JOIN assay.Plate AS P ON P.RowId = W.PlateId
                    WHERE P.Container = ? AND P.AssayType = ? AND W.SampleId IS NOT NULL AND W.RowId NOT IN (
                        SELECT WellId FROM assay.WellGroupPositions AS WGP WHERE WGP.WellId = W.RowId
                    )
                """).add(containerId).add(TsvPlateLayoutHandler.TYPE);

                Map<Integer, Map<Integer, PlateManager.WellGroupChange>> wellGroupChanges = new HashMap<>();
                Collection<Map<String, Object>> sampleWellRows = new SqlSelector(scope, wellSql).getMapCollection();
                for (Map<String, Object> sampleWellRow : sampleWellRows)
                {
                    Integer plateRowId = (Integer) sampleWellRow.get("PlateId");
                    Integer wellRowId = (Integer) sampleWellRow.get("RowId");
                    PlateManager.WellGroupChange change = new PlateManager.WellGroupChange(plateRowId, wellRowId, WellGroup.Type.SAMPLE.name(), null, null);

                    wellGroupChanges.computeIfAbsent(plateRowId, HashMap::new).put(wellRowId, change);
                }

                if (wellGroupChanges.isEmpty())
                {
                    _log.info(String.format("No well group updates for plates in \"%s\".", container.getPath()));
                    continue;
                }

                _log.info(String.format("Updating \"%d\" well groups across \"%d\" plates in \"%s\".", sampleWellRows.size(), wellGroupChanges.entrySet().size(), container.getPath()));
                computeWellGroups(container, User.getAdminServiceUser(), wellGroupChanges);
                _log.info(String.format("Completed well group update in \"%s\".", container.getPath()));

                tx.commit();
            }
        }
    }

    // This is a functional copy of PlateManager.computeWellGroups() prior to the replicates refactor
    // to represent replicates with the "Replicate Groups" column. When this is removed the methods called on
    // PlateManager should be once again made private if possible.
    private static void computeWellGroups(
        Container container,
        User user,
        Map<Integer, Map<Integer, PlateManager.WellGroupChange>> wellGroupChanges
    ) throws Exception
    {
        for (var entry : wellGroupChanges.entrySet())
        {
            var plate = (PlateImpl) PlateManager.get().requirePlate(container, entry.getKey(), "Failed to update well groups.");
            if (!TsvPlateLayoutHandler.TYPE.equalsIgnoreCase(plate.getAssayType()))
                continue;

            var wellChanges = entry.getValue();
            Map<Pair<WellGroup.Type, String>, List<Position>> wellGroupings = new HashMap<>();

            for (var wellData : PlateManager.get().getWellData(container, user, plate.getRowId(), false, false))
            {
                WellGroup.Type type = wellData.getType();
                String wellGroup = wellData.getWellGroup();

                Integer wellRowId = wellData.getRowId();
                var wellChange = wellChanges.get(wellRowId);
                if (wellChange != null)
                {
                    if (wellChange.type() != null)
                    {
                        String typeStr = StringUtils.trimToNull(wellChange.type());
                        if (typeStr != null)
                            type = WellGroup.Type.valueOf(typeStr);
                        else
                            type = null;
                    }
                    if (wellChange.group() != null)
                        wellGroup = StringUtils.trimToNull(wellChange.group());
                }

                // Type/Group are not set and are not being updated
                if (type == null && wellGroup == null)
                    continue;

                var position = plate.getPosition(wellData.getRow(), wellData.getCol());

                // Specifying a group requires that a type is also specified
                if (type == null)
                {
                    throw new ValidationException(String.format(
                        "Well %s must specify a \"%s\" when a \"%s\" is specified.",
                        position.getDescription(),
                        WellTable.Column.Type.name(),
                        WellTable.Column.WellGroup.name()
                    ));
                }

                var wellGroupKey = Pair.of(type, wellGroup);
                wellGroupings.computeIfAbsent(wellGroupKey, k -> new ArrayList<>()).add(position);
            }

            // Mark pre-existing well groups on this plate for deletion
            for (WellGroup existingWellGroup : plate.getWellGroups())
                plate.markWellGroupForDeletion(existingWellGroup);

            // Create new well groups for this plate
            for (var wellGrouping : wellGroupings.entrySet())
            {
                var typeGroup = wellGrouping.getKey();
                plate.addWellGroup(typeGroup.second, typeGroup.first, wellGrouping.getValue());
            }

            PlateManager.get().savePlateImpl(container, user, plate);
        }
    }

    /**
     * Called from assay-24.009-24.010.sql to rename existing plate metadata fields that might collide with
     * new concentration and amount fields.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static void renameWellMetadataFields(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        Set<String> reservedNames = new CaseInsensitiveHashSet(Column.Amount.name(),
                Column.AmountUnits.name(),
                Column.Concentration.name(),
                Column.ConcentrationUnits.name());

        Set<Container> metadataContainers = new HashSet<>();
        for (Container container : ContainerManager.getAllChildren(ContainerManager.getRoot()))
        {
            if (isBiologicsFolder(container))
                metadataContainers.add(PlateManager.get().getPlateMetadataDomainContainer(container));
        }

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        for (Container container : metadataContainers)
        {
            Domain domain = PlateManager.get().getPlateMetadataDomain(container, User.getAdminServiceUser(), true);
            if (domain != null)
            {
                try (DbScope.Transaction tx = scope.ensureTransaction())
                {
                    boolean dirty = false;
                    for (DomainProperty dp : domain.getProperties())
                    {
                        if (reservedNames.contains(dp.getName()))
                        {
                            String newName = ensureNewName(dp, domain);
                            _log.info(String.format("Renaming plate metadata property %s to %s for folder %s", dp.getName(), newName, container.getPath()));
                            dp.setName(newName);
                            dirty = true;
                        }
                    }

                    if (dirty)
                        domain.save(User.getAdminServiceUser());

                    // create the new fields in the existing domains
                    DomainKind<?> domainKind = domain.getDomainKind();
                    if (domainKind instanceof PlateMetadataDomainKind pmdk)
                    {
                        pmdk.ensureDomainProperties(domain, container);
                        domain.save(User.getAdminServiceUser());
                    }

                    tx.commit();
                }
                catch (Exception e)
                {
                    _log.error(e);
                }
            }
        }
    }

    private static final String METADATA_RENAME_SUFFIX = "_PREV";

    private static String ensureNewName(DomainProperty dp, Domain domain)
    {
        String newName = dp.getName() + METADATA_RENAME_SUFFIX;
        int ordinal = 1;

        while (domain.getPropertyByName(newName) != null)
        {
            newName = String.format("%s%s%d", dp.getName(), METADATA_RENAME_SUFFIX, ordinal++);
        }
        return newName;
    }

    /**
     * Called from assay-24.010-24.011.sql, which populates the new barcode field with formatted rowid values.
     * We then set the barcode DbSequence here to the maximum rowId to ensure subsequently unique values.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static void updateBarcodeSequence(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            SQLFragment sql = new SQLFragment("SELECT MAX(rowId) FROM ").append(AssayDbSchema.getInstance().getTableInfoPlate(), "");
            Integer maxRowId = new SqlSelector(AssayDbSchema.getInstance().getSchema(), sql).getObject(Integer.class);

            if (maxRowId != null)
            {
                TableInfo plateTable = AssayDbSchema.getInstance().getTableInfoPlate();
                DbSequence sequence = DbSequenceManager.get(ContainerManager.getRoot(), PlateTable.PLATE_BARCODE_SEQUENCE);
                sequence.ensureMinimum(maxRowId);
            }

            tx.commit();
        }
    }

    /**
     * Called from assay-24.013-24.014.sql, which adds an LSID column to plate sets. Here, we populate the LSID values.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static void addLsidToPlateSets(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbSchema schema = AssayDbSchema.getInstance().getSchema();
        try (DbScope.Transaction tx = schema.getScope().ensureTransaction())
        {
            TableInfo plateSetTable = AssayDbSchema.getInstance().getTableInfoPlateSet();
            try (Results rs = new TableSelector(plateSetTable).getResults())
            {
                while (rs.next())
                {
                    Map<String, Object> row = rs.getRowMap();
                    Container container = ContainerManager.getForId(rs.getString("Container"));
                    Lsid lsid = PlateManager.get().getLsid(PlateSet.class, container);

                    SQLFragment sql = new SQLFragment("UPDATE ").append(plateSetTable, "")
                            .append(" SET LSID = ?")
                            .add(lsid)
                            .append(" WHERE RowId = ?")
                            .add(row.get("rowId"));
                    new SqlExecutor(schema).execute(sql);
                }
            }
            tx.commit();
        }
    }

    private static void addInsertedValues(List<List<?>> insertedValues, Integer rowId, String... types)
    {
        for (String type : types)
        {
            insertedValues.add(Arrays.asList(rowId, null, null, type));
        }
    }

    /**
     * Called from assay-24.012-24.013.sql, in order to support the ability to add or remove 'built-in' columns from
     * the plate grid view.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static void updateBuiltInColumns(ModuleContext ctx)
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            SQLFragment sqlFragment = new SQLFragment("SELECT DISTINCT plateset FROM assay.Plate WHERE assaytype = '" + TsvPlateLayoutHandler.TYPE + "'");
            ArrayList<Integer> plateSetIds = new SqlSelector(AssayDbSchema.getInstance().getSchema(), sqlFragment).getArrayList(Integer.class);

            Set<Integer> assayPSes = new HashSet<>();
            Set<Integer> primaryPSes = new HashSet<>();
            Set<Integer> templatePSes = new HashSet<>();

            for (Integer plateSetId : plateSetIds)
            {
                PlateSet plateSet = PlateService.get().getPlateSet(ContainerFilter.getUnsafeEverythingFilter(), plateSetId);
                if (plateSet == null)
                    throw new IllegalStateException("updateBuiltInColumns: Plate Set with plate of id " + plateSetId + " not found.");

                SQLFragment sql = new SQLFragment("SELECT template, type FROM assay.PlateSet WHERE rowid = " + plateSet.getRowId() + "");
                Map<String, Object> result = new SqlSelector(AssayDbSchema.getInstance().getSchema(), sql).getMap();

                boolean isTemplatePlateSet = (boolean) result.get("template");
                boolean isAssayPlateSet = result.get("type").equals("assay");
                boolean isPrimaryPlateSet = result.get("type").equals("primary");

                if (isTemplatePlateSet)
                    templatePSes.add(plateSet.getRowId());
                else if (isAssayPlateSet)
                    assayPSes.add(plateSet.getRowId());
                else if (isPrimaryPlateSet)
                    primaryPSes.add(plateSet.getRowId());
            }

            List<List<?>> insertedValues = new LinkedList<>();

            assayPSes.forEach(rowId -> addInsertedValues(insertedValues, rowId, "SampleID", "Type", "WellGroup"));
            primaryPSes.forEach(rowId -> addInsertedValues(insertedValues, rowId, "SampleID"));
            templatePSes.forEach(rowId -> addInsertedValues(insertedValues, rowId, "Type", "WellGroup"));

            String insertSql = "INSERT INTO " + AssayDbSchema.getInstance().getTableInfoPlateSetProperty() +
                    " (plateSetId, propertyId, propertyURI, FieldKey)" +
                    " VALUES (?, CAST(? AS INT), CAST(? AS VARCHAR), CAST(? AS VARCHAR))";
            Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, insertedValues);

            tx.commit();
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static boolean isBiologicsFolder(Container container)
    {
        return container != null && "Biologics".equals(ContainerManager.getFolderTypeName(container));
    }

    /**
     * Called from assay-24.013-24.014.sql, in order to support row level exclusions for plate enabled assays.
     * The upgrade ensures the default assay plate data states as well as creates the result domain qc state field.
     */
    @DeferredUpgrade
    public static void initializeWellExclusions(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            Set<ExpProtocol> protocols = new HashSet<>();
            for (Container container : ContainerManager.getAllChildren(ContainerManager.getRoot()))
            {
                if (isBiologicsFolder(container))
                {
                    PlateDataStateManager.get().ensureDefaultStates(container, User.getAdminServiceUser());
                    protocols.addAll(AssayService.get().getAssayProtocols(container));
                }
            }

            for (ExpProtocol protocol : protocols)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null)
                {
                    if (provider.isPlateMetadataEnabled(protocol))
                    {
                        // ensure the QC state column exists in the result domain
                        Domain resultDomain = provider.getResultsDomain(protocol, true);
                        if (resultDomain.getPropertyByName(AssayResultDomainKind.Column.State.name()) == null)
                        {
                            _log.info(String.format("Adding the %s field to the results domain for assay : %s", AssayResultDomainKind.Column.State.name(), protocol.getName()));
                            DomainProperty dp = resultDomain.addProperty(new PropertyStorageSpec(AssayResultDomainKind.Column.State.name(), JdbcType.INTEGER));
                            dp.setLabel("QC State");
                            dp.setImportAliasSet(Set.of("QCState", "QC State"));
                            dp.setLookup(new Lookup(null, SchemaKey.fromParts(CoreSchema.getInstance().getSchemaName()), CoreSchema.DATA_STATES_TABLE_NAME));
                            dp.setShownInInsertView(false);
                            dp.setShownInUpdateView(false);

                            resultDomain.save(User.getAdminServiceUser());
                        }
                    }
                }
            }
            tx.commit();
        }
    }

    /**
     * Called from assay-24.015-24.016.sql, in order to support hit selection criteria for plate enabled assays.
     * The upgrade creates the run domain hit selection criteria field.
     */
    @DeferredUpgrade
    public static void initializeHitSelectionCriteria(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        try (DbScope.Transaction tx = AssayDbSchema.getInstance().getSchema().getScope().ensureTransaction())
        {
            Set<ExpProtocol> protocols = new HashSet<>();
            for (Container container : ContainerManager.getAllChildren(ContainerManager.getRoot()))
            {
                if (isBiologicsFolder(container))
                    protocols.addAll(AssayService.get().getAssayProtocols(container));
            }

            for (ExpProtocol protocol : protocols)
            {
                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider != null && provider.isPlateMetadataEnabled(protocol))
                {
                    // ensure the QC state column exists in the result domain
                    Domain runDomain = provider.getRunDomain(protocol, true);
                    if (runDomain != null && runDomain.getPropertyByName(HIT_SELECTION_CRITERIA_COLUMN_NAME) == null)
                    {
                        _log.info("Adding the \"{}\" field to the run domain for assay : {}", HIT_SELECTION_CRITERIA_COLUMN_NAME, protocol.getName());
                        DomainProperty dp = runDomain.addProperty(new PropertyStorageSpec(HIT_SELECTION_CRITERIA_COLUMN_NAME, JdbcType.VARCHAR));
                        dp.setShownInInsertView(false);
                        dp.setShownInUpdateView(false);

                        runDomain.save(User.getAdminServiceUser());
                    }
                }
            }

            tx.commit();
        }
    }

    /**
     * Called from assay-25.000-25.001.sql
     * Migrate replicate well groups to be represented via the "Replicate Group" column.
     */
    @SuppressWarnings({"UnusedDeclaration"})
    public static void migrateReplicateGroups(ModuleContext ctx) throws Exception
    {
        if (ctx.isNewInstall())
            return;

        DbScope scope = AssayDbSchema.getInstance().getSchema().getScope();

        // - For all "Standard" plates that have a "REPLICATE" well group:
        //   - If the plate does not contain a "SAMPLE" zone, then create one
        //   - For each "REPLICATE" well group:
        //     - Get all the wells that are in the group
        //     - Add those wells to the "SAMPLE" zone. This will be done in the assay.WellGroupPositions table

        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            long numPlatesWithReplicates;
            {
                SQLFragment plateRowIdsSql = new SQLFragment("""
                    SELECT DISTINCT P.RowId
                    FROM assay.Plate AS P
                    INNER JOIN assay.WellGroup AS WG ON WG.PlateId = P.RowId
                    WHERE P.AssayType = ? AND WG.TypeName = ?
                """)
                .add("Standard")   // TsvPlateLayoutHandler.TYPE
                .add("REPLICATE"); // WellGroup.Type.REPLICATE

                numPlatesWithReplicates = new SqlSelector(scope, plateRowIdsSql).getRowCount();
            }

            if (numPlatesWithReplicates > 0)
            {
                ensureSampleZoneWellGroups(scope);

                // Insert missing well positions into the "SAMPLE" well groups where name is NULL
                {
                    SQLFragment sql = new SQLFragment("""
                        INSERT INTO assay.WellGroupPositions (wellGroupId, wellId)
                        SELECT sampleWG.RowId AS wellGroupId, replicateWGP.wellId
                        FROM assay.WellGroup AS replicateWG
                        INNER JOIN assay.WellGroupPositions AS replicateWGP ON replicateWG.RowId = replicateWGP.wellGroupId
                        INNER JOIN assay.WellGroup AS sampleWG ON sampleWG.PlateId = replicateWG.PlateId
                        INNER JOIN assay.Plate AS P ON P.RowId = replicateWG.PlateId
                        LEFT JOIN assay.WellGroupPositions AS sampleWGP ON sampleWG.RowId = sampleWGP.wellGroupId AND replicateWGP.wellId = sampleWGP.wellId
                        WHERE P.AssayType = ? AND replicateWG.TypeName = ? AND sampleWG.TypeName = ? AND sampleWG.Name IS NULL AND sampleWGP.wellId IS NULL
                    """)
                    .add("Standard")  // TsvPlateLayoutHandler.TYPE
                    .add("REPLICATE") // WellGroup.Type.REPLICATE;
                    .add("SAMPLE");   // WellGroup.Type.SAMPLE;

                    new SqlExecutor(scope).execute(sql);
                }
            }

            // Display the ReplicateGroup column by default on each plate set where WellGroup is currently displayed
            {
                SQLFragment sql = new SQLFragment("""
                    INSERT INTO assay.PlateSetProperty (PlateSetId, FieldKey)
                    SELECT DISTINCT PSP.PlateSetId, ? AS FieldKey
                    FROM assay.PlateSetProperty AS PSP WHERE PSP.FieldKey = ?
                """)
                .add("ReplicateGroup") // WellTable.Column.ReplicateGroup
                .add("WellGroup");     // WellTable.Column.WellGroup

                new SqlExecutor(scope).execute(sql);
            }

            tx.commit();
        }
    }

    private static void ensureSampleZoneWellGroups(DbScope scope) throws Exception
    {
        List<List<?>> sampleZoneRowValues = new ArrayList<>();

        // Determine the set of plates that do not yet have a "SAMPLE" well group that does not have a name (a.k.a. "Sample zone").
        SQLFragment needSampleZoneSql = new SQLFragment("""
            SELECT DISTINCT P.RowId, P.Container, WG.Template
            FROM assay.Plate AS P
            INNER JOIN assay.WellGroup AS WG ON WG.PlateId = P.RowId
            WHERE P.AssayType = ? AND WG.TypeName = ?
            AND P.RowId NOT IN (
                SELECT PP.RowId
                FROM assay.Plate AS PP
                INNER JOIN assay.WellGroup AS WGG ON WGG.PlateId = PP.RowId
                WHERE PP.AssayType = ? AND WGG.TypeName = ? AND WGG.Name IS NULL
            )
            ORDER BY P.RowId
        """)
        .add("Standard")  // TsvPlateLayoutHandler.TYPE
        .add("REPLICATE") // WellGroup.Type.REPLICATE;
        .add("Standard")  // TsvPlateLayoutHandler.TYPE
        .add("SAMPLE");   // WellGroup.Type.SAMPLE;

        try (ResultSet rs = new SqlSelector(scope, needSampleZoneSql).getResultSet())
        {
            Map<String, Container> containers = new HashMap<>();
            while (rs.next())
            {
                int plateRowId = rs.getInt("RowId");
                String containerId = rs.getString("Container");
                boolean template = rs.getBoolean("Template");

                Container container = containers.computeIfAbsent(containerId, ContainerManager::getForId);
                if (container == null)
                {
                    // This is never expected to occur due to schema constraint of foreign key assay.WellGroup.Container -> core.Containers
                    throw new IllegalStateException(String.format("Unable to resolve container with entityId \"%s\" for plate rowId (%d).", containerId, plateRowId));
                }

                Lsid lsid = PlateManager.get().getLsid(WellGroup.class, container);
                sampleZoneRowValues.add(List.of(plateRowId, lsid.toString(), container.getEntityId(), template, "SAMPLE")); // WellGroup.Type.SAMPLE;
            }
        }

        if (!sampleZoneRowValues.isEmpty())
        {
            String insertSql = "INSERT INTO assay.WellGroup (PlateId, LSID, Container, Template, TypeName) VALUES (?, ?, ?, ?, ?)";
            Table.batchExecute(AssayDbSchema.getInstance().getSchema(), insertSql, sampleZoneRowValues);
        }

        _log.info("Inserted {} new \"sample zone\" well groups.", sampleZoneRowValues.size());
    }
}
