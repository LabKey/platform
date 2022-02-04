package org.labkey.experiment.pipeline;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.pipeline.AbstractTaskFactory;
import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.RecordedActionSet;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.experiment.api.SampleTypeDomainKind;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SampleReloadTask extends PipelineJob.Task<SampleReloadTask.Factory>
{
    public static final String SAMPLE_NAME_KEY = "name";
    public static final String SAMPLE_ID_KEY = "id";
    public static final String ALTERNATE_KEY_LOOKUP_OPTION = "alternateKeyLookup";
    public static final String MERGE_OPTION = "mergeData";

    private boolean _mergeData;
    private boolean _alternateKeyLookup;

    private SampleReloadTask(SampleReloadTask.Factory factory, PipelineJob job)
    {
        super(factory, job);
    }

    @NotNull
    @Override
    public RecordedActionSet run()
    {
        PipelineJob job = getJob();
        FileAnalysisJobSupport support = job.getJobSupport(FileAnalysisJobSupport.class);
        job.setLogFile(new File(support.getDataDirectory(), FileUtil.makeFileNameWithTimestamp("triggered_sample_reload", "log")));
        Map<String, String> params = support.getParameters();

        job.setStatus("RELOADING", "Job started at: " + DateUtil.nowISO());

        // guaranteed to only have a single file
        assert support.getInputFiles().size() == 1;
        File dataFile = support.getInputFiles().get(0);
        Logger log = job.getLogger();

        log.info("Loading " + dataFile.getName());

        // parse any options
        if (params.containsKey(MERGE_OPTION))
            _mergeData = Boolean.parseBoolean(params.get(MERGE_OPTION));
        if (params.containsKey(ALTERNATE_KEY_LOOKUP_OPTION))
            _alternateKeyLookup = Boolean.parseBoolean(params.get(ALTERNATE_KEY_LOOKUP_OPTION));

        log.info("data merge option set as : " + _mergeData);
        log.info("import by alternate key option set as : " + _alternateKeyLookup);

        if (params.containsKey(SAMPLE_NAME_KEY) && params.containsKey(SAMPLE_ID_KEY))
        {
            log.error("Sample ID and name cannot be specified at the same time.");
            return new RecordedActionSet();
        }

        ExpSampleType sampleType = null;
        String sampleName = FileUtil.getBaseName(dataFile.getName());

        if (params.containsKey(SAMPLE_NAME_KEY))
        {
            sampleName = params.get(SAMPLE_NAME_KEY);
            sampleType = SampleTypeService.get().getSampleType(job.getContainer(), job.getUser(), sampleName);
            if (sampleType != null)
                log.info("Sample Type matching the 'name' capture group was resolved : " + sampleName);
            else
                log.info("Sample Type matching the 'name' capture group was not resolved : " + sampleName);
        }
        else if (params.containsKey(SAMPLE_ID_KEY))
        {
            int id = Integer.parseInt(params.get(SAMPLE_ID_KEY));
            sampleType = SampleTypeService.get().getSampleType(id);
            if (sampleType != null)
                log.info("Sample Type matching the 'id' capture group was resolved : " + sampleType.getName());
            else
                log.info("Sample Type matching the 'id' capture group was not resolved : " + params.get(SAMPLE_ID_KEY));
        }

        if (sampleType == null)
        {
            // if we aren't trying a named capture group resolution, see if there is a match of an existing sample type
            // by file name
            if (!params.containsKey(SAMPLE_NAME_KEY))
                sampleType = SampleTypeService.get().getSampleType(job.getContainer(), job.getUser(), sampleName);

            // still no resolution to an existing sample type, create a new one inferring the schema from the data file
            if (sampleType == null)
            {
                log.info("Creating a new Sample Type of name : " + sampleName);

                try (DataLoader loader = DataLoader.get().createLoader(dataFile, null, true, job.getContainer(), null))
                {
                    DomainKind domainKind = PropertyService.get().getDomainKindByName(SampleTypeDomainKind.NAME);
                    Set<String> reservedProps = domainKind.getReservedPropertyNames(null, null);
                    reservedProps.remove("name");
                    List<GWTPropertyDescriptor> props = new ArrayList<>();
                    boolean hasNameField = false;

                    for (ColumnDescriptor col : loader.getColumns())
                    {
                        if (!reservedProps.contains(col.getColumnName()))
                        {
                            if ("name".equalsIgnoreCase(col.getColumnName()))
                                hasNameField = true;
                            GWTPropertyDescriptor prop = new GWTPropertyDescriptor(col.getColumnName(), col.getRangeURI());
                            prop.setContainer(job.getContainerId());
                            prop.setMvEnabled(col.isMvEnabled());

                            props.add(prop);
                        }
                    }
                    // if we don't find a name column default to the first field as the id column
                    sampleType = SampleTypeService.get().createSampleType(job.getContainer(), job.getUser(), sampleName, "Created by file watcher task", props,
                            Collections.emptyList(), hasNameField ? -1 : 0, -1, -1, -1, null);
                }
                catch (Exception e)
                {
                    log.error("Error trying to create a new Sample Type", e);
                }
            }
        }
        importSamples(job.getContainer(), job.getUser(), sampleType, dataFile, log);
        job.getLogger().info("Done importing " + job.getDescription());
        
        return new RecordedActionSet();
    }

    private void importSamples(Container c, User user, @Nullable ExpSampleType sampleType, File dataFile, Logger log)
    {
        if (sampleType != null)
        {
            UserSchema userSchema = QueryService.get().getUserSchema(user, c, SamplesSchema.SCHEMA_NAME);
            if (userSchema != null)
            {
                TableInfo tinfo = userSchema.getTable(sampleType.getName());
                if (tinfo != null)
                {
                    QueryUpdateService qus = tinfo.getUpdateService();
                    if (qus != null)
                    {
                        try (DataLoader loader = DataLoader.get().createLoader(dataFile, null, true, c, null))
                        {
                            BatchValidationException errors = new BatchValidationException();
                            DataIteratorContext context = new DataIteratorContext(errors);

                            if (_mergeData)
                                context.setInsertOption(QueryUpdateService.InsertOption.MERGE);
                            context.setAllowImportLookupByAlternateKey(_alternateKeyLookup);

                            int count = qus.loadRows(user, c, loader, context, null);
                            log.info("Imported a total of " + count + " rows into : " + sampleType.getName());
                            if (context.getErrors().hasErrors())
                            {
                                for (ValidationException error : context.getErrors().getRowErrors())
                                    log.error(error.getMessage());
                            }
                        }
                        catch (Exception e)
                        {
                            log.error("import failed", e);
                        }
                    }
                    else
                        log.warn("Could not get the update service for sample : " + sampleType.getName());
                }
                else
                    log.warn("Could not get the tableInfo for sample : " + sampleType.getName());
            }
            else
                log.warn("Could not get the samples schema");
        }
        else
            log.info("Sample type could not be resolved, no data will be imported");
    }

    public static class Factory extends AbstractTaskFactory<AbstractTaskFactorySettings, Factory>
    {
        public Factory()
        {
            super(SampleReloadTask.class);
        }

        @Override
        public PipelineJob.Task createTask(PipelineJob job)
        {
            return new SampleReloadTask(this, job);
        }

        @Override
        public List<FileType> getInputTypes()
        {
            return Collections.emptyList();
        }

        @Override
        public List<String> getProtocolActionNames()
        {
            return Collections.emptyList();
        }

        @Override
        public String getStatusName()
        {
            return "RELOAD SAMPLES";
        }

        @Override
        public boolean isJobComplete(PipelineJob job)
        {
            return false;
        }
    }
}
