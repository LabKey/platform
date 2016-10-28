/*
 * Copyright (c) 2013-2016 LabKey Corporation
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
package org.labkey.api.pipeline;

import org.apache.commons.beanutils.ConversionException;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * User: klum
 * Date: 11/12/13
 */
public abstract class AbstractSpecimenTransformTask
{
    protected final PipelineJob _job;

    public AbstractSpecimenTransformTask(PipelineJob job)
    {
        _job = job;
    }

    public abstract void transform(File input, File output) throws PipelineJobException;

    /**
     * Transform a row of data from the parsed input data file the transformed specimen row
     * During the transform, it's expected that any lab, primary, derivative and additive
     * information would be extracted.
     *
     * @param inputRow
     * @param rowIndex
     * @param labIds
     * @param primaryIds
     * @param derivativeIds
     * @return the transformed specimen row
     * @throws java.io.IOException
     */
    protected abstract Map<String, Object> transformRow(Map<String, Object> inputRow, int rowIndex, Map<String, Integer> labIds, Map<String, Integer> primaryIds, Map<String, Integer> derivativeIds);

    protected abstract Set<String> getIgnoredHashColumns();
    protected abstract  Map<String,Integer> getLabIds();
    protected abstract  Map<String,Integer> getPrimaryIds();
    protected abstract  Map<String,Integer> getDerivativeIds();
    protected abstract  Map<String,Integer> getAdditiveIds();
    private int rowIndex = 0;
    private Set<String> hashes = new HashSet<>();

    protected List<Map<String, Object>> transformRows(List<Map<String, Object>> inputRows) throws IOException
    {
        return inputRows
            .stream()
            .filter(MapFilter.getMapFilter(this))
            .map(MapTransformer.getMapTransformer(this, getLabIds(), getPrimaryIds(), getDerivativeIds()))
            .collect(Collectors.toList());
    }

    protected interface MapFilter extends Predicate<Map<String, Object>>
    {
        static MapFilter getMapFilter(AbstractSpecimenTransformTask task)
        {
            return row -> {
                try
                {
                    return task.hashes.add(task.hashRow(row));
                }
                catch (IOException e)
                {
                    return true;    // Keep row
                }
            };
        }
    }

    protected interface MapTransformer extends UnaryOperator<Map<String, Object>>
    {
        static MapTransformer getMapTransformer(AbstractSpecimenTransformTask task, Map<String, Integer> labIds, Map<String, Integer> primaryIds, Map<String, Integer> derivativeIds)
        {
            return row -> task.transformRow(row, task.rowIndex++, labIds, primaryIds, derivativeIds);
        }
    }

