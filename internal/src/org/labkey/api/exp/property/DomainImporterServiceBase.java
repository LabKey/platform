/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.exp.property;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.PropertyType;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.GWTImportException;
import org.labkey.api.gwt.client.ui.domain.ImportStatus;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Nov 4, 2008
 */
public abstract class DomainImporterServiceBase extends DomainEditorServiceBase implements DomainImporterService
{
    /** The number of sample data rows to return **/
    private static final int NUM_SAMPLE_ROWS = 5;

    protected  SessionTempFileHolder _sessionFileHolder;
    protected File _importFile;
    private int _numSampleRows = NUM_SAMPLE_ROWS;

    public DomainImporterServiceBase(ViewContext context)
    {
        super(context);
        setImportFile(context);
    }

    protected void setImportFile(ViewContext context)
    {
        // Retrieve the file from the session now, so we can delete it later. If we're importing in a background thread,
        // we won't have a session at the end of import.
        HttpSession session = getViewContext().getSession();
        _sessionFileHolder = (SessionTempFileHolder)session.getAttribute("org.labkey.domain.tempFile");
        _importFile = (null == _sessionFileHolder ? null : _sessionFileHolder.getFile());
    }

    public List<InferencedColumn> inferenceColumns() throws GWTImportException
    {
        try (DataLoader loader = getDataLoader())
        {
            return getColumns(loader);
        }
    }

    @NotNull
    private File getImportFile() throws GWTImportException
    {
        if (null == _importFile)
            throw new GWTImportException("No import file uploaded");

        return _importFile;
    }

    protected void deleteImportFile()
    {
        assert null == _sessionFileHolder || null == _importFile || _importFile == _sessionFileHolder.getFile();
        if (null != _sessionFileHolder)
            _sessionFileHolder.delete();
        else if (null != _importFile)
            _importFile.delete();
    }

    @NotNull
    protected DataLoader getDataLoader() throws GWTImportException
    {
        try
        {
            return DataLoader.get().createLoader(getImportFile(), null, true, getContainer(), null);
        }
        catch (IOException e)
        {
            throw new GWTImportException(e.getMessage());
        }
    }

    public int getNumSampleRows()
    {
        return _numSampleRows;
    }

    public void setNumSampleRows(int numSampleRows)
    {
        _numSampleRows = numSampleRows;
    }

    private List<InferencedColumn> getColumns(DataLoader loader) throws GWTImportException
    {
        List<InferencedColumn> result = new ArrayList<>();

        try
        {
            ColumnDescriptor[] columns = loader.getColumns();
            String[][] data = loader.getFirstNLines(_numSampleRows + 1); // also need the header
            int numRows = data.length;

            for (int colIndex=0; colIndex<columns.length; colIndex++)
            {
                ColumnDescriptor column = columns[colIndex];
                GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
                String name = column.name;

                if (name.length() > 2 && name.startsWith("\"") && name.endsWith("\""))
                    name = name.substring(1, name.length()-1);

                prop.setName(name);
                PropertyType rangeURI = PropertyType.fromName(column.getRangeURI());

                if (null != rangeURI)
                    prop.setRangeURI(rangeURI.getURI());
                else
                    prop.setRangeURI(column.getRangeURI());

                prop.setMvEnabled(column.isMvEnabled());
                List<String> columnData = new ArrayList<>();

                for (int rowIndex=1; rowIndex<numRows; rowIndex++)
                {
                    String datum = "";

                    if (data[rowIndex].length > colIndex) // Not guaranteed that every row has every column
                    {
                        datum = data[rowIndex][colIndex];

                        if (column.clazz == Integer.class && !datum.isEmpty())
                        {
                            // data comes back as "3.0", but we know it's an integer column... so convert to "3", #21232
                            try
                            {
                                datum = String.valueOf(Double.valueOf(datum).intValue());
                            }
                            catch (NumberFormatException e)
                            {
                                // must not have been integer (beyond inference) so ignore conversion (issue 23472)
                            }
                        }
                    }

                    columnData.add(datum);
                }

                InferencedColumn infColumn = new InferencedColumn(prop, columnData);
                result.add(infColumn);
            }
        }
        catch (IOException e)
        {
            throw new GWTImportException(e.getMessage());
        }

        return result;
    }

    public GWTDomain getDomainDescriptor(String typeURI)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, getContainer());
    }

    public abstract ImportStatus importData(GWTDomain domain, Map<String, String> mappedColumnNames) throws GWTImportException;
}
