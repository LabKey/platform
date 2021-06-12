package org.labkey.specimen.actions;

import org.labkey.api.view.ViewContext;

public interface HiddenFormInputGenerator
{
    String getHiddenFormInputs(ViewContext ctx);
}
