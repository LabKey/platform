package org.labkey.api.assay.transform;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.vfs.FileLike;
import org.labkey.vfs.FileSystemLike;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AnalysisScript
{
    final FileLike _script;
    Set<DataTransformService.TransformOperation> _operations = new HashSet<>();

    public AnalysisScript(File script, Set<DataTransformService.TransformOperation> operations)
    {
        this(script);
        _operations = operations;
    }

    private AnalysisScript(File script, List<String> operations)
    {
        this(script);
        for (String op : operations)
        {
            if (op != null)
                _operations.add(DataTransformService.TransformOperation.valueOf(op));
        }
    }

    private AnalysisScript(File script)
    {
        if (!script.exists())
        {
            _script = new FileSystemLike.Builder(script).build().getRoot();
        }
        else
        {
            _script = FileSystemLike.wrapFile(script);
        }
    }

    public FileLike getScript()
    {
        return _script;
    }

    public String getScriptPath()
    {
        return _script.toNioPathForRead().toString();
    }

    public boolean canExecute(DataTransformService.TransformOperation operation)
    {
        return _operations.contains(operation);
    }

    private static JSONObject toJson(AnalysisScript script)
    {
        JSONObject json = new JSONObject();

        json.put("operations", script._operations);
        json.put("script", script._script.toNioPathForRead());

        return json;
    }

    @Nullable
    public static JSONArray toJson(Collection<AnalysisScript> scripts)
    {
        if (!scripts.isEmpty())
        {
            JSONArray json = new JSONArray();
            for (AnalysisScript script : scripts)
            {
                json.put(toJson(script));
            }
            return json;
        }
        return null;
    }

    @Nullable
    public static List<AnalysisScript> fromJson(String jsonStr)
    {
        try
        {
            List<AnalysisScript> scripts = new ArrayList<>();
            JSONArray json = new JSONArray(jsonStr);
            for (Object o : json.toList())
            {
                if (o instanceof Map<?,?> props)
                {
                    File script = new File(String.valueOf(props.get("script")));
                    List<String> ops = (List<String>) props.get("operations");

                    scripts.add(new AnalysisScript(script, ops));
                }
            }
            return scripts;
        }
        catch (JSONException e)
        {
            // ignore
            return null;
        }
    }
}
