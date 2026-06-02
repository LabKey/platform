/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.specimen.importer;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.pipeline.AbstractSpecimenTransformTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.writer.PrintWriters;
import org.labkey.vfs.FileLike;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class QueryBasedTransformTask extends AbstractSpecimenTransformTask
{
    public QueryBasedTransformTask(@Nullable PipelineJob job)
    {
        super(job);
    }

    private final Map<String, Integer> _primaryIds = new LinkedHashMap<>();
    private final Map<String, Integer> _derivativeIds = new LinkedHashMap<>();
    private final Map<String, Integer> _additiveIds = new LinkedHashMap<>();

    @Nullable
    private String getPrimaryType(Map<String, Object> row)
    {
        String colNameLK = getNonNullValue(row, "primary_type");
        return ("".equals(colNameLK)) ? getNonNullValue(row, "PrimaryType") : colNameLK;
    }

    @Nullable
    private String getDerivative(Map<String, Object> row)
    {
        String colNameLK = getNonNullValue(row, "derivative_type");
        return ("".equals(colNameLK)) ? getNonNullValue(row, "DerivativeType") : colNameLK;
    }

    @Nullable
    private String getAdditive(Map<String, Object> row)
    {
        String colNameLK = getNonNullValue(row, "additive_type");
        return ("".equals(colNameLK)) ? getNonNullValue(row, "AdditiveType") : colNameLK;
    }

    private Integer getType(String type, Map<String, Integer> typeMap)
    {
        Integer id = typeMap.get(type);
        if (id == null && !StringUtils.isEmpty(type))
        {
            id = typeMap.size() + 1;
            typeMap.put(type, id);
        }
        return id;
    }

    public void transform(FileLike input, FileLike output) throws PipelineJobException
    {
        info("Starting to transform input file " + input + " to output file " + output);

        try
        {
            DataLoaderFactory df = DataLoader.get().findFactory(input, null);
            if (null == df)
                throw new PipelineJobException("Unable to create a data loader factory for the file: " + input.getName());
            DataLoader loader = df.createLoader(input.openInputStream(), true, _job.getContainer());
            loader.setInferTypes(false);

            try (ZipOutputStream zOut = new ZipOutputStream(output.openOutputStream()))
            {
                zOut.putNextEntry(new ZipEntry("specimens.tsv"));
                PrintWriter writer = PrintWriters.getPrintWriter(zOut);
                final QueryBasedSpecimenTransform.QueryBasedTSVWriter tsvWriter = new QueryBasedSpecimenTransform.QueryBasedTSVWriter(Collections.emptyList());

                tsvWriter.setFileHeader(Collections.singletonList("# " + "specimens"));
                tsvWriter.setPrintWriter(writer);
                try
                {
                    loader.forEach(row -> {
                        if (MapFilter.getMapFilter(this).test(row))
                        {
                            if (!row.containsKey("records_id"))
                                row.put("record_id", tsvWriter.getRowCount());

                            row.put("primary_specimen_type_id", getType(getPrimaryType(row), _primaryIds));
                            row.put("derivative_type_id", getType(getDerivative(row), _derivativeIds));
                            row.put("additive_type_id", getType(getAdditive(row), _additiveIds));

                            tsvWriter.writeRow(row);
                        }
                    });
                }
                finally
                {
                    writer.flush();
                    zOut.closeEntry();
                }

                if (tsvWriter.getRowCount() > 0)
                    info("After removing duplicates, there are " + tsvWriter.getRowCount() + " rows of data");
                else
                    throw new PipelineJobException("There are no rows of data");

                writePrimaries(getPrimaryIds(), zOut);
                writeDerivatives(getDerivativeIds(), zOut);
                writeAdditives(getAdditiveIds(), zOut);
            }
        }

        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    protected Map<String, Object> transformRow(Map<String, Object> inputRow, int rowIndex, Map<String, Integer> labIds, Map<String, Integer> primaryIds, Map<String, Integer> derivativeIds)
    {
        return null;
    }

    @Override
    protected Set<String> getIgnoredHashColumns()
    {
        return new CaseInsensitiveHashSet();
    }

    @Override
    protected Map<String, Integer> getLabIds()
    {
        return new LinkedHashMap<>();
    }

    @Override
    protected Map<String, Integer> getPrimaryIds()
    {
        return _primaryIds;
    }

    @Override
    protected Map<String, Integer> getDerivativeIds()
    {
        return _derivativeIds;
    }

    @Override
    protected Map<String, Integer> getAdditiveIds()
    {
        return _additiveIds;
    }
}
