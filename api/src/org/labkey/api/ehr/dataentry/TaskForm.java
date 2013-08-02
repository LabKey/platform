package org.labkey.api.ehr.dataentry;

import org.labkey.api.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 12:45 PM
 */
public class TaskForm extends AbstractDataEntryForm
{
    protected TaskForm(Module owner, String name, String label, String category, List<FormSection> sections)
    {
        super(owner, name, label, category, sections);
        setJavascriptClass("EHR.panel.TaskDataEntryPanel");
        setStoreCollectionClass("EHR.data.TaskStoreCollection");

        for (FormSection s : getFormSections())
        {
            s.addConfigSource("Task");
        }
    }

    public static TaskForm create(Module owner, String category, String name, String label, List<FormSection> formSections)
    {
        List<FormSection> sections = new ArrayList<FormSection>();
        sections.add(new TaskFormSection());
        sections.add(new AnimalDetailsFormSection());
        sections.addAll(formSections);

        return new TaskForm(owner, name, label, category, sections);
    }
}
