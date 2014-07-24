package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.di.TaskrefTask;
import org.labkey.etl.xml.SettingType;
import org.labkey.etl.xml.TaskRefType;
import org.labkey.etl.xml.TransformType;

import java.util.Map;
/**
 * User: tgaluhn
 * Date: 7/21/2014
 */
public class TaskrefTransformStepMeta extends StepMetaImpl
{
    private TaskrefTask taskInstance = null;
    private String taskClassName = "";

    @Override
    public String toString()
    {
        return TaskrefTask.class.getSimpleName() + " " + taskClassName;
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
            throw new XmlException("Taskref class not found: " + taskClassName);
        }

        Map<String, String> xmlSettings = new CaseInsensitiveHashMap<>();
        for (SettingType setting : taskref.getSettings().getSettingArray())
        {
            xmlSettings.put(setting.getName(), setting.getValue());
        }

        initializeClass(taskClass, xmlSettings);
    }

    private void initializeClass(Class taskClass, Map<String, String> xmlSettings) throws XmlException
    {
        try
        {
            Object taskObject = taskClass.newInstance();
            if (!(taskObject instanceof TaskrefTask))
                throw new XmlException("Taskref class " + taskClass.getName() + " must implement " + TaskrefTask.class.getName());
            taskInstance = (TaskrefTask)taskObject;
        }
        catch (InstantiationException | IllegalAccessException e)
        {
            throw new XmlException("Exception instantiating taskref class " + taskClass.getName(), e);
        }

        validateSettings(xmlSettings);

        taskInstance.setSettings(xmlSettings);
    }

    private void validateSettings(Map<String, String> xmlSettings) throws XmlException
    {
        StringBuilder sb = new StringBuilder();
        for (String requiredSetting : taskInstance.getRequiredSettings())
        {
            if (!xmlSettings.containsKey(requiredSetting))
            {
                if (sb.length() == 0)
                    sb.append("Taskref missing required setting(s):\n");
                sb.append(requiredSetting).append("\n");
            }
        }
        if (sb.length() > 0)
            throw new XmlException(sb.toString());
    }

    public TaskrefTask getTaskInstance()
    {
        return taskInstance;
    }

    @Override
    public boolean isUseSource()
    {
        return false;
    }
}
