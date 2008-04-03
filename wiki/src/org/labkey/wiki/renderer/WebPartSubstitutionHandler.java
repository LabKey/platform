package org.labkey.wiki.renderer;

import org.labkey.api.view.*;
import org.labkey.api.util.CaseInsensitiveHashMap;
import org.labkey.api.wiki.FormattedHtml;

import java.io.StringWriter;
import java.util.Map;
import java.util.Stack;

/**
 * User: adam
 * Date: Jun 27, 2007
 * Time: 2:53:12 AM
 */
public class WebPartSubstitutionHandler implements HtmlRenderer.SubstitutionHandler
{
    private static ThreadLocal<Stack<Map>> _paramsStack = new ThreadLocal<Stack<Map>>()
    {
        @Override
        protected Stack<Map> initialValue()
        {
            return new Stack<Map>();
        }
    };


    public FormattedHtml getSubstitution(Map<String, String> params)
    {
        params = new CaseInsensitiveHashMap<String>(params);
        Stack<Map> stack = _paramsStack.get();

        if (stack.contains(params))
            return new FormattedHtml("<br><font class='error' color='red'>Error: recursive rendering</font>");

        stack.push(params);

        try
        {
            String partName = params.get("partName");
            WebPartFactory desc = Portal.getPortalPartCaseInsensitive(partName);

            if (null == desc)
                return new FormattedHtml("<br><font class='error' color='red'>Error: Could not find webpart \"" + partName + "\"</font>");

            String partLocation = params.get("location");

            Portal.WebPart part = new Portal.WebPart();
            part.setName(partName);
            if (partLocation != null)
                part.setLocation(partLocation);
            part.getPropertyMap().putAll(params);

            WebPartView view = null;
            try
            {
                view = desc.getWebPartViewSafe(HttpView.currentContext(), part);
                view.setEmbedded(true);  // Let the webpart know it's being embedded in another page

                String showFrame = params.get("showFrame");

                if (null != showFrame && !Boolean.parseBoolean(showFrame))
                    view.setFrame(WebPartView.FrameType.NONE);
            }
            catch (Exception e)
            {
                //
            }

            if (null == view)
                return null;

            view.addAllObjects(params);
            StringWriter sw = new StringWriter();

            try
            {
                view.include(view, sw);
            }
            catch (Exception e)
            {
                return null;
            }

            return new FormattedHtml(sw.toString(), true);  // All webparts are considered volatile... CONSIDER: Be more selective (e.g., query & messages, but not search) 
        }
        finally
        {
            Map m = stack.pop();
            assert m == params : "Stack problem while checking for recursive webpart rendering: popped params didn't match the params that were pushed";
        }
    }
}
