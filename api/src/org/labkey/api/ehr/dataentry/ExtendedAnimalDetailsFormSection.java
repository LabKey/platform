package org.labkey.api.ehr.dataentry;

import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 6/9/13
 * Time: 4:15 PM
 */
public class ExtendedAnimalDetailsFormSection extends AbstractFormSection
{
    public ExtendedAnimalDetailsFormSection()
    {
        super("AnimalDetails", "Animal Details", "ehr-animaldetailsextendedpanel");
    }

    @Override
    protected List<FormElement> getFormElements(DataEntryFormContext ctx)
    {
        return Collections.emptyList();
    }
}
