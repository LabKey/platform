package org.labkey.experiment.api;

import org.labkey.api.exp.IdentifiableBase;

/**
 * User: jeckels
 * Date: Sep 26, 2007
 */
public abstract class ExpIdentifiableBaseImpl<Type extends IdentifiableBase> extends ExpObjectImpl
{
    protected Type _object;
    
    public ExpIdentifiableBaseImpl(Type object)
    {
        _object = object;
    }

    public String getLSID()
    {
        return _object.getLSID();
    }

    public Type getDataObject()
    {
        return _object;
    }

    public void setLSID(String lsid)
    {
        _object.setLSID(lsid);
    }

    public String getName()
    {
        return _object.getName();
    }

    public void setName(String name)
    {
        _object.setName(name);
    }



    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        ExpIdentifiableBaseImpl that = (ExpIdentifiableBaseImpl) o;

        if (_object.getLSID() != null ? !_object.getLSID().equals(that._object.getLSID()) : that._object.getLSID() != null) return false;

        return true;
    }

    public int hashCode()
    {
        int result = super.hashCode();
        result = 31 * result + (_object.getLSID() != null ? _object.getLSID().hashCode() : 0);
        return result;
    }
}
