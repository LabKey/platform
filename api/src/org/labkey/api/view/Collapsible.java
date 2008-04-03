package org.labkey.api.view;

/**
 * User: brittp
 * Date: Apr 10, 2007
 * Time: 1:54:56 PM
 */
public interface Collapsible
{
    void setCollapsed(boolean collapsed);
    boolean isCollapsed();
    Collapsible[] getChildren();
    Collapsible findSubtree(String path);
    String getId();
}
