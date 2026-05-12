package org.labkey.assay.plate;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.BeforeClass;
import org.junit.Test;
import org.labkey.api.assay.AbstractAssayTsvDataHandler;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayResultDomainKind;
import org.labkey.api.assay.AssayRunUploadContext;
import org.labkey.api.assay.AssaySchema;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.SimpleAssayDataImportHelper;
import org.labkey.api.assay.TsvDataHandler;
import org.labkey.api.assay.plate.AssayPlateMetadataService;
import org.labkey.api.assay.plate.ExcelPlateReader;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateDataStateManager;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.plate.PlateSet;
import org.labkey.api.assay.plate.PlateType;
import org.labkey.api.assay.plate.PlateUtils;
import org.labkey.api.assay.plate.Position;
import org.labkey.api.assay.plate.PositionImpl;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.assay.plate.WellCustomField;
import org.labkey.api.assay.plate.WellGroup;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.LongHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.Results;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.qc.DataState;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.security.roles.Role;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.assay.TSVProtocolSchema;
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.plate.query.PlateSchema;
import org.labkey.assay.plate.query.PlateTable;
import org.labkey.assay.plate.query.WellTable;
import org.labkey.assay.query.AssayDbSchema;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.labkey.api.assay.AssayRunUploadContext.ReImportOption.MERGE_DATA;
import static org.labkey.api.util.IntegerUtils.asLongElseNull;
import static org.labkey.api.util.IntegerUtils.integerEquals;

public class AssayPlateMetadataServiceImpl implements AssayPlateMetadataService
{
    private static final Logger LOG = LogHelper.getLogger(AssayPlateMetadataServiceImpl.class, "Plate Metadata Logger");

