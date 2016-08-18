package org.labkey.experiment.controllers.exp;

import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.browse.PipelinePathForm;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.view.NotFoundException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nick Arnold on 8/17/16.
 */
public class ImportXarForm extends PipelinePathForm
{
    private String _module;

    public String getModule()
    {
        return _module;
    }

    public void setModule(String module)
    {
        _module = module;
    }

    /**
     * This override is to allow for .xar's to be imported from module resources. If a module
     * is specified the list of files will be expected to be found in getPath(), otherwise, returns
     * default pipeline xar processing.
     */
    @Override
    public List<File> getValidatedFiles(Container c, boolean allowNonExistentFiles)
    {
        if (_module == null)
            return super.getValidatedFiles(c, allowNonExistentFiles);

        Module m = ModuleLoader.getInstance().getModuleForSchemaName(_module);
        if (m == null)
        {
            throw new NotFoundException("Could not find module " + _module);
        }

        Resource r = m.getModuleResource(getPath());

        // path is expected to be a directory
        if (r == null || !r.isCollection() || !r.exists())
        {
            throw new NotFoundException("Could not find path " + getPath());
        }

        List<File> files = new ArrayList<>();
        for (String fileName : getFile())
        {
            Resource rf = m.getModuleResource(getPath() + "/" + fileName);

            if (rf == null || !rf.isFile())
            {
                throw new NotFoundException("Could not find file '" + fileName + "' in '" + getPath() + "'");
            }

            File f = ((FileResource) rf).getFile();
            if (!allowNonExistentFiles && !NetworkDrive.exists(f))
            {
                throw new NotFoundException("Could not find file '" + f + "'");
            }
            files.add(f);
        }

        return files;
    }
}
