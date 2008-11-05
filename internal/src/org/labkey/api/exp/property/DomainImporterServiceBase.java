/*
 * Copyright (c) 2008 LabKey Corporation
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

import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.DomainImporterService;
import org.labkey.api.gwt.client.ui.domain.ImportException;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.util.SessionTempFileHolder;
import org.labkey.api.view.ViewContext;
import org.labkey.common.tools.ColumnDescriptor;
import org.labkey.common.tools.DataLoader;
import org.labkey.common.tools.ExcelLoader;
import org.labkey.common.tools.TabLoader;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * User: jgarms
 * Date: Nov 4, 2008
 */
public class DomainImporterServiceBase extends BaseRemoteService implements DomainImporterService
{
    /** The number of sample data rows to return **/
    private static final int NUM_SAMPLE_ROWS = 5;

    public DomainImporterServiceBase(ViewContext context)
    {
        super(context);
    }

    public List<InferencedColumn> inferenceColumns() throws ImportException
    {
        HttpSession session = getViewContext().getSession();
        SessionTempFileHolder fileHolder = (SessionTempFileHolder)session.getAttribute("org.labkey.domain.tempFile");

        if (fileHolder == null)
            throw new ImportException("No temp file uploaded");

        File file = fileHolder.getFile();
        DataLoader loader = getDataLoader(file);

        try
        {
            return getColumns(loader);
        }
        finally
        {
            loader.close();
        }
        
    }

    private DataLoader getDataLoader(File file) throws ImportException
    {
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

            for (int i=0; i<columns.length; i++)
            {
                ColumnDescriptor column = columns[i];
                GWTPropertyDescriptor prop = new GWTPropertyDescriptor();
                prop.setName(column.name);
                prop.setRangeURI(column.getRangeURI());

                List<String> columnData = new ArrayList<String>();
                for (int rowIndex=1; rowIndex<numRows; rowIndex++)
                {
                    columnData.add(data[rowIndex][i]);
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
}
