/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.freezerpro;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.pipeline.AbstractSpecimenTransformTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipOutputStream;

/**
 * User: klum
 * Date: 11/12/13
 */
public class FreezerProTransformTask extends AbstractSpecimenTransformTask
{
    public static final FileType FREEZER_PRO_FILE_TYPE = new FileType(".fzp.csv");
    private static final Set<String> IGNORED_HASH_COLUMNS = new CaseInsensitiveHashSet(PageFlowUtil.set("comments"));

    private static final Map<String, Integer> STANDARD_LAB_IDS;
    private static final Map<String, Integer> STANDARD_PRIMARY_TYPE_IDS;
    private static final Map<String, Integer> STANDARD_DERIVATIVE_TYPE_IDS;
    private static final Map<String, String> DERIVATIVE_PRIMARY_MAPPINGS;

    static
    {
        Map<String, Integer> labs = new LinkedHashMap<>();
        labs.put("unknown", labs.size() + 1);
        STANDARD_LAB_IDS = Collections.unmodifiableMap(labs);

        Map<String, Integer> primaryTypes = new LinkedHashMap<>();
        primaryTypes.put("Blood", primaryTypes.size() + 1);
        primaryTypes.put("unknown", primaryTypes.size() + 1);
        STANDARD_PRIMARY_TYPE_IDS = Collections.unmodifiableMap(primaryTypes);

        Map<String, String> mappings = new HashMap<>();
        mappings.put("Blood", "Blood");
        mappings.put("PBMC", "Blood");
        mappings.put("H-PBMC", "Blood");
        DERIVATIVE_PRIMARY_MAPPINGS = Collections.unmodifiableMap(mappings);

        Map<String, Integer> derivativeTypes = new LinkedHashMap<>();
        derivativeTypes.put("Blood", derivativeTypes.size() + 1);
        derivativeTypes.put("PBMC", derivativeTypes.size() + 1);
        derivativeTypes.put("H-PBMC", derivativeTypes.size() + 1);
        STANDARD_DERIVATIVE_TYPE_IDS = Collections.unmodifiableMap(derivativeTypes);
    }

    private final Map<String, Integer> _labIds = new LinkedHashMap<>();
    private final Map<String, Integer> _primaryIds = new LinkedHashMap<>(STANDARD_PRIMARY_TYPE_IDS);
    private final Map<String, Integer> _derivativeIds = new LinkedHashMap<>(STANDARD_DERIVATIVE_TYPE_IDS);


    public FreezerProTransformTask(@Nullable PipelineJob job)
    {
        super(job);
    }

