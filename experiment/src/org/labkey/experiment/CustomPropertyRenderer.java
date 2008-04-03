package org.labkey.experiment;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.exp.ObjectProperty;

import java.util.List;

/**
 * User: jeckels
 * Date: Jan 23, 2006
 */
public interface CustomPropertyRenderer
{
    public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties);

    public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties);

    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, ViewContext context);
}
