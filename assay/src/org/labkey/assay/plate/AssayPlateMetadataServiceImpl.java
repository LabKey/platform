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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ImportAliasable;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.ParameterMapStatement;
import org.labkey.api.data.PropertyStorageSpec;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.data.statistics.MathStat;
import org.labkey.api.data.statistics.StatsService;
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
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.qc.DataLoaderSettings;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.assay.TSVProtocolSchema;
import org.labkey.assay.plate.model.WellBean;
import org.labkey.assay.query.AssayDbSchema;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
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
import static org.labkey.api.assay.AssayResultDomainKind.REPLICATE_LSID_COLUMN_NAME;
import static org.labkey.api.assay.AssayResultDomainKind.WELL_LSID_COLUMN_NAME;

public class AssayPlateMetadataServiceImpl implements AssayPlateMetadataService
{
    private static final Logger LOG = LogHelper.getLogger(AssayPlateMetadataServiceImpl.class, "Plate Metadata Logger");

    @Override
    public DataIteratorBuilder mergePlateMetadata(
        Container container,
        User user,
        Integer plateSetId,
        DataIteratorBuilder rows,
        AssayProvider provider,
        ExpProtocol protocol
    )
    {
        Domain resultDomain = provider.getResultsDomain(protocol);
        DomainProperty plateProperty = resultDomain.getPropertyByName(AssayResultDomainKind.PLATE_COLUMN_NAME);
        DomainProperty wellLocationProperty = resultDomain.getPropertyByName(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME);

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
            final Map<Integer, ExpMaterial> sampleMap = new HashMap<>();
            final CaseInsensitiveMapWrapper<Object> sharedCasing = new CaseInsensitiveMapWrapper<>(new HashMap<>());

            @Override
            public Map<String, Object> apply(Map<String, Object> row)
            {
                // ensure the result data includes a wellLocation field with values like : A1, F12, etc
                Object wellLocation = PropertyService.get().getDomainPropertyValueFromRow(wellLocationProperty, row);
                if (wellLocation == null)
                    throw new RuntimeValidationException("Imported data must contain a WellLocation column to support plate metadata integration.");

                if (AssayPlateMetadataService.isExperimentalAppPlateEnabled())
                {
                    // Copy so we can put new values
                    row = new CaseInsensitiveMapWrapper<>(new HashMap<>(row), sharedCasing);

                    // include metadata that may have been applied directly to the plate
                    rowCounter++;

                    Object plateIdentifier = PropertyService.get().getDomainPropertyValueFromRow(plateProperty, row);
                    if (plateIdentifier == null)
                        throw new RuntimeValidationException("Unable to resolve plate identifier for results row (" + rowCounter + ").");

                    Plate plate = PlateService.get().getPlate(cf, plateSetId, plateIdentifier);
                    if (plate == null)
                        throw new RuntimeValidationException("Unable to resolve the plate \"" + plateIdentifier + "\" for the results row (" + rowCounter + ").");

                    plateIdentifierMap.putIfAbsent(plateIdentifier, new Pair<>(plate, new HashMap<>()));

                    // if the plate identifier is the plate name, we need to make sure it resolves during importRows
                    // so replace it with the plateId (which will be unique)
                    if (!StringUtils.isNumeric(plateIdentifier.toString()))
                        PropertyService.get().replaceDomainPropertyValue(plateProperty, row, plate.getPlateId());

                    // create the map of well locations to the well for the given plate
                    Map<Position, WellBean> positionToWell = plateIdentifierMap.get(plateIdentifier).second;
                    if (positionToWell.isEmpty())
                    {
                        SimpleFilter filter = SimpleFilter.createContainerFilter(plate.getContainer());
                        filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
                        Set<Integer> wellSamples = new HashSet<>();
                        for (WellBean well : new TableSelector(AssayDbSchema.getInstance().getTableInfoWell(), filter, null).getArrayList(WellBean.class))
                        {
                            positionToWell.put(new PositionImpl(plate.getContainer(), well.getRow(), well.getCol()), well);
                            if (well.getSampleId() != null && !sampleMap.containsKey(well.getSampleId()))
                                wellSamples.add(well.getSampleId());
                        }

                        if (!wellSamples.isEmpty())
                            // stash away any samples associated with the plate
                            ExperimentService.get().getExpMaterials(wellSamples).forEach(s -> sampleMap.put(s.getRowId(), s));
                    }

                    PositionImpl well = new PositionImpl(null, String.valueOf(wellLocation));
                    // need to adjust the column value to be 0 based to match the template locations
                    well.setColumn(well.getColumn() - 1);

                    if (positionToWell.containsKey(well))
                    {
                        WellBean wellBean = positionToWell.get(well);
                        for (WellCustomField customField : PlateManager.get().getWellCustomFields(user, plate, wellBean.getRowId()))
                            row.put(customField.getName(), customField.getValue());

                        // include the sample information from the well (Issue 50276)
                        if (!sampleMap.isEmpty())
                        {
                            ExpMaterial sample = sampleMap.get(wellBean.getSampleId());
                            row.put("SampleID", sample != null ? sample.getRowId() : null);
                            row.put("SampleName", sample != null ? sample.getName() : null);
                        }
                    }
                    else
                        throw new RuntimeValidationException("Unable to resolve well \"" + wellLocation + "\" for plate \"" + plate.getName() + "\".");
                }

                return row;
            }
        });
    }

    @Override
    public DataIteratorBuilder parsePlateData(
        Container container,
        User user,
        @NotNull AssayRunUploadContext<?> context,
        ExpData data,
        AssayProvider provider,
        ExpProtocol protocol,
        Integer plateSetId,
        File dataFile,
        DataLoaderSettings settings
    ) throws ExperimentException
    {
        // get the ordered list of plates for the plate set
        ContainerFilter cf = PlateManager.get().getPlateContainerFilter(protocol, container, user);
        PlateSet plateSet = PlateManager.get().getPlateSet(cf, plateSetId);
        if (plateSet == null)
            throw new ExperimentException("Plate set " + plateSetId + " not found.");
        if (plateSet.isTemplate())
            throw new ExperimentException(String.format("Plate set \"%s\" is a template plate set. Template plate sets do not support associating assay data.", plateSet.getName()));

        List<Plate> plates = PlateManager.get().getPlatesForPlateSet(plateSet);
        if (plates.isEmpty())
            throw new ExperimentException("No plates were found for the plate set (" + plateSetId + ").");

        List<Map<String, Object>> rows = _parsePlateData(container, user, data, provider, protocol, plateSet, plates, dataFile, settings);

        // check if we are merging the re-imported data
        if (context.getReImportOption() == AssayRunUploadContext.ReImportOption.MERGE_DATA && context.getReRunId() != null)
        {
            rows = mergeReRunData(container, user, context, rows, plateSet, plates, provider, protocol, data, dataFile, settings);
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
            List<Plate> plates,
            File dataFile,
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
                else
                    return parsePlateRows(provider, protocol, plates, rawRows);
            }
            catch (IOException e)
            {
                throw new ExperimentException(e);
            }
        }
    }

    /**
     * Takes the current incoming data and combines it with any data uploaded in the previous run (re-run ID). Data
     * can be combined for plates within a plate set, but only on a per plate boundary. If there is data for plates
     * in both sets of data, the most recent data will take precedence.
     *
     * @param rows The incoming data rows
     * @param plateSet The plate set the import is targeting
     * @param plates The list of plates in this plate set
     * @param data The ExpData object for this run
     * @param dataFile The current uploaded file
     * @return The new, combined data
     */
    private List<Map<String, Object>> mergeReRunData(
            Container container,
            User user,
            @NotNull AssayRunUploadContext<?> context,
            List<Map<String, Object>> rows,
            PlateSet plateSet,
            List<Plate> plates,
            AssayProvider provider,
            ExpProtocol protocol,
            ExpData data,
            File dataFile,
            DataLoaderSettings settings
    ) throws ExperimentException
    {
        ExpRun run = ExperimentService.get().getExpRun(context.getReRunId());
        if (run != null)
        {
            for (ExpData prevData : run.getDataOutputs())
            {
                // find the data file for the replaced run
                if (prevData.getDataType().getNamespacePrefix().equals(TsvDataHandler.NAMESPACE))
                {
                    File prevFile = prevData.getFile();
                    if (prevFile != null && prevFile.exists())
                    {
                        // incoming plate data has precedence over any previous plate data.
                        Set<String> existingPlates = new HashSet<>();
                        rows.forEach(r -> existingPlates.add(r.get(AssayResultDomainKind.PLATE_COLUMN_NAME).toString()));
                        List<Map<String, Object>> newRows = new ArrayList<>(rows);

                        // parse the previous data and combine with any new data
                        boolean dataMerged = false;
                        for (Map<String, Object> row : _parsePlateData(container, user, prevData, provider, protocol, plateSet, plates, prevFile, settings))
                        {
                            String plate = String.valueOf(row.get(AssayResultDomainKind.PLATE_COLUMN_NAME));
                            if (plate != null && !existingPlates.contains(plate))
                            {
                                newRows.add(row);
                                dataMerged = true;
                            }
                        }

                        if (dataMerged)
                        {
                            // replace the contents of the uploaded data file with the new combined data
                            try (TSVMapWriter writer = new TSVMapWriter(newRows))
                            {
                                File dir = dataFile.getParentFile() != null ? dataFile.getParentFile() : AssayFileWriter.ensureUploadDirectory(container);
                                String newName = FileUtil.getBaseName(dataFile) + ".tsv";
                                Path newPath = AssayFileWriter.findUniqueFileName(newName, dir.toPath());
                                writer.write(newPath.toFile());

                                // Consider : should we delete the uploaded data file in this case?

                                // update the ExpData file URI
                                data.setDataFileURI(FileUtil.getAbsoluteCaseSensitiveFile(newPath.toFile()).toURI());
                                data.setName(String.format("%s (merged with previous run)", newName));
                                data.save(user);
                            }
                            catch (IOException e)
                            {
                                throw new ExperimentException(e);
                            }
                        }

                        if (dataMerged)
                            rows = newRows;
                    }
                    else
                    {
                        LOG.error(String.format("Unable to locate the data file for replaced run ID : %d", context.getReRunId()));
                        return rows;
                    }
                }
            }
        }
        else
            throw new ExperimentException(String.format("Unable to resolve the replaced run with ID : %d", context.getReRunId()));

        return rows;
    }

    private boolean isGridFormat(List<Map<String, Object>> data)
    {
        // best guess whether the incoming data is in a graphical grid format
        if (data.isEmpty())
            return true;

        // only the tabular formats will have the well location field
        return !data.get(0).containsKey(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME) && !data.get(0).containsKey("Well Location");
    }

    private List<Map<String, Object>> parsePlateRows(
        AssayProvider provider,
        ExpProtocol protocol,
        List<Plate> plates,
        List<Map<String, Object>> data
    ) throws ExperimentException
    {
        DomainProperty plateProp = provider.getResultsDomain(protocol).getPropertyByName(AssayResultDomainKind.PLATE_COLUMN_NAME);
        Set<String> importAliases = new CaseInsensitiveHashSet(plateProp.getImportAliasSet());
        importAliases.add(AssayResultDomainKind.PLATE_COLUMN_NAME);

        // check whether the data rows have plate identifiers
        String plateIdField = data.get(0).keySet().stream().filter(importAliases::contains).findFirst().orElse(null);
        boolean hasPlateIdentifiers = plateIdField != null && (data.stream().filter(row -> row.get(plateIdField) != null).findFirst().orElse(null) != null);

        if (hasPlateIdentifiers)
            return data;

        final String ERROR_MESSAGE = "Unable to automatically assign plate identifiers to the data rows because %s. Please include plate identifiers for the data rows.";

        // verify all plates in the set have the same shape
        Set<PlateType> types = plates.stream().map(Plate::getPlateType).collect(Collectors.toSet());
        if (types.size() > 1)
            throw new ExperimentException(String.format(ERROR_MESSAGE, "the plate set contains different plate types"));

        PlateType type = types.stream().toList().get(0);
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
        String plateFieldName = plateIdField != null ? plateIdField : AssayResultDomainKind.PLATE_COLUMN_NAME;
        for (Map<String, Object> row : data)
        {
            // well location field is required, return if not provided or it will fail downstream
            String well = String.valueOf(row.get(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME));
            if (well == null)
                return data;

            Position position = new PositionImpl(null, well);
            if (positions.contains(position))
                throw new ExperimentException(String.format(ERROR_MESSAGE, "there is more than one well referencing the same position in the plate " + position));

            positions.add(position);
            Map<String, Object> newRow = new HashMap<>(row);
            newRow.put(plateFieldName, plates.get(curPlate).getPlateId());
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
            super(info.getData(), info.getAnnotations());

            // locate the plate in the plate set this grid is associated with plus an optional
            // measure name
            List<Plate> plates = PlateManager.get().getPlatesForPlateSet(plateSet);
            List<String> annotations = getAnnotations();

            // single annotation can only be a plate identifier
            if (annotations.size() == 1)
                _plate = getPlateForId(annotations.get(0), plates);
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

        private @NotNull Plate getPlateForId(String annotation, List<Plate> platesetPlates) throws ExperimentException
        {
            Plate plate = platesetPlates.stream().filter(p -> p.isIdentifierMatch(annotation)).findFirst().orElse(null);
            if (plate == null)
                throw new ExperimentException("The plate identifier (" + annotation + ") is not valid for the configured plate set.");

            return plate;
        }

        private @Nullable String getPrefixedValue(String annotation, String prefix)
        {
            if (annotation != null && annotation.trim().toLowerCase().startsWith(prefix))
            {
                String[] parts = annotation.split(":");
                if (parts.length == 2)
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
        List<Plate> plates,
        File dataFile
    ) throws ExperimentException
    {
        // parse the data file for each distinct plate type found in the set of plates for the plateSetId
        ExcelPlateReader plateReader = new ExcelPlateReader();
        MultiValuedMap<PlateType, PlateGridInfo> plateTypeGrids = new HashSetValuedHashMap<>();

        boolean hasPlateIdentifiers = false;
        boolean missingPlateIdentifiers = false;
        boolean multipleMeasures = false;

        for (Plate plate : plates)
        {
            if (!plateTypeGrids.containsKey(plate.getPlateType()))
            {
                Plate p = PlateService.get().createPlate(container, TsvPlateLayoutHandler.TYPE, plate.getPlateType());
                for (PlateUtils.GridInfo gridInfo : plateReader.loadMultiGridFile(p, dataFile))
                {
                    PlateGridInfo plateInfo = new PlateGridInfo(gridInfo, plateSet);
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

        List<DomainProperty> measureProperties = provider.getResultsDomain(protocol).getProperties().stream().filter(DomainProperty::isMeasure).collect(Collectors.toList());
        if (!multipleMeasures && measureProperties.size() != 1)
            throw new ExperimentException("The assay protocol must have exactly one measure property to support graphical plate layout file parsing.");
        else if (multipleMeasures && measureProperties.isEmpty())
            throw new ExperimentException("There are multiple measures specified in the data file but the assay protocol does not define any measures");

        String defaultMeasureName = measureProperties.get(0).getName();

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
                                measureDataRows.computeIfAbsent(well, f -> getDataRowFromWell(currentPlate.getPlateId(), well, measureName)).put(measureName, well.getValue());
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
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(AssayResultDomainKind.PLATE_COLUMN_NAME, plateId);
        row.put(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME, well.getDescription());
        row.put(measure, well.getValue());
        return row;
    }

    @Override
    @NotNull
    public OntologyManager.UpdateableTableImportHelper getImportHelper(
        Container container,
        User user,
        ExpRun run,
        ExpData data,
        ExpProtocol protocol,
        AssayProvider provider
    )
    {
        return new PlateMetadataImportHelper(data, container, user, run, protocol, provider);
    }

    @Override
    public void updateReplicateStatsDomain(User user, ExpProtocol protocol, GWTDomain<GWTPropertyDescriptor> update, Domain resultsDomain) throws ExperimentException
    {
        Domain replicateDomain = ensurePlateReplicateStatsDomain(protocol);
        boolean domainDirty = false;
        Set<String> domainBaseProperties = replicateDomain.getBaseProperties().stream().map(DomainProperty::getName).collect(Collectors.toSet());
        Map<String, DomainProperty> existingFields = new HashMap<>();
        replicateDomain.getProperties().forEach(dp -> {
            if (!domainBaseProperties.contains(dp.getName()))
                existingFields.put(dp.getName(), dp);
        });

        for (GWTPropertyDescriptor prop : update.getFields())
        {
            // for measures of type : numeric create the stats fields
            if (prop.isMeasure())
            {
                PropertyType type = PropertyType.getFromURI(null, prop.getRangeURI());
                if (type.getJdbcType().isNumeric())
                {
                    for (String name : PlateReplicateStatsDomainKind.getStatsFieldNames(prop.getName()))
                    {
                        // check for additions
                        if (!existingFields.containsKey(name))
                        {
                            // create the property
                            PropertyStorageSpec spec = new PropertyStorageSpec(name, JdbcType.DOUBLE);
                            replicateDomain.addProperty(spec);

                            domainDirty = true;
                        }
                        else
                            existingFields.remove(name);
                    }
                }
            }
        }

        // check for removals
        if (!existingFields.isEmpty())
        {
            domainDirty = true;
            for (DomainProperty prop : existingFields.values())
                prop.delete();
        }

        if (domainDirty)
            replicateDomain.save(user);
    }

    @Override
    public @Nullable Domain getPlateReplicateStatsDomain(ExpProtocol protocol)
    {
        String uri = getPlateReplicateStatsDomainUri(protocol);
        return PropertyService.get().getDomain(protocol.getContainer(), uri);
    }

    private String getPlateReplicateStatsDomainUri(ExpProtocol protocol)
    {
        DomainKind domainKind = PropertyService.get().getDomainKindByName(PlateReplicateStatsDomainKind.KIND_NAME);
        return domainKind.generateDomainURI(AssaySchema.NAME, protocol.getName(), protocol.getContainer(), null);
    }

    private Domain ensurePlateReplicateStatsDomain(ExpProtocol protocol)
    {
        Domain domain = getPlateReplicateStatsDomain(protocol);
        if (domain == null)
            domain = PropertyService.get().createDomain(protocol.getContainer(), getPlateReplicateStatsDomainUri(protocol), PlateReplicateStatsDomainKind.NAME);

        return domain;
    }

    /**
     * Computes and inserts replicate statistics into the protocol schema table.
     *
     * @param run The run associated with the replicate values, only required in the insert case
     * @param forInsert Boolean value to indicate insert or update of the table rows
     * @param replicateRows The assay result rows grouped by replicate well lsid.
     */
    @Override
    public void insertReplicateStats(
            Container container,
            User user,
            ExpProtocol protocol,
            @Nullable ExpRun run,
            boolean forInsert,
            Map<Lsid, List<Map<String, Object>>> replicateRows
    ) throws ExperimentException
    {
        if (replicateRows.isEmpty())
            return;

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            throw new ExperimentException(String.format("Unable to find the provider for protocol : %s", protocol.getName()));

        if (run == null && forInsert)
            throw new ExperimentException("Run is required when inserting into the replicate stats table");

        Domain resultDomain = provider.getResultsDomain(protocol);
        Map<String, List<Double>> measures = new CaseInsensitiveHashMap<>();
        resultDomain.getProperties().forEach(dp -> {
            if (dp.isMeasure() && dp.getJdbcType().isNumeric())
                measures.put(dp.getName(), new ArrayList<>());
        });

        if (!measures.isEmpty())
        {
            List<Map<String, Object>> replicates = new ArrayList<>();
            List<Map<String, Object>> keys = new ArrayList<>();

            for (Map.Entry<Lsid, List<Map<String, Object>>> entry : replicateRows.entrySet())
            {
                if (!entry.getValue().isEmpty())
                {
                    // organize values for each replicate well group by measure
                    for (Map<String, Object> row : entry.getValue())
                    {
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
                        if (!Double.isNaN(stat.getMean()))
                            replicateRow.put(measure.getKey() + PlateReplicateStatsDomainKind.REPLICATE_MEAN_SUFFIX, stat.getMean());
                        if (!Double.isNaN(stat.getStdDev()))
                            replicateRow.put(measure.getKey() + PlateReplicateStatsDomainKind.REPLICATE_STD_DEV_SUFFIX, stat.getStdDev());
                    }
                }
            }

            if (!replicates.isEmpty())
            {
                try
                {
                    // persist to the replicate stats table
                    QueryUpdateService qus = getReplicateStatsUpdateService(container, user, provider, protocol);
                    if (qus == null)
                        throw new ExperimentException(String.format("There is no replicate stats update service available for assay : %s", protocol.getName()));

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
        }
    }

    @Nullable
    private QueryUpdateService getReplicateStatsUpdateService(Container container, User user, AssayProvider provider, ExpProtocol protocol)
    {
        AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
        TableInfo tableInfo = schema.createTable(TSVProtocolSchema.PLATE_REPLICATE_STATS_TABLE, null);
        if (tableInfo != null)
            return tableInfo.getUpdateService();

        return null;
    }

    @Override
    public void deleteReplicateStats(
            Container container,
            User user,
            ExpProtocol protocol,
            List<Map<String,Object>> keys
    ) throws ExperimentException
    {
        if (keys.isEmpty())
            return;

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            throw new ExperimentException(String.format("Unable to find the provider for protocol : %s", protocol.getName()));

        try
        {
            QueryUpdateService qus = getReplicateStatsUpdateService(container, user, provider, protocol);
            if (qus == null)
                throw new ExperimentException(String.format("There is no replicate stats update service available for assay : %s", protocol.getName()));

            qus.deleteRows(user, container, keys, null, null);
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    private static class PlateMetadataImportHelper extends SimpleAssayDataImportHelper
    {
        private final Map<Integer, Map<Position, Lsid>> _wellPositionMap;       // map of plate position to well table
        private final Map<Integer, Map<Position, Lsid>> _wellReplicateMap;      // map of plate position to replicate stats table
        private final Map<Lsid, List<Map<String, Object>>> _replicateRows;
        private final Map<Object, Plate> _plateIdentifierMap;
        private final Container _container;
        private final User _user;
        private final ExpRun _run;
        private final ExpProtocol _protocol;
        private final AssayProvider _provider;

        public PlateMetadataImportHelper(ExpData data, Container container, User user, ExpRun run, ExpProtocol protocol, AssayProvider provider)
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
        }

        @Override
        public void bindAdditionalParameters(Map<String, Object> map, ParameterMapStatement target) throws ValidationException
        {
            super.bindAdditionalParameters(map, target);

            Domain runDomain = _provider.getRunDomain(_protocol);
            Domain resultDomain = _provider.getResultsDomain(_protocol);
            DomainProperty plateSetProperty = runDomain.getPropertyByName(AssayPlateMetadataService.PLATE_SET_COLUMN_NAME);
            DomainProperty plateProperty = resultDomain.getPropertyByName(AssayResultDomainKind.PLATE_COLUMN_NAME);
            DomainProperty wellLocationProperty = resultDomain.getPropertyByName(AssayResultDomainKind.WELL_LOCATION_COLUMN_NAME);

            // get the plate associated with this row (checking the results domain field first)
            Object plateIdentifier = PropertyService.get().getDomainPropertyValueFromRow(plateProperty, map);
            Plate plate = _plateIdentifierMap.get(plateIdentifier);
            if (plate == null)
            {
                if (plateSetProperty != null && plateIdentifier != null)
                {
                    Object plateSetVal = _run.getProperty(plateSetProperty);
                    Integer plateSetRowId = plateSetVal != null ? Integer.parseInt(String.valueOf(plateSetVal)) : null;
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
                filter.addCondition(FieldKey.fromParts("PlateId"), plate.getRowId());
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
                    target.put(WELL_LSID_COLUMN_NAME, positionToWellLsid.get(pos));

                // find the associated replicate well group for this position (if any)
                if (positionToReplicateLsid.containsKey(pos))
                {
                    Lsid lsid = positionToReplicateLsid.get(pos);
                    target.put(REPLICATE_LSID_COLUMN_NAME, lsid);
                    _replicateRows.computeIfAbsent(lsid, k -> new ArrayList<>()).add(map);
                }
            }
        }

        @Override
        public void afterBatchInsert(int rowCount)
        {
            try
            {
                // compute replicate calculations and insert into the replicate stats table
                AssayPlateMetadataService.get().insertReplicateStats(_container, _user, _protocol, _run, true, _replicateRows);
            }
            catch (ExperimentException e)
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

            PlateSet plateSet = PlateManager.get().createPlateSet(container, user, new PlateSetImpl(), plates, null);
            List<Plate> plateSetPlates = PlateManager.get().getPlatesForPlateSet(plateSet);
            assertEquals("Expected two plates to be created.", 2, plateSetPlates.size());
            Plate plate = plateSetPlates.get(0);

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