    @Override
    public void transform(File input, File output) throws PipelineJobException
    {
        info("Starting to transform input file " + input + " to output file " + output);

        try
        {
            DataLoaderFactory df = DataLoader.get().findFactory(input, null);
            if (null == df)
                throw new PipelineJobException("Unable to create a data loader factory for the file: " + input.getName());

            DataLoader loader = df.createLoader(input, true, _job.getContainer());
            loader.setInferTypes(false);
            List<Map<String, Object>> inputRows = loader.load();
            List<Map<String, Object>> outputRows = transformRows(inputRows);

            if (outputRows.size() > 0)
                info("After removing duplicates, there are " + outputRows.size() + " rows of data");
            else
                throw new PipelineJobException("There are no rows of data");

            // Create a ZIP archive with the appropriate TSVs
            try (ZipOutputStream zOut = new ZipOutputStream(new FileOutputStream(output)))
            {
                writeTSV(zOut, outputRows, "specimens");

                writeLabs(getLabIds(), zOut);
                writePrimaries(getPrimaryIds(), zOut);
                writeDerivatives(getDerivativeIds(), zOut);
                writeAdditives(zOut);
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    @Override
    @Nullable
    protected Map<String, Object> transformRow(Map<String, Object> inputData, int rowIndex, Map<String, Integer> labIds, Map<String, Integer> primaryIds, Map<String, Integer> derivativeIds)
    {
        Map<String, Object> inputRow = new CaseInsensitiveHashMap<>(inputData);
        Map<String, Object> outputRow = new CaseInsensitiveHashMap<>();
        inputData = null;

        String lab = "unknown";
        Integer labId = labIds.get(lab);

        String derivative = getNonNullValue(inputRow, "sample type");
        if (StringUtils.isEmpty(derivative))
        {
            derivative = getNonNullValue(inputRow, "sampletype_name");
        }
        // Check if it has a known primary type
        String primary = DERIVATIVE_PRIMARY_MAPPINGS.get(derivative);
        if (primary == null)
        {
            // If not, use the original value as both the primary and derivative
            primary = derivative;
        }
        Integer primaryId = primaryIds.get(primary);
        if (primaryId == null && !StringUtils.isEmpty(primary))
        {
            // Put it into our mapping so it gets written to primary_types.tsv
            primaryId = primaryIds.size() + 1;
            primaryIds.put(primary, primaryId);
        }
        Integer derivativeId = derivativeIds.get(derivative);
        if (derivativeId == null && !StringUtils.isEmpty(derivative))
        {
            // Put it into our mapping so it gets written to derivative.tsv
            derivativeId = derivativeIds.size() + 1;
            derivativeIds.put(derivative, derivativeId);
        }

        outputRow.put("record_id", rowIndex);
        String ptid = getSubjectID(inputRow);
        if (ptid == null)
        {
            warn("Skipping data row could not find 'patient id' value, row number " + rowIndex);
            return null;
        }
        outputRow.put("ptid", ptid);

        String uniqueSampleId = getGlobalUniqueSampleId(inputRow);
        if (StringUtils.isEmpty(uniqueSampleId))
        {
            warn("Skipping data row could not find 'barcode' or 'barcode_tag' value, row number " + rowIndex);
            return null;
        }
        outputRow.put("global_unique_specimen_id", uniqueSampleId);

        String uid = removeNonNullValue(inputRow, "uid");
        if (StringUtils.isEmpty(uid))
        {
            warn("Skipping data row could not find 'uid' value, row number " + rowIndex);
            return null;
        }
        outputRow.put("unique_specimen_id", uid);

        Date collectionDate = getDrawDate(inputRow);
        outputRow.put("draw_timestamp", collectionDate);
        outputRow.put("visit_value", "-1");

        Date processDate = parseDate("date of process", inputRow);
        Date processTime = parseTime("time of process", inputRow);

        outputRow.put("processing_date", processDate);
        outputRow.put("processing_time", processTime);
        String processedBy = removeNonNullValue(inputRow, "processed by");
        outputRow.put("processed_by_initials", processedBy);

        outputRow.put("lab_id", labId);
        outputRow.put("originating_location", labId);
        outputRow.put("shipped_from_lab", labId);
        outputRow.put("shipped_to_lab", labId);

        outputRow.put("primary_specimen_type_id", primaryId);
        outputRow.put("derivative_type_id", derivativeId);

        // freezer location
        String level1 = removeNonNullValue(inputRow, "level1");
        outputRow.put("fr_level1", level1);
        String level2 = removeNonNullValue(inputRow, "level2");
        outputRow.put("fr_level2", level2);
        String box = removeNonNullValue(inputRow, "box");
        outputRow.put("fr_container", box);
        String position = removeNonNullValue(inputRow, "position");
        outputRow.put("fr_position", position);

        // protocol
        //String protocol = removeNonNullValue(outputRow, "study protocol");
        //outputRow.put("protocol_number", protocol);

        String comments = removeNonNullValue(inputRow, "comments");
        outputRow.put("comments", comments);

        // add any remaining columns, non-built in columns can be imported provided the admin has
        // configured the specimen domain in the destination study
        for (Map.Entry<String, Object> entry : inputRow.entrySet())
        {
            String colName = ColumnInfo.legalNameFromName(entry.getKey());
            if (!outputRow.containsKey(colName))
            {
                outputRow.put(colName, entry.getValue());
            }
        }
        return outputRow;
    }

    @Nullable
    private String getSubjectID(Map<String, Object> row)
    {
        if (row.containsKey("patient id"))
            return removeNonNullValue(row, "patient id");
        else if (row.containsKey("name"))
            return removeNonNullValue(row, "name");

        return null;
    }

    @Nullable
    private String getGlobalUniqueSampleId(Map<String, Object> row)
    {
        if (row.containsKey("barcode"))
            return removeNonNullValue(row, "barcode");
        else if (row.containsKey("barcode_tag"))
            return removeNonNullValue(row, "barcode_tag");

        return null;
    }

    @Nullable
    private Date getDrawDate(Map<String, Object> row)
    {
        if (row.containsKey("date of draw") && row.containsKey("time of draw"))
            return parseDateTime("date of draw", "time of draw", row);
        else if (row.containsKey("date of draw"))
            return parseDate("date of draw", row);
        else if (row.containsKey("created at"))
            return parseDate("created at", row);

        return null;
    }

    @Override
    protected Set<String> getIgnoredHashColumns()
    {
        return IGNORED_HASH_COLUMNS;
    }

    @Override
    protected Map<String, Integer> getLabIds()
    {
        return _labIds;
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
        return Collections.emptyMap();
    }

    public static class TestCase extends Assert
    {
        private Mockery _context;
        private PipelineJob _job;
        private FreezerProTransformTask _task;

        @Before
        public void setUp()
        {
            _context = new Mockery();
            _context.setImposteriser(ClassImposteriser.INSTANCE);
            _job = _context.mock(PipelineJob.class);
            _task = new FreezerProTransformTask(_job);
        }

        @Test
        public void testDeduplication() throws IOException
        {
            ArrayListMap<String,Object> template = new ArrayListMap<>();

            List<Map<String, Object>> inputRows = new ArrayList<>();
            ArrayListMap<String, Object> row1 = new ArrayListMap(template.getFindMap());
            row1.put("patient id", "ptid1");
            row1.put("uid", "1111");
            row1.put("barcode", "barcode-1");
            row1.put("sample type", "PBMC");
            inputRows.add(row1);
            ArrayListMap<String, Object> row2 = new ArrayListMap(template.getFindMap());
            row2.putAll(row1);
            inputRows.add(row2);
            ArrayListMap<String, Object> row3 = new ArrayListMap(template.getFindMap());
            row3.putAll(row1);
            row3.put("patient id", "ptid2");
            inputRows.add(row3);

            List<Map<String, Object>> outputRows = _task.transformRows(inputRows);
            assertEquals(2, outputRows.size());
        }

        @Test
        public void testPrimaryAndDerivatives() throws IOException
        {
            ArrayListMap<String,Object> template = new ArrayListMap<>();
            List<Map<String, Object>> inputRows = new ArrayList<>();

            ArrayListMap<String,Object> row1 = new ArrayListMap<>(template.getFindMap());
            row1.put("patient id", "ptid1");
            row1.put("uid", "1111");
            row1.put("barcode", "barcode-1");
            row1.put("sample type", "PBMC");
            inputRows.add(row1);

            ArrayListMap<String,Object> row2 = new ArrayListMap<>(template.getFindMap());
            row2.putAll(row1);
            row2.put("sample type", "H-PBMC");
            inputRows.add(row2);

            ArrayListMap<String,Object> row3 = new ArrayListMap<>(template.getFindMap());
            row3.putAll(row1);
            row3.put("sample type", "Urine");
            inputRows.add(row3);

            ArrayListMap<String,Object> row4 = new ArrayListMap<>(template.getFindMap());
            row4.putAll(row3);
            row4.put("sample type", "Serum");
            inputRows.add(row4);

            List<Map<String, Object>> outputRows = _task.transformRows(inputRows);

            Map<String, Integer> primaryIds = _task.getPrimaryIds();
            Map<String, Integer> derivativeIds = _task.getDerivativeIds();

            assertEquals(4, outputRows.size());
            assertEquals(primaryIds.get("Blood"), outputRows.get(0).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("PBMC"), outputRows.get(0).get("derivative_type_id"));
            assertEquals(primaryIds.get("Blood"), outputRows.get(1).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("H-PBMC"), outputRows.get(1).get("derivative_type_id"));
            assertEquals(primaryIds.get("Urine"), outputRows.get(2).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("Urine"), outputRows.get(2).get("derivative_type_id"));
            assertEquals(primaryIds.get("Serum"), outputRows.get(3).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("Serum"), outputRows.get(3).get("derivative_type_id"));
        }
    }
}
