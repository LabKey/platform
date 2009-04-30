/*
 * Copyright (c) 2008-2009 LabKey Corporation
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
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.view.ViewContext;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.common.tools.ColumnDescriptor;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: jgarms
 * Date: Nov 4, 2008
 */
public abstract class DomainImporterServiceBase extends BaseRemoteService implements DomainImporterService
{
    /** The number of sample data rows to return **/
    private static final int NUM_SAMPLE_ROWS = 5;

    public DomainImporterServiceBase(ViewContext context)
    {
        super(context);
    }

    public List<InferencedColumn> inferenceColumns() throws ImportException
    {
        DataLoader loader = getDataLoader();

        try
        {
            return getColumns(loader);
        }
        finally
        {
            loader.close();
        }
    }

    @NotNull
    private File getImportFile() throws ImportException
    {
        HttpSession session = getViewContext().getSession();
        SessionTempFileHolder fileHolder = (SessionTempFileHolder)session.getAttribute("org.labkey.domain.tempFile");

        if (fileHolder == null)
            throw new ImportException("No temp file uploaded");

        return fileHolder.getFile();
    }

    protected void deleteImportFile()
    {
        try
        {
            //noinspection ResultOfMethodCallIgnored
            getImportFile().delete();
            HttpSession session = getViewContext().getSession();
            session.removeAttribute("org.labkey.domain.tempFile");
        }
        catch (ImportException ie)
        {
            // Nothing to do here -- we don't care if we couldn't find the file
        }
    }

    @NotNull
    protected DataLoader getDataLoader() throws ImportException
    {
        File file = getImportFile();
        String filename = file.getName();
        try
        {
            if (filename.endsWith("xls"))
            {
                return new ExcelLoader(file, true);
            }
            else if (filename.endsWith("txt") || filename.endsWith("tsv"))
            {
                return new TabLoader(file, true);
            }
            else if (filename.endsWith("csv"))
            {
                TabLoader loader = new TabLoader(file, true);
                loader.parseAsCSV();
                return loader;
            }
            else
            {
                throw new ImportException("Unknown file type. Please upload a file with a suffix of .xls, .txt, .tsv or .csv");
            }
        }
        catch (IOException e)
        {
            throw new ImportException(e.getMessage());
        }

    }

    private List<InferencedColumn> getColumns(DataLoader loader) throws ImportException
    {
        List<InferencedColumn> result = new ArrayList<InferencedColumn>();

        try
        {
            ColumnDescriptor[] columns = loader.getColumns();

            String[][] data = loader.getFirstNLines(NUM_SAMPLE_ROWS + 1); // also need the header
            int numRows = data.length;

            for (int colIndex=0; colIndex<columns.length; colIndex++)
            {
                ColumnDescriptor column = columns[colIndex];
                GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
                prop.setName(column.name);
                prop.setRangeURI(column.getRangeURI());

                List<String> columnData = new ArrayList<String>();
                for (int rowIndex=1; rowIndex<numRows; rowIndex++)
                {
                    String datum = "";
                    if (data[rowIndex].length > colIndex) // Not guaranteed that every row has every column
                        datum = data[rowIndex][colIndex];
                    columnData.add(datum);
                }

                InferencedColumn infColumn = new InferencedColumn(prop, columnData);

                result.add(infColumn);
            }
        }
        catch (IOException e)
        {
            throw new ImportException(e.getMessage());
        }

        return result;
    }

    public GWTDomain getDomainDescriptor(String typeURI)
    {
        return DomainUtil.getDomainDescriptor(getUser(), typeURI, getContainer());
    }

    public abstract List<String> importData(GWTDomain domain, Map<String, String> mappedColumnNames) throws ImportException;

    public List<String> updateDomainDescriptor(GWTDomain orig, GWTDomain update)
    {
        try
        {
            return DomainUtil.updateDomainDescriptor(orig, update, getContainer(), getUser());
        }
        catch (ChangePropertyDescriptorException e)
        {
            return Collections.singletonList(e.getMessage());
        }
    }
}
