package org.labkey.api.view.template;

import org.labkey.api.view.JspView;

import java.util.List;
import java.util.Map;

/**
 * User: adam
 * Date: Nov 29, 2007
 * Time: 9:51:54 AM
 */
public class TemplateHeaderView extends JspView<TemplateHeaderView.TemplateHeaderBean>
{
    public TemplateHeaderView(List<String> containerLinks, String upgradeMessage, Map<String, Throwable> moduleErrors, PageConfig page)
    {
        super("/org/labkey/api/view/template/header.jsp", new TemplateHeaderBean(containerLinks, upgradeMessage, moduleErrors, page));
    }

    public TemplateHeaderView(PageConfig page)
    {
        this(null, null, null, page);
    }

    public static class TemplateHeaderBean
    {
        public List<String> containerLinks;
        public String upgradeMessage;
        public Map<String, Throwable> moduleFailures;
        public PageConfig pageConfig;

        private TemplateHeaderBean(List<String> containerLinks, String upgradeMessage, Map<String, Throwable> moduleFailures, PageConfig page)
        {
            this.containerLinks = containerLinks;
            this.upgradeMessage = upgradeMessage;
            this.moduleFailures = moduleFailures;
            this.pageConfig = page;
        }
    }
}
