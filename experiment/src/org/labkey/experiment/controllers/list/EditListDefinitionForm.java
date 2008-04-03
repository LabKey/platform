package org.labkey.experiment.controllers.list;

import org.apache.struts.action.ActionMapping;
import org.labkey.api.exp.list.ListDefinition;

import javax.servlet.http.HttpServletRequest;

public class EditListDefinitionForm extends ListDefinitionForm
{
    public String ff_keyName;
    public String ff_description;
    public String ff_titleColumn;
    public ListDefinition.DiscussionSetting ff_discussionSetting;
    public boolean ff_allowDelete;
    public boolean ff_allowUpload;
    public boolean ff_allowExport;

    public void reset(ActionMapping actionMapping, HttpServletRequest request)
    {
        super.reset(actionMapping, request);
        ff_keyName = getList().getKeyName();
        ff_description = getList().getDescription();
        ff_discussionSetting = getList().getDiscussionSetting();

        // Because of funky checkbox behavior, always assume false in post case
        ff_allowDelete = false;
        ff_allowUpload = false;
        ff_allowExport = false;
    }

    public void setDefaults()
    {
        ff_allowDelete = getList().getAllowDelete();
        ff_allowUpload = getList().getAllowUpload();
        ff_allowExport = getList().getAllowExport();
    }

    public void setFf_keyName(String name)
    {
        this.ff_keyName = name;
    }

    public void setFf_description(String description)
    {
        this.ff_description = description;
    }

    public void setFf_titleColumn(String ff_titleColumn)
    {
        this.ff_titleColumn = ff_titleColumn;
    }

    public void setFf_discussionSetting(int value)
    {
        this.ff_discussionSetting = ListDefinition.DiscussionSetting.getForValue(value);
    }

    public void setFf_allowDelete(boolean allowDelete)
    {
        ff_allowDelete = allowDelete;
    }

    public void setFf_allowUpload(boolean ff_allowUpload)
    {
        this.ff_allowUpload = ff_allowUpload;
    }

    public void setFf_allowExport(boolean ff_allowExport)
    {
        this.ff_allowExport = ff_allowExport;
    }
}
