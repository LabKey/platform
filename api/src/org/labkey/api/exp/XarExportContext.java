package org.labkey.api.exp;

import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.LoggerGetter;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Folder export context that allows filtering of objects in the Xar
 */
public class XarExportContext extends FolderExportContext
{
    private Set<Integer> _includedAssayRuns = new HashSet<>();
    private Map<Integer, Set<Integer>> _includedSamples = new HashMap<>();
    private Map<Integer, Set<Integer>> _includedDataClasses = new HashMap<>();

    public XarExportContext(User user, Container c, Set<String> dataTypes, String format, LoggerGetter logger)
    {
        super(user, c, dataTypes, format, logger);
    }

    public Set<Integer> getIncludedAssayRuns()
    {
        return _includedAssayRuns;
    }

    public void setIncludedAssayRuns(Map<Integer, Set<Integer>> includedRuns)
    {
        if (includedRuns != null)
        {
            _includedAssayRuns = includedRuns.keySet();
        }
    }

    public Map<Integer, Set<Integer>> getIncludedSamples()
    {
        return _includedSamples;
    }

    public void setIncludedSamples(Map<Integer, Set<Integer>> includedSamples)
    {
        if (includedSamples != null)
            _includedSamples = includedSamples;
    }

    public Map<Integer, Set<Integer>> getIncludedDataClasses()
    {
        return _includedDataClasses;
    }

    public void setIncludedDataClasses(Map<Integer, Set<Integer>> includedDataClasses)
    {
        if (includedDataClasses != null)
            _includedDataClasses = includedDataClasses;
    }
}
