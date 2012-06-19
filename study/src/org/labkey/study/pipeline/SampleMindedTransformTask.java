/*
 * Copyright (c) 2009-2011 LabKey Corporation
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

import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.reader.ExcelLoader;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/*
* This task is used to transform SampleMinded specimen files (.xlsx) into our standard specimen import format, a
* a file with extension ".specimens" which is a ZIP file.
* User: jeckels
*/

public class SampleMindedTransformTask extends PipelineJob.Task<SampleMindedTransformTask.Factory>
{
    private static final String TRANSFORM_PROTOCOL_ACTION_NAME = "SampleMindedTransform";

    private SampleMindedTransformTask(Factory factory, PipelineJob job)
    {
        super(factory, job);
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
        primaryTypes.put("Tissue Slide", primaryTypes.size() + 1);
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
        mappings.put("Tissue Slide", "Tissue Slide");
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
        STANDARD_DERIVATIVE_TYPE_IDS = Collections.unmodifiableMap(derivativeTypes);
    }


    @Override
    public RecordedActionSet run() throws PipelineJobException
    {
        RecordedAction action = new RecordedAction(TRANSFORM_PROTOCOL_ACTION_NAME);
        File input = getJob().getJobSupport(SpecimenJobSupport.class).getInputFile();
        action.addInput(input, "SampleMindedExport");
        File output = getJob().getJobSupport(SpecimenJobSupport.class).getSpecimenArchive();
        action.addOutput(output, "SpecimenArchive", false);

        Map<String, Integer> labIds = new LinkedHashMap<String, Integer>();

        Map<String, Integer> primaryIds = new LinkedHashMap<String, Integer>(STANDARD_PRIMARY_TYPE_IDS);
        Map<String, Integer> derivativeIds = new LinkedHashMap<String, Integer>(STANDARD_DERIVATIVE_TYPE_IDS);

        try
        {
            File labsFile = new File(input.getParent(), "labs.txt");
            if (NetworkDrive.exists(labsFile) && labsFile.isFile())
            {
                parseLabs(labIds, labsFile);
                getJob().info("Parsed " + labIds + " labs from " + labsFile);
            }
            else
            {
                getJob().debug("No such file " + labsFile + " so not parsing supplemental lab information");
            }

            ExcelLoader loader = new ExcelLoader(input, true);
            Set<String> hashes = new HashSet<String>();
            List<Map<String, Object>> inputRows = loader.load();
            List<Map<String, Object>> outputRows = new ArrayList<Map<String, Object>>();

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
                if (!hashes.add(hashRow(inputRow)))
                {
                    continue;
                }

                Object barcode = inputRow.get("barcode");
                Object collectionDate = inputRow.get("collectiondate");
                if (barcode == null || barcode.toString().toLowerCase().endsWith("-invalid") || collectionDate == null)
                {
                    getJob().warn("Skipping data row with missing or invalid barcode, row number " + rowIndex);
                    continue;
                }
                if (collectionDate == null)
                {
                    getJob().warn("Skipping data row missing collection date, row number " + rowIndex);
                    continue;
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

                String derivative = inputRow.get("specimentype") == null ? null : inputRow.get("specimentype").toString();
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
                String ptid = inputRow.get("participantid") == null ? "" : inputRow.get("participantid").toString();
                // Prefix the PTID with the studynum value if it isn's already there
                if (!ptid.startsWith(inputRow.get("studynum").toString()))
                {
                    ptid = inputRow.get("studynum").toString() + ptid;
                }
                outputRow.put("ptid", ptid);
                outputRow.put("tube_type", inputRow.get("vesseldomaintype"));
                // Fix up the visit number
                String visit = inputRow.get("visitname") == null ? "" : inputRow.get("visitname").toString();
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
                String activity = inputRow.get("activity") == null ? "" : inputRow.get("activity").toString();
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

                String destinationSite = inputRow.get("destinationsite") == null ? null : inputRow.get("destinationsite").toString();
                if ("N/A".equalsIgnoreCase(destinationSite))
                {
                    destinationSite = shortName;
                }

                Integer labId;
                try
                {
                    // Try using the given name as the ID if it's an integer
                    labId = Integer.parseInt(destinationSite);
                    if (!labIds.containsKey(labId.toString()))
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
                outputRows.add(outputRow);
            }

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

        return new RecordedActionSet(action);
    }

    /**
     * Parse a TSV file with lab names and IDs. First column is assumed to be lab ID. Second column is the name.
     * Assume no other columns. 
     */
    private void parseLabs(Map<String, Integer> labIds, File labsFile) throws IOException
    {
        FileInputStream fIn = new FileInputStream(labsFile);
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(fIn));
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
        finally
        {
            try { fIn.close(); } catch (IOException ignored) {}
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
            tsvWriter.setPrintWriter(writer);
            tsvWriter.write();
        }
        finally
        {
            writer.flush();
            zOut.closeEntry();
        }
    }

    private String hashRow(Map<String, Object> inputRow) throws IOException
    {
        // Use a TreeMap to ensure we have consistent key ordering
        Map<String, Object> treeMap = new TreeMap<String, Object>(inputRow);
        // Build up a string with all of the values that we care about, concatentated together
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : treeMap.entrySet())
        {
            if (!IGNORED_HASH_COLUMNS.contains(entry.getKey()))
            {
                sb.append(entry.getKey());
                sb.append(": ");
                sb.append(entry.getValue());
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

    public static class Factory extends AbstractSpecimenTaskFactory<Factory>
    {
        public Factory()
        {
            super(SampleMindedTransformTask.class);
        }

        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SampleMindedTransformTask(this, job);
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.singletonList(TRANSFORM_PROTOCOL_ACTION_NAME);
        }

        @Override
        public boolean isParticipant(PipelineJob job) throws IOException
        {
            // Only run this task if the input is a SampleMinded export
            File input = job.getJobSupport(SpecimenJobSupport.class).getInputFile();
            return SpecimenBatch.SAMPLE_MINDED_FILE_TYPE.isType(input);
        }

        @Override
        public String getStatusName()
        {
            return "SAMPLEMINDED TRANSFORM";
        }
    }
}
