package org.labkey.experiment;

import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.view.ViewContext;

import java.util.List;

/**
 * User: jeckels
 * Date: Jan 23, 2006
 */
public class ExternalDocsLabelCustomPropertyRenderer implements CustomPropertyRenderer
{
    public static final String URI = "terms.fhcrc.org#ExternalDocumentation.label";

    public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        throw new UnsupportedOperationException();
    }

    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, ViewContext context)
    {
        throw new UnsupportedOperationException();
    }

    public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return false;
    }
}
