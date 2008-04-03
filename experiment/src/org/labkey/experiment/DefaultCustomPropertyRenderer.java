package org.labkey.experiment;

import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * User: jeckels
 * Date: Jan 23, 2006
 */
public class DefaultCustomPropertyRenderer implements CustomPropertyRenderer
{
    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, ViewContext context)
    {
        Object o = prop.value();
        if (o == null)
        {
            return "";
        }
        return PageFlowUtil.filter(o.toString());
    }

    public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return true;
    }

    public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return PageFlowUtil.filter(prop.getName());
    }
}
