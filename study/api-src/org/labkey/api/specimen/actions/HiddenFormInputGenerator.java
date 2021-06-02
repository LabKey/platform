package org.labkey.api.specimen.actions;

import org.labkey.api.view.ViewContext;

public interface HiddenFormInputGenerator
{
    String getHiddenFormInputs(ViewContext ctx);
}
