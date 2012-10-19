/*
 * Copyright (c) 2009-2012 LabKey Corporation
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

package org.labkey.study.pipeline;

import org.apache.commons.beanutils.ConversionException;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/*
* This is used to transform SampleMinded specimen files (.xlsx) into our standard specimen import format, a
* a file with extension ".specimens" which is a ZIP file.
* User: jeckels
*/

public class SampleMindedTransformTask
{
    public static final FileType SAMPLE_MINDED_FILE_TYPE = new FileType(".xlsx");

    private final PipelineJob _job;
    private static final String INVALID_SUFFIX = "-invalid";

    public SampleMindedTransformTask(PipelineJob job)
    {
        _job = job;
    }

    private static final Map<String, Integer> STANDARD_PRIMARY_TYPE_IDS;
    private static final Map<String, Integer> STANDARD_DERIVATIVE_TYPE_IDS;
    private static final Map<String, String> DERIVATIVE_PRIMARY_MAPPINGS;

    private static final Set<String> IGNORED_HASH_COLUMNS = new CaseInsensitiveHashSet(PageFlowUtil.set("comments", "episodetype", "episodevalue"));

    static
    {
        Map<String, Integer> primaryTypes = new LinkedHashMap<String, Integer>();
        primaryTypes.put("BAL", primaryTypes.size() + 1);
        primaryTypes.put("Blood", primaryTypes.size() + 1);
        primaryTypes.put("Nasal Swab", primaryTypes.size() + 1);
        primaryTypes.put("Tissue", primaryTypes.size() + 1);
        primaryTypes.put("Urine", primaryTypes.size() + 1);
        primaryTypes.put("unknown", primaryTypes.size() + 1);
        STANDARD_PRIMARY_TYPE_IDS = Collections.unmodifiableMap(primaryTypes);

        Map<String, String> mappings = new HashMap<String, String>();
        mappings.put("BAL", "BAL");
        mappings.put("BAL Pellet", "BAL");
        mappings.put("BAL Supernatant", "BAL");
        mappings.put("Blood", "Blood");
        mappings.put("PBMC", "Blood");
        mappings.put("Plasma", "Blood");
        mappings.put("Serum", "Blood");
        mappings.put("Nasal Swab", "Nasal Swab");
        mappings.put("Tissue", "Tissue");
        mappings.put("Tissue Slide", "Tissue");
        mappings.put("Urine", "Urine");
        mappings.put("Urine Pellet", "Urine");
        mappings.put("Urine Supernatant", "Urine");
        DERIVATIVE_PRIMARY_MAPPINGS = Collections.unmodifiableMap(mappings);

        Map<String, Integer> derivativeTypes = new LinkedHashMap<String, Integer>();
        derivativeTypes.put("BAL", derivativeTypes.size() + 1);
        derivativeTypes.put("BAL Pellet", derivativeTypes.size() + 1);
        derivativeTypes.put("BAL Supernatant", derivativeTypes.size() + 1);
        derivativeTypes.put("Blood", derivativeTypes.size() + 1);
        derivativeTypes.put("Nasal Swab", derivativeTypes.size() + 1);
        derivativeTypes.put("PBMC", derivativeTypes.size() + 1);
        derivativeTypes.put("Plasma", derivativeTypes.size() + 1);
        derivativeTypes.put("Serum", derivativeTypes.size() + 1);
        derivativeTypes.put("Tissue", derivativeTypes.size() + 1);
        derivativeTypes.put("Tissue Slide", derivativeTypes.size() + 1);
        derivativeTypes.put("Urine", derivativeTypes.size() + 1);
        derivativeTypes.put("Urine Pellet", derivativeTypes.size() + 1);
        derivativeTypes.put("Urine Supernatant", derivativeTypes.size() + 1);
        STANDARD_DERIVATIVE_TYPE_IDS = Collections.unmodifiableMap(derivativeTypes);
    }

    private PipelineJob getJob()
    {
        return _job;
    }

