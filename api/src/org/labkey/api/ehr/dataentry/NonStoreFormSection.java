package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.view.template.ClientDependency;

import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 1/11/14
 * Time: 6:40 AM
 */
public class NonStoreFormSection extends AbstractFormSection
{
    public NonStoreFormSection(String name, String label, String xtype)
    {
        super(name, label, xtype);
    }

    public NonStoreFormSection(String name, String label, String xtype, List<ClientDependency> dependencies)
    {
        super(name, label, xtype);

        if (dependencies != null)
        {
            for (ClientDependency cd : dependencies)
            {
                addClientDependency(cd);
            }
        }
    }

    @Override
    protected List<FormElement> getFormElements(DataEntryFormContext ctx)
    {
        return Collections.emptyList();
    }

    @Override
    public JSONObject toJSON(DataEntryFormContext ctx)
    {
        JSONObject ret = super.toJSON(ctx);
        ret.put("supportsTemplates", false);

        return ret;
    }
}
