package org.labkey.freezerpro;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
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
            List<Map<String, Object>> inputRows = loader.load();
            List<Map<String, Object>> outputRows = transformRows(inputRows);

            if (outputRows.size() > 0)
                info("After removing duplicates, there are " + outputRows.size() + " rows of data");
            else
                error("There are no rows of data");

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
    protected Map<String, Object> transformRow(Map<String, Object> inputRow, int rowIndex, Map<String, Integer> labIds, Map<String, Integer> primaryIds, Map<String, Integer> derivativeIds)
    {
        Map<String, Object> outputRow = new CaseInsensitiveHashMap<>(inputRow);
        inputRow = null;

        String lab = "unknown";
        Integer labId = labIds.get(lab);

        String derivative = getNonNullValue(outputRow, "sample type");
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
        String ptid = removeNonNullValue(outputRow, "patient id");
        outputRow.put("ptid", ptid);
        String barcode = removeNonNullValue(outputRow, "barcode");
        outputRow.put("global_unique_specimen_id", barcode);
        String uid = removeNonNullValue(outputRow, "uid");
        outputRow.put("unique_specimen_id", uid);

        Date collectionDate = parseDateTime("date of draw", "time of draw", outputRow);
        outputRow.put("draw_timestamp", collectionDate);
        outputRow.put("visit_value", "-1");

        Date processDate = parseDate("date of process", outputRow);
        Date processTime = parseTime("time of process", outputRow);

        outputRow.put("processing_date", processDate);
        outputRow.put("processing_time", processTime);
        String processedBy = removeNonNullValue(outputRow, "processed by");
        outputRow.put("processed_by_initials", processedBy);

        outputRow.put("lab_id", labId);
        outputRow.put("originating_location", labId);
        outputRow.put("shipped_from_lab", labId);
        outputRow.put("shipped_to_lab", labId);

        outputRow.put("primary_specimen_type_id", primaryId);
        outputRow.put("derivative_type_id", derivativeId);

        // freezer location
        String level1 = removeNonNullValue(outputRow, "level1");
        outputRow.put("fr_level1", level1);
        String level2 = removeNonNullValue(outputRow, "level2");
        outputRow.put("fr_level2", level2);
        String box = removeNonNullValue(outputRow, "box");
        outputRow.put("fr_container", box);
        String position = removeNonNullValue(outputRow, "position");
        outputRow.put("fr_position", position);

        // protocol
        String protocol = removeNonNullValue(outputRow, "study protocol");
        outputRow.put("protocol_number", protocol);

        String comments = removeNonNullValue(outputRow, "comments");
        outputRow.put("comments", comments);
        outputRow.put("quality_comments", comments);

        return outputRow;
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
}
