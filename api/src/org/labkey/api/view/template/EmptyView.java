package org.labkey.api.view.template;

import org.labkey.api.view.HttpView;

import java.io.PrintWriter;

/**
 * User: adam
 * Date: May 1, 2007
 * Time: 1:06:15 PM
 */

// Do nothing view... useful for testing templates, etc.
public class EmptyView extends HttpView
{
    @Override
    protected void renderInternal(Object model, PrintWriter out) throws Exception
    {
    }
}
