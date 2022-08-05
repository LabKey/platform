/*
 * Copyright (c) 2004-2018 Fred Hutchinson Cancer Research Center
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
    private final ResultsFactory _factory;
    private List<DisplayColumn> _displayColumns;
    private final Map<String, String> _renameColumnMap;

    private int _dataRowCount;
    private Results _results;

    public TSVGridWriter(ResultsFactory factory)
    {
        _factory = factory;
        _renameColumnMap = Collections.emptyMap();
    }

    /**
     * Create a TSVGridWriter given a ResultsFactory and a set of DisplayColumns.
     *
     * @param factory ResultsFactory (supplier of ResultSet/Map<FieldKey,ColumnInfo>).
     * @param displayColumns The DisplayColumns.
     */
    public TSVGridWriter(ResultsFactory factory, List<DisplayColumn> displayColumns)
    {
        _factory = factory;
        _displayColumns = init(displayColumns);
        _renameColumnMap = Collections.emptyMap();
    }

    public TSVGridWriter(ResultsFactory factory, List<DisplayColumn> displayColumns, Map<String, String> renameColumnMap)
    {
        _factory = factory;
        _displayColumns = init(displayColumns);
        _renameColumnMap = renameColumnMap;
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

    @Override
    protected void write()
    {
        setResultsHandleAndClose(results -> {
            _results = results; // TODO: Change write() signature to take context and stop setting member variable
            TSVGridWriter.super.write();
            return null;
        });
    }

    private interface ResultsHandler<T>
    {
        T handle(Results results) throws IOException;
    }

    private <T> T setResultsHandleAndClose(ResultsHandler<T> handler)
    {
        try (Results results = _factory.get())
        {
            if (null == _displayColumns)
                _displayColumns = init(results.getFieldMap().values());
            return handler.handle(results);
        }
        catch (SQLException ex)
        {
            throw new RuntimeSQLException(ex);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void writeColumnHeaders()
    {
        RenderContext context = getRenderContext();
        writeColumnHeaders(context, _displayColumns, _renameColumnMap);
    }

    private RenderContext getRenderContext()
    {
        return HttpView.hasCurrentView() ? new RenderContext(HttpView.currentContext()) : new RenderContext();
    }

    @Override
    protected void writeBody()
    {
        writeBody(_results);
    }

    private void writeBody(Results results)
    {
        try
        {
            RenderContext ctx = getRenderContext();
            ctx.setResults(results);

            // Output all the data cells
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(results);

            while (results.next())
            {
                ctx.setRow(factory.getRowMap(results));
                writeRow(ctx, _displayColumns);
            }
        }
        catch (SQLException ex)
        {
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
     */
    @NotNull
    public List<File> writeBatchFiles(@NotNull File outputDir, @NotNull String baseName, @Nullable String extension, int batchSize, @Nullable FieldKey batchColumn)
    {
        extension = StringUtils.trimToEmpty(extension);
        String ext = "".equals(extension) || extension.startsWith(".") ? extension : "." + extension;

        return setResultsHandleAndClose(results->{
            if (batchSize > 0 && null != batchColumn && !results.hasColumn(batchColumn))
                throw new IllegalArgumentException("Batch column " + batchColumn + " not found in results");
            return writeResultSetBatches(results, outputDir, baseName, ext, batchSize, batchColumn);
        });
    }

    @NotNull
    private List<File> writeResultSetBatches(Results results, File outputDir, String baseName, String extension, int batchSize, @Nullable FieldKey batchColumn) throws IOException
    {
        int currentBatchSize = 0;
        int totalBatches = 1;
        Object previousBatchColumnValue = null;
        Object newBatchColumnValue;
        List<File> outputFiles = new ArrayList<>();
        outputFiles.add(startBatchFile(outputDir, baseName, extension, batchSize, totalBatches));
        RenderContext ctx = getRenderContext();
        ctx.setResults(results);
        try
        {
            ResultSetRowMapFactory factory = ResultSetRowMapFactory.create(results);
            while (results.next())
            {
                ctx.setRow(factory.getRowMap(results));
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
                        closeBatchFile();
                        currentBatchSize = 1;
                        outputFiles.add(startBatchFile(outputDir, baseName, extension, batchSize, ++totalBatches));
                    }
                }
                writeRow(ctx, _displayColumns);
            }
        }
        catch (SQLException ex)
        {
            throw new RuntimeSQLException(ex);
        }
        closeBatchFile();
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

    private void closeBatchFile() throws IOException
    {
        writeFileFooter();
        super.close();
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
