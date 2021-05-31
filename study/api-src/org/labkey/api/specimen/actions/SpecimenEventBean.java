package org.labkey.api.specimen.actions;

import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.specimen.Vial;

public class SpecimenEventBean extends ReturnUrlForm
{
    private Vial _vial;

    public SpecimenEventBean(Vial vial, String returnUrl)
    {
        _vial = vial;
        setReturnUrl(returnUrl);
    }

    public Vial getVial()
    {
        return _vial;
    }

    public void setVial(Vial vial)
    {
        _vial = vial;
    }
}
