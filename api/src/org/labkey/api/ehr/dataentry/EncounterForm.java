package org.labkey.api.ehr.dataentry;

import org.labkey.api.ehr.security.EHRInProgressInsertPermission;
import org.labkey.api.module.Module;
import org.labkey.api.security.permissions.Permission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 4/27/13
 * Time: 12:45 PM
 */
public class EncounterForm extends TaskForm
{
    protected EncounterForm(Module owner, String name, String label, String category, List<FormSection> sections)
    {
        super(owner, name, label, category, sections);
        setStoreCollectionClass("EHR.data.EncounterStoreCollection");

        for (FormSection s : getFormSections())
        {
            s.addConfigSource("Encounter");
        }
    }

    public static EncounterForm create(Module owner, String category, String name, String label, List<FormSection> formSections)
    {
        List<FormSection> sections = new ArrayList<FormSection>();
        sections.add(new TaskFormSection());
        sections.add(new EncounterFormSection());
        sections.add(new AnimalDetailsFormSection());
        sections.addAll(formSections);

        return new EncounterForm(owner, name, label, category, sections);
    }

    @Override
    protected List<Class<? extends Permission>> getAvailabilityPermissions()
    {
        return Collections.<Class<? extends Permission>>singletonList(EHRInProgressInsertPermission.class);
    }
}
