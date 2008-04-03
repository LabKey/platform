package org.labkey.experiment;

import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.experiment.controllers.exp.ExperimentController;

import java.util.List;
import java.net.URL;
import java.net.MalformedURLException;

/**
 * User: jeckels
 * Date: Jan 23, 2006
 */
public class ExternalDocsURLCustomPropertyRenderer implements CustomPropertyRenderer
{
    public static final String URI = "terms.fhcrc.org#ExternalDocumentation.href";

    public boolean shouldRender(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return true;
    }

    public String getDescription(ObjectProperty prop, List<ObjectProperty> siblingProperties)
    {
        return "External documentation";
    }

    public String getValue(ObjectProperty prop, List<ObjectProperty> siblingProperties, ViewContext context)
    {
        String label = null;
        for (ObjectProperty p : siblingProperties)
        {
            if (ExternalDocsLabelCustomPropertyRenderer.URI.equals(p.getPropertyURI()))
            {
                label = p.getStringValue();
            }
        }
        if (label == null)
        {
            label = prop.getStringValue();
        }
        String link = prop.getStringValue();
        try
        {
            URL url = new URL(prop.getStringValue());
            if (url.getProtocol().equals("file"))
            {
                ActionURL h = new ActionURL(ExperimentController.ShowExternalDocsAction.class, context.getContainer());
                h.addParameter("objectURI", prop.getObjectURI());
                h.addParameter("propertyURI", prop.getPropertyURI());
                link = h.toString();
            }
        }
        catch (MalformedURLException e)
        {
            // That's OK, we won't try to do anything with the link
        }
        return "<a href=\"" + link + "\">" + PageFlowUtil.filter(label) + "</a>";
    }
}
