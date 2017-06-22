/*
 * Copyright (c) 2009-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
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
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.MapDataIterator;
import org.labkey.api.pipeline.AbstractSpecimenTransformTask;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.reader.DataLoaderFactory;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.User;
import org.labkey.api.study.Location;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.FileType;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;
import org.labkey.study.StudySchema;
import org.labkey.study.model.ParticipantIdImportHelper;
import org.labkey.study.model.SequenceNumImportHelper;
import org.labkey.study.model.StudyManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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

public class SampleMindedTransformTask extends AbstractSpecimenTransformTask
{
    public static final FileType SAMPLE_MINDED_FILE_TYPE = new FileType(".xlsx");
    private static final String INVALID_SUFFIX = "-invalid";
    private static final Map<String, Integer> STANDARD_PRIMARY_TYPE_IDS;
    private static final Map<String, Integer> STANDARD_DERIVATIVE_TYPE_IDS;
    private static final Map<String, String> DERIVATIVE_PRIMARY_MAPPINGS;

    private static final Set<String> IGNORED_HASH_COLUMNS = new CaseInsensitiveHashSet(PageFlowUtil.set("comments", "episodetype", "episodevalue"));

    static
    {
        Map<String, Integer> primaryTypes = new LinkedHashMap<>();
        primaryTypes.put("BAL", primaryTypes.size() + 1);
        primaryTypes.put("Blood", primaryTypes.size() + 1);
        primaryTypes.put("Nasal Swab", primaryTypes.size() + 1);
        primaryTypes.put("Tissue", primaryTypes.size() + 1);
        primaryTypes.put("Urine", primaryTypes.size() + 1);
        primaryTypes.put("unknown", primaryTypes.size() + 1);
        STANDARD_PRIMARY_TYPE_IDS = Collections.unmodifiableMap(primaryTypes);

        Map<String, String> mappings = new HashMap<>();
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

        Map<String, Integer> derivativeTypes = new LinkedHashMap<>();
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

    private final Map<String, Integer> _labIds = new LinkedHashMap<>();
    private final Map<String, Integer> _primaryIds = new LinkedHashMap<>(STANDARD_PRIMARY_TYPE_IDS);
    private final Map<String, Integer> _derivativeIds = new LinkedHashMap<>(STANDARD_DERIVATIVE_TYPE_IDS);
    boolean _validate = true;


    public SampleMindedTransformTask(@Nullable PipelineJob job)
    {
        super(job);
    }


    public void setValidate(boolean b)
    {
        _validate = b;
    }

    @Override
    protected Map<String,Integer> getLabIds()
    {
        return _labIds;
    }


    @Override
    protected Map<String,Integer> getPrimaryIds()
    {
        return _primaryIds;
    }


    @Override
    protected Map<String,Integer> getDerivativeIds()
    {
        return _derivativeIds;
    }

    @Override
    protected Map<String,Integer> getAdditiveIds()
    {
        return Collections.emptyMap();
    }

    @Override
    protected Set<String> getIgnoredHashColumns()
    {
        return IGNORED_HASH_COLUMNS;
    }

    @Override
    public void transform(File input, File output) throws PipelineJobException
    {
        info("Starting to transform input file " + input + " to output file " + output);

        try
        {
            File labsFile = new File(input.getParent(), "labs.txt");
            if (NetworkDrive.exists(labsFile) && labsFile.isFile())
            {
                try (FileInputStream fIn = new FileInputStream(labsFile))
                {
                    parseLabs(_labIds, Readers.getReader(fIn));
                }
                info("Parsed " + _labIds.size() + " labs from " + labsFile);
            }
            else
            {
                debug("No such file " + labsFile + " so not parsing supplemental lab information");
            }

            ExcelLoader loader = new ExcelLoader(input, true);
            loader.setInferTypes(false);

            // Create a ZIP archive with the appropriate TSVs
            try (ZipOutputStream zOut = new ZipOutputStream(new FileOutputStream(output)))
            {
                // Add a new file to the ZIP
                zOut.putNextEntry(new ZipEntry("specimens.tsv"));
                PrintWriter writer = PrintWriters.getPrintWriter(zOut);
                final SpecimenTransformTSVWriter tsvWriter = new SpecimenTransformTSVWriter(Collections.emptyList());;
                tsvWriter.setFileHeader(Collections.singletonList("# " + "specimens"));
                tsvWriter.setPrintWriter(writer);
                try
                {
                    loader.forEach(row -> {
                        if (MapFilter.getMapFilter(this).test(row))
                        {
                            tsvWriter.writeRow(MapTransformer.getMapTransformer(this, getLabIds(), getPrimaryIds(), getDerivativeIds()).apply(row));
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

                writeLabs(_labIds, zOut);
                writePrimaries(_primaryIds, zOut);
                writeDerivatives(_derivativeIds, zOut);
                writeAdditives(Collections.emptyMap(), zOut);
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

        toDate("CollectionDate", outputRow);
        toDate("ActivitySaveDateTime", outputRow);
        toDate("LabRecievedTime", outputRow);
        toInt("sitecode", outputRow);

        String vesselDomainType = (String)outputRow.get("VesselDomainType");
        if (null != vesselDomainType && vesselDomainType.length() > 64)
            outputRow.put("VesselDomainType", vesselDomainType.substring(0,64));

        // Get the barcode and strip off the "-INVALID" suffix, if present
        String barcode = removeNonNullValue(outputRow, "barcode");
        if (barcode.toLowerCase().endsWith(INVALID_SUFFIX))
        {
            barcode = barcode.substring(0, barcode.length() - INVALID_SUFFIX.length());
        }
        barcode = barcode.trim();
        if (_validate && StringUtils.isEmpty(barcode))
        {
            warn("Skipping data row missing 'barcode' value, row number " + rowIndex);
            return null;
        }

        String collectionDate = removeNonNullValue(outputRow, "collectiondate");
        if (_validate && StringUtils.isEmpty(collectionDate))
        {
            warn("Skipping data row missing 'collectiondate' value, row number " + rowIndex);
            return null;
        }

        String shortName = outputRow.get("siteshortname") == null ? null : outputRow.get("siteshortname").toString();
        Integer locationId = labIds.get(shortName);
        if (locationId == null)
        {
            // We don't have an existing ID to use for this site, so find one that's available
            locationId = 1;
            while (labIds.containsValue(locationId))
            {
                locationId += 1;
            }
            labIds.put(shortName, locationId);
        }

        String derivative = getNonNullValue(outputRow, "specimentype");
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
        outputRow.put("originating_location", locationId);      // specimensevent
        outputRow.put("locationid", locationId);                // missing specimen/visit
        outputRow.put("global_unique_specimen_id", barcode);
        String ptid = removeNonNullValue(outputRow, "participantid");
        outputRow.put("ptid", ptid);
        outputRow.put("tube_type", outputRow.get("vesseldomaintype"));  // specimenevent
        outputRow.put("tubetype", outputRow.get("vesseldomaintype"));   // missing specimen
        // Fix up the visit number
        String visit = removeNonNullValue(outputRow, "visitname");
        if (visit.toLowerCase().startsWith("visit"))
        {
            visit = visit.substring("visit".length()).trim();
        }
        // UNDONE: This should not be here this is what SequenceNumImportHelper and the visit import maps is for
        if ("SE".equalsIgnoreCase(visit) || "SR".equalsIgnoreCase(visit) || "CIB".equalsIgnoreCase(visit)
                || "SR Wk1 - Wk2".equalsIgnoreCase(visit) || "AR2".equalsIgnoreCase(visit))
        {
            visit = "999";
        }
        else if ("PT1".equalsIgnoreCase(visit))
        {
            visit = "-1";
        }
        outputRow.put("visit_value", visit);

        outputRow.put("primary_specimen_type_id", primaryId);
        outputRow.put("derivative_type_id", derivativeId);
        outputRow.put("draw_timestamp", collectionDate);
        // Sort the date into the appropriate column based on the activity description
        String activity = getNonNullValue(outputRow, "activity");
        Object activitySaveDateTime = outputRow.get("activitysavedatetime");
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
        outputRow.put("processed_by_initials", outputRow.get("activityuser"));
        if (!outputRow.containsKey("comments"))
            outputRow.put("comments", activity);

        String destinationLocation = getNonNullValue(outputRow, "destination_site");
        if ("".equals(destinationLocation))
        {
            destinationLocation = getNonNullValue(outputRow, "destinationsite");
        }
        if (destinationLocation.trim().length() == 0 || "N/A".equalsIgnoreCase(destinationLocation))
        {
            destinationLocation = shortName;
        }

        Integer labId;
        try
        {
            // Try using the given name as the ID if it's an integer
            labId = Integer.parseInt(destinationLocation);
            if (!labIds.containsValue(labId))
            {
                labIds.put(labId.toString(), labId);
            }
        }
        catch (NumberFormatException e)
        {
            labId = labIds.get(destinationLocation);
            if (labId == null)
            {
                // We don't have an existing ID, so find one that's available
                labId = 1;
                while (labIds.containsValue(labId))
                {
                    labId += 1;
                }
                labIds.put(destinationLocation, labId);
            }
        }

        outputRow.put("lab_id", labId);
        outputRow.put("ship_batch_number", outputRow.get("airbillnumber"));

        return outputRow;
    }



    /* for missing visit, missing specimen, we use the existing lookup values */
    void loadLookupsFromDb(Container c)
    {
        DbSchema study = StudySchema.getInstance().getSchema();
        StudyManager sm = StudyManager.getInstance();
        String primaryTypeSelectName = StudySchema.getInstance().getTableInfoSpecimenPrimaryType(c).getSelectName();
        String derivativeSelectName = StudySchema.getInstance().getTableInfoSpecimenDerivative(c).getSelectName();
        for (Location l : sm.getSites(c))
            _labIds.put(l.getLabel(), l.getRowId());
        (new SqlSelector(study,"SELECT primaryType, rowId FROM " + primaryTypeSelectName + " WHERE container=?", c)).forEach(new Selector.ForEachBlock<ResultSet>(){
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                _primaryIds.put(rs.getString(1), rs.getInt(2));
            }
        });
        (new SqlSelector(study,"SELECT derivative, rowId FROM " + derivativeSelectName + " WHERE container=?", c)).forEach(new Selector.ForEachBlock<ResultSet>(){
            @Override
            public void exec(ResultSet rs) throws SQLException
            {
                _derivativeIds.put(rs.getString(1), rs.getInt(2));
            }
        });
    }

    static void importNotDone(File source, PipelineJob job)
    {
        job.setStatus("Importing missing specimens");
        QuerySchema schema = DefaultSchema.get(job.getUser(), job.getContainer()).getSchema("rho");
        if (null == schema)
        {
            job.warn("Rho module is not installed in this folder.");
            return;
        }
        TableInfo target = schema.getTable("MissingSpecimen");
        if (null == target)
        {
            job.warn("Table not found: rho.MissingSpecimen");
            return;
        }
        importXls(target, source, job);
    }

    static void importSkipVisit(File source, PipelineJob job)
    {
        job.setStatus("Importing missing visits");
        QuerySchema schema = DefaultSchema.get(job.getUser(), job.getContainer()).getSchema("rho");
        if (null == schema)
        {
            job.warn("Rho module is not installed in this folder.");
            return;
        }
        TableInfo target = schema.getTable("MissingVisit");
        if (null == target)
        {
            job.warn("Table not found: rho.MissingVisit");
            return;
        }
        importXls(target, source, job);
    }

    static void importXls(@NotNull TableInfo target, @NotNull File source, PipelineJob job)
    {
        Study study = StudyManager.getInstance().getStudy(job.getContainer());
        if (null == study)
            return;
        DbScope scope = target.getSchema().getScope();
        DataIteratorContext context = new DataIteratorContext();

        Map<Enum, Object> options = new HashMap<>();
        options.put(QueryUpdateService.ConfigParameters.Logger, job.getLogger());
        context.setConfigParameters(options);

        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            DataLoaderFactory df = DataLoader.get().findFactory(source, null);
            if (null == df)
                return;
            DataLoader dl = df.createLoader(source, true, study.getContainer());
            DataIteratorBuilder sampleminded = StudyService.get().wrapSampleMindedTransform(job.getUser(), dl, context, study, target);
            Map<String,Object> empty = new HashMap<>();

            // would be nice to have deleteAll() in QueryUpdateService
            new SqlExecutor(scope).execute("DELETE FROM rho." + target.getName() + " WHERE container=?", study.getContainer());
            int count = target.getUpdateService().importRows(job.getUser(), study.getContainer(), sampleminded, context.getErrors(), null, empty);
            if (!context.getErrors().hasErrors())
            {
                transaction.commit();
            }
            else
            {
                // TODO write errors to log
            }
        }
        catch (SQLException x)
        {
            boolean isConstraint = RuntimeSQLException.isConstraintException(x);
            if (isConstraint)
                context.getErrors().addRowError(new ValidationException(x.getMessage()));
            else
                throw new RuntimeSQLException(x);
        }
        catch (IOException x)
        {
            context.getErrors().addRowError(new ValidationException(x.getMessage()));
        }
        finally
        {
            // write errors to log
            for (ValidationException error : context.getErrors().getRowErrors())
                job.error(error.getMessage());
        }
    }

    /**
     * This lets us re-use a lot of the column mapping stuff we do for sample minded specimen import.  In particular
     * the missing visit/specimen has similar column mappings.
     *
     * TODO this should probably be turned inside out so that transform() uses the DataIterator.
     */
    public static DataIteratorBuilder wrapSampleMindedTransform(User user, DataIteratorBuilder in, DataIteratorContext context,
                                                                Study study, TableInfo target)
    {
        return new _DataIteratorBuilder(user, in, context, study, target);
    }


    static class _DataIteratorBuilder implements DataIteratorBuilder
    {
        DataIteratorBuilder _input;
        Study _study;
        TableInfo _target;
        User _user;

        private _DataIteratorBuilder(User user, DataIteratorBuilder in, DataIteratorContext context, Study study, TableInfo target)
        {
            _input = in;
            _study = study;
            _target = target;
            _user = user;
        }

        @Override
        public DataIterator getDataIterator(DataIteratorContext context)
        {
            try
            {
                DataIterator iterator = _input.getDataIterator(context);
                iterator = LoggingDataIterator.wrap(iterator);

                boolean barcode=false, siteshortname=false;
                boolean global_unique_specimen_id=false, lab_id=false;
                int colCount = iterator.getColumnCount();
                for (int icol=0 ; icol<=colCount ; icol++)
                {
                    String name = iterator.getColumnInfo(icol).getName();
                    barcode |= StringUtils.equalsIgnoreCase("barcode", name);
                    siteshortname |= StringUtils.equalsIgnoreCase("siteshortname", name);
                    global_unique_specimen_id |= StringUtils.equalsIgnoreCase("global_unique_specimen_id", name);
                    lab_id |= StringUtils.equalsIgnoreCase("lab_id", name);
                }
                boolean lookslikeSampleMinded = barcode && siteshortname && !global_unique_specimen_id && !lab_id;

                if (lookslikeSampleMinded)
                    return LoggingDataIterator.wrap(_DataIterator.create(_user, iterator, context, _study, _target));
                else
                    return iterator;
            }
            catch (BatchValidationException ex)
            {
                return null;
            }
        }
    }

    static class _DataIterator extends ListofMapsDataIterator
    {
        static DataIterator create(User user, DataIterator source, DataIteratorContext context, Study study, TableInfo target) throws BatchValidationException
        {
            List<ColumnInfo> cols = target.getColumns();
            return new _DataIterator(user, cols, source, context, study);
        }

        _DataIterator(User user, List<ColumnInfo> cols, DataIterator source, DataIteratorContext context, Study study)
                throws BatchValidationException
        {
            super(cols);

            try
            {

                SequenceNumImportHelper snih = new SequenceNumImportHelper(study, null);
                ParticipantIdImportHelper piih = new ParticipantIdImportHelper(study, user, null);

                List<Map<String, Object>> inputRows = new ArrayList<>();
                MapDataIterator di = DataIteratorUtil.wrapMap(source, false);
                while (di.next())
                    inputRows.add(di.getMap());

                SampleMindedTransformTask task = new SampleMindedTransformTask(null);
                task.loadLookupsFromDb(study.getContainer());

                task.setValidate(false);
                List<Map<String, Object>> outputRows = task.transformRows(inputRows);

                int rownumber = 0;
                for (Map<String,Object> row : outputRows)
                {
                    rownumber++;
                    Object visit = row.get("visit_value");
                    Object collection = row.get("draw_timestamp");
                    Object ptid = StringUtils.defaultString((String) row.get("participantid"), (String) row.remove("ptid"));
                    Object translatedPtid = piih.translateParticipantId(ptid);
                    row.put("participantid", translatedPtid);
                    Double sn = snih.translateSequenceNum(visit,collection);
                    row.put("sequencenum", sn);
                    if (null == sn)
                    {
                        String msg = null == visit ? "visit not specified" : "visit not recognized: " + visit;
                        ValidationException vex = new ValidationException(msg);
                        vex.setRowNumber(rownumber);
                        context.getErrors().addRowError(vex);
                        context.checkShouldCancel();
                    }
                }

                _rows = initRows(outputRows);
            }
            catch (IOException x)
            {
                context.getErrors().addRowError(new ValidationException(x.getMessage()));
            }
            catch (ValidationException e)
            {
                throw new BatchValidationException(e);
            }
        }
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
            Map<String, Integer> labs = new HashMap<>();
            _task.parseLabs(labs, new BufferedReader(new StringReader("Location Number\tLocation Name\n501\tLab AA\n502\tLab BB\n503\tLab CC")));
            assertEquals("Wrong number of labs", 3, labs.size());
            assertEquals("Wrong lab name", 501, labs.get("Lab AA").intValue());
        }

        @Test
        public void testLabLookup() throws IOException
        {
            Map<String, Integer> labs = new HashMap<>();
            labs.put("TestLabA", 501);
            labs.put("TestLabB", 502);

            // Try a row that uses the lab's ID
            Map<String, Object> row1 = new ArrayListMap<>();
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
            Map<String, Object> row2 = new ArrayListMap<>();
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
            Map<String, Integer> labs = new HashMap<>();
            _task.parseLabs(labs, new BufferedReader(new StringReader("# labs\t\t\t\t\t\t\t\t\n" +
                    "lab_id\tldms_lab_code\tlabware_lab_code\tlab_name\tlab_upload_code\tis_sal\tis_repository\tis_clinic\tis_endpoint\n" +
                    "23\t\t\tFAKE (12)\t\t\t\t\t\n" +
                    "4\t\t\tFDSJ (10)\t\t\t\t\t\n" +
                    "15\t\t\tMFDS (17)\t\t\t\t\t\n" +
                    "1\t\t\tPWER (11)\t\t\t\t\t\n" +
                    "5\t\t\tOTJD (02)\t\t\t\t\t\n" +
                    "21\t\t\tVCXF (19)\t\t\t\t\t")));
            assertEquals("Wrong number of labs", 6, labs.size());
            assertEquals("Wrong lab name", 23, labs.get("FAKE (12)").intValue());
        }


        @Test
        public void testDeduplication() throws IOException
        {
            ArrayListMap<String,Object> template = new ArrayListMap<>();

            List<Map<String, Object>> inputRows = new ArrayList<>();
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

            List<Map<String, Object>> outputRows = _task.transformRows(inputRows);
            assertEquals(2, outputRows.size());
        }


        @Test
        public void testInvalidBarcodeDetection()
        {
            Map<String, Object> row1 = new ArrayListMap<>();
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

            Map<String, Object> row1 = new ArrayListMap<>();
            assertEquals(null, _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>()));
        }

        @Test
        public void testCollectionDateBucketing()
        {
            Map<String, Object> row = new ArrayListMap<>();
            row.put("participant", "ptid1");
            row.put("barcode", "barcode");
            String date = "May 5, 2012";
            row.put("collectiondate", "May 6, 2012");
            row.put("activitysavedatetime", date);
            row.put("activity", "Some Lab Receiving");
            Map<String, Object> outputRow = _task.transformRow(row, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Bad receiving handling", new Date(date), outputRow.get("lab_receipt_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("ship_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("processing_date"));

            row.put("activity", "Ship Specimens from Clinical Location");
            outputRow = _task.transformRow(row, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Bad receiving handling", null, outputRow.get("lab_receipt_date"));
            assertEquals("Bad receiving handling", new Date(date), outputRow.get("ship_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("processing_date"));

            row.put("activity", "Aliquoting");
            outputRow = _task.transformRow(row, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>());
            assertEquals("Bad receiving handling", null, outputRow.get("lab_receipt_date"));
            assertEquals("Bad receiving handling", null, outputRow.get("ship_date"));
            assertEquals("Bad receiving handling", new Date(date), outputRow.get("processing_date"));
        }

        @Test
        public void testMissingCollectionDate()
        {
            _context.checking(new Expectations()
            {{
                oneOf(_job).warn("Skipping data row missing 'collectiondate' value, row number 1");
            }});

            Map<String, Object> row1 = new ArrayListMap<>();
            row1.put("participant", "ptid1");
            row1.put("barcode", "barcode-1");
            row1.put("collectiondate", "");
            row1.put("visitname", "Visit 01");

            assertEquals(null, _task.transformRow(row1, 1, new HashMap<String, Integer>(), new HashMap<String, Integer>(), new HashMap<String, Integer>()));
        }

        @Test
        public void testPrimaryAndDerivatives() throws IOException
        {
            ArrayListMap<String,Object> template = new ArrayListMap<>();
            List<Map<String, Object>> inputRows = new ArrayList<>();

            ArrayListMap<String,Object> row1 = new ArrayListMap<>(template.getFindMap());
            row1.put("participant", "ptid1");
            row1.put("barcode", "barcode-1");
            row1.put("collectiondate", "May 5 2001");
            row1.put("visitname", "Visit 01");
            row1.put("specimentype", "PBMC");
            inputRows.add(row1);

            ArrayListMap<String,Object> row2 = new ArrayListMap<>(template.getFindMap());
            row2.putAll(row1);
            row2.put("specimentype", "Blood");
            inputRows.add(row2);

            ArrayListMap<String,Object> row3 = new ArrayListMap<>(template.getFindMap());
            row3.putAll(row1);
            row3.put("specimentype", "NewType!");
            inputRows.add(row3);

            ArrayListMap<String,Object> row4 = new ArrayListMap<>(template.getFindMap());
            row4.putAll(row3);
            row4.put("participant", "ptid2");
            inputRows.add(row4);

            ArrayListMap<String,Object> row5 = new ArrayListMap<>(template.getFindMap());
            row5.putAll(row3);
            row5.put("specimentype", "Tissue Slide");
            inputRows.add(row5);

            ArrayListMap<String,Object> row6 = new ArrayListMap<>(template.getFindMap());
            row6.putAll(row3);
            row6.put("specimentype", "Urine Supernatant");
            inputRows.add(row6);

            ArrayListMap<String,Object> row7 = new ArrayListMap<>(template.getFindMap());
            row7.putAll(row3);
            row7.put("specimentype", "Urine Pellet");
            inputRows.add(row7);

            List<Map<String, Object>> outputRows = _task.transformRows(inputRows);

            Map<String, Integer> labIds = _task.getLabIds();
            Map<String, Integer> primaryIds = _task.getPrimaryIds();
            Map<String, Integer> derivativeIds = _task.getDerivativeIds();

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
