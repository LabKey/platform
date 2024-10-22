package org.labkey.experiment.lineage;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.IdentifiableBase;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.api.ExpLineageService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.experiment.api.Data;
import org.labkey.experiment.api.ExperimentRun;
import org.labkey.experiment.api.ExperimentServiceImpl;
import org.labkey.experiment.api.Material;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.labkey.api.data.CompareType.IN;

public class ExpLineageServiceImpl implements ExpLineageService
{
    private static final Logger LOG = LogHelper.getLogger(ExpLineageServiceImpl.class, "Experiment lineage service");

    public static ExpLineageServiceImpl get()
    {
        return (ExpLineageServiceImpl) ExpLineageService.get();
    }

    public record LineageResult(
        Set<Identifiable> seeds,
        Set<ExpLineage.Edge> edges,
        Set<Integer> dataIds,
        Set<Integer> materialIds,
        Set<Integer> runIds,
        Set<String> objectLsids
    )
    {
        public boolean isEmpty()
        {
            return edges.isEmpty();
        }
    }

    private record SeedIdentifiers(Set<Integer> objectIds, Set<String> lsids)
    {
        public boolean isEmpty()
        {
            return objectIds.isEmpty();
        }
    }

    private SeedIdentifiers getLineageSeedIdentifiers(Container c, User user, @NotNull Set<Identifiable> seeds)
    {
        // validate seeds
        Set<Integer> seedObjectIds = new HashSet<>(seeds.size());
        Set<String> seedLsids = new HashSet<>(seeds.size());

        for (Identifiable seed : seeds)
        {
            if (seed.getLSID() == null)
                throw new RuntimeException("Lineage not available for unknown object");

            // CONSIDER: add objectId to Identifiable?
            int objectId = -1;
            if (seed instanceof ExpObject expObjectSeed)
                objectId = expObjectSeed.getObjectId();
            else if (seed instanceof IdentifiableBase identifiableSeed)
                objectId = identifiableSeed.getObjectId();

            if (objectId == -1)
                throw new RuntimeException("Lineage not available for unknown object: " + seed.getLSID());

            if (seed instanceof ExpRunItem && ExperimentServiceImpl.get().isUnknownMaterial((ExpRunItem) seed))
            {
                LOG.warn("Lineage not available for unknown material: " + seed.getLSID());
                continue;
            }

            // ensure the user has read permission in the seed container
            if (c != null && !c.equals(seed.getContainer()))
            {
                if (!seed.getContainer().hasPermission(user, ReadPermission.class))
                    throw new UnauthorizedException("Lineage not available. User does not have permission to view seed \"" + seed.getLSID() + "\".");
            }

            if (!seedLsids.add(seed.getLSID()))
                throw new RuntimeException("Requested lineage for duplicate LSID seed: " + seed.getLSID());

            if (!seedObjectIds.add(objectId))
                throw new RuntimeException("Requested lineage for duplicate objectId seed: " + objectId);
        }

        return new SeedIdentifiers(seedObjectIds, seedLsids);
    }