    @Override
    public DataIteratorBuilder mergePlateMetadata(
        Container container,
        User user,
        Long plateSetId,
        DataIteratorBuilder rows,
        AssayProvider provider,
        ExpProtocol protocol
    )
    {
        Domain resultDomain = provider.getResultsDomain(protocol);
        DomainProperty plateProperty = resultDomain.getPropertyByName(AssayResultDomainKind.Column.Plate.name());
        DomainProperty wellLocationProperty = resultDomain.getPropertyByName(AssayResultDomainKind.Column.WellLocation.name());

        return DataIteratorUtil.mapTransformer(rows, cols ->
        {
            List<String> result = new ArrayList<>(cols);
            Domain plateDomain = PlateManager.get().getPlateMetadataDomain(container, user);
            if (plateDomain != null)
            {
                result.addAll(plateDomain.getProperties().stream().map(ImportAliasable::getName).toList());
            }
            result.add("SampleID");
            result.add("SampleName");
            return result;
        }, new Function<>()
        {
            final Map<Object, Pair<Plate, Map<Position, WellBean>>> plateIdentifierMap = new HashMap<>();
            final ContainerFilter cf = PlateManager.get().getPlateContainerFilter(protocol, container, user);
            int rowCounter = 0;
            final Map<Long, ExpMaterial> sampleMap = new LongHashMap<>();
            final CaseInsensitiveMapWrapper<Object> sharedCasing = new CaseInsensitiveMapWrapper<>(new HashMap<>());

            @Override
            public Map<String, Object> apply(Map<String, Object> row)
            {
                // ensure the result data includes a wellLocation field with position value (e.g., A1, F12, etc.)
                Object wellLocation = PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, row);
                if (wellLocation == null)
                    throw new RuntimeValidationException("Imported data must contain a WellLocation column to support plate metadata integration.");

                // Copy so we can put new values
                row = new CaseInsensitiveMapWrapper<>(new HashMap<>(row), sharedCasing);

                // include metadata that may have been applied directly to the plate
                rowCounter++;

                Object plateIdentifier = PropertyService.get().getDomainPropertyValueFromRow(plateProperty, row);
                if (plateIdentifier == null)
                    throw new RuntimeValidationException("Unable to resolve plate identifier for results row (" + rowCounter + ").");

                plateIdentifierMap.computeIfAbsent(plateIdentifier, k -> {
                    Plate plate = PlateService.get().getPlate(cf, plateSetId, plateIdentifier);
                    if (plate == null)
                        throw new RuntimeValidationException("Unable to resolve the plate \"" + plateIdentifier + "\" for the results row (" + rowCounter + ").");

                    return Pair.of(plate, new HashMap<>());
                });
                Plate plate = plateIdentifierMap.get(plateIdentifier).first;

                // if the plate identifier is the plate name, we need to make sure it resolves during importRows
                // so replace it with the plateId (which will be unique)
                if (!StringUtils.isNumeric(plateIdentifier.toString()))
                    PropertyService.get().replaceDomainPropertyValue(plateProperty, row, plate.getPlateId());

                // create the map of well locations to the well for the given plate
                Map<Position, WellBean> positionToWell = plateIdentifierMap.get(plateIdentifier).second;
                if (positionToWell.isEmpty())
                {
                    SimpleFilter filter = SimpleFilter.createContainerFilter(plate.getContainer());
                    filter.addCondition(WellTable.Column.PlateId.fieldKey(), plate.getRowId());
                    Set<Long> wellSamples = new HashSet<>();
                    for (WellBean well : new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, null).getArrayList(WellBean.class))
                    {
                        positionToWell.put(new PositionImpl(plate.getContainer(), well.getRow(), well.getCol()), well);
                        if (well.getSampleId() != null && !sampleMap.containsKey(well.getSampleId()))
                            wellSamples.add(well.getSampleId());
                    }

                    if (!wellSamples.isEmpty())
                    {
                        // stash away any samples associated with the plate
                        ExperimentService.get().getExpMaterials(wellSamples).forEach(s -> sampleMap.put(s.getRowId(), s));
                    }
                }

                PositionImpl well = new PositionImpl(null, String.valueOf(wellLocation));
                // need to adjust the column value to be 0-based to match the template locations
                well.setColumn(well.getColumn() - 1);

                if (!positionToWell.containsKey(well))
                    throw new RuntimeValidationException("Unable to resolve well \"" + wellLocation + "\" for plate \"" + plate.getName() + "\".");

                WellBean wellBean = positionToWell.get(well);
                for (WellCustomField customField : PlateManager.get().getWellCustomFields(user, plate, wellBean.getRowId()))
                    row.put(customField.getName(), customField.getValue());

                // Issue 50276: include the sample information from the well
                if (!sampleMap.isEmpty())
                {
                    ExpMaterial sample = sampleMap.get(wellBean.getSampleId());
                    row.put("SampleID", sample != null ? sample.getRowId() : null);
                    row.put("SampleName", sample != null ? sample.getName() : null);
                }

                return row;
            }
        });
    }

    private List<? extends Plate> getPlatesForPlateSet(
        Container container,
        User user,
        Long plateSetId,
        ExpProtocol protocol
    ) throws ExperimentException
    {
        // get the ordered list of plates for the plate set
        ContainerFilter cf = PlateManager.get().getPlateContainerFilter(protocol, container, user);
        PlateSet plateSet = PlateManager.get().getPlateSet(cf, plateSetId);
        if (plateSet == null)
            throw new ExperimentException("Plate set " + plateSetId + " not found.");
        if (plateSet.isTemplate())
            throw new ExperimentException(String.format("Plate set \"%s\" is a template plate set. Template plate sets do not support associating assay data.", plateSet.getName()));

        return PlateManager.get().getPlatesForPlateSet(plateSet);
    }

    @Override
    public DataIteratorBuilder parsePlateData(
        Container container,
        User user,
        @NotNull AssayRunUploadContext<?> context,
        ExpData data,
        AssayProvider provider,
        ExpProtocol protocol,
        Long plateSetId,
        FileLike dataFile,
        DataLoaderSettings settings
    ) throws ExperimentException
    {
        // get the ordered list of plates for the plate set
        List<? extends Plate> plates = getPlatesForPlateSet(container, user, plateSetId, protocol);
        if (plates.isEmpty())
            throw new ExperimentException("No plates were found for the plate set (" + plateSetId + ").");
        PlateSet plateSet = plates.getFirst().getPlateSet();

        List<Map<String, Object>> rows = _parsePlateData(container, user, data, provider, protocol, plateSet, plates, dataFile, settings);

        if (context.getReRunId() != null && context.getReImportOption() != MERGE_DATA)
        {
            // remove hit selections if we are replacing a run
            ExpRun prevRun = ExperimentService.get().getExpRun(context.getReRunId());
            if (prevRun != null)
                PlateManager.get().deleteHits(FieldKey.fromParts("RunId"), List.of(prevRun));
        }
        return MapDataIterator.of(rows);
    }

    /**
     * Parses the plate data file which can be either in the tabular or graphical format.
     */
    private List<Map<String, Object>> _parsePlateData(
        Container container,
        User user,
        ExpData data,
        AssayProvider provider,
        ExpProtocol protocol,
        PlateSet plateSet,
        List<? extends Plate> plates,
        FileLike dataFile,
        DataLoaderSettings settings
    ) throws ExperimentException
    {
        Domain dataDomain = provider.getResultsDomain(protocol);
        try (DataLoader loader = AbstractAssayTsvDataHandler.createLoaderForImport(dataFile, data.getRun(), dataDomain, settings, true))
        {
            DataIteratorBuilder dataRows = (diContext) -> loader.getDataIterator(diContext);
            // we can use the data loader to parse tabular plate data, if the data is in the graphical grid
            // Excel format, we will need to parse the file directly.
            try (MapDataIterator i = DataIteratorUtil.wrapMap(dataRows.getDataIterator(new DataIteratorContext()), false))
            {
                List<Map<String, Object>> rawRows = i.stream().toList();

                if (isGridFormat(rawRows))
                {
                    List<Map<String, Object>> gridRows = parsePlateGrids(container, user, provider, protocol, plateSet, plates, dataFile);

                    // best attempt at returning something we can import
                    return gridRows.isEmpty() && !rawRows.isEmpty() ? rawRows : gridRows;
                }

                return parsePlateRows(provider, protocol, plates, rawRows);
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
        }
    }

    @Override
    public @Nullable Long getPlateSetId(AssayRunUploadContext<?> context, AssayProvider provider, ExpProtocol protocol) throws ExperimentException
    {
        Domain runDomain = provider.getRunDomain(protocol);
        DomainProperty propertyPlateSet = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME);
        if (propertyPlateSet == null)
        {
            throw new ExperimentException("The assay run domain for the assay '" + protocol.getName() + "' does not contain a plate set property.");
        }

        Map<DomainProperty, String> runProps = context.getRunProperties();
        Object plateSetVal = runProps.getOrDefault(propertyPlateSet, null);
        return plateSetVal != null ? Long.parseLong(String.valueOf(plateSetVal)) : null;
    }

    @Override
    public DataIteratorBuilder mergeReRunData(
            Container container,
            User user,
            @NotNull AssayRunUploadContext<?> context,
            DataIterator resultData,
            AssayProvider provider,
            ExpProtocol protocol,
            ExpData data
    ) throws ExperimentException
    {
        Long plateSetId = getPlateSetId(context, provider, protocol);
        List<? extends Plate> plates = getPlatesForPlateSet(container, user, plateSetId, protocol);
        if (plates.isEmpty())
            throw new ExperimentException("No plates were found for the plate set (" + plateSetId + ").");

        ExpRun run = ExperimentService.get().getExpRun(context.getReRunId());
        if (run == null)
            throw new ExperimentException(String.format("Unable to resolve the replaced run with ID : %d", context.getReRunId()));

        // incoming plate data has precedence over any previous plate data.
        Map<Object, Plate> plateMap = new HashMap<>();
        for (var p : plates)
        {
            plateMap.put(p.getRowId(), p);
            plateMap.put(p.getPlateId(), p);
        }

        List<Map<String, Object>> rows = resultData.stream().toList();
        Set<Object> incomingPlates = new HashSet<>();       // incoming plates may be either row IDs or plate IDs
        for (var row : rows)
        {
            var plateId = row.get(AssayResultDomainKind.Column.Plate.name());
            // plateMap contains Strings and Longs
            if (asLongElseNull(plateId) instanceof Long l)
                plateId = l;
            if (plateId != null)
                incomingPlates.add(plateId);
        }

        // parse the existing run data and combine with any new data
        AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
        TableInfo resultsTable = schema.createDataTable(null, false);
        if (resultsTable == null)
            throw new ExperimentException(String.format("Unable to query the assay results for protocol : %s", protocol.getName()));

        // The plate identifier is either a row ID or plate ID on incoming data, need to match that when merging existing data.
        FieldKey plateFieldKey = AssayResultDomainKind.Column.Plate.fieldKey();
        // Note that in the case where there is a transform script on the assay design, the LK data parsing might not have
        // found any rows, and we might be deferring to the transform script to do that parsing. This block of code should
        // be able to proceed in that case by just passing through all run results to the transform script for the run being replaced.
        if (!rows.isEmpty())
        {
            Object plateObj = rows.getFirst().get(AssayResultDomainKind.Column.Plate.name());
            if (plateObj instanceof String)
                plateFieldKey = FieldKey.fromParts(AssayResultDomainKind.Column.Plate.name(), PlateTable.Column.PlateId.name());
        }

        FieldKey finalPlateFieldKey = plateFieldKey;
        List<FieldKey> columns = resultsTable.getDomain().getProperties().stream().map(dp -> {
            if (dp.getName().equalsIgnoreCase(AssayResultDomainKind.Column.Plate.name()))
                return finalPlateFieldKey;
            return FieldKey.fromParts(dp.getName());
        }).toList();
        Map<FieldKey, ColumnInfo> columnInfoMap = QueryService.get().getColumns(resultsTable, columns);

        List<Map<String, Object>> newRows = new ArrayList<>();
        Set<Long> prevPlateRowIDs = new HashSet<>();

        try
        {
            SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("Run"), run.getRowId());
            try (Results results = QueryService.get().select(resultsTable, columnInfoMap.values(), filter, new Sort(FieldKey.fromParts("RowId"))))
            {
                while (results.next())
                {
                    Object plate = results.getObject(plateFieldKey);
                    // plateMap contains Strings and Longs
                    if (asLongElseNull(plate) instanceof Long l)
                        plate = l;
                    if (plateMap.containsKey(plate) && !incomingPlates.contains(plate))
                    {
                        Map<String, Object> row = new HashMap<>();
                        Map<FieldKey, Object> rowMap = results.getFieldKeyRowMap();
                        for (Map.Entry<FieldKey, ColumnInfo> entry : columnInfoMap.entrySet())
                        {
                            if (rowMap.containsKey(entry.getKey()))
                                row.put(entry.getValue().getName(), rowMap.get(entry.getKey()));
                        }
                        row.put(AssayResultDomainKind.Column.Plate.name(), plate);
                        newRows.add(row);
                        prevPlateRowIDs.add(plateMap.get(plate).getRowId());
                    }
                }
            }
        }
        catch (Throwable e)
        {
            throw UnexpectedException.wrap(e);
        }
        // add incoming data at the end
        newRows.addAll(rows);

        if (!prevPlateRowIDs.isEmpty())
        {
            try (DbScope.Transaction tx = AssayDbSchema.getInstance().getScope().ensureTransaction())
            {
                FileLike dataFile = data.getFileLike();

                // replace the contents of the uploaded data file with the new combined data
                FileLike dir = dataFile.getParent() != null ? dataFile.getParent() : AssayFileWriter.ensureUploadDirectory(container);
                String newName = FileUtil.getBaseName(dataFile.toNioPathForRead().toFile()) + ".tsv";
                FileLike newPath = FileUtil.findUniqueFileName(newName, dir);
                try (TSVMapWriter writer = new TSVMapWriter(newRows))
                {
                    writer.write(newPath.toNioPathForWrite().toFile());
                    dataFile.delete();
                }
                catch (IOException e)
                {
                    throw new ExperimentException(e);
                }

                // update the ExpData file URI
                data = ensureExpDataForRun(data);
                data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(newPath.toNioPathForRead().toFile()).toURI());
                data.setName(String.format("%s (merged with previous run)", newName));
                data.save(user);

                // Remove all hit selections that we don't plan on carrying forward to the new run
                // (which will happen in the PlateMetadataImportHelper). These would be all selections
                // that aren't associated with plates merged from the previous run
                DbScope scope = AssayDbSchema.getInstance().getScope();

                SQLFragment sql = new SQLFragment("SELECT AR.rowId FROM ").append(resultsTable, "AR")
                        .append(" JOIN ").append(ExperimentService.get().getTinfoData(), "ED")
                        .append(" ON AR.dataid = ED.rowid")
                        .append(" WHERE ED.runId = ? ").add(run.getRowId())
                        .append(" AND AR.plate NOT IN (")
                        .append(StringUtils.repeat("?", ", ", prevPlateRowIDs.size()))
                        .append(")")
                        .addAll(prevPlateRowIDs);

                List<Long> rowIds = new SqlSelector(scope, sql).getArrayList(Long.class);
                if (!rowIds.isEmpty())
                    PlateManager.get().deleteHits(protocol.getRowId(), rowIds);

                tx.commit();
            }
        }
        else
        {
            // no previous plate data carried forward, remove all hits from the previous run
            PlateManager.get().deleteHits(FieldKey.fromParts("RunId"), List.of(run));
        }

        if (!prevPlateRowIDs.isEmpty())
            rows = newRows;

        return MapDataIterator.of(rows);
    }

    /**
     * The ExpData parameter passed into the merge function isn't always the object representing the uploaded data.
     * The data transformer will create a fake object to pass the data in when creating the parsed data outputs. In
     * this case find the one attached to the run representing the object in the database.
     */
    private ExpData ensureExpDataForRun(ExpData expData)
    {
        if (expData.getSourceApplication() == null)
        {
            for (ExpData data : expData.getRun().getDataOutputs())
            {
                if (data.getDataType()!= null && data.getDataType().getNamespacePrefix().equalsIgnoreCase(TsvDataHandler.NAMESPACE))
                {
                    if (data.getSourceApplication() != null)
                        return data;
                }
            }
        }
        return expData;
    }

    private boolean isGridFormat(List<Map<String, Object>> data)
    {
        // best guess whether the incoming data is in a graphical grid format
        if (data.isEmpty())
            return true;

        // only the tabular formats will have the well location field
        return !data.getFirst().containsKey(AssayResultDomainKind.Column.WellLocation.name()) && !data.getFirst().containsKey("Well Location");
    }

    private List<Map<String, Object>> parsePlateRows(
        AssayProvider provider,
        ExpProtocol protocol,
        List<? extends Plate> plates,
        List<Map<String, Object>> data
    ) throws ExperimentException
    {
        DomainProperty plateProp = provider.getResultsDomain(protocol).getPropertyByName(AssayResultDomainKind.Column.Plate.name());
        Set<String> importAliases = new CaseInsensitiveHashSet(plateProp.getImportAliasSet());
        importAliases.add(AssayResultDomainKind.Column.Plate.name());

        // check whether the data rows have plate identifiers
        String plateIdField = data.getFirst().keySet().stream().filter(importAliases::contains).findFirst().orElse(null);
        boolean hasPlateIdentifiers = plateIdField != null && (data.stream().filter(row -> row.get(plateIdField) != null).findFirst().orElse(null) != null);

        if (hasPlateIdentifiers)
            return resolvePlateIdentifier(plates, data, plateIdField);

        final String ERROR_MESSAGE = "Unable to automatically assign plate identifiers to the data rows because %s. Please include plate identifiers for the data rows.";

        // verify all plates in the set have the same shape
        Set<PlateType> types = plates.stream().map(Plate::getPlateType).collect(Collectors.toSet());
        if (types.size() > 1)
            throw new ExperimentException(String.format(ERROR_MESSAGE, "the plate set contains different plate types"));

        PlateType type = types.stream().toList().getFirst();
        int plateSize = type.getRows() * type.getColumns();
        if ((data.size() % plateSize) != 0)
            throw new ExperimentException(String.format(ERROR_MESSAGE, "the number of rows in the data (" + data.size() + ") does not fit evenly and would result in a plate with partial wells filled"));

        if (data.size() > (plates.size() * plateSize))
            throw new ExperimentException(String.format(ERROR_MESSAGE, "the number of rows in the data (" + data.size() + ") exceeds the total number of wells available in the plate set (" + (plates.size() * plateSize) + ")"));

        // attempt to add the plate identifier into the data rows in the order that they appear in the plate set
        List<Map<String, Object>> newData = new ArrayList<>();
        int rowCount = 0;
        int curPlate = 0;
        Set<Position> positions = new HashSet<>();
        for (Map<String, Object> row : data)
        {
            // well location field is required, return if not provided or it will fail downstream
            String well = String.valueOf(row.get(AssayResultDomainKind.Column.WellLocation.name()));
            if (well == null)
                return data;

            Position position = new PositionImpl(null, well);
            if (positions.contains(position))
                throw new ExperimentException(String.format(ERROR_MESSAGE, "there is more than one well referencing the same position in the plate " + position));

            positions.add(position);
            Map<String, Object> newRow = new HashMap<>(row);
            newRow.put(AssayResultDomainKind.Column.Plate.name(), plates.get(curPlate).getRowId());
            newData.add(newRow);

            if (++rowCount >= plateSize)
            {
                // move to the next plate in the set
                rowCount = 0;
                curPlate++;
                positions.clear();
            }
        }

        return newData;
    }

    // Resolves a pre-calculated "plateIdField" to a plate rowId and furnishes new "data" rows with the plate rowId.
    private List<Map<String, Object>> resolvePlateIdentifier(List<? extends Plate> plates, List<Map<String, Object>> data, String plateIdField)
    {
        var newData = new ArrayList<Map<String, Object>>();
        var plateIdentifiers = new HashMap<Object, Long>();

        for (var row : data)
        {
            var newRow = new CaseInsensitiveHashMap<>(row);
            var plateId = row.get(plateIdField);

            if (plateId != null)
            {
                var plateRowId = plateIdentifiers.computeIfAbsent(plateId, (k) -> {
                    for (var plate : plates)
                    {
                        if (k instanceof Number numberKey)
                        {
                            if (null != plate.getRowId() && integerEquals(plate.getRowId(), numberKey))
                                return plate.getRowId();
                        }
                        else if (k instanceof String stringKey)
                        {
                            if (plate.getPlateId().equalsIgnoreCase(stringKey) || plate.getName().equalsIgnoreCase(stringKey))
                                return plate.getRowId();
                        }
                    }
                    return null;
                });

                if (plateRowId != null)
                    newRow.put(AssayResultDomainKind.Column.Plate.name(), plateRowId);
            }

            newData.add(newRow);
        }

        return newData;
    }

    /**
     * Helper class to organize plate grid info and annotations
     */
    private static class PlateGridInfo extends PlateUtils.GridInfo
    {
        public static final String PLATE_PREFIX = "plate";
        public static final String MEASURE_PREFIX = "measure";
        private Plate _plate;
        private String _measureName;

        public PlateGridInfo(PlateUtils.GridInfo info, PlateSet plateSet) throws ExperimentException
        {
            this(info, plateSet, null);
        }

        public PlateGridInfo(PlateUtils.GridInfo info, PlateSet plateSet, Set<String> measureAliases) throws ExperimentException
        {
            super(info.getData(), info.getAnnotations());

            // locate the plate in the plate set this grid is associated with plus an optional
            // measure name
            List<? extends Plate> plates = PlateManager.get().getPlatesForPlateSet(plateSet);
            List<String> annotations = getAnnotations();

            // if the plate set only has one plate, then treat a single annotation as the measure
            // otherwise a single annotation can only be a plate identifier
            if (annotations.size() == 1)
            {
                String annotation = annotations.getFirst();
                if (plates.size() == 1 && measureAliases != null && measureAliases.contains(annotation))
                {
                    _plate = plates.getFirst();
                    _measureName = annotation;
                }
                else
                    _plate = getPlateForId(annotation, plates);
            }
            else
            {
                // multiple annotation must have an annotation prefix
                for (String annotation : annotations)
                {
                    String plateID = getPrefixedValue(annotation, PLATE_PREFIX);
                    if (plateID != null)
                        _plate = getPlateForId(plateID, plates);
                    else
                        _measureName = getPrefixedValue(annotation, MEASURE_PREFIX);
                }
            }
        }

        private @NotNull Plate getPlateForId(String annotation, List<? extends Plate> platesetPlates) throws ExperimentException
        {
            Plate plate = platesetPlates.stream().filter(p -> p.isIdentifierMatch(annotation)).findFirst().orElse(null);
            if (plate == null)
                throw new ExperimentException("The plate identifier (" + annotation + ") is not valid for the configured plate set.");

            return plate;
        }

        private @Nullable String getPrefixedValue(String annotation, String prefix)
        {
            if (annotation != null)
            {
                // Issue 52782: measure name may contain a colon
                String[] parts = annotation.split(":", 2);
                if (parts.length == 2 && parts[0].trim().equalsIgnoreCase(prefix))
                {
                    return parts[1].trim();
                }
            }
            return null;
        }

        public @Nullable Plate getPlate()
        {
            return _plate;
        }

        public @Nullable String getMeasureName()
        {
            return _measureName;
        }
    }

    private List<Map<String, Object>> parsePlateGrids(
        Container container,
        User user,
        AssayProvider provider,
        ExpProtocol protocol,
        PlateSet plateSet,
        List<? extends Plate> plates,
        FileLike dataFile
    ) throws ExperimentException
    {
        // parse the data file for each distinct plate type found in the set of plates for the plateSetId
        ExcelPlateReader plateReader = new ExcelPlateReader();
        plateReader.setEmptyWellValue(Double.NaN); // Issue 51553
        MultiValuedMap<PlateType, PlateGridInfo> plateTypeGrids = new HashSetValuedHashMap<>();

        boolean hasPlateIdentifiers = false;
        boolean missingPlateIdentifiers = false;
        boolean multipleMeasures = false;

        List<DomainProperty> measureProperties = provider.getResultsDomain(protocol).getProperties().stream().filter(DomainProperty::isMeasure).collect(Collectors.toList());
        Set<String> measureAliases = new CaseInsensitiveHashSet();
        measureProperties.forEach(p -> measureAliases.addAll(PropertyService.get().getDomainPropertyImportAliases(p)));

        for (Plate plate : plates)
        {
            if (!plateTypeGrids.containsKey(plate.getPlateType()))
            {
                Plate p = PlateService.get().createPlate(container, TsvPlateLayoutHandler.TYPE, plate.getPlateType());
                for (PlateUtils.GridInfo gridInfo : plateReader.loadMultiGridFile(p, dataFile))
                {
                    PlateGridInfo plateInfo = new PlateGridInfo(gridInfo, plateSet, measureAliases);
                    plateTypeGrids.put(plate.getPlateType(), plateInfo);

                    if (plateInfo.getPlate() != null && !hasPlateIdentifiers)
                        hasPlateIdentifiers = true;
                    if (plateInfo.getPlate() == null && !missingPlateIdentifiers)
                        missingPlateIdentifiers = true;
                    if (plateInfo.getMeasureName() != null && !multipleMeasures)
                        multipleMeasures = true;
                }
            }
        }

        // if we didn't parse any plate data from the input file, it is possible that a transform script might be
        //  handling the file parsing so we don't want to error out here
        if (!plateTypeGrids.isEmpty())
        {
            if (!multipleMeasures && measureProperties.size() != 1)
                throw new ExperimentException("The assay protocol must have exactly one measure property to support graphical plate layout file parsing.");
            else if (multipleMeasures && measureProperties.isEmpty())
                throw new ExperimentException("There are multiple measures specified in the data file but the assay protocol does not define any measures");
        }

        String defaultMeasureName = measureProperties.getFirst().getName();

        // if any of the plateGrids keys have plate identifiers, import using those identifiers
        List<Map<String, Object>> dataRows = new ArrayList<>();
        if (hasPlateIdentifiers)
        {
            if (missingPlateIdentifiers)
                throw new ExperimentException("Some plate grids parsed from the file are missing plate identifiers.");

            for (Map.Entry<PlateType, Collection<PlateGridInfo>> plateTypeMapEntry : plateTypeGrids.asMap().entrySet())
            {
                if (multipleMeasures)
                {
                    // group by plate within the plate type
                    MultiValuedMap<Plate, PlateGridInfo> plateMaps = new HashSetValuedHashMap<>();
                    plateTypeMapEntry.getValue().forEach(gi -> plateMaps.put(gi.getPlate(), gi));

                    for (Map.Entry<Plate, Collection<PlateGridInfo>> entry : plateMaps.asMap().entrySet())
                    {
                        Set<String> measures = new HashSet<>();
                        Map<Position, Map<String, Object>> measureDataRows = new LinkedHashMap<>();
                        for (PlateGridInfo gridInfo : entry.getValue())
                        {
                            String measureName = gridInfo.getMeasureName();
                            if (measureName == null)
                                throw new ExperimentException("The measure name for plate (" + gridInfo.getPlate().getPlateId() + ") has not been specified in the data file.");

                            if (measures.contains(measureName))
                                throw new ExperimentException("The measure name (" + measureName + ") has been previously associated with data for the same plate.");
                            measures.add(measureName);

                            Plate currentPlate = gridInfo.getPlate();
                            Plate dataForPlate = PlateService.get().createPlate(currentPlate, gridInfo.getData(), null);
                            // get wells guarantees a consistent row/column oriented order
                            for (Well well : dataForPlate.getWells())
                            {
                                measureDataRows.computeIfAbsent(well, f -> getDataRowFromWell(currentPlate.getPlateId(), well, measureName)).put(measureName, getWellValue(well));
                            }
                        }

                        // add combined measures to rows for the entire run
                        dataRows.addAll(measureDataRows.values());
                    }
                }
                else
                {
                    for (PlateGridInfo gridInfo : plateTypeMapEntry.getValue())
                    {
                        Plate matchingPlate = gridInfo.getPlate();
                        if (matchingPlate != null)
                        {
                            double[][] plateGrid = gridInfo.getData();
                            PlateType plateGridType = PlateManager.get().getPlateType(plateGrid.length, plateGrid[0].length);
                            if (matchingPlate.getPlateType().equals(plateGridType))
                            {
                                Plate dataForPlate = PlateService.get().createPlate(matchingPlate, plateGrid, null);
                                for (Well well : dataForPlate.getWells())
                                    dataRows.add(getDataRowFromWell(matchingPlate.getPlateId(), well, defaultMeasureName));
                            }
                        }
                    }
                }
            }
        }
        // else if only one plateType was parsed (i.e. all 96-well plate grids), use plateGrids ordering to match plate set order
        else if (plateTypeGrids.keySet().size() == 1)
        {
            for (Map.Entry<PlateType, Collection<PlateGridInfo>> entry : plateTypeGrids.asMap().entrySet())
            {
                if (entry.getValue().size() > plates.size())
                    throw new ExperimentException("The number of plate grids parsed from the file exceeds the number of plates in the plate set.");

                int plateIndex = 0;
                for (PlateGridInfo gridInfo : entry.getValue())
                {
                    Plate targetPlate = plates.get(plateIndex++);
                    Plate dataForPlate = PlateService.get().createPlate(targetPlate, gridInfo.getData(), null);
                    for (Well well : dataForPlate.getWells())
                        dataRows.add(getDataRowFromWell(targetPlate.getPlateId(), well, defaultMeasureName));
                }
            }
        }
        else if (plateTypeGrids.keySet().size() > 1)
            throw new ExperimentException("Unable to match the plate grids parsed from the file to the plates in the plate set. Please include plate identifiers for the plate grids.");

        return dataRows;
    }

    private Map<String, Object> getDataRowFromWell(String plateId, Well well, String measure)
    {
        Map<String, Object> row = new CaseInsensitiveHashMap<>();
        row.put(AssayResultDomainKind.Column.Plate.name(), plateId);
        row.put(AssayResultDomainKind.Column.WellLocation.name(), well.getDescription());
        row.put(measure, getWellValue(well));
        return row;
    }

    // Issue 51553: account for empty wells in the plate graphical parsing by using Double.NaN
    private @Nullable Double getWellValue(Well well)
    {
        double value = well.getValue();
        return Double.isNaN(value) ? null : value;
    }

    @Override
    @NotNull
    public OntologyManager.UpdateableTableImportHelper getImportHelper(
        Container container,
        User user,
        ExpRun run,
        ExpData data,
        ExpProtocol protocol,
        AssayProvider provider,
        @Nullable AssayRunUploadContext<?> context
    )
    {
        return new PlateMetadataImportHelper(data, container, user, run, protocol, provider, context);
    }

    private @NotNull DomainProperty addField(Domain replicateDomain, String fieldName)
    {
        // create the property and copy the format
        PropertyStorageSpec spec = new PropertyStorageSpec(fieldName, JdbcType.DOUBLE);

        // Default formatting is 4 decimal places
        DomainProperty domainProperty = replicateDomain.addProperty(spec);
        domainProperty.setFormat("#.####");

        return domainProperty;
    }

    private Map<String, DomainProperty> getExistingFields(Domain replicateDomain)
    {
        Set<String> domainBaseProperties = replicateDomain.getBaseProperties().stream().map(DomainProperty::getName).collect(Collectors.toSet());
        Map<String, DomainProperty> existingFields = new HashMap<>();
        replicateDomain.getProperties().forEach(dp -> {
            if (!domainBaseProperties.contains(dp.getName()))
                existingFields.put(dp.getName(), dp);
        });

        return existingFields;
    }

    @Override
    public Map<String, List<GWTPropertyDescriptor>> previewFilterCriteriaColumns(@NotNull ExpProtocol protocol, List<String> columnNames)
    {
        return previewFilterCriteriaColumns(protocol.getContainer(), protocol.getName(), columnNames);
    }

    @Override
    public Map<String, List<GWTPropertyDescriptor>> previewFilterCriteriaColumns(@NotNull Container container, String protocolName, List<String> columnNames)
    {
        if (columnNames.isEmpty())
            return Collections.emptyMap();

        var replicateDomain = ensurePlateReplicateStatsDomain(container, protocolName);
        var existingFields = getExistingFields(replicateDomain);
        var columnMap = new HashMap<String, List<GWTPropertyDescriptor>>();

        for (var rawName : columnNames)
        {
            var columnName = StringUtils.trimToNull(rawName);
            if (columnName == null)
                continue;

            var properties = new ArrayList<GWTPropertyDescriptor>();

            for (var name : PlateReplicateStatsDomainKind.getStatsFieldNames(columnName))
            {
                DomainProperty dp;
                if (existingFields.containsKey(name))
                    dp = existingFields.get(name);
                else
                    dp = addField(replicateDomain, name);

                properties.add(DomainUtil.getPropertyDescriptor(dp));
            }

            columnMap.put(columnName, properties);
        }

        // Notably, this method does not commit/save the changes made on the underlying domain.

        return columnMap;
    }

    @Override
    public void updateReplicateStatsDomain(
        User user,
        ExpProtocol protocol,
        GWTDomain<GWTPropertyDescriptor> original,
        GWTDomain<GWTPropertyDescriptor> update
    ) throws ValidationException
    {
        var replicateDomain = ensurePlateReplicateStatsDomain(protocol);
        var existingReplicateFields = getExistingFields(replicateDomain);

        var originalFields = new HashMap<Integer, GWTPropertyDescriptor>();
        for (var field : original.getFields())
            originalFields.put(field.getPropertyId(), field);

        var domainDirty = false;
        var fieldsToRemove = new ArrayList<DomainProperty>();

        for (var updateField : update.getFields())
        {
            var propertyId = updateField.getPropertyId();
            var isNew = replicateDomain.isNew() || !originalFields.containsKey(propertyId);
            var isValidType = updateField.isMeasure() && PropertyType.getFromURI(null, updateField.getRangeURI()).getJdbcType().isNumeric();

            if (isNew)
            {
                if (isValidType)
                {
                    for (var name : PlateReplicateStatsDomainKind.getStatsFieldNames(updateField.getName()))
                    {
                        addField(replicateDomain, name);
                        domainDirty = true;
                    }
                }
            }
            else
            {
                var originalField = originalFields.get(propertyId);
                var renamed = !originalField.getName().equals(updateField.getName());
                var wasValidType = originalField.isMeasure() && PropertyType.getFromURI(null, originalField.getRangeURI()).getJdbcType().isNumeric();

                if (isValidType)
                {
                    if (wasValidType)
                    {
                        if (renamed)
                        {
                            var originalNames = PlateReplicateStatsDomainKind.getStatsFieldNames(originalField.getName());
                            var updatedNames = PlateReplicateStatsDomainKind.getStatsFieldNames(updateField.getName());

                            for (int i = 0; i < originalNames.size(); i++)
                            {
                                var name = originalNames.get(i);
                                if (existingReplicateFields.containsKey(name))
                                {
                                    var updatedName = updatedNames.get(i);
                                    var dp = replicateDomain.getPropertyByName(name);
                                    dp.setName(updatedName);
                                    domainDirty = true;
                                }
                            }
                        }
                        else
                        {
                            var updatedNames = PlateReplicateStatsDomainKind.getStatsFieldNames(updateField.getName());
                            for (String updatedName : updatedNames)
                            {
                                if (!existingReplicateFields.containsKey(updatedName))
                                {
                                    addField(replicateDomain, updatedName);
                                    domainDirty = true;
                                }
                            }
                        }
                    }
                    else
                    {
                        // something else to numeric measure
                        for (var name : PlateReplicateStatsDomainKind.getStatsFieldNames(updateField.getName()))
                        {
                            addField(replicateDomain, name);
                            domainDirty = true;
                        }
                    }
                }
                else if (wasValidType)
                {
                    // numeric measure to something else
                    var fieldName = renamed ? originalField.getName() : updateField.getName();
                    for (var name : PlateReplicateStatsDomainKind.getStatsFieldNames(fieldName))
                    {
                        var field = existingReplicateFields.get(name);
                        if (field != null)
                            fieldsToRemove.add(field);
                    }
                }
            }

            originalFields.remove(propertyId);
        }

        // The only fields that remain in "originalFields" are ones that no longer exist in the updated domain.
        // Remove any related replicate fields.
        for (var originalField : originalFields.values())
        {
            var wasValidType = originalField.isMeasure() && PropertyType.getFromURI(null, originalField.getRangeURI()).getJdbcType().isNumeric();
            if (wasValidType)
            {
                var fieldName = originalField.getName();
                for (var name : PlateReplicateStatsDomainKind.getStatsFieldNames(fieldName))
                {
                    var field = existingReplicateFields.get(name);
                    if (field != null)
                        fieldsToRemove.add(field);
                }
            }
        }

        if (!fieldsToRemove.isEmpty())
        {
            domainDirty = true;
            for (DomainProperty prop : fieldsToRemove)
                prop.delete();
        }

        if (domainDirty)
        {
            try
            {
                replicateDomain.save(user);
            }
            catch (ExperimentException e)
            {
                throw new ValidationException(e.getMessage());
            }
        }
    }

    @Override
    public @Nullable Domain getPlateReplicateStatsDomain(ExpProtocol protocol)
    {
        return getPlateReplicateStatsDomain(protocol.getContainer(), protocol.getName(), false);
    }

    private @Nullable Domain getPlateReplicateStatsDomain(Container container, String protocolName, boolean forUpdate)
    {
        String uri = getPlateReplicateStatsDomainUri(container, protocolName);
        return PropertyService.get().getDomain(container, uri, forUpdate);
    }

    private String getPlateReplicateStatsDomainUri(Container container, String protocolName)
    {
        var domainKind = PropertyService.get().getDomainKindByName(PlateReplicateStatsDomainKind.KIND_NAME);
        return domainKind.generateDomainURI(AssaySchema.NAME, protocolName, container, null);
    }

    private @NotNull Domain ensurePlateReplicateStatsDomain(ExpProtocol protocol)
    {
        return ensurePlateReplicateStatsDomain(protocol.getContainer(), protocol.getName());
    }

    private @NotNull Domain ensurePlateReplicateStatsDomain(Container container, String protocolName)
    {
        Domain domain = getPlateReplicateStatsDomain(container, protocolName, true);
        if (domain == null)
            domain = PropertyService.get().createDomain(container, getPlateReplicateStatsDomainUri(container, protocolName), PlateReplicateStatsDomainKind.NAME);

        return domain;
    }

    @Override
    public void insertReplicateStats(
        Container container,
        User user,
        ExpProtocol protocol,
        @NotNull ExpRun run,
        Map<Lsid, List<Map<String, Object>>> replicateRows
    ) throws ValidationException
    {
        insertOrUpdateReplicateStats(container, user, protocol, run, true, replicateRows);
    }

    @Override
    public void updateReplicateStats(
        Container container,
        User user,
        ExpProtocol protocol,
        Map<Lsid, List<Map<String, Object>>> replicateRows
    ) throws ValidationException
    {
        insertOrUpdateReplicateStats(container, user, protocol, null, false, replicateRows);
    }

    private void insertOrUpdateReplicateStats(
        Container container,
        User user,
        ExpProtocol protocol,
        @Nullable ExpRun run,
        boolean forInsert,
        Map<Lsid, List<Map<String, Object>>> replicateRows
    ) throws ValidationException
    {
        if (replicateRows.isEmpty())
            return;

        if (run == null && forInsert)
            throw new ValidationException("Run is required when inserting into the replicate stats table");

        AssayProvider provider = requireProvider(protocol);
        Domain resultDomain = provider.getResultsDomain(protocol);
        Map<String, List<Double>> measures = new CaseInsensitiveHashMap<>();
        resultDomain.getProperties().forEach(dp -> {
            if (dp.isMeasure() && dp.getJdbcType().isNumeric())
                measures.put(dp.getName(), new ArrayList<>());
        });

        if (measures.isEmpty())
            return;

        DomainProperty qcStateProp = getAssayStateProp(resultDomain);

        List<Map<String, Object>> replicates = new ArrayList<>();
        List<Map<String, Object>> keys = new ArrayList<>();

        for (Map.Entry<Lsid, List<Map<String, Object>>> entry : replicateRows.entrySet())
        {
            if (!entry.getValue().isEmpty())
            {
                // reset measure values for each replicate well group
                measures.forEach((k, v) -> v.clear());

                // organize values for each replicate well group by measure
                for (Map<String, Object> row : entry.getValue())
                {
                    // check whether this data row should be included for calculations
                    DataState state = getStateFromRow(container, row, qcStateProp);
                    if (!PlateDataStateManager.get().isOperationPermitted(state, PlateDataStateManager.DataOperation.analysis))
                        continue;

                    for (Map.Entry<String, Object> col : row.entrySet())
                    {
                        if (measures.containsKey(col.getKey()) && col.getValue() != null)
                            measures.get(col.getKey()).add(Double.valueOf(String.valueOf(col.getValue())));
                    }
                }

                keys.add(Map.of(PlateReplicateStatsDomainKind.Column.Lsid.name(), entry.getKey().toString()));
                Map<String, Object> replicateRow = new HashMap<>();
                replicates.add(replicateRow);
                replicateRow.put(PlateReplicateStatsDomainKind.Column.Lsid.name(), entry.getKey());
                if (run != null)
                    replicateRow.put(PlateReplicateStatsDomainKind.Column.Run.name(), run.getRowId());

                for (Map.Entry<String, List<Double>> measure : measures.entrySet())
                {
                    MathStat stat = StatsService.get().getStats(measure.getValue());

                    double mean = stat.getMean();
                    replicateRow.put(measure.getKey() + PlateReplicateStatsDomainKind.REPLICATE_MEAN_SUFFIX, Double.isNaN(mean) ? null : mean);

                    double stdDev = stat.getStdDev();
                    replicateRow.put(measure.getKey() + PlateReplicateStatsDomainKind.REPLICATE_STD_DEV_SUFFIX, Double.isNaN(stdDev) ? null : stdDev);
                }
            }
        }

        if (replicates.isEmpty())
            return;

        try
        {
            // persist to the replicate stats table
            QueryUpdateService qus = getReplicateStatsUpdateService(container, user, provider, protocol);

            BatchValidationException errors = new BatchValidationException();
            if (forInsert)
                qus.insertRows(user, container, replicates, errors, null, null);
            else
                qus.updateRows(user, container, replicates, keys, errors, null, null);

            if (errors.hasErrors())
            {
                throw new ExperimentException(errors.getLastRowError());
            }
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    @Nullable
    private static DataState getStateFromRow(Container container, Map<String, Object> row, @Nullable DomainProperty stateProp) throws ValidationException
    {
        if (stateProp != null)
        {
            Set<String> importAlias = new CaseInsensitiveHashSet(stateProp.getName());
            importAlias.addAll(stateProp.getImportAliasSet());
            for (Map.Entry<String, Object> entry : row.entrySet())
            {
                if (importAlias.contains(entry.getKey()))
                {
                    if (asLongElseNull(entry.getValue()) instanceof Long stateRowId)
                    {
                        DataState state = PlateDataStateManager.get().getStateForRowId(container, stateRowId);
                        if (state == null)
                            throw new ValidationException(String.format("No data states for the rowID %d was found.", stateRowId));

                        return state;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    public static DomainProperty getAssayStateProp(Domain resultDomain)
    {
        if (resultDomain == null)
            return null;

        return resultDomain.getProperties().stream().filter(dp -> AssayResultDomainKind.Column.State.name().equalsIgnoreCase(dp.getName()))
                .findFirst().orElse(null);
    }

    /**
     * Ensure the data state value in the row represents a data state in scope and is a valid state for plate based
     * assays.
     */
    @Nullable
    public static DataState validateRowDataStates(Container container, Map<String, Object> row, DomainProperty stateProp) throws ValidationException
    {
        DataState state = getStateFromRow(container, row, stateProp);
        if (state != null)
        {
            if (PlateDataStateManager.StateType.getType(state.getStateType()) == null)
                throw new ValidationException(String.format("The data state '%s' is not valid for this assay.", state.getLabel()));
        }

        return state;
    }

    private @NotNull AssayProvider requireProvider(ExpProtocol protocol) throws ValidationException
    {
        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            throw new ValidationException(String.format("Unable to find the provider for protocol : %s", protocol.getName()));

        return provider;
    }

    @NotNull
    private QueryUpdateService getReplicateStatsUpdateService(
        Container container,
        User user,
        AssayProvider provider,
        ExpProtocol protocol
    ) throws ValidationException
    {
        QueryUpdateService qus = null;
        AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
        if (schema instanceof TSVProtocolSchema tsvProtocolSchema)
        {
            TableInfo tableInfo = tsvProtocolSchema.createPlateReplicateStatsTable(null, true);
            if (tableInfo != null)
                qus = tableInfo.getUpdateService();
        }

        if (qus == null)
            throw new ValidationException(String.format("There is no replicate stats update service available for assay : %s", protocol.getName()));

        return qus;
    }

    @Override
    public void deleteReplicateStats(
        Container container,
        User user,
        ExpProtocol protocol,
        List<Map<String, Object>> keys
    ) throws ValidationException
    {
        if (keys.isEmpty())
            return;

        AssayProvider provider = requireProvider(protocol);
        QueryUpdateService qus = getReplicateStatsUpdateService(container, user, provider, protocol);

        try
        {
            qus.deleteRows(user, container, keys, null, null);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    @Override
    public void applyHitSelectionCriteria(
        Container container,
        User user,
        ExpProtocol protocol,
        TableInfo table,
        List<Long> runIds
    ) throws ValidationException
    {
        if (runIds.isEmpty())
            return;

        var provider = requireProvider(protocol);
        var filterCriteria = provider.getFilterCriteria(protocol);
        if (filterCriteria.isEmpty())
            return;

        var domain = table.getDomain();
        if (domain == null)
        {
            LOG.error("Automatic hit selection failed. Unable to resolve domain from table ({}).", table);
            return;
        }

        var url = new ActionURL();
        var replicateDomain = AssayPlateMetadataService.get().getPlateReplicateStatsDomain(protocol);

        for (var criteria : filterCriteria)
        {
            var domainProperty = domain.getProperty(criteria.propertyId());
            boolean isReplicateProperty = false;

            if (domainProperty == null && replicateDomain != null)
            {
                domainProperty = replicateDomain.getProperty(criteria.propertyId());
                isReplicateProperty = domainProperty != null;
            }

            if (domainProperty == null)
            {
                LOG.error("Automatic hit selection failed. Unable to resolve domain property from propertyId ({}).", criteria.propertyId());
                return;
            }

            FieldKey fieldKey;
            if (isReplicateProperty)
                fieldKey = FieldKey.fromParts(AssayResultDomainKind.Column.Replicate.name(), domainProperty.getName());
            else
                fieldKey = FieldKey.fromParts(domainProperty.getName());

            var ct = CompareType.getByURLKey(criteria.operation());
            if (ct == null)
            {
                LOG.error("Automatic hit selection failed. Unable to resolve filter comparison type from operation \"{}\".", criteria.operation());
                return;
            }

            url.addFilter(null, fieldKey, ct, criteria.value());
        }

        var filter = new SimpleFilter();

        // Applying filters via ActionURL allows for automatic type coercion of the filter value
        filter.addUrlFilters(url, null);

        // Generate the description for the applied filter criteria prior to incorporating additional clauses
        var criteriaDescription = generateFilterCriteriaDescription(filter);

        // The referenced plate well must have a sample value
        filter.addCondition(FieldKey.fromParts("Well", "SampleId"), null, CompareType.NONBLANK);

        // Filter out result rows that are excluded
        filterOutExcludedRows(container, table, filter);

        // Apply filter only for the specified runs
        filter.addInClause(FieldKey.fromParts("Run"), runIds);

        // Remove previous hits against the runs that have been modified
        PlateManager.get().deleteHitsForRuns(runIds);

        var matchingResults = new TableSelector(table, Collections.singleton(table.getColumn(FieldKey.fromParts("RowId"))), filter, null).getArrayList(Long.class);

        try
        {
            if (!matchingResults.isEmpty())
                PlateManager.get().markHits(container, user, protocol.getRowId(), true, matchingResults, null);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        var runDomain = provider.getRunDomain(protocol);
        if (runDomain != null)
        {
            var property = runDomain.getPropertyByName(HIT_SELECTION_CRITERIA_COLUMN_NAME);
            if (property != null)
            {
                var pd = property.getPropertyDescriptor();
                for (var run : ExperimentService.get().getExpRuns(runIds))
                {
                    var value = run.getProperty(pd);
                    if (!criteriaDescription.equals(value))
                        run.setProperty(user, pd, criteriaDescription);
                }
            }
        }
    }

    private static void filterOutExcludedRows(Container container, TableInfo table, SimpleFilter filter)
    {
        PlateDataStateManager stateManager = PlateDataStateManager.get();
        var exclusionStateRowIds = stateManager.getStates(container)
                .stream()
                .filter(state -> !stateManager.isOperationPermitted(state, PlateDataStateManager.DataOperation.hitSelection))
                .map(DataState::getRowId)
                .toList();

        if (!exclusionStateRowIds.isEmpty())
            filter.addCondition(table.getColumn(AssayResultDomainKind.Column.State.name()), exclusionStateRowIds, CompareType.NOT_IN);
    }

    private static String generateFilterCriteriaDescription(SimpleFilter filter)
    {
        var formatter = new SimpleFilter.ColumnNameFormatter()
        {
            @Override
            public String format(FieldKey fieldKey)
            {
                // Display the fieldKey label (as opposed to the toDisplayString()) for these criteria
                return fieldKey.getLabel();
            }
        };

        var parts = new ArrayList<String>();
        for (var clause : filter.getClauses())
        {
            var sub = new StringBuilder();
            clause.appendFilterText(sub, formatter);
            parts.add(sub.toString());
        }

        return StringUtils.join(parts, " and ");
    }

    @Override
    public @NotNull UserSchema getPlateSchema(QuerySchema querySchema, Set<Role> contextualRoles)
    {
        return new PlateSchema(querySchema, contextualRoles);
    }

    record WellSampleData(Long sampleId, Integer row, Integer col) {}

    @Override
    public Map<String, Long> getWellLocationToSampleIdMap(Long plateId)
    {
        // Note: this method intentionally does not use PlateManager.get().getWellData, by selecting only the columns
        // we need there is a small but measurable performance boost when importing plate assay data
        SimpleFilter filter = new SimpleFilter(WellTable.Column.PlateId.fieldKey(), plateId);
        Set<String> columns = Set.of(WellTable.Column.SampleID.name(), WellTable.Column.Row.name(), WellTable.Column.Col.name());
        List<WellSampleData> wells = new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), columns, filter, null).getArrayList(WellSampleData.class);
        Map<String, Long> wellLocationToSampleIdMap = new HashMap<>();

        for (WellSampleData well : wells)
        {
            PositionImpl pos = new PositionImpl(null, well.row, well.col);
            wellLocationToSampleIdMap.put(pos.getDescription(), well.sampleId);
        }

        return wellLocationToSampleIdMap;
    }

    @Override
    public boolean isWellLookup(ColumnInfo col)
    {
        if (col == null) return false;

        if (!col.isLookup()) return false;

        var wellTable = AssayDbSchema.getInstance().getTableInfoWell();
        var lookupTable = col.getFkTableInfo();

        return lookupTable.getSchema().getName().equalsIgnoreCase(wellTable.getSchema().getName())
                && lookupTable.getName().equalsIgnoreCase(wellTable.getName());
    }

    private static class PlateMetadataImportHelper extends SimpleAssayDataImportHelper
    {
        private final Map<Long, Map<Position, Lsid>> _wellPositionMap;       // map of plate position to well table
        private final Map<Long, Map<Position, Lsid>> _wellReplicateMap;      // map of plate position to replicate stats table
        private final Map<Lsid, List<Map<String, Object>>> _replicateRows;
        private final Map<Object, Plate> _plateIdentifierMap;
        private final Container _container;
        private final User _user;
        private final ExpRun _run;
        private final ExpProtocol _protocol;
        private final AssayProvider _provider;
        private final AssayRunUploadContext<?> _context;

        public PlateMetadataImportHelper(
            ExpData data,
            Container container,
            User user,
            ExpRun run,
            ExpProtocol protocol,
            AssayProvider provider,
            @Nullable AssayRunUploadContext<?> context
        )
        {
            super(data, protocol, provider);
            _wellPositionMap = new HashMap<>();
            _wellReplicateMap = new HashMap<>();
            _replicateRows = new HashMap<>();
            _plateIdentifierMap = new HashMap<>();
            _container = container;
            _user = user;
            _run = run;
            _protocol = protocol;
            _provider = provider;
            _context = context;
        }

        @Override
        public void bindAdditionalParameters(Map<String, Object> map, ParameterMapStatement target) throws ValidationException
        {
            super.bindAdditionalParameters(map, target);

            Domain runDomain = _provider.getRunDomain(_protocol);
            Domain resultDomain = _provider.getResultsDomain(_protocol);
            DomainProperty stateProp = AssayPlateMetadataServiceImpl.getAssayStateProp(resultDomain);
            DomainProperty plateSetProperty = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME);
            DomainProperty plateProperty = resultDomain.getPropertyByName(AssayResultDomainKind.Column.Plate.name());
            DomainProperty wellLocationProperty = resultDomain.getPropertyByName(AssayResultDomainKind.Column.WellLocation.name());

            // get the plate associated with this row (checking the results domain field first)
            Object plateIdentifier = PropertyService.get().getDomainPropertyValueFromRow(plateProperty, map);
            Plate plate = _plateIdentifierMap.get(plateIdentifier);
            if (plate == null)
            {
                if (plateSetProperty != null && plateIdentifier != null)
                {
                    Object plateSetVal = _run.getProperty(plateSetProperty);
                    Long plateSetRowId = plateSetVal != null ? Long.parseLong(String.valueOf(plateSetVal)) : null;
                    plate = PlateService.get().getPlate(PlateManager.get().getPlateContainerFilter(_protocol, _container, _user), plateSetRowId, plateIdentifier);
                }
                _plateIdentifierMap.put(plateIdentifier, plate);
            }

            if (plate == null)
                throw new ValidationException("Unable to resolve the plate for the data result row.");

            // create the map of well locations to the well table lsid for the plate
            if (!_wellPositionMap.containsKey(plate.getRowId()))
            {
                Map<Position, Lsid> positionToWellLsid = new HashMap<>();
                SimpleFilter filter = SimpleFilter.createContainerFilter(plate.getContainer());
                filter.addCondition(WellTable.Column.PlateId.fieldKey(), plate.getRowId());
                for (WellBean well : new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, null).getArrayList(WellBean.class))
                    positionToWellLsid.put(new PositionImpl(plate.getContainer(), well.getRow(), well.getCol()), Lsid.parse(well.getLsid()));
                _wellPositionMap.put(plate.getRowId(), positionToWellLsid);
            }
            Map<Position, Lsid> positionToWellLsid = _wellPositionMap.get(plate.getRowId());

            // create the map of well locations to the replicate stats table lsid for the plate
            if (!_wellReplicateMap.containsKey(plate.getRowId()))
            {
                Map<Position, Lsid> positionToReplicateLsid = new HashMap<>();

                for (WellGroup wellGroup : plate.getWellGroups(WellGroup.Type.REPLICATE))
                {
                    // will need to generate a new lsid for the replicate table
                    Lsid lsid = PlateReplicateStatsDomainKind.generateReplicateLsid(_container, _run, plate.getPlateSet(), wellGroup);
                    if (lsid != null)
                        wellGroup.getPositions().forEach(p -> positionToReplicateLsid.put(p, lsid));
                }
                _wellReplicateMap.put(plate.getRowId(), positionToReplicateLsid);
            }
            Map<Position, Lsid> positionToReplicateLsid = _wellReplicateMap.get(plate.getRowId());

            // to join plate based metadata to assay results we need to line up the incoming assay results with the
            // corresponding well on the plate used in the import
            String wellLocationStr = (String) PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, map);
            if (wellLocationStr != null)
            {
                PositionImpl pos = new PositionImpl(_container, wellLocationStr);
                // need to adjust the column value to be 0 based to match the template locations
                pos.setCol(pos.getColumn() - 1);
                if (positionToWellLsid.containsKey(pos))
                    target.put(AssayResultDomainKind.Column.WellLsid.name(), positionToWellLsid.get(pos));

                // find the associated replicate well group for this position (if any)
                if (positionToReplicateLsid.containsKey(pos))
                {
                    Lsid lsid = positionToReplicateLsid.get(pos);
                    target.put(AssayResultDomainKind.Column.ReplicateLsid.name(), lsid);
                    _replicateRows.computeIfAbsent(lsid, k -> new ArrayList<>()).add(map);
                }
            }

            // Validate any data state values on the row. No hit selection / data state processing is done on import
            // because at this time transform script hit selection is not supported nor is there any intersection
            // in the re-import case yet.
            validateRowDataStates(_container, map, stateProp);
        }

        /**
         * Is data being added to a previous run
         */
        private boolean isExistingRun()
        {
            return _context.getReImportOption() == AssayRunUploadContext.ReImportOption.MERGE_DATA && _context.getReRunId() != null;
        }

        @Override
        public void afterBatchInsert(int rowCount)
        {
            try (var tx = AssayDbSchema.getInstance().getScope().ensureTransaction())
            {
                // compute replicate calculations and insert into the replicate stats table
                AssayPlateMetadataService.get().insertReplicateStats(_container, _user, _protocol, _run, _replicateRows);

                AssayProtocolSchema schema = _provider.createProtocolSchema(_user, _container, _protocol, null);
                TableInfo resultsTable = schema.createDataTable(null, false);
                boolean isReimport = isExistingRun();

                // Re-select any hits that were present in the previous run, this works in conjunction with the code in
                // mergeReRunData where previous hits are removed for any data unchanged by the new incoming data. At this
                // point any remaining hits should represent selections we plan to move forward to the new run
                if (isReimport)
                {
                    ExpRun prevRun = ExperimentService.get().getExpRun(_context.getReRunId());
                    if (prevRun != null)
                    {
                        SQLFragment sql = new SQLFragment("SELECT AR.rowId FROM ").append(resultsTable, "AR")
                                .append(" JOIN ").append(AssayDbSchema.getInstance().getTableInfoHit(), "HT")
                                .append(" ON AR.welllsid = HT.welllsid")
                                .append(" JOIN ").append(ExperimentService.get().getTinfoData(), "ED")
                                .append(" ON AR.dataid = ED.rowid")
                                .append(" WHERE HT.runId = ? ").add(prevRun.getRowId())
                                .append(" AND ED.runId = ? ").add(_run.getRowId());
                        List<Long> rowIds = new SqlSelector(AssayDbSchema.getInstance().getScope(), sql).getArrayList(Long.class);
                        if (!rowIds.isEmpty())
                            PlateManager.get().markHits(_container, _user, _protocol.getRowId(), true, rowIds, null);

                        // remove the selections from the previous run
                        PlateManager.get().deleteHitsForRuns(List.of(prevRun.getRowId()));
                    }
                }

                AssayPlateMetadataService.get().applyHitSelectionCriteria(_container, _user, _protocol, resultsTable, List.of(_run.getRowId()));

                PlateManager.get().addPlateImportAuditEvents(_container, _user, tx, _plateIdentifierMap.values().stream().toList(), _run, isReimport);

                tx.commit();
            }
            catch (Throwable e)
            {
                throw UnexpectedException.wrap(e);
            }
        }
    }

    public static final class TestCase
    {
        private static Container container;
        private static User user;

        @BeforeClass
        public static void setup()
        {
            JunitUtil.deleteTestContainer();

            container = JunitUtil.getTestContainer();
            user = TestContext.get().getUser();
        }

        @Test
        public void testGridAnnotations() throws Exception
        {
            PlateType plateType = PlateManager.get().getPlateType(8, 12);
            assertNotNull("Expected 8x12 plate type to resolve", plateType);

            List<PlateManager.PlateData> plates = List.of(
                    new PlateManager.PlateData(null, plateType.getRowId(), null, null, Collections.emptyList()),
                    new PlateManager.PlateData(null, plateType.getRowId(), null, null, Collections.emptyList())
            );

            PlateSet plateSet = PlateManager.get().createPlateSet(container, user, new PlateSetImpl(), plates, null, null);
            List<? extends Plate> plateSetPlates = PlateManager.get().getPlatesForPlateSet(plateSet);
            assertEquals("Expected two plates to be created.", 2, plateSetPlates.size());
            Plate plate = plateSetPlates.getFirst();

            PlateGridInfo gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getPlateId())),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation", plate.getRowId(), gridInfo.getPlate().getRowId());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getName())),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation", plate.getRowId(), gridInfo.getPlate().getRowId());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getRowId().toString())),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation", plate.getRowId(), gridInfo.getPlate().getRowId());

            // test for multiple annotations
            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getPlateId(), "Density")),
                    plateSet);
            assertNull("Expected plate to not resolve on annotation without a prefix", gridInfo.getPlate());
            assertNull("Expected measure to not resolve on annotation without a prefix", gridInfo.getMeasureName());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of("PLATE:" + plate.getPlateId(), "Density")),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation with a prefix", plate.getRowId(), gridInfo.getPlate().getRowId());
            assertNull("Expected measure to not resolve on annotation without a prefix", gridInfo.getMeasureName());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of("plate:" + plate.getPlateId(), "MEASURE : Density")),
                    plateSet);
            assertEquals("Expected plate to resolve on annotation with a prefix", plate.getRowId(), gridInfo.getPlate().getRowId());
            assertEquals("Expected measure to resolve on annotation with a prefix", "Density", gridInfo.getMeasureName());

            gridInfo = new PlateGridInfo(
                    new PlateUtils.GridInfo(new double[8][12], List.of(plate.getPlateId(), "measure : Density")),
                    plateSet);
            assertNull("Expected plate to not resolve on annotation without a prefix", gridInfo.getPlate());
            assertEquals("Expected measure to resolve on annotation with a prefix", "Density", gridInfo.getMeasureName());
        }
    }
}
