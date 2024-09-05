package org.labkey.assay.plate;

import org.apache.commons.lang3.ArrayUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.util.UnexpectedException;
import org.labkey.assay.plate.query.WellTable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class PlateSetExport
{
    public static final String DESTINATION = "Destination ";
    public static final String SOURCE = "Source ";
    public static final String SAMPLE_ID_COL = "sampleId";
    public static final String ROW_ID_COL = "rowid";
    public static final String PLATE_SET_ID_COL = "plateSetId";
    public static final String PLATE_NAME_COL = "plateName";
    public static final String PLATE_BARCODE_COL = "barcode";

    private final Map<String, FieldKey> FKMap = Map.of(
        SAMPLE_ID_COL, FieldKey.fromParts("sampleid", "name"),
        WellTable.Column.Position.name(), FieldKey.fromParts("position"),
        ROW_ID_COL, FieldKey.fromParts("rowid"),
        WellTable.Column.Row.name(), FieldKey.fromParts("plateid", "platetype", "rows"),
        WellTable.Column.Col.name(), FieldKey.fromParts("plateid", "platetype", "columns"),
        WellTable.Column.PlateId.name(), FieldKey.fromParts("plateid"),
        PLATE_SET_ID_COL, FieldKey.fromParts("plateid", "plateset"),
        PLATE_NAME_COL, FieldKey.fromParts("plateid", "name"),
        PLATE_BARCODE_COL, FieldKey.fromParts("plateid", "barcode")
    );

    public PlateSetExport()
    {
    }

    // Returns the base set of columns as well as the metadata columns included on default plate view
    private Collection<ColumnInfo> getWellColumns(TableInfo wellTable, List<FieldKey> includedMetaDataCols)
    {
        List<FieldKey> defaultCols = new ArrayList<>(FKMap.values());
        defaultCols.addAll(includedMetaDataCols);
        return QueryService.get().getColumns(wellTable, defaultCols).values();
    }

    private Object[] getDataRow(String prefix, Results rs, List<FieldKey> includedMetaDataCols) throws SQLException
    {
        List<Object> baseColumns = new ArrayList<>(
            Arrays.asList(
                rs.getString(FKMap.get(PLATE_NAME_COL)),
                rs.getString(FKMap.get(PLATE_BARCODE_COL)),
                rs.getString(FKMap.get(WellTable.Column.Position.name())),
                rs.getInt(FKMap.get(WellTable.Column.Row.name())) * rs.getInt(FKMap.get(WellTable.Column.Col.name()))
            )
        );

        if (!prefix.equals(PlateSetExport.DESTINATION))
            baseColumns.add(rs.getString(FKMap.get(SAMPLE_ID_COL)));

        for (FieldKey col : includedMetaDataCols)
            baseColumns.add(rs.getString(col));

        return baseColumns.toArray();
    }

    // Returns array of ColumnDescriptors used as column layout once fed to an ArrayExcelWriter
    public static ColumnDescriptor[] getColumnDescriptors(String prefix, List<FieldKey> includedMetadataCols)
    {
        List<ColumnDescriptor> baseColumns = new ArrayList<>(
            Arrays.asList(
                new ColumnDescriptor(prefix + "Plate ID"),
                new ColumnDescriptor(prefix + "Barcode"),
                new ColumnDescriptor(prefix + "Well"),
                new ColumnDescriptor(prefix + "Plate Type")
            )
        );

        if (!PlateSetExport.DESTINATION.equals(prefix))
            baseColumns.add(new ColumnDescriptor("Sample ID"));

        List<ColumnDescriptor> metadataColumns = includedMetadataCols
                .stream()
                .map(fk -> new ColumnDescriptor(fk.getParts().size() > 1 ? fk.getParent().getCaption() : fk.getCaption()))
                .toList();

        baseColumns.addAll(metadataColumns);
        return baseColumns.toArray(new ColumnDescriptor[0]);
    }

    // Create sampleIdToRow of the following form:
    // {<sample id>: [{dataRow1}, {dataRow2}, ... ], ... }
    // Where the data rows contain the key's sample
    private Map<String, List<Object[]>> processPlateSet(TableInfo wellTable, List<FieldKey> includedMetadataCols, int plateSetId, String plateSetExport) {
        Map<String, List<Object[]>> sampleIdToRow = new LinkedHashMap<>();
        try (Results rs = QueryService.get().select(wellTable, getWellColumns(wellTable, includedMetadataCols), new SimpleFilter(FKMap.get(PLATE_SET_ID_COL), plateSetId), new Sort(ROW_ID_COL)))
        {
            while (rs.next())
            {
                String sampleId = rs.getString(FKMap.get(SAMPLE_ID_COL));
                if (sampleId == null)
                    continue;

                sampleIdToRow.computeIfAbsent(sampleId, key -> new ArrayList<>())
                        .add(getDataRow(plateSetExport, rs, includedMetadataCols));
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        return sampleIdToRow;
    }

    public List<Object[]> getWorklist(
        TableInfo wellTable,
        int sourcePlateSetId,
        int destinationPlateSetId,
        List<FieldKey> sourceIncludedMetadataCols,
        List<FieldKey> destinationIncludedMetadataCols
    )
    {
        List<Object[]> plateDataRows = new ArrayList<>();
        Map<String, List<Object[]>> sampleIdToDestinationRow = processPlateSet(wellTable, destinationIncludedMetadataCols, destinationPlateSetId, PlateSetExport.DESTINATION);
        Map<String, List<Object[]>> sampleIdToSourceRow = processPlateSet(wellTable, sourceIncludedMetadataCols, sourcePlateSetId, PlateSetExport.SOURCE);

        for (Map.Entry<String, List<Object[]>> entry : sampleIdToSourceRow.entrySet()) {
            String sampleId = entry.getKey();
            List<Object[]> sourceRows = entry.getValue();

            List<Object[]> destinationRows = sampleIdToDestinationRow.get(sampleId);

            // Support scenario where source samples have no destination sample to match against
            if (destinationRows == null)
            {
                for (Object[] sourceRow : sourceRows)
                    plateDataRows.add(Arrays.copyOf(sourceRow, sourceRow.length + destinationIncludedMetadataCols.size() + 4));
            }
            // Support aliquot scenario where a single sample is split into many in destination
            else if (sourceRows.size() == 1)
            {
                for (Object[] destinationRow : destinationRows)
                    plateDataRows.add(ArrayUtils.addAll(sourceRows.get(0), destinationRow));
            }
            // Catch many-to-many operations
            else if (sourceRows.size() != destinationRows.size())
            {
                Set<String> samplesSet = sourceRows.stream().map(row -> (String) row[0]).collect(Collectors.toSet());
                throw new UnsupportedOperationException("Many-to-many single-sample operation detected. See sample(s): " + String.join(", ", samplesSet));
            }
            // Support 1:1 scenarios
            else
            {
                for (int i = 0; i < sourceRows.size(); i++)
                {
                    Object[] destinationRow = (i >= destinationRows.size()) ? new Object[destinationIncludedMetadataCols.size() + 4] : destinationRows.get(i);
                    plateDataRows.add(ArrayUtils.addAll(sourceRows.get(i), destinationRow));
                }
            }
        }

        return plateDataRows;
    }

    public List<Object[]> getInstrumentInstructions(TableInfo wellTable, int plateSetId, List<FieldKey> includedMetadataCols)
    {
        List<Object[]> plateDataRows = new ArrayList<>();
        try (Results rs = QueryService.get().select(wellTable, getWellColumns(wellTable, includedMetadataCols), new SimpleFilter(FKMap.get(PLATE_SET_ID_COL), plateSetId), new Sort(ROW_ID_COL)))
        {
            while (rs.next())
            {
                if (rs.getString(FKMap.get(SAMPLE_ID_COL)) == null)
                    continue;

                plateDataRows.add(getDataRow("", rs, includedMetadataCols));
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        return plateDataRows;
    }
}
