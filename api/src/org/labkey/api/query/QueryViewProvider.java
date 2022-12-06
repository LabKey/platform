package org.labkey.api.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.view.ViewContext;
import org.springframework.validation.BindException;

public interface QueryViewProvider<FormType>
{
    String getDataRegionName();

    @Nullable
    QueryView createView(ViewContext viewContext, FormType form, BindException errors);
}
