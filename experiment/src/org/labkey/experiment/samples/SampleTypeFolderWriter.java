package org.labkey.experiment.samples;

import org.labkey.api.admin.BaseFolderWriter;
import org.labkey.api.admin.FolderArchiveDataTypes;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterFactory;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.writer.VirtualFile;
import org.labkey.experiment.LSIDRelativizer;
import org.labkey.experiment.XarExporter;
import org.labkey.experiment.xar.XarExportSelection;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.study.xml.SampleType;

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SampleTypeFolderWriter extends BaseFolderWriter
{
    private static final String DEFAULT_DIRECTORY = "sample-types";
    private static final String XAR_FILE_NAME = "sample_types.xar";
    private static final String XAR_XML_FILE_NAME = XAR_FILE_NAME + ".xml";

    private SampleTypeFolderWriter()
    {
    }

    @Override
    public String getDataType()
    {
        return FolderArchiveDataTypes.SAMPLE_TYPES;
    }

    @Override
    public boolean show(Container c)
    {
        return !SampleTypeService.get().getSampleTypes(c, null, false).isEmpty();
    }

    @Override
    public void write(Container object, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        XarExportSelection selection = new XarExportSelection();
        List<ExpSampleType> sampleTypes = new ArrayList<>();

        for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(ctx.getContainer(), ctx.getUser(), true))
        {
            sampleTypes.add(sampleType);
            selection.addSampleType(sampleType);
        }

        // add any sample derivation runs
        List<ExpRun> runs = ExperimentService.get().getExpRuns(ctx.getContainer(), null, null).stream()
                .filter(run -> run.getProtocol().getLSID().equals(ExperimentService.SAMPLE_DERIVATION_PROTOCOL_LSID))
                .collect(Collectors.toList());

        selection.addRuns(runs);

        // TODO : remove setting the xar folder
        ctx.getXml().addNewXar().setDir(DEFAULT_DIRECTORY);
        VirtualFile xarDir = vf.getDir(DEFAULT_DIRECTORY);

        XarExporter exporter = new XarExporter(LSIDRelativizer.FOLDER_RELATIVE, selection, ctx.getUser(), XAR_XML_FILE_NAME, ctx.getLogger());

        try (OutputStream fOut = xarDir.getOutputStream(XAR_FILE_NAME))
        {
            exporter.writeAsArchive(fOut);
        }

        // write out the sample rows that aren't participating in the derivation protocol
        UserSchema userSchema = QueryService.get().getUserSchema(ctx.getUser(), ctx.getContainer(), SamplesSchema.SCHEMA_NAME);
        if (userSchema != null)
        {
            for (ExpSampleType sampleType : sampleTypes)
            {
                TableInfo tinfo = userSchema.getTable(sampleType.getName());
                if (tinfo != null)
                {
                    Collection<ColumnInfo> columns = getColumnsToExport(tinfo);
                }
            }
        }
    }

    private Collection<ColumnInfo> getColumnsToExport(TableInfo tinfo)
    {
        List<ColumnInfo> columns = new ArrayList<>();

        for (ColumnInfo col : tinfo.getColumns())
        {
            if (col.isUserEditable() || col.isKeyField())
            {
                columns.add(col);
            }
        }
        return columns;
    }

    public static class Factory implements FolderWriterFactory
    {
        @Override
        public FolderWriter create()
        {
            return new SampleTypeFolderWriter();
        }
    }
}
