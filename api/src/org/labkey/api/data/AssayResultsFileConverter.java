package org.labkey.api.data;

import org.labkey.api.assay.AssayResultsFileWriter;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.URIUtil;

import java.io.File;

public class AssayResultsFileConverter extends ExpDataFileConverter
{
    ExpRun _run;

    public AssayResultsFileConverter(ExpRun run)
    {
        _run = run;
    }

    @Override
    public Object convert(Class type, Object value)
    {
        Object convertedValue = super.convert(type, value);

        // if value was null or the ExpDataFileConverter was unable to convert it, return null
        if (convertedValue == null)
            return null;

        // if we have a run, first try to resolve the file relative to the run's results directory
        if (_run != null && value instanceof String)
        {
            File runRoot = AssayResultsFileWriter.getAssayFilesDirectoryPath(_run).toFile();
            if (runRoot.exists())
            {
                File resultsFile = new File(runRoot, value.toString());
                if (resultsFile.exists() && URIUtil.isDescendant(runRoot.toURI(), resultsFile.toURI()))
                    return resultsFile;
            }
        }

        return convertedValue;
    }
}