    public void transform(File input, File output) throws PipelineJobException
    {
        Map<String, Integer> labIds = new LinkedHashMap<String, Integer>();

        Map<String, Integer> primaryIds = new LinkedHashMap<String, Integer>(STANDARD_PRIMARY_TYPE_IDS);
        Map<String, Integer> derivativeIds = new LinkedHashMap<String, Integer>(STANDARD_DERIVATIVE_TYPE_IDS);

        try
        {
            File labsFile = new File(input.getParent(), "labs.txt");
            if (NetworkDrive.exists(labsFile) && labsFile.isFile())
            {
                FileInputStream fIn = new FileInputStream(labsFile);
                try
                {
                    parseLabs(labIds, new BufferedReader(new InputStreamReader(fIn)));
                }
                finally
                {
                    try { fIn.close(); } catch (IOException ignored) {}
                }
                getJob().info("Parsed " + labIds.size() + " labs from " + labsFile);
            }
            else
            {
                getJob().debug("No such file " + labsFile + " so not parsing supplemental lab information");
            }

            ExcelLoader loader = new ExcelLoader(input, true);
            List<Map<String, Object>> inputRows = loader.load();
            List<Map<String, Object>> outputRows = transformRows(labIds, primaryIds, derivativeIds, inputRows);

            getJob().info("After removing duplicates, there are " + outputRows.size() + " rows of data");

            // Create a ZIP archive with the appropriate TSVs
            ZipOutputStream zOut = null;
            try
            {
                zOut = new ZipOutputStream(new FileOutputStream(output));
                writeTSV(zOut, outputRows, "specimens");

                writeLabs(labIds, zOut);
                writePrimaries(primaryIds, zOut);
                writeDerivatives(derivativeIds, zOut);
                writeAdditives(zOut);
            }
            finally
            {
                if (zOut != null) { try { zOut.close(); } catch (IOException ignored) {} }
            }
        }
        catch (IOException e)
        {
            throw new PipelineJobException(e);
        }
    }

    private List<Map<String, Object>> transformRows(Map<String, Integer> labIds, Map<String, Integer> primaryIds, Map<String, Integer> derivativeIds, List<Map<String, Object>> inputRows)
            throws IOException
    {
        List<Map<String, Object>> outputRows = new ArrayList<Map<String, Object>>(inputRows.size());
        Set<String> hashes = new HashSet<String>();
        int rowIndex = 0;
        
        // Crank through all of the input rows
        for (Iterator<Map<String, Object>> iter = inputRows.iterator(); iter.hasNext(); )
        {
            Map<String, Object> inputRow = iter.next();
            // Remove it from the input list immediately to make it eligible for garbage collection
            // once we're done processing it
            iter.remove();
            rowIndex++;
            
            // Check if it's a duplicate row
            if (hashes.add(hashRow(inputRow)))
            {
                Map<String, Object> outputRow = transformRow(inputRow, rowIndex, labIds, primaryIds, derivativeIds);
                if (outputRow != null)
                {
                    outputRows.add(outputRow);
                }
            }
        }

        return outputRows;
    }


    private void toDate(String key, Map<String,Object> row)
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


    private void toInt(String key, Map<String,Object> row)
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


