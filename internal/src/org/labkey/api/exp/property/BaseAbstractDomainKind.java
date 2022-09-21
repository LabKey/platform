package org.labkey.api.exp.property;

import org.json.old.JSONObject;

public abstract class BaseAbstractDomainKind extends AbstractDomainKind<JSONObject>
{
    @Override
    public Class<? extends JSONObject> getTypeClass()
    {
        return JSONObject.class;
    }
}