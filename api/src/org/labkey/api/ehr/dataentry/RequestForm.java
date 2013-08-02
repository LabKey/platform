package org.labkey.api.ehr.dataentry;

import org.labkey.api.module.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 12:45 PM
 */
public class RequestForm extends AbstractDataEntryForm
{
    protected RequestForm(Module owner, String name, String label, String category, List<FormSection> sections)
    {
        super(owner, name, label, category, sections);
        setJavascriptClass("EHR.panel.RequestDataEntryPanel");
        setStoreCollectionClass("EHR.data.RequestStoreCollection");

        for (FormSection s : getFormSections())
        {
            s.addConfigSource("Request");
        }
    }

    public static RequestForm create(Module owner, String category, String name, String label, List<FormSection> formSections)
    {
        List<FormSection> sections = new ArrayList<FormSection>();
        sections.add(new RequestFormSection());
        sections.add(new AnimalDetailsFormSection());
        sections.addAll(formSections);

        return new RequestForm(owner, name, label, category, sections);
    }

    @Override
    protected List<String> getButtonConfigs()
    {
        List<String> defaultButtons = new ArrayList<String>();
        defaultButtons.add("DISCARD");
        defaultButtons.add("REQUEST");
        defaultButtons.add("APPROVE");

        return defaultButtons;
    }

    @Override
    protected List<String> getMoreActionButtonConfigs()
    {
        return Collections.emptyList();
    }
}
