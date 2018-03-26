package org.labkey.api.view.template;

import org.labkey.api.view.ViewContext;

public interface WarningProvider
{
    // Add warnings for conditions that will never change while the server is running (e.g., size of JVM heap or Tomcat version).
    // These are displayed to site administrators only.
    default void addStaticWarnings(Warnings warnings)
    {
    }

    // Add warnings based on the current context (folder, user, page, etc.)
    default void addWarnings(Warnings warnings, ViewContext context)
    {
    }

    // Add dismissible warnings based on the current context (folder, user, page, etc.)
    default void addDismissibleWarnings(Warnings warnings, ViewContext context)
    {
    }
}
