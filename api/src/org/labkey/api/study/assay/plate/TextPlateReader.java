/*
 * Copyright (c) 2008-2015 LabKey Corporation
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

package org.labkey.api.study.assay.plate;

import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.Readers;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.exp.ExperimentException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.labkey.api.util.NumberUtilsLabKey;

import java.io.File;
import java.io.LineNumberReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * User: Karl Lum
 * Date: Jan 23, 2008
 */
public class TextPlateReader extends AbstractPlateReader implements PlateReader
{
    public static final String TYPE = "txt";

    public String getType()
    {
        return TYPE;
    }

    public double[][] loadFile(PlateTemplate template, File dataFile) throws ExperimentException
    {
        String fileName = dataFile.getName().toLowerCase();
        if (!fileName.endsWith(".txt"))
            throw new ExperimentException("Unable to load data file: Invalid Format");

        try {
            double[][] cellValues = new double[template.getRows()][template.getColumns()];
            LineNumberReader reader = new LineNumberReader(Readers.getReader(dataFile));
            try
            {
                List<String> data = new ArrayList<>();
                String line;
                while((line = reader.readLine()) != null)
                {
                    if (!StringUtils.isEmpty(line))
                        data.add(line);
                }

                int startRow = getStartRow(data);
                if (startRow == -1)
                {
                    throw new ExperimentException(dataFile.getName() + " does not appear to be a valid data file: unable to locate spot counts");
                }

                for (int i=0; i < template.getRows(); i++)
                {
                    if (!getRowData(cellValues[i], data.get(startRow + i), i))
                        throw new ExperimentException(dataFile.getName() + " does not appear to be a valid data file: unable to locate spot counts");
                }
            }
            finally
            {
                try { reader.close(); } catch (IOException e) {}
            }
            return cellValues;
        }
        catch (IOException e)
        {
            throw new ExperimentException(e);
        }
    }

    private int getStartRow(List<String> data)
    {
        Pattern rowHeader = Pattern.compile("\\s+1\\s+2\\s+3\\s+4\\s+5\\s+6\\s+7\\s+8\\s+9\\s+10\\s+11\\s+12");
        for (int row = 0; row < data.size(); row++)
        {
            String line = data.get(row);
            if (rowHeader.matcher(line).find())
                return row + 1;
        }
        return -1;
    }

    private boolean getRowData(double[] row, String line, int index)
    {
        StringTokenizer tokenizer = new StringTokenizer(line);
        char start = 'A';
        start += index;

        if (tokenizer.nextToken().equalsIgnoreCase(String.valueOf(start)))
        {
            try
            {
                int i=0;
                while (tokenizer.hasMoreTokens())
                {
                    String token = tokenizer.nextToken();
                    row[i++] = convertWellValue(token);
                    if (i == row.length)
                        return true;
                }
            }
            catch (ValidationException e)
            {
                return false;
            }
        }
        return false;
    }
}
