package org.labkey.issue;

import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.view.ViewContext;

/**
 * Created by klum on 5/6/2016.
 */
public interface NewColumnType extends ColumnType
{
    DisplayColumn getRenderer(ViewContext context);
}
