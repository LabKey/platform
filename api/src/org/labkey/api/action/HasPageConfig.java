package org.labkey.api.action;

import org.labkey.api.view.template.PageConfig;

/**
 * Created by IntelliJ IDEA.
 * User: matthewb
 * Date: May 31, 2007
 * Time: 4:20:23 PM
 */
public interface HasPageConfig
{
    void setPageConfig(PageConfig page);
    PageConfig getPageConfig();
}