    public LineageResult getLineageResult(Container container, User user, @NotNull Set<Identifiable> seeds, @NotNull ExpLineageOptions options)
    {
        var seedIdentifiers = getLineageSeedIdentifiers(container, user, seeds);

        if (seedIdentifiers.isEmpty())
            return new LineageResult(seeds, emptySet(), emptySet(), emptySet(), emptySet(), emptySet());

        // Side-effect
        options.setUseObjectIds(true);

        var edges = new HashSet<ExpLineage.Edge>();
        for (var seed : seeds)
        {
            // create additional edges from the run for each ExpMaterial or ExpData seed
            if (seed instanceof ExpRunItem runSeed && !ExperimentServiceImpl.get().isUnknownMaterial(runSeed))
            {
                var pair = ExperimentServiceImpl.get().collectRunsAndRolesToInvestigate(runSeed, options);

                // add edges for initial runs and roles up
                for (Map.Entry<String, String> runAndRole : pair.first.entrySet())
                    edges.add(new ExpLineage.Edge(runAndRole.getKey(), seed.getLSID()));

                // add edges for initial runs and roles down
                for (Map.Entry<String, String> runAndRole : pair.second.entrySet())
                    edges.add(new ExpLineage.Edge(seed.getLSID(), runAndRole.getKey()));
            }
        }

        var dataIds = new HashSet<Integer>();
        var materialIds = new HashSet<Integer>();
        var runIds = new HashSet<Integer>();
        var objectLsids = new HashSet<String>();

        var sql = ExperimentServiceImpl.get().generateExperimentTreeSQLObjectIdsSeeds(seedIdentifiers.objectIds(), options);
        new SqlSelector(ExperimentServiceImpl.get().getExpSchema(), sql).forEachMap((m)->
        {
            Integer depth = (Integer) m.get("depth");
            String parentLSID = (String) m.get("parent_lsid");
            String childLSID = (String) m.get("child_lsid");

            String parentExpType = (String) m.get("parent_exptype");
            String childExpType = (String) m.get("child_exptype");

            Integer parentRowId = (Integer )m.get("parent_rowid");
            Integer childRowId = (Integer) m.get("child_rowid");

            if (parentRowId == null || childRowId == null)
            {
                LOG.error(String.format("Node not found for lineage: %s.\n  depth=%d, parentLsid=%s, parentType=%s, parentRowId=%d, childLsid=%s, childType=%s, childRowId=%d",
                        StringUtils.join(seedIdentifiers.lsids(), ", "), depth, parentLSID, parentExpType, parentRowId, childLSID, childExpType, childRowId));
            }
            else
            {
                edges.add(new ExpLineage.Edge(parentLSID, childLSID));

                // Don't include the seed in the lineage collections
                if (!seedIdentifiers.lsids().contains(parentLSID))
                {
                    // process parents
                    if (ExpData.DEFAULT_CPAS_TYPE.equals(parentExpType))
                        dataIds.add(parentRowId);
                    else if (ExpMaterial.DEFAULT_CPAS_TYPE.equals(parentExpType))
                        materialIds.add(parentRowId);
                    else if (ExpRun.DEFAULT_CPAS_TYPE.equals(parentExpType))
                        runIds.add(parentRowId);
                    else if (ExpObject.DEFAULT_CPAS_TYPE.equals(parentExpType))
                        objectLsids.add(parentLSID);
                }

                // Don't include the seed in the lineage collections
                if (!seedIdentifiers.lsids().contains(childLSID))
                {
                    // process children
                    if (ExpData.DEFAULT_CPAS_TYPE.equals(childExpType))
                        dataIds.add(childRowId);
                    else if (ExpMaterial.DEFAULT_CPAS_TYPE.equals(childExpType))
                        materialIds.add(childRowId);
                    else if (ExpRun.DEFAULT_CPAS_TYPE.equals(childExpType))
                        runIds.add(childRowId);
                    else if (ExpObject.DEFAULT_CPAS_TYPE.equals(childExpType))
                        objectLsids.add(childLSID);
                }
            }
        });

        return new LineageResult(seeds, edges, dataIds, materialIds, runIds, objectLsids);
    }

    @Override
    public @NotNull ExpLineage getLineage(Container c, User user, @NotNull Identifiable start, @NotNull ExpLineageOptions options)
    {
        return getLineage(c, user, Set.of(start), options);
    }

    @NotNull
    public ExpLineage getLineage(Container c, User user, @NotNull Set<Identifiable> seeds, @NotNull ExpLineageOptions options)
    {
        LineageResult result = getLineageResult(c, user, seeds, options);
        if (result.isEmpty())
            return new ExpLineage(result.seeds(), emptySet(), emptySet(), emptySet(), emptySet(), emptySet());

        Map<GUID, Boolean> hasPermission = new HashMap<>();
        final Predicate<Identifiable> lineageItemFilter = (item) -> {
            if (item == null)
                return false;

            return hasPermission.computeIfAbsent(item.getContainer().getEntityId(), (id) -> item.getContainer().hasPermission(user, ReadPermission.class));
        };

        LsidManager lsidManager = LsidManager.get();
        ExperimentServiceImpl expSvc = ExperimentServiceImpl.get();
        Set<ExpData> data = expSvc.getExpDatas(result.dataIds).stream().filter(lineageItemFilter).collect(toSet());
        Set<ExpMaterial> materials = expSvc.getExpMaterials(result.materialIds).stream().filter(lineageItemFilter).collect(toSet());
        Set<ExpRun> runs = expSvc.getExpRuns(result.runIds).stream().filter(lineageItemFilter).collect(toSet());
        Set<Identifiable> otherObjects = result.objectLsids.stream().map(lsidManager::getObject).filter(lineageItemFilter).collect(toSet());

        return new ExpLineage(seeds, data, materials, runs, otherObjects, result.edges);
    }

