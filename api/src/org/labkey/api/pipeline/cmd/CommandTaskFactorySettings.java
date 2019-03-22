/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
package org.labkey.api.pipeline.cmd;

import org.labkey.api.pipeline.AbstractTaskFactorySettings;
import org.labkey.api.pipeline.TaskId;
import org.labkey.api.pipeline.WorkDirectory;
import org.labkey.api.util.FileType;

import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * <code>CommandTaskFactorySettings</code> may be used with Spring configuration
 * to register a <code>CommandTask</code> with the <code>PipelineJobService</code>.
*/
public class CommandTaskFactorySettings extends AbstractTaskFactorySettings
{
    private String _cloneName;
    private String _statusName;
    private String _protocolActionName;
    private Map<String, String> _environment = new HashMap<>();
    private Map<String, TaskPath> _inputPaths = new HashMap<>();
    private Map<String, TaskPath> _outputPaths = new HashMap<>();
    private ListToCommandArgs _converter = new ListToCommandArgs();
    private Boolean _copyInput;
    private Boolean _removeInput;
    private Boolean _pipeToOutput;
    private Integer _pipeOutputLineInterval;
    private Boolean _preview;
    private String _actionableInput;
    private String _installPath;
    // optional timeout in seconds
    private Integer _timeout;

    public CommandTaskFactorySettings(String name)
    {
        this(CommandTask.class, name);
    }

    public CommandTaskFactorySettings(Class namespaceClass, String name)
    {
        super(namespaceClass, name);
        _converter.setSwitchFormat(new UnixSwitchFormat());
    }

    public CommandTaskFactorySettings(TaskId id)
    {
        super(id);
        _converter.setSwitchFormat(new UnixSwitchFormat());
    }

    public TaskId getCloneId()
    {
        return new TaskId(CommandTask.class, _cloneName);
    }

    public String getCloneName()
    {
        return _cloneName;
    }

    public void setCloneName(String cloneName)
    {
        _cloneName = cloneName;
    }

    public String getStatusName()
    {
        return _statusName;
    }

    public void setStatusName(String statusName)
    {
        _statusName = statusName;
    }

    public String getProtocolActionName()
    {
        return _protocolActionName;
    }

    public void setProtocolActionName(String protocolActionName)
    {
        _protocolActionName = protocolActionName;
    }

    public void setInputType(FileType inputType)
    {
        _inputPaths.put(WorkDirectory.Function.input.toString(), new TaskPath(inputType));
    }

    public void setInputExtension(String ext)
    {
        setInputType(new FileType(ext));
    }

    public Map<String, TaskPath> getInputPaths()
    {
        return _inputPaths;
    }

    public void setInputPaths(Map<String, TaskPath> inputPaths)
    {
        // Override existing paths, but keep old paths as defaults,
        // if no override exists.
        _inputPaths.putAll(inputPaths);
    }

    public FileType getOutputType()
    {
        TaskPath tp = _outputPaths.get(WorkDirectory.Function.output.toString());
        return (tp == null ? null : tp.getType());
    }

    public void setOutputType(FileType outputType)
    {
        _outputPaths.put(WorkDirectory.Function.output.toString(), new TaskPath(outputType));
    }

    public void setOutputExtension(String ext)
    {
        setOutputType(new FileType(ext));
    }

    public Map<String, TaskPath> getOutputPaths()
    {
        return _outputPaths;
    }

    public void setOutputPaths(Map<String, TaskPath> outputPaths)
    {
        // Override existing paths, but keep old paths as defaults,
        // if no override exists.
        _outputPaths.putAll(outputPaths);
    }

    public SwitchFormat getSwitchFormat()
    {
        return _converter.getSwitchFormat();
    }

    public void setSwitchFormat(SwitchFormat switchFormat)
    {
        _converter.setSwitchFormat(switchFormat);
    }

    public ListToCommandArgs getConverter()
    {
        return _converter;
    }
    
    public List<TaskToCommandArgs> getConverters()
    {
        return _converter.getConverters();
    }

    public void setConverters(List<TaskToCommandArgs> converters)
    {
        _converter.setConverters(converters);
    }

    public boolean isCopyInput()
    {
        return isCopyInputSet() && _copyInput.booleanValue();
    }

    public boolean isCopyInputSet()
    {
        return _copyInput != null;
    }

    public void setCopyInput(boolean copyInput)
    {
        _copyInput = copyInput;
    }

    public boolean isRemoveInput()
    {
        return isRemoveInputSet() && _removeInput.booleanValue();
    }

    public boolean isRemoveInputSet()
    {
        return _removeInput != null;
    }

    public void setRemoveInput(boolean removeInput)
    {
        _removeInput = removeInput;
    }

    public boolean isPipeToOutput()
    {
        return isPipeToOutputSet() && _pipeToOutput.booleanValue();
    }

    public boolean isPipeToOutputSet()
    {
        return _pipeToOutput != null;
    }

    public void setPipeToOutput(boolean pipeToOutput)
    {
        _pipeToOutput = pipeToOutput;
    }

    public int getPipeOutputLineInterval()
    {
        return (isPipeOutputLineIntervalSet() ? 0 : _pipeOutputLineInterval.intValue());
    }

    public boolean isPipeOutputLineIntervalSet()
    {
        return _pipeOutputLineInterval == null;
    }

    public void setPipeOutputLineInterval(int pipeOutputLineInterval)
    {
        _pipeOutputLineInterval = pipeOutputLineInterval;
    }

    public boolean isPreview()
    {
        return isPreviewSet() && _preview.booleanValue();
    }

    public boolean isPreviewSet()
    {
        return _preview != null;
    }

    public void setPreview(boolean preview)
    {
        _preview = preview;
    }

    public void setActionableInput(String actionableInput)
    {
        _actionableInput = actionableInput;
    }

    public String getActionableInput()
    {
        return _actionableInput;
    }

    public void setInstallPath(String installPath)
    {
        _installPath = installPath;
    }

    public String getInstallPath()
    {
        return _installPath;
    }

    public Integer getTimeout()
    {
        return _timeout;
    }

    public void setTimeout(Integer timeout)
    {
        _timeout = timeout;
    }

    public Map<String, String> getEnvironment()
    {
        return _environment;
    }

    public void setEnvironment(Map<String, String> environment)
    {
        _environment = environment;
    }
}
