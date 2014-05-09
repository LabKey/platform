package org.labkey.api.ehr.dataentry;

import org.json.JSONObject;
import org.labkey.api.module.Module;
import org.labkey.api.view.template.ClientDependency;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**

 */
public class BulkEditFormType extends AbstractDataEntryForm
{
    private String _keyField;

    protected BulkEditFormType(DataEntryFormContext ctx, Module owner, String name, String label, String category, String keyField, List<FormSection> sections)
    {
        super(ctx, owner, name, label, category, sections);
        setJavascriptClass("EHR.panel.BulkEditDataEntryPanel");

        _keyField = keyField;

        addClientDependency(ClientDependency.fromFilePath("ehr/panel/BulkEditDataEntryPanel.js"));
    }

    public static BulkEditFormType create(DataEntryFormContext ctx, Module owner, String name, String title, String keyField, FormSection section)
    {
        return new BulkEditFormType(ctx, owner, name, title, "BulkEdit", keyField, Arrays.asList(section));
    }

    @Override
    public JSONObject toJSON()
    {
        JSONObject json = super.toJSON();
        json.put("keyField", _keyField);

        return json;
    }

    @Override
    protected List<String> getButtonConfigs()
    {
        return Collections.singletonList("BASICSUBMIT");
    }

    @Override
    protected List<String> getMoreActionButtonConfigs()
    {
        return Collections.emptyList();
    }

    @Override
    public boolean isVisible()
    {
        return false;
    }
}
