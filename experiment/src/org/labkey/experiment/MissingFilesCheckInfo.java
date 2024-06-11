package org.labkey.experiment;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class MissingFilesCheckInfo
{
    long _missingFilesCount = 0;
    long _validFilesCount = 0;
    Set<String> _missingFilePaths = new HashSet<>();

    public MissingFilesCheckInfo()
    {}

    public long getMissingFilesCount()
    {
        return _missingFilesCount;
    }

    public long getValidFilesCount()
    {
        return _validFilesCount;
    }

    public Set<String> getMissingFilePaths()
    {
        return _missingFilePaths;
    }

    public void addMissingFile(String path, boolean trackMissingFiles)
    {
        _missingFilesCount++;
        if (trackMissingFiles)
            _missingFilePaths.add(path);
    }

    public void incrementValidFilesCount()
    {
        _validFilesCount++;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("missingFilesCount", _missingFilesCount);
        json.put("validFilesCount", _validFilesCount);
        if (!_missingFilePaths.isEmpty())
            json.put("missingFilePaths", _missingFilePaths);
        return json;
    }
}
