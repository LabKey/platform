/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.Warnings;

public abstract class AbstractDismissibleWarningMessageImpl implements DismissibleWarningMessage, WarningProvider
{
    public static final String JS_METHOD_NAME = "dismissMessage";
    public static final String BANNER_ID = "dismissableBanner";

    //Use with String.format(DISMISSAL_SCRIPT_FORMAT, "dismissMyWarning", PageFlowUtil.jsString(dismissURL), myMessageText, myId);
    protected final static String DISMISSAL_SCRIPT_FORMAT =
        "<script type=\"text/javascript\">\n" +
        "    (function($) {\n" +
        "        function %1$s() {\n" +
        "            var config = {\n" +
        "                url: %2$s,\n" +
        "                method: 'POST',\n" +
        "                success: function () {$(\".lk-dismissable-warn\").hide()},\n" +
        "                failure: LABKEY.Utils.displayAjaxErrorResponse\n" +
        "            };\n" +
        "            LABKEY.Ajax.request(config); \n" +
        "            return false;\n" +
        "        }\n" +
        "       $('body').on('click', 'a.lk-dismissable-warn-close', function() {\n" +
        "           %1$s();\n" +
        "       });" +
        "    })(jQuery);\n" +
        "</script>\n" +
        "<div id=\"%4$s\">%3$s</div>";

    /**
     * Get the HTML string for the ribbon
     *
     * @param viewContext for ribbon message
     * @return String of HTML containing the ribbon div/container & text, and the js-script element to hide it
     */
    protected @Nullable String getMessageHtml(ViewContext viewContext, String jsDismissalMethodName, String bannerId)
    {
        String msg = getMessageText(viewContext);
        if (StringUtils.isBlank(msg))
            return null;

        String dismissURL = getDismissActionUrl(viewContext);
        return String.format(DISMISSAL_SCRIPT_FORMAT, jsDismissalMethodName, PageFlowUtil.jsString(dismissURL), msg, bannerId);
    }

    /**
     * Get the HTML string for the ribbon using default names
     *
     * @param viewContext for ribbon message
     * @return String of HTML containing the ribbon div/container & text, and the js-script element to hide it
     */
    public @Nullable String getMessageHtml(ViewContext viewContext)
    {
        return getMessageHtml(viewContext, getJsDismissalMethodName(), getBannerId());
    }

    protected String getJsDismissalMethodName()
    {
        return JS_METHOD_NAME;
    }

    protected String getBannerId()
    {
        return BANNER_ID;
    }

    /**
     * Get the URL string for the action to take to dismiss the ribbon message
     * @param viewContext to update
     * @return success or failure of the update
     */
    protected abstract String getDismissActionUrl(ViewContext viewContext);

    /**
     * Get the ribbon contents to display.
     * @param viewContext of the ribbon
     * @return the text to display
     */
    protected abstract String getMessageText(ViewContext viewContext);

    @Override
    public void addDismissibleWarnings(Warnings warnings, ViewContext context)
    {
        if (showMessage(context))
        {
            String messageHtml = getMessageHtml(context);
            if (!StringUtils.isEmpty(messageHtml))
                warnings.add(messageHtml);
        }
    }

}
