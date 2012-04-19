package org.labkey.api.admin;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineJobWarning;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * User: cnathe
 * Date: Apr 16, 2012
 */
public class FolderImporterImpl implements FolderImporter<FolderDocument.Folder>
{
    private FolderSerializationRegistry _registry;
    private Collection<FolderImporter> _importers;
    private PipelineJob _job;
    private boolean _usingVirtualFile = false;

    public FolderImporterImpl()
    {
        this(null);
        _usingVirtualFile = true;
    }

    public FolderImporterImpl(@Nullable PipelineJob job)
    {
        _registry = ServiceRegistry.get().getService(FolderSerializationRegistry.class);
        if (null == _registry)
        {
            throw new RuntimeException();
        }

        _importers = _registry.getRegisteredInitialFolderImporters();
        _importers.addAll(_registry.getRegisteredFinalFolderImporters());

        _job = job;
    }

    @Override
    public String getDescription()
    {
        return null;
    }

    @Override
    public void process(@Nullable PipelineJob job, ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        for (FolderImporter importer : _importers)
        {
            if (!_usingVirtualFile || importer.supportsVirtualFile())
                importer.process(job, ctx, vf);
        }
    }

    @Override
    public Collection<PipelineJobWarning> postProcess(ImportContext<FolderDocument.Folder> ctx, VirtualFile vf) throws Exception
    {
        List<PipelineJobWarning> warnings = new ArrayList<PipelineJobWarning>();
        for (FolderImporter importer : _importers)
        {
            if (null != _job) _job.setStatus("POST-PROCESS " + importer.getDescription());
            Collection<PipelineJobWarning> importerWarnings = importer.postProcess(ctx, vf);
            if (null != importerWarnings)
                warnings.addAll(importerWarnings);
        }
        return warnings;
    }

    @Override
    public boolean supportsVirtualFile()
    {
        return false;
    }
}
