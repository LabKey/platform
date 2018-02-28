package org.labkey.api.view;

public interface DismissibleWarningMessage
{
    default boolean showMessage(ViewContext viewContext)
    {
        return false;
    }

    String getMessageHtml(ViewContext viewContext);
}
