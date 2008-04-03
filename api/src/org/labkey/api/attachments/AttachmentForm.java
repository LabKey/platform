package org.labkey.api.attachments;

import org.labkey.api.view.ViewForm;

/**
 * User: adam
* Date: Jan 3, 2007
* Time: 3:41:22 PM
*/
public class AttachmentForm extends ViewForm
{
    private String _entityId = null;
    private String _name = null;


    public String getEntityId()
    {
        return _entityId;
    }


    public void setEntityId(String entityId)
    {
        this._entityId = entityId;
    }


    public String getName()
    {
        return _name;
    }


    public void setName(String name)
    {
        this._name = name;
    }
}
