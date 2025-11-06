package org.labkey.api.data;

import org.json.JSONArray;
import org.labkey.api.util.FileUtil;

import java.util.List;

public class TSVJSONWriter extends TSVWriter
{
    private final JSONArray _rows;
    private final String _filename;

    /**
     * Writes a JSONArray of JSONArrays to TSV.
     * @param filename The filename without a file extension
     * @param rows A JSONArray object that is expected to be an array of arrays. e.g. [[1,2,3], [4,5,6]].
     */
    public TSVJSONWriter(String filename, JSONArray rows)
    {
        _filename = filename;
        _rows = rows;
        _headerRowVisible = false;
    }

    private List<String> jsonArrayToStringList(JSONArray jsonArray)
    {
        return jsonArray.toList()
                .stream()
                .map(obj -> (obj == null) ? "" : String.valueOf(obj))
                .toList();
    }

    @Override
    protected int writeBody()
    {
        for (int i = 0; i < _rows.length(); i++)
        {
            List<String> values = jsonArrayToStringList(_rows.getJSONArray(i));
            writeLine(values);
        }

        return _rows.length();
    }

    @Override
    protected String getFilename()
    {
        return FileUtil.makeLegalName(_filename + "." + getFilenameExtension());
    }
}