    private record StreamContext(
        User user,
        ApiJsonWriter writer,
        ExperimentJSONConverter.Settings settings,
        Map<String, ExpLineage.Edges> nodesAndEdges,
        Map<GUID, Boolean> hasPermission
    )
    {
        boolean hasPermission(Container container)
        {
            return this.hasPermission.computeIfAbsent(container.getEntityId(), (id) -> container.hasPermission(user, ReadPermission.class));
        }

        @NotNull
        ExpLineage.Edges popEdges(String lsid)
        {
            var edges = this.nodesAndEdges.get(lsid);
            this.nodesAndEdges.remove(lsid);
            return edges == null ? ExpLineage.Edges.emptyEdges : edges;
        }
    }

    public void streamLineage(Container container, User user, HttpServletResponse response, Set<Identifiable> seeds, ExpLineageOptions options) throws IOException
    {
        var lineage = getLineageResult(container, user, seeds, options);

        var context = new StreamContext(
            user,
            new ApiJsonWriter(response),
            new ExperimentJSONConverter.Settings(options.isIncludeProperties(), options.isIncludeInputsAndOutputs(), options.isIncludeRunSteps()),
            ExpLineage.processEdges(lineage.edges()),
            new HashMap<>()
        );

        context.writer.startResponse();

        writeNodes(lineage, context);
        writeSeed(lineage, context, options);

        context.writer.endResponse();
    }

    private static void writeNodes(LineageResult lineage, StreamContext context) throws IOException
    {
        context.writer.startObject("nodes");

        if (!context.nodesAndEdges.isEmpty())
        {
            if (!lineage.dataIds.isEmpty())
                writeNodes(lineage.dataIds, ExperimentServiceImpl.get().getTinfoData(), Data.class, context);
            if (!lineage.materialIds.isEmpty())
                writeNodes(lineage.materialIds, ExperimentServiceImpl.get().getTinfoMaterial(), Material.class, context);
            if (!lineage.runIds.isEmpty())
                writeNodes(lineage.runIds, ExperimentServiceImpl.get().getTinfoExperimentRun(), ExperimentRun.class, context);
            if (!lineage.objectLsids.isEmpty())
            {
                var lsidManager = LsidManager.get();
                lineage.objectLsids.stream().map(lsidManager::getObject).filter(Objects::nonNull).forEach((node) -> writeNode(node, context));
            }
        }

        for (var seed : lineage.seeds())
            writeNode(seed, context);

        // Write out the edges for any node that was not previously written
        // (e.g. not resolved, user does not have permissions to read, etc.).
        for (var entry : context.nodesAndEdges.entrySet())
            context.writer.writeProperty(entry.getKey(), nodeToJson(null, entry.getValue(), context));

        context.writer.endObject();
    }

    private static <T extends Identifiable> void writeNodes(Set<Integer> rowIds, TableInfo table, Class<T> clazz, StreamContext context)
    {
        new TableSelector(table, new SimpleFilter(FieldKey.fromParts("RowId"), rowIds, IN), null).forEach(clazz, (node) -> writeNode(node.getExpObject(), context));
    }

    private static void writeNode(Identifiable node, StreamContext context)
    {
        if (node == null)
            return;

        try
        {
            if (context.hasPermission(node.getContainer()))
                context.writer.writeProperty(node.getLSID(), nodeToJson(node, context.popEdges(node.getLSID()), context));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject nodeToJson(@Nullable Identifiable node, ExpLineage.Edges edges, StreamContext context)
    {
        JSONObject json;

        if (node == null)
            json = new JSONObject();
        else
        {
            json = ExperimentJSONConverter.serialize(node, context.user, context.settings);
            json.put("type", node.getLSIDNamespacePrefix());
        }

        json.put("parents", edges.parents().stream().map(ExpLineage.Edge::toParentJSON).toList());
        json.put("children", edges.children().stream().map(ExpLineage.Edge::toChildJSON).toList());

        return json;
    }

    private static void writeSeed(LineageResult lineage, StreamContext context, ExpLineageOptions options) throws IOException
    {
        // If the request was made with a single 'seed' property, use single 'seed' property in the response
        // otherwise, include an array of 'seed' regardless of the number of seed items.
        if (options.isSingleSeedRequested())
            context.writer.writeProperty("seed", lineage.seeds().stream().findFirst().orElseThrow().getLSID());
        else
            context.writer.writeProperty("seeds", lineage.seeds().stream().map(Identifiable::getLSID).toList());
    }
}
