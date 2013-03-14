package org.labkey.study.model;

import org.labkey.api.data.Entity;

/**
 * User: Nick Arnold
 * Date: 3/13/13
 */
public class ExtendedSpecimenRequestView extends Entity
{
    private boolean _active;
    private String _body;

    public static ExtendedSpecimenRequestView createView(String body)
    {
        ExtendedSpecimenRequestView view = new ExtendedSpecimenRequestView();
        view.setBody(body);
        view.setActive(true);
        return view;
    }

    public String getBody()
    {
        return _body;
    }

    public void setBody(String body)
    {
        _body = body;
    }

    public boolean isActive()
    {
        return _active;
    }

    public void setActive(boolean active)
    {
        _active = active;
    }
}
