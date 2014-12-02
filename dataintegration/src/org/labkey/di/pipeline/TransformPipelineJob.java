/*
 * Copyright (c) 2013-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.di.pipeline;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.property.SystemProperty;
import org.labkey.api.pipeline.ParamParser;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PropertiesJobSupport;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.pipeline.TaskPipeline;
import org.labkey.api.pipeline.file.FileAnalysisJobSupport;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.di.VariableMap;
import org.labkey.di.VariableMapImpl;
import org.labkey.di.data.TransformProperty;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: jeckels
 * Date: 2/20/13
 */
public class TransformPipelineJob extends PipelineJob implements TransformJobSupport, PropertiesJobSupport, FileAnalysisJobSupport
{
    private final TransformDescriptor _etlDescriptor;
    private int _runId;
    private Integer _expRunId;
    private Integer _recordCount;
    private TransformJobContext _transformJobContext;
    private final VariableMapImpl _variableMap = new VariableMapImpl(null);
    private final Map<String,VariableMapImpl> _stepVariableMaps = new HashMap<>();
    public static final String ETL_PREFIX = "ETL Job: ";


    public TransformPipelineJob(TransformJobContext info, TransformDescriptor etlDescriptor)
    {
        super(ETLPipelineProvider.NAME,
                new ViewBackgroundInfo(info.getContainer(), info.getUser(), null),
                PipelineService.get().findPipelineRoot(info.getContainer()));
        _etlDescriptor = etlDescriptor;
        File etlLogDir = getPipeRoot().resolvePath("etlLogs");
        //TODO: The string replace is a temp workaround until we remove the braces from the module name and switch
        // to passing around a TaskId instead.
        String filePath = StringUtils.replace(StringUtils.replace(etlDescriptor.getId(), "{", ""), "}", "");
        File etlLogFile = new File(etlLogDir, FileUtil.makeFileNameWithTimestamp(filePath, "etl.log"));
        _transformJobContext = new TransformJobContext(etlDescriptor, info.getContainer(), info.getUser(), info._params);
        setLogFile(etlLogFile);
        initVariableMap(info);
    }

    private void initVariableMap(TransformJobContext info)
    {
        JSONObject savedState = TransformManager.get().getTransformConfiguration(info.getContainer(), info.getJobDescriptor()).getJsonState();
        if (!savedState.isEmpty())
        {
            for (Map.Entry<String, Object> e : savedState.entrySet())
            {
                _variableMap.put(e.getKey(), e.getValue());
            }
            JSONObject steps = (JSONObject)_variableMap.get("steps");
            if (!steps.isEmpty())
            {
                for (String k : steps.keySet())
                {
                    _stepVariableMaps.put(k, new VariableMapImpl(_variableMap, steps.getJSONObject(k)));
                }
            }
        }

        Map<ParameterDescription,Object> declaredVariables = _etlDescriptor.getDeclaredVariables();
        for (Map.Entry<ParameterDescription,Object> entry : declaredVariables.entrySet())
        {
            ParameterDescription pd = entry.getKey();
            Object value = entry.getValue();
            if (info._params.containsKey(pd))
                value = info._params.get(pd);
            _variableMap.put(pd,value);
        }

        _variableMap.put(TransformProperty.RanStep1, false, VariableMap.Scope.global);
    }

    public VariableMap getVariableMap()
    {
        return _variableMap;
    }


    public VariableMap getStepVariableMap(String id)
    {
        VariableMapImpl vm = _stepVariableMaps.get(id);
        if (null == vm)
            _stepVariableMaps.put(id, vm = new VariableMapImpl(_variableMap));
        return vm;
    }


    public void logRunFinish(TaskStatus status, Integer expRunId, Integer recordCount)
    {
        TransformRun run = getTransformRun();
        if (run != null)
        {
            // Mark that the job has finished successfully
            run.setStatus(status.toString());
            run.setEndTime(new Date());
            run.setExpRunId(expRunId);
            run.setRecordCount(recordCount);
            update(run);
        }

        if (TaskStatus.complete == status)
        {
            JSONObject state = _variableMap.toJSONObject();
            state.remove(TransformProperty.RanStep1);
            JSONObject steps = new JSONObject();
            for (Map.Entry<String,VariableMapImpl> e : _stepVariableMaps.entrySet())
            {
                JSONObject step = e.getValue().toJSONObject();
                if (null == step || step.isEmpty())
                    continue;
                steps.put(e.getKey(), step);
            }
            state.put("steps",steps);
            TransformConfiguration cfg = TransformManager.get().getTransformConfiguration(getContainer(),_etlDescriptor);
            cfg.setJsonState(state);
            TransformManager.get().saveTransformConfiguration(getUser(),cfg);
        }
    }


    @Override
    protected void done(Throwable throwable)
    {
        super.done(throwable);

        TaskStatus status = TaskStatus.complete;

        if (this.isCancelled())
            status = TaskStatus.cancelled;

        if (this.getErrors() > 0)
            status = TaskStatus.error;

        logRunFinish(status, _expRunId, _recordCount);
    }