    /** Write out an empty additives.tsv, since we aren't using them */
    protected void writeAdditives(Map<String, Integer> additiveIds, ZipOutputStream file) throws IOException
    {
        if (additiveIds.isEmpty())
        {
            file.putNextEntry(new ZipEntry("additives.tsv"));

            try (PrintWriter writer = new PrintWriter(file))
            {
                writer.write("# additives\n");
                writer.write("additive_id\tldms_additive_code\tlabware_additive_code\tadditive\n");
            }
        }
        else
        {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : additiveIds.entrySet())
            {
                Map<String, Object> row = new HashMap<>();

                // We know the id and the type name
                row.put("additive_id", entry.getValue());
                row.put("additive", entry.getKey());
                // All the other columns are blank
                row.put("ldms_additive_code", null);
                row.put("labware_additive_code", null);

                rows.add(row);
            }
            writeTSV(file, rows, "additives");
        }
    }

    protected void writePrimaries(Map<String, Integer> primaryIds, ZipOutputStream file) throws IOException
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : primaryIds.entrySet())
        {
            Map<String, Object> row = new HashMap<>();
            // We know the id and the type name
            row.put("primary_type_id", entry.getValue());
            row.put("primary_type", entry.getKey());
            // All the other columns are blank
            row.put("primary_type_ldms_code", null);
            row.put("primary_type_labware_code", null);
            rows.add(row);
        }

        writeTSV(file, rows, "primary_types");
    }

    protected void writeDerivatives(Map<String, Integer> derivatives, ZipOutputStream file) throws IOException
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : derivatives.entrySet())
        {
            Map<String, Object> row = new HashMap<>();
            // We know the id and the name
            row.put("derivative_id", entry.getValue());
            row.put("derivative", entry.getKey());
            // Everything else is left blank
            row.put("ldms_derivative_code", null);
            row.put("labware_derivative_code", null);
            rows.add(row);
        }

        writeTSV(file, rows, "derivatives");
    }

    protected void writeLabs(Map<String, Integer> labIds, ZipOutputStream file) throws IOException
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : labIds.entrySet())
        {
            Map<String, Object> row = new HashMap<>();
            // We know the id and the name
            row.put("lab_id", entry.getValue());
            row.put("lab_name", entry.getKey());
            row.put("ldms_lab_code", -1);               // ldms code required : issue #22217
            // Everything else is left blank
            row.put("lab_upload_code", null);
            row.put("is_sal", null);
            row.put("is_repository", null);
            row.put("is_clinic", null);
            row.put("is_endpoint", null);
            rows.add(row);
        }

        writeTSV(file, rows, "labs");
    }

    protected String hashRow(Map<String, Object> inputRow) throws IOException
    {
        // Check that Map is a type that has consistent key ordering
        if (!(inputRow instanceof ArrayListMap))
            throw new IllegalStateException();

        StringBuilder sb = new StringBuilder();
        for (String key : inputRow.keySet())
        {
            if (!getIgnoredHashColumns().contains(key))
            {
                sb.append(key);
                sb.append(": ");
                sb.append(String.valueOf(inputRow.get(key)));
                sb.append(";");
            }
        }

        // Hash the row to reduce the size in memory
        return FileUtil.sha1sum(sb.toString().getBytes());
    }

    protected void writeTSV(ZipOutputStream zOut, List<Map<String, Object>> outputRows, String baseName) throws IOException
    {
        // Add a new file to the ZIP
        zOut.putNextEntry(new ZipEntry(baseName + ".tsv"));
        PrintWriter writer = new PrintWriter(zOut);
        try
        {
            TSVMapWriter tsvWriter = new TSVMapWriter(outputRows);
            // Write a comment into the header
            tsvWriter.setFileHeader(Collections.singletonList("# " + baseName));
            // Set the writer separately from the call to write() so that the underlying stream doesn't get closed
            // when it's finished writing the TSV - we need to keep writing to the ZIP
            if (!outputRows.isEmpty())
            {
                tsvWriter.setPrintWriter(writer);
                tsvWriter.write();
            }
        }
        finally
        {
            writer.flush();
            zOut.closeEntry();
        }
    }

    protected static class SpecimenTransformTSVWriter extends TSVMapWriter
    {
        private int _rowCount;
        public SpecimenTransformTSVWriter(Iterable<Map<String, Object>> rows)
        {
            super(new ArrayList<>(), rows);     // start with empty column list
        }

        @Override
        public void writeRow(Map<String, Object> row)
        {
            if (null != row)
            {
                if (_rowCount == 0)
                {
                    writeFileHeader();
                    setColumns(row.keySet());
                    writeColumnHeaders();

                }
                super.writeRow(row);
                _rowCount++;
            }
        }

        public int getRowCount()
        {
            return _rowCount;
        }
    }

    protected void toDate(String key, Map<String, Object> row)
    {
        Object d = row.get(key);
        if (null == d || d instanceof Date)
            return;
        try
        {
            Date date = new Date(DateUtil.parseDateTime(String.valueOf(d)));
            row.put(key,date);
        }
        catch (ConversionException x)
        {
            /* */
        }
    }

    @Nullable
    protected Date parseDateTime(String keyDate, @Nullable String keyTime, Map<String, Object> row)
    {
        Object d = row.get(keyDate);
        if (null == d)
            return null;
        try
        {
            Date date = d instanceof Date ? (Date)d : new Date(DateUtil.parseDateTime(String.valueOf(d)));
            if (keyTime != null)
            {
                Object t = row.get(keyTime);
                if (t != null)
                {
                    long time = DateUtil.parseTime(String.valueOf(t));
                    date = new Date(date.getTime() + time);
                }
            }
            return date;
        }
        catch (ConversionException x)
        {
            /* */
        }
        return null;
    }

    protected Date parseDate(String keyDate, Map<String, Object> row)
    {
        Object d = row.get(keyDate);
        if (null == d)
            return null;
        try
        {
            Date date = d instanceof Date ? (Date)d : new Date(DateUtil.parseDateTime(String.valueOf(d)));
            return date;
        }
        catch (ConversionException x)
        {
            /* */
        }
        return null;
    }

    protected Date parseTime(String key, Map<String, Object> row)
    {
        Object d = row.get(key);
        if (null == d)
            return null;
        try
        {
            long time = DateUtil.parseTime(String.valueOf(d));
            return new Date(time);
        }
        catch (ConversionException x)
        {
            /* */
        }
        return null;
    }

    protected void toInt(String key, Map<String, Object> row)
    {
        Object i = row.get(key);
        if (null == i || i instanceof Integer)
            return;
        try
        {
            if (i instanceof Number)
                i = ((Number)i).intValue();
            else
                i = Integer.parseInt(String.valueOf(i));
            row.put(key,i);
        }
        catch (NumberFormatException x)
        {
            /* */
        }
    }

    protected String getNonNullValue(Map<String, Object> inputRow, String name)
    {
        return inputRow.get(name) == null ? "" : inputRow.get(name).toString();
    }

    protected String removeNonNullValue(Map<String, Object> inputRow, String name)
    {
        Object o = inputRow.remove(name);
        return o == null ? "" : o.toString();
    }

    protected void debug(String msg)
    {
        if (null != _job)
            _job.debug(msg);
    }


    protected void info(String msg)
    {
        if (null != _job)
            _job.info(msg);
    }


    protected void warn(String msg)
    {
        if (null != _job)
            _job.warn(msg);
    }

    protected void error(String msg)
    {
        if (null != _job)
            _job.error(msg);
    }
}
