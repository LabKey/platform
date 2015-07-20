/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.di.TaskRefTask;
import org.labkey.etl.xml.SettingType;
import org.labkey.etl.xml.TaskRefType;
import org.labkey.etl.xml.TransformType;

import java.util.Map;
/**
 * User: tgaluhn
 * Date: 7/21/2014
 */
public class TaskRefTransformStepMeta extends StepMetaImpl
{
    public static final String TASKREF_CLASS_NOT_FOUND = "TaskRef class not found: ";
    public static final String TASKREF_CLASS_MUST_IMPLEMENT_INTERFACE = "TaskRef class must implement interface: ";
    public static final String TASKREF_CLASS_INSTANTIATION_EXCEPTION = "Exception instantiating taskRef class ";
    public static final String TASKREF_MISSING_REQUIRED_SETTING = "TaskRef missing required setting(s):";
    private TaskRefTask taskInstance = null;
    private String taskClassName = "";

    @Override
    public String toString()
    {
        return TaskRefTask.class.getSimpleName() + " " + taskClassName;
    }

    @Override
    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        // TODO: Possible, also pass global etl filter strategy info into task.
        TaskRefType taskref = transformXML.getTaskref();
        taskClassName = taskref.getRef();
        Class taskClass;
        try
        {
            taskClass = Class.forName(taskClassName);
        }
        catch (ClassNotFoundException e)
        {
            throw new XmlException(TASKREF_CLASS_NOT_FOUND + taskClassName);
        }

        Map<String, String> xmlSettings = new CaseInsensitiveHashMap<>();
        if (taskref.getSettings() != null)
        {
            for (SettingType setting : taskref.getSettings().getSettingArray())
            {
                xmlSettings.put(setting.getName(), setting.getValue());
            }
        }

        initializeClass(taskClass, xmlSettings);
    }

    private void initializeClass(Class taskClass, Map<String, String> xmlSettings) throws XmlException
    {
        try
        {
            Object taskObject = taskClass.newInstance();
            if (!(taskObject instanceof TaskRefTask))
                throw new XmlException(TASKREF_CLASS_MUST_IMPLEMENT_INTERFACE + taskClassName + " does not implement " + TaskRefTask.class.getName());
            taskInstance = (TaskRefTask)taskObject;
        }
        catch (InstantiationException | IllegalAccessException e)
        {
            throw new XmlException(TASKREF_CLASS_INSTANTIATION_EXCEPTION + taskClassName, e);
        }
        taskInstance.setSettings(xmlSettings);
    }

    public TaskRefTask getTaskInstance()
    {
        return taskInstance;
    }

    @Override
    public boolean isUseSource()
    {
        return false;
    }
}
