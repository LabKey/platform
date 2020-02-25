package org.labkey.api.exp.property;

import org.json.JSONObject;

public abstract class BaseAbstractDomainKind<T> extends AbstractDomainKind<JSONObject>
{
    @Override
    public Class<? extends JSONObject> getTypeClass()
    {
        return JSONObject.class;
    }
}
