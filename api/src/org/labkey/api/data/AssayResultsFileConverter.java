package org.labkey.api.data;

import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayResultsFileWriter;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.FileUtil;
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
                String valueStr = value.toString();

                for (int i = 0; i < 5; i++) // try up to 5 times to find a case-sensitive match
                {
                    String resultsFileName = AssayFileWriter.getAppendedFileName(valueStr, i);
                    File resultsFile = FileUtil.appendName(runRoot, resultsFileName);
                    if (!resultsFile.exists() || !URIUtil.isDescendant(runRoot.toURI(), resultsFile.toURI()))
                        break;

                    if (isCaseSensitiveFileNameMatch(resultsFileName, resultsFile))
                        return resultsFile;
                }
            }
        }

        return convertedValue;
    }

    // if two files were uploaded with the same name but different casing, then the file system will uniquify the names
    // when saved (i.e. test.txt and Test-1.txt) on a case-sensitive file system, so we have to check here to see if
    // the resolved results file name matches the canonical file name
    private boolean isCaseSensitiveFileNameMatch(String value, File resultsFile)
    {
        String caseSensitivePath = FileUtil.getAbsoluteCaseSensitiveFile(resultsFile).getAbsolutePath();
        String caseSensitiveName = AssayResultsFileWriter.getFileNameWithoutPath(caseSensitivePath);
        return value.equals(caseSensitiveName);
    }
}
