package org.labkey.elispot.plate;

import org.labkey.api.study.PlateTemplate;
import org.labkey.api.exp.ExperimentException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

import java.io.File;
import java.io.LineNumberReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Jan 23, 2008
 */
public class TextPlateReader implements ElispotPlateReaderService.I
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
            LineNumberReader reader = new LineNumberReader(new FileReader(dataFile));
            try
            {
                List<String> data = new ArrayList<String>();
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

    private static int getStartRow(List<String> data)
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

    private static boolean getRowData(double[] row, String line, int index)
    {
        StringTokenizer tokenizer = new StringTokenizer(line);
        char start = 'A';
        start += index;

        if (tokenizer.nextToken().equalsIgnoreCase(String.valueOf(start)))
        {
            int i=0;
            while (tokenizer.hasMoreTokens())
            {
                String token = tokenizer.nextToken();
                if (!NumberUtils.isNumber(token))
                    return false;
                row[i++] = NumberUtils.toDouble(token);
                if (i == row.length)
                    return true;
            }
        }
        return false;
    }
}