    private Map<String, Object> transformRow(Map<String, Object> inputRow, int rowIndex, Map<String, Integer> labIds, Map<String, Integer> primaryIds, Map<String, Integer> derivativeIds)
    {
        toDate("CollectionDate", inputRow);
        toDate("ActivitySaveDateTime", inputRow);
        toDate("LabRecievedTime", inputRow);
        toInt("sitecode", inputRow);

        String vesselDomainType = (String)inputRow.get("VesselDomainType");
        if (null != vesselDomainType && vesselDomainType.length() > 32)
            inputRow.put("VesselDomainType", vesselDomainType.substring(0,32));

        // Get the barcode and strip off the "-INVALID" suffix, if present
        String barcode = inputRow.get("barcode") == null ? null : inputRow.get("barcode").toString();
        if (barcode != null && barcode.toLowerCase().endsWith(INVALID_SUFFIX))
        {
            barcode = barcode.substring(0, barcode.length() - INVALID_SUFFIX.length());
        }
        barcode = barcode == null ? null : barcode.trim();
        if (barcode == null || barcode.length() == 0)
        {
            getJob().warn("Skipping data row missing 'barcode' value, row number " + rowIndex);
            return null;
        }

        Object collectionDate = getNonNullValue(inputRow, "collectiondate");
        if (collectionDate == null || "".equals(collectionDate))
        {
            getJob().warn("Skipping data row missing 'collectiondate' value, row number " + rowIndex);
            return null;
        }

        Map<String, Object> outputRow = new HashMap<String, Object>();

        String shortName = inputRow.get("siteshortname") == null ? null : inputRow.get("siteshortname").toString();
        Integer siteId = labIds.get(shortName);
        if (siteId == null)
        {
            // We don't have an existing ID to use for this site, so find one that's available
            siteId = 1;
            while (labIds.containsValue(siteId))
            {
                siteId += 1;
            }
            labIds.put(shortName, siteId);
        }

        String derivative = getNonNullValue(inputRow, "specimentype");
        // Check if it has a known primary type
        String primary = DERIVATIVE_PRIMARY_MAPPINGS.get(derivative);
        if (primary == null)
        {
            // If not, use the original value as both the primary and derivative
            primary = derivative;
        }
        Integer primaryId = primaryIds.get(primary);
        if (primaryId == null)
        {
            // Put it into our mapping so it gets written to primary_types.tsv
            primaryId = primaryIds.size() + 1;
            primaryIds.put(primary, primaryId);
        }
        Integer derivativeId = derivativeIds.get(derivative);
        if (derivativeId == null)
        {
            // Put it into our mapping so it gets written to derivative.tsv
            derivativeId = derivativeIds.size() + 1;
            derivativeIds.put(derivative, derivativeId);
        }

        outputRow.put("record_id", rowIndex);
        outputRow.put("originating_location", siteId);
        outputRow.put("global_unique_specimen_id", barcode);
        String ptid = getNonNullValue(inputRow, "participantid");
        // Prefix the PTID with the studynum value if it isn's already there
        String studyNum = getNonNullValue(inputRow, "studynum");
        if (!ptid.startsWith(studyNum))
        {
            ptid = studyNum + ptid;
        }
        outputRow.put("ptid", ptid);
        outputRow.put("tube_type", inputRow.get("vesseldomaintype"));
        // Fix up the visit number
        String visit = getNonNullValue(inputRow, "visitname");
        if (visit.toLowerCase().startsWith("visit"))
        {
            visit = visit.substring("visit".length()).trim();
        }
        if ("SE".equalsIgnoreCase(visit) || "SR".equalsIgnoreCase(visit))
        {
            visit = "999";
        }
        outputRow.put("visit_value", visit);

        outputRow.put("primary_specimen_type_id", primaryId);
        outputRow.put("derivative_type_id", derivativeId);
        outputRow.put("draw_timestamp", collectionDate);
        // Sort the date into the appropriate column based on the activity description
        String activity = getNonNullValue(inputRow, "activity");
        Object activitySaveDateTime = inputRow.get("activitysavedatetime");
        if (activity.contains("Ship"))
        {
            outputRow.put("ship_date", activitySaveDateTime);
            outputRow.put("lab_receipt_date", null);
            outputRow.put("processing_date", null);
        }
        else if (activity.contains("Receiv"))
        {
            outputRow.put("ship_date", null);
            outputRow.put("lab_receipt_date", activitySaveDateTime);
            outputRow.put("processing_date", null);
        }
        else
        {
            outputRow.put("ship_date", null);
            outputRow.put("lab_receipt_date", null);
            outputRow.put("processing_date", activitySaveDateTime);
        }
        outputRow.put("processed_by_initials", inputRow.get("activityuser"));
        outputRow.put("processed_by_initials", inputRow.get("activityuser"));
        outputRow.put("comments", activity);

        String destinationSite = getNonNullValue(inputRow, "destination_site");
        if ("".equals(destinationSite))
        {
            destinationSite = getNonNullValue(inputRow, "destinationsite");
        }
        if (destinationSite.trim().length() == 0 || "N/A".equalsIgnoreCase(destinationSite))
        {
            destinationSite = shortName;
        }

        Integer labId;
        try
        {
            // Try using the given name as the ID if it's an integer
            labId = Integer.parseInt(destinationSite);
            if (!labIds.containsValue(labId))
            {
                labIds.put(labId.toString(), labId);
            }
        }
        catch (NumberFormatException e)
        {
            labId = labIds.get(destinationSite);
            if (labId == null)
            {
                // We don't have an existing ID, so find one that's available
                labId = 1;
                while (labIds.containsValue(labId))
                {
                    labId += 1;
                }
                labIds.put(destinationSite, labId);
            }
        }

        outputRow.put("lab_id", labId);
        outputRow.put("ship_batch_number", inputRow.get("airbillnumber"));

        return outputRow;
    }


    private String getNonNullValue(Map<String, Object> inputRow, String name)
    {
        return inputRow.get(name) == null ? "" : inputRow.get(name).toString();
    }


