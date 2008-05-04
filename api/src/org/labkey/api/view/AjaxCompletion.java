package org.labkey.api.view;

import org.labkey.common.util.Pair;

/**
 * User: adam
 * Date: Sep 23, 2007
 * Time: 5:43:55 PM
 */
public final class AjaxCompletion extends Pair<String, String>
{
    public AjaxCompletion(String display, String insert)
    {
        super(display,insert);
    }

    public AjaxCompletion(String displayAndInsert)
    {
        this(displayAndInsert, displayAndInsert);
    }

    public String getDisplayText()
    {
        return getKey();
    }

    public String getInsertionText()
    {
        return getValue();
    }
}