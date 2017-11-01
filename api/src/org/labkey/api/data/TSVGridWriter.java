/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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

package org.labkey.api.data;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ResultSetRowMapFactory;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.view.HttpView;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TSVGridWriter extends TSVColumnWriter implements ExportWriter
{
    private final Results _rs;
    protected final List<DisplayColumn> _displayColumns;

    private int _dataRowCount;

    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns) throws SQLException, IOException
    {
        this(ctx, tinfo, displayColumns, tinfo.getName());
    }

    public TSVGridWriter(RenderContext ctx, TableInfo tinfo, List<DisplayColumn> displayColumns, String name) throws IOException
    {
        try
        {
            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(tinfo, Collections.emptySet(), RenderContext.getSelectColumns(displayColumns, tinfo));
            _rs = ctx.getResultSet(columns, displayColumns, tinfo, null, null, Table.ALL_ROWS, Table.NO_OFFSET, name, false);
            _displayColumns = init(displayColumns);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
    }


    public TSVGridWriter(Results results) throws SQLException
    {
        _rs = results;
        _displayColumns = init(results.getFieldMap().values());
    }

    /**
     * Create a TSVGridWriter for a Results (ResultSet/fieldMap) and a set of DisplayColumns.
     * You can use use {@link QueryService#getColumns(TableInfo, Collection<FieldKey>, Collection<ColumnInfo>)}
     * to obtain a fieldMap which will include any extra ColumnInfo required by the selected DisplayColumns.
     *
     * @param rs Results (ResultSet/Map<FieldKey,ColumnInfo>).
     * @param displayColumns The DisplayColumns.
     */

    public TSVGridWriter(Results rs, List<DisplayColumn> displayColumns)
    {
        _rs = rs;
        _displayColumns = init(displayColumns);
    }

    private static List<DisplayColumn> init(Collection<ColumnInfo> cols)
    {
        List<DisplayColumn> dataColumns = new LinkedList<>();

        for (ColumnInfo col : cols)
            dataColumns.add(col.getDisplayColumnFactory().createRenderer(col));

        return init(dataColumns);
    }


    private static List<DisplayColumn> init(List<DisplayColumn> displayColumns)
    {
        for (DisplayColumn displayColumn : displayColumns)
        {
            displayColumn.setRequiresHtmlFiltering(false);
        }

        return displayColumns;
    }


    public Map<FieldKey, ColumnInfo> getFieldMap()
    {
        return null==_rs ? null : _rs.getFieldMap();
    }

    @Override
    public void writeColumnHeaders()
    {
        RenderContext context = getRenderContext();
        writeColumnHeaders(context, _displayColumns);
    }

    private RenderContext getRenderContext()
    {
        return HttpView.hasCurrentView() ? new RenderContext(HttpView.currentContext()) : new RenderContext();
    }

    @Override
    protected void writeBody()
    {
         writeResultSet(_rs);
    }

    public void writeResultSet(Results rs)
    {
        RenderContext context = getRenderContext();
        context.setResults(rs);
        writeResultSet(context, rs);
    }


    public void writeResultSet(RenderContext ctx, Results rs)
    {
        try
        {
            // Output all the data cells
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);

            while (rs.next())
            {
                ctx.setRow(factory.getRowMap(rs));
                writeRow(ctx, _displayColumns);
            }
        }
        catch (SQLException ex)
        {
            closeResults();
            throw new RuntimeSQLException(ex);
        }
    }

    /**
     * Write multiple files, limiting size by either number of rows or number of values found in a specified batch column.
     * @param outputDir Directory to write files to.
     * @param baseName The base name to use for output files. When batchSize > 0, will be appended with "-1", "-2", etc.
     * @param extension The full extension to add to the base (+ batch number) filename. Leading "." is optional and will be added if missing.
     * @param batchSize The number of records (or batchColumn values, see below), to include in one file before closing and starting the next. 0 == no batching, write a single file.
     * @param batchColumn Optional. Sentinel column to monitor in the result set. Instead of incrementing batch counter on every row, only increment when this value changes.
     *                    Note this method does not sort the resultset, so this will work best if the results are grouped or ordered by the batchColumn values.
     * @return List of the output Files.
     * @throws IOException
     */
    @NotNull
    public List<File> writeBatchFiles(@NotNull File outputDir, @NotNull String baseName, @Nullable String extension, int batchSize, @Nullable FieldKey batchColumn) throws IOException
    {
        if (batchSize > 0 && null != batchColumn && !_rs.hasColumn(batchColumn))
            throw new IllegalArgumentException("Batch column " + batchColumn + "not found in results");
        extension = StringUtils.trimToEmpty(extension);
        extension = "".equals(extension) || extension.startsWith(".") ? extension : "." + extension;
        return writeResultSetBatches(_rs, outputDir, baseName, extension, batchSize, batchColumn);
    }

    @NotNull
    private List<File> writeResultSetBatches(Results rs, File outputDir, String baseName, String extension, int batchSize, @Nullable FieldKey batchColumn) throws IOException
    {
        int currentBatchSize = 0;
        int totalBatches = 1;
        Object previousBatchColumnValue = null;
        Object newBatchColumnValue;
        List<File> outputFiles = new ArrayList<>();
        outputFiles.add(startBatchFile(outputDir, baseName, extension, batchSize, totalBatches));
        RenderContext ctx = getRenderContext();
        ctx.setResults(rs);
        try
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(rs);
            while (rs.next())
            {
                ctx.setRow(factory.getRowMap(rs));
                if (batchSize > 0)
                {
                    if (null != batchColumn)
                    {
                        newBatchColumnValue = ctx.get(batchColumn, Object.class);
                        if ((null != newBatchColumnValue && !newBatchColumnValue.equals(previousBatchColumnValue))
                                || (null != previousBatchColumnValue && null == newBatchColumnValue)
                                || (null == previousBatchColumnValue && currentBatchSize == 0))
                        {
                            previousBatchColumnValue = newBatchColumnValue;
                            currentBatchSize++;
                        }
                    }
                    else
                    {
                        currentBatchSize++;
                    }
                    if (currentBatchSize > batchSize)
                    {
                        // Close this file and start the next
                        closeBatchFile(false);
                        currentBatchSize = 1;
                        outputFiles.add(startBatchFile(outputDir, baseName, extension, batchSize, ++totalBatches));
                    }
                }
                writeRow(ctx, _displayColumns);
            }
        }
        catch (SQLException ex)
        {
            closeResults();
            throw new RuntimeSQLException(ex);
        }
        closeBatchFile(true);
        return outputFiles;
    }

    @NotNull
    private File startBatchFile(File outputDir, String baseName, String extension, int batchSize, int totalBatches) throws IOException
    {
        String batchId = batchSize == 0 ? "" : "-" + totalBatches;
        File file = new File(outputDir, baseName + batchId + extension);
        prepare(file);
        writeFileHeader();
        if (isHeaderRowVisible())
            writeColumnHeaders();
        return file;
    }

    private void closeBatchFile(boolean closeResultSet) throws IOException
    {
        writeFileFooter();
        if (closeResultSet)
            close();
        else
            super.close();
    }

    @Override
    public void close() throws IOException
    {
        closeResults();
        super.close();
    }

    private void closeResults()
    {
        if (_rs != null) try { _rs.close(); } catch (SQLException e) {}
    }

    protected void writeRow(RenderContext ctx, List<DisplayColumn> displayColumns)
    {
        writeLine(getValues(ctx, displayColumns));
        _dataRowCount++;
    }

    @Override
    public int getDataRowCount()
    {
        return _dataRowCount;
    }

}