    /**
     * Parse a TSV file with lab names and IDs. First column is assumed to be lab ID. Second column is the name.
     * Assume no other columns. 
     */
    private void parseLabs(Map<String, Integer> labIds, BufferedReader reader) throws IOException
    {
        String line;
        while ((line = reader.readLine()) != null)
        {
            // Split it into chunks
            String[] pieces = line.split("\\s");

            // If we found multiple chunks
            if (pieces.length > 1)
            {
                String prefix = pieces[0].trim();
                try
                {
                    // Try parsing the first value as the lab's ID
                    Integer labId = Integer.parseInt(prefix);
                    // If it succeeds, use the rest of the line as the lab name
                    labIds.put(line.substring(prefix.length()).trim(), labId);
                }
                catch (NumberFormatException ignored) {}
            }
        }
    }

    private void writeTSV(ZipOutputStream zOut, List<Map<String, Object>> outputRows, String baseName) throws IOException
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


    private String hashRow(Map<String, Object> inputRow) throws IOException
    {
        // Check that Map is a type that has consistent key ordering
        if (!(inputRow instanceof ArrayListMap))
            throw new IllegalStateException();

        StringBuilder sb = new StringBuilder();
        for (String key : inputRow.keySet())
        {
            if (!IGNORED_HASH_COLUMNS.contains(key))
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


    /** Write out an empty additives.tsv, since we aren't using them */
    private void writeAdditives(ZipOutputStream file) throws IOException
    {
        file.putNextEntry(new ZipEntry("additives.tsv"));

        PrintWriter writer = new PrintWriter(file);
        try
        {
            writer.write("# additives\n");
            writer.write("additive_id\tldms_additive_code\tlabware_additive_code\tadditive\n");
        }
        finally
        {
            writer.close();
        }
    }

    private void writePrimaries(Map<String, Integer> primaryIds, ZipOutputStream file) throws IOException
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : primaryIds.entrySet())
        {
            Map<String, Object> row = new HashMap<String, Object>();
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

    private void writeDerivatives(Map<String, Integer> derivatives, ZipOutputStream file) throws IOException
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : derivatives.entrySet())
        {
            Map<String, Object> row = new HashMap<String, Object>();
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

    private void writeLabs(Map<String, Integer> labIds, ZipOutputStream file) throws IOException
    {
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<String, Integer> entry : labIds.entrySet())
        {
            Map<String, Object> row = new HashMap<String, Object>();
            // We know the id and the name
            row.put("lab_id", entry.getValue());
            row.put("lab_name", entry.getKey());
            // Everything else is left blank
            row.put("ldms_lab_code", null);
            row.put("lab_upload_code", null);
            row.put("is_sal", null);
            row.put("is_repository", null);
            row.put("is_clinic", null);
            row.put("is_endpoint", null);
            rows.add(row);
        }

        writeTSV(file, rows, "labs");
    }

    public static class TestCase extends Assert
    {
        private Mockery _context;
        private PipelineJob _job;
        private SampleMindedTransformTask _task;

        @Before
        public void setUp()
        {
            _context = new Mockery();
            _context.setImposteriser(ClassImposteriser.INSTANCE);
            _job = _context.mock(PipelineJob.class);
            _task = new SampleMindedTransformTask(_job);
        }

        @Test
        public void testParseLabsTSV() throws IOException
        {
            Map<String, Integer> labs = new HashMap<String, Integer>();
            _task.parseLabs(labs, new BufferedReader(new StringReader("Site Number\tSite Name\n501\tLab AA\n502\tLab BB\n503\tLab CC")));
            assertEquals("Wrong number of labs", 3, labs.size());
            assertEquals("Wrong lab name", 501, labs.get("Lab AA"));
        }

        @Test
        public void testLabLookup() throws IOException
        {
            Map<String, Integer> labs = new HashMap<String, Integer>();
            labs.put("TestLabA", 501);
            labs.put("TestLabB", 502);

            // Try a row that uses the lab's ID
            Map<String, Object> row1 = new ArrayListMap<String, Object>();
            row1.put("participant", "ptid1");
            row1.put("barcode", "barcode-1");
            row1.put("collectiondate", "May 5, 2012");
            row1.put("visitname", "Visit 01");
            row1.put("destinationsite", "502");
            row1.put("siteshortname", "TestLabA");

            Map<String, Object> outputRow1 = _task.transformRow(row1, 1, labs, new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Wrong number of labs", 2, labs.size());
            assertEquals(502, outputRow1.get("lab_id"));

            // Try another row that uses the lab's name
            Map<String, Object> row2 = new ArrayListMap<String, Object>();
            row2.put("participant", "ptid1");
            row2.put("barcode", "barcode-1");
            row2.put("collectiondate", "May 5, 2012");
            row2.put("visitname", "Visit 01");
            row2.put("destinationsite", "TestLabB");
            row2.put("siteshortname", "TestLabA");

            Map<String, Object> outputRow2 = _task.transformRow(row2, 1, labs, new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Wrong number of labs", 2, labs.size());
            assertEquals(502, outputRow2.get("lab_id"));
        }

        @Test
        public void testParseWideLabsTSV() throws IOException
        {
            Map<String, Integer> labs = new HashMap<String, Integer>();
            _task.parseLabs(labs, new BufferedReader(new StringReader("# labs\t\t\t\t\t\t\t\t\n" +
                    "lab_id\tldms_lab_code\tlabware_lab_code\tlab_name\tlab_upload_code\tis_sal\tis_repository\tis_clinic\tis_endpoint\n" +
                    "23\t\t\tFAKE (12)\t\t\t\t\t\n" +
                    "4\t\t\tFDSJ (10)\t\t\t\t\t\n" +
                    "15\t\t\tMFDS (17)\t\t\t\t\t\n" +
                    "1\t\t\tPWER (11)\t\t\t\t\t\n" +
                    "5\t\t\tOTJD (02)\t\t\t\t\t\n" +
                    "21\t\t\tVCXF (19)\t\t\t\t\t")));
            assertEquals("Wrong number of labs", 6, labs.size());
            assertEquals("Wrong lab name", 23, labs.get("FAKE (12)"));
        }


        @Test
        public void testDeduplication() throws IOException
        {
            ArrayListMap<String,Object> template = new ArrayListMap<String,Object>();

            List<Map<String, Object>> inputRows = new ArrayList<Map<String, Object>>();
            ArrayListMap<String, Object> row1 = new ArrayListMap(template.getFindMap());
            row1.put("participant", "ptid1");
            row1.put("barcode", "barcode-1");
            row1.put("collectiondate", "May 5, 2012");
            row1.put("visitname", "Visit 01");
            inputRows.add(row1);
            ArrayListMap<String, Object> row2 = new ArrayListMap(template.getFindMap());
            row2.putAll(row1);
            inputRows.add(row2);
            ArrayListMap<String, Object> row3 = new ArrayListMap(template.getFindMap());
            row3.putAll(row1);
            row3.put("participant", "ptid2");
            inputRows.add(row3);

            List<Map<String, Object>> outputRows = _task.transformRows(new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>(), inputRows);
            assertEquals(2, outputRows.size());
        }


        @Test
        public void testInvalidBarcodeDetection()
        {
            Map<String, Object> row1 = new ArrayListMap<String, Object>();
            row1.put("participant", "ptid1");
            row1.put("collectiondate", "May 5, 2012");
            row1.put("visitname", "Visit 01");

            _context.checking(new Expectations()
            {{
                oneOf(_job).warn("Skipping data row missing 'barcode' value, row number 1");
            }});
            assertEquals(null, _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>()));

            row1.put("barcode", "");
            _context.checking(new Expectations()
            {{
                oneOf(_job).warn("Skipping data row missing 'barcode' value, row number 1");
            }});
            assertEquals(null, _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>()));

            row1.put("barcode", INVALID_SUFFIX);
            _context.checking(new Expectations()
            {{
                oneOf(_job).warn("Skipping data row missing 'barcode' value, row number 1");
            }});
            assertEquals(null, _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>()));

            row1.put("barcode", "4324329-invalid");
            Map<String, Object> outputRow = _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("4324329", outputRow.get("global_unique_specimen_id"));
        }

        @Test
        public void testEmptyRow()
        {
            _context.checking(new Expectations()
            {{
                oneOf(_job).warn("Skipping data row missing 'barcode' value, row number 1");
            }});

            Map<String, Object> row1 = new ArrayListMap<String, Object>();
            assertEquals(null, _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>()));
        }

        @Test
        public void testCollectionDateBucketing()
        {
            Map<String, Object> row = new ArrayListMap<String, Object>();
            row.put("participant", "ptid1");
            row.put("barcode", "barcode");
            String date = "May 5, 2012";
            row.put("collectiondate", "May 6, 2012");
            row.put("activitysavedatetime", date);
            row.put("activity", "Some Lab Receiving");
            Map<String, Object> outputRow = _task.transformRow(row, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Bad receiving handling", date, outputRow.get("lab_receipt_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("ship_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("processing_date"));

            row.put("activity", "Ship Specimens from Clinical Site");
            outputRow = _task.transformRow(row, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Bad receiving handling", null, outputRow.get("lab_receipt_date"));
            assertEquals("Bad receiving handling", date, outputRow.get("ship_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("processing_date"));

            row.put("activity", "Aliquoting");
            outputRow = _task.transformRow(row, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Bad receiving handling", null, outputRow.get("lab_receipt_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("ship_date"));
            assertEquals("Bad receiving handling", date, outputRow.get("processing_date"));
        }

        @Test
        public void testMissingCollectionDate()
        {
            _context.checking(new Expectations()
            {{
                oneOf(_job).warn("Skipping data row missing 'collectiondate' value, row number 1");
            }});

            Map<String, Object> row1 = new ArrayListMap<String, Object>();
            row1.put("participant", "ptid1");
            row1.put("barcode", "barcode-1");
            row1.put("collectiondate", "");
            row1.put("visitname", "Visit 01");

            assertEquals(null, _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>()));
        }

        @Test
        public void testPrimaryAndDerivatives() throws IOException
        {
            ArrayListMap<String,Object> template = new ArrayListMap<String,Object>();
            List<Map<String, Object>> inputRows = new ArrayList<Map<String, Object>>();

            ArrayListMap<String,Object> row1 = new ArrayListMap<String, Object>(template.getFindMap());
            row1.put("participant", "ptid1");
            row1.put("barcode", "barcode-1");
            row1.put("collectiondate", "May 5 2001");
            row1.put("visitname", "Visit 01");
            row1.put("specimentype", "PBMC");
            inputRows.add(row1);

            ArrayListMap<String,Object> row2 = new ArrayListMap<String, Object>(template.getFindMap());
            row2.putAll(row1);
            row2.put("specimentype", "Blood");
            inputRows.add(row2);

            ArrayListMap<String,Object> row3 = new ArrayListMap<String, Object>(template.getFindMap());
            row3.putAll(row1);
            row3.put("specimentype", "NewType!");
            inputRows.add(row3);

            ArrayListMap<String,Object> row4 = new ArrayListMap<String, Object>(template.getFindMap());
            row4.putAll(row3);
            row4.put("participant", "ptid2");
            inputRows.add(row4);

            ArrayListMap<String,Object> row5 = new ArrayListMap<String, Object>(template.getFindMap());
            row5.putAll(row3);
            row5.put("specimentype", "Tissue Slide");
            inputRows.add(row5);

            ArrayListMap<String,Object> row6 = new ArrayListMap<String, Object>(template.getFindMap());
            row6.putAll(row3);
            row6.put("specimentype", "Urine Supernatant");
            inputRows.add(row6);

            ArrayListMap<String,Object> row7 = new ArrayListMap<String, Object>(template.getFindMap());
            row7.putAll(row3);
            row7.put("specimentype", "Urine Pellet");
            inputRows.add(row7);

            Map<String, Integer> primaryIds = new LinkedHashMap<String, Integer>(STANDARD_PRIMARY_TYPE_IDS);
            Map<String, Integer> derivativeIds = new LinkedHashMap<String, Integer>(STANDARD_DERIVATIVE_TYPE_IDS);

            List<Map<String, Object>> outputRows = _task.transformRows(new HashMap<String, Integer>(), primaryIds, derivativeIds, inputRows);
            assertEquals(7, outputRows.size());
            assertEquals(primaryIds.get("Blood"), outputRows.get(0).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("PBMC"), outputRows.get(0).get("derivative_type_id"));
            assertEquals(primaryIds.get("Blood"), outputRows.get(1).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("Blood"), outputRows.get(1).get("derivative_type_id"));
            assertEquals(primaryIds.get("NewType!"), outputRows.get(2).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("NewType!"), outputRows.get(2).get("derivative_type_id"));
            assertEquals(primaryIds.get("NewType!"), outputRows.get(3).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("NewType!"), outputRows.get(3).get("derivative_type_id"));
            assertEquals(primaryIds.get("Tissue"), outputRows.get(4).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("Tissue Slide"), outputRows.get(4).get("derivative_type_id"));
            assertEquals(primaryIds.get("Urine"), outputRows.get(5).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("Urine Supernatant"), outputRows.get(5).get("derivative_type_id"));
            assertEquals(primaryIds.get("Urine"), outputRows.get(6).get("primary_specimen_type_id"));
            assertEquals(derivativeIds.get("Urine Pellet"), outputRows.get(6).get("derivative_type_id"));
        }
    }
}
