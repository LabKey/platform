package org.labkey.experiment.controllers.list;

import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.view.ViewForm;

public class NewListForm extends ViewForm
{
    public String ff_name;
    public String ff_keyType = ListDefinition.KeyType.AutoIncrementInteger.toString();
    public String ff_keyName = "Key";
    public String ff_description;

    public void setFf_name(String ff_name)
    {
        this.ff_name = ff_name;
    }

    public void setFf_keyType(String ff_keyType)
    {
        this.ff_keyType = ff_keyType;
    }

    public void setFf_keyName(String ff_keyName)
    {
        this.ff_keyName = ff_keyName;
    }

    public void setFf_description(String ff_description)
    {
        this.ff_description = ff_description;
    }
}
