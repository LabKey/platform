package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.ehr.dataentry.AbstractFormSection;
import org.labkey.api.ehr.dataentry.FormElement;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: bimber
 * Date: 6/9/13
 * Time: 4:15 PM
 */
public class AnimalDetailsFormSection extends AbstractFormSection
{
    public AnimalDetailsFormSection()
    {
        super("AnimalDetails", "Animal Details", "ehr-animaldetailspanel");
    }

    @Override
    protected List<FormElement> getFormElements(Container c, User u)
    {
        return Collections.emptyList();
    }
}
