package org.labkey.specimen.actions;

import org.labkey.api.specimen.location.LocationImpl;
import org.labkey.api.view.ActionURL;

public class SelectSpecimenProviderBean
{
    private final HiddenFormInputGenerator _sourceForm;
    private final LocationImpl[] _possibleLocations;
    private final ActionURL _formTarget;

    public SelectSpecimenProviderBean(HiddenFormInputGenerator sourceForm, LocationImpl[] possibleLocations, ActionURL formTarget)
    {
        _sourceForm = sourceForm;
        _possibleLocations = possibleLocations;
        _formTarget = formTarget;
    }

    public LocationImpl[] getPossibleLocations()
    {
        return _possibleLocations;
    }

    public ActionURL getFormTarget()
    {
        return _formTarget;
    }

    public HiddenFormInputGenerator getSourceForm()
    {
        return _sourceForm;
    }
}
