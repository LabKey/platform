package org.labkey.assay.plate;

import org.apache.commons.lang3.ArrayUtils;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Results;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.util.UnexpectedException;
import org.labkey.assay.plate.query.WellTable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PlateSetExport
{
    public static final String DESTINATION = "Destination ";
    public static final String SOURCE = "Source ";
    public static final String SAMPLE_ID_COL = "sampleId";
    public static final String ROW_ID_COL = "rowid";
    public static final String PLATE_SET_ID_COL = "plateSetId";
    public static final String PLATE_NAME_COL = "plateName";

    private final Map<String, FieldKey> FKMap = Map.of(
        SAMPLE_ID_COL, FieldKey.fromParts("sampleid", "name"),
        WellTable.Column.Position.name(), FieldKey.fromParts("position"),
        ROW_ID_COL, FieldKey.fromParts("rowid"),
        WellTable.Column.Row.name(), FieldKey.fromParts("plateid", "platetype", "rows"),
        WellTable.Column.Col.name(), FieldKey.fromParts("plateid", "platetype", "columns"),
        WellTable.Column.PlateId.name(), FieldKey.fromParts("plateid"),
        PLATE_SET_ID_COL, FieldKey.fromParts("plateid", "plateset"),
        PLATE_NAME_COL, FieldKey.fromParts("plateid", "name")
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
                rs.getString(FKMap.get(WellTable.Column.Position.name())),
                rs.getInt(FKMap.get(WellTable.Column.Row.name())) * rs.getInt(FKMap.get(WellTable.Column.Col.name())) + "-well"
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
                new ColumnDescriptor(prefix + "Well"),
                new ColumnDescriptor(prefix + "Plate Type")
            )
        );

        if (!PlateSetExport.DESTINATION.equals(prefix))
            baseColumns.add(new ColumnDescriptor("Sample ID"));

        List<ColumnDescriptor> metadataColumns = includedMetadataCols
                .stream()
                .map(fk -> new ColumnDescriptor(fk.getCaption()))
                .toList();

        baseColumns.addAll(metadataColumns);
        return baseColumns.toArray(new ColumnDescriptor[0]);
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
        Map<String, List<Object[]>> sampleIdToDestinationRow = new LinkedHashMap<>();

        // Create sampleIdToDestinationRow of the following form:
        // {<sample id>: [{dataRow1}, {dataRow2}, ... ], ... }
        // Where the data rows contain the key's sample
        try (Results rs = QueryService.get().select(wellTable, getWellColumns(wellTable, destinationIncludedMetadataCols), new SimpleFilter(FKMap.get(PLATE_SET_ID_COL), destinationPlateSetId), new Sort(ROW_ID_COL)))
        {
            while (rs.next()) {
                String sampleId = rs.getString(FKMap.get(SAMPLE_ID_COL));
                if (sampleId == null) {
                    continue;
                }

                sampleIdToDestinationRow.computeIfAbsent(sampleId, key -> new ArrayList<>())
                        .add(getDataRow(PlateSetExport.DESTINATION, rs, destinationIncludedMetadataCols));
            }
        }
        catch (SQLException e)
        {
            throw UnexpectedException.wrap(e);
        }

        // 1. Retrieve origin row
        // 2. Use sampleIdToDestinationRow to look up destination rows that share the same sample id
        // 3. Iterate and concatenate all destination rows to origin row, and add result to final data rows list
        // This ensures that in the case a sample has been aliquoted and exists in multiple destination wells, but only
        // one source well, the Excel will still be formed as intended
        try (Results rs = QueryService.get().select(wellTable, getWellColumns(wellTable, sourceIncludedMetadataCols), new SimpleFilter(FKMap.get(PLATE_SET_ID_COL), sourcePlateSetId), new Sort(ROW_ID_COL)))
        {
            while (rs.next())
            {
                String sampleId = rs.getString(FKMap.get(SAMPLE_ID_COL));
                if (sampleId == null)
                    continue;

                Object[] sourceDataRow = getDataRow(PlateSetExport.SOURCE, rs, sourceIncludedMetadataCols);

                List<Object[]> destinationDataRows = sampleIdToDestinationRow.get(sampleId);
                if (destinationDataRows == null)
                    throw new ValidationException("There are samples plated in the origin Plate Set with no corresponding well in the destination Plate Set.");

                for (Object[] dataRow : destinationDataRows)
                    plateDataRows.add(ArrayUtils.addAll(sourceDataRow, dataRow));
            }
        }
        catch (Exception e)
        {
            throw UnexpectedException.wrap(e);
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
