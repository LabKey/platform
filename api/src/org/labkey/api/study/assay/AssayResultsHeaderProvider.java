package org.labkey.api.study.assay;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ActionButton;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.List;

public interface AssayResultsHeaderProvider
{
    @NotNull
    default List<ActionButton> getButtons(AssayProvider provider, ExpProtocol protocol, ViewContext viewContext, String runId)
    {
        return Collections.emptyList();
    }
}