    private void update(TransformRun run)
    {
        TransformManager.get().updateTransformRun(getUser(), run);
    }


    private TransformRun getTransformRun()
    {
        TransformRun run  = TransformManager.get().getTransformRun(getContainer(), _runId);
        if (run == null)
        {
            getLogger().error("Unable to find database record for run with TransformRunId " + _runId);
            setStatus(TaskStatus.error);
        }

        return run;
    }


    public int getTransformRunId()
    {
        return _runId;
    }


    @Override
    public TaskPipeline getTaskPipeline()
    {
        if (null != _etlDescriptor)
            return _etlDescriptor.getTaskPipeline();

        return null;
    }

    //
    // TransformJobSupport
    //
    public TransformDescriptor getTransformDescriptor()
    {
        return _etlDescriptor;
    }

    public TransformJobContext getTransformJobContext()
    {
        return _transformJobContext;
    }

    //
    // PropertiesJobSupport
    //
    public Map<PropertyDescriptor, Object> getProps()
    {
        Map<PropertyDescriptor, Object> propMap = new LinkedHashMap<>();
        Set<String> keys = _variableMap.keySet();
        for(String key : keys)
        {

            ParameterDescription pd = _variableMap.getDescriptor(key);
            if (null != pd)
            {
                if (pd instanceof SystemProperty)
                    propMap.put(((SystemProperty)pd).getPropertyDescriptor(), _variableMap.get(key));
                if (pd instanceof PropertyDescriptor)
                    propMap.put((PropertyDescriptor)pd, _variableMap.get(key));
            }
        }

        return propMap;
    }


    //
    // Called by the ExpGeneratorTask after it has finished
    // generating the experiment run for the current set
    // of actions for this transform job.
    //
    public void clearActionSet(ExpRun run)
    {
        // Gather the rollup record count for all the tasks run
        Set<RecordedAction> actions = getActionSet().getActions();
        int recordCount = 0;

        for (RecordedAction action : actions)
        {
            recordCount += getRecordCountForAction(action);
        }

        if (null != run)
            _expRunId = run.getRowId();

        _recordCount = recordCount;
        super.clearActionSet(run);
    }

    public int getRecordCountForAction(RecordedAction action)
    {
        int recordCount = 0;
        Map<PropertyDescriptor, Object> propMap = action.getProps();

        if (propMap.containsKey(TransformProperty.RecordsInserted.getPropertyDescriptor()))
            recordCount += (Integer) propMap.get(TransformProperty.RecordsInserted.getPropertyDescriptor());

        if (propMap.containsKey(TransformProperty.RecordsDeleted.getPropertyDescriptor()))
            recordCount += (Integer) propMap.get(TransformProperty.RecordsDeleted.getPropertyDescriptor());

        if (propMap.containsKey(TransformProperty.RecordsModified.getPropertyDescriptor()))
            recordCount += (Integer) propMap.get(TransformProperty.RecordsModified.getPropertyDescriptor());

        return recordCount;
    }

    @Override
    public URLHelper getStatusHref()
    {
        return null;
    }

    @Override
    public String getDescription()
    {
        return ETL_PREFIX + _etlDescriptor.getDescription();
    }

    public void setRunId(int runId)
    {
        _runId = runId;
    }

    /*
     *   FileAnalysisJobSupport methods & parameters support
     *
    */
    private File _analysisDirectory;
    private String _baseName;
    private Map<String, String> _parameters = new HashMap<>();

    @Override
    public Map<String, String> getParameters()
    {
        return _parameters;
    }

    @Override
    public String getProtocolName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getJoinedBaseName()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<String> getSplitBaseNames()
    {
        throw new UnsupportedOperationException();
    }

    public void setBaseName(String baseName)
    {
        _baseName = baseName;
    }

    @Override
    public String getBaseName()
    {
        if (_baseName != null)
            return _baseName;
        else throw new IllegalStateException("File basename has not been set.");
    }

    @Override
    public File getDataDirectory()
    {
        throw new UnsupportedOperationException();
    }

    public void setAnalysisDirectory(File analysisDirectory)
    {
        _analysisDirectory = analysisDirectory;
    }

    @Override
    public File getAnalysisDirectory()
    {
        if (_analysisDirectory != null)
            return _analysisDirectory;
        else throw new IllegalStateException("File analysis directory has not been set.");
    }

    @Override
    public File findInputFile(String name)
    {
            return new File(getAnalysisDirectory(), name);
    }

    @Override
    public File findOutputFile(String name)
    {
        return new File(getAnalysisDirectory(), name);
    }

    @Override
    public ParamParser createParamParser()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getParametersFile()
    {
        return null;
    }

    @Nullable
    @Override
    public File getJobInfoFile()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<File> getInputFiles()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileType.gzSupportLevel getGZPreference()
    {
        throw new UnsupportedOperationException();
    }
}
