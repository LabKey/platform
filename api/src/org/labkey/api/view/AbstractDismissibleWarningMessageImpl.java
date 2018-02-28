package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;

public abstract class AbstractDismissibleWarningMessageImpl implements DismissibleWarningMessage
{
    //Use with String.format(DISMISSAL_SCRIPT_FORMAT, "dismissMyWarning", PageFlowUtil.jsString(dismissURL), myMessageText, myId);
    protected final static String DISMISSAL_SCRIPT_FORMAT = "<script type=\"text/javascript\">\n" +
            "                (function($) {\n" +
            "                    function %1$s() {\n" +
            "                        var config = {\n" +
            "                            url: %2$s,\n" +
            "                            method: 'POST',\n" +
            "                            success: function () {$(\".lk-dismissable-warn\").hide()},\n" +
            "                            failure: LABKEY.Utils.displayAjaxErrorResponse\n" +
            "                        };\n" +
            "                        LABKEY.Ajax.request(config); \n" +
            "                        return false;\n" +
            "                    }\n" +
            "                   $('body').on('click', 'a.lk-dismissable-warn-close', function() {\n" +
            "                       %1$s();\n" +
            "                   });" +
            "                })(jQuery);\n" +
            "            </script>\n" +
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
        return getMessageHtml(viewContext, "dismissMessage", "dismissableBanner");
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
}
