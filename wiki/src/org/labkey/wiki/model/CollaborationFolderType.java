package org.labkey.wiki.model;

import org.labkey.api.module.DefaultFolderType;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.AppProps;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ViewContext;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Container;
import org.labkey.wiki.WikiModule;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: Mark Igra
 * Date: Aug 4, 2006
 * Time: 3:41:26 PM
 */
public class CollaborationFolderType extends DefaultFolderType
{
    public CollaborationFolderType(WikiModule wikiModule)
    {
        super("Collaboration",
                "Build a web site for publishing and exchanging information. " +
                        "Your tools include Message Boards, Issue Trackers and Wikis. Share information within your own group, across groups or with the public by configuring user permissions.",
              null,
              Arrays.asList(new WikiModule.WikiWebPartFactory().createWebPart(),
                      new WikiModule.WikiTOCFactory().createWebPart(),
                      Portal.getPortalPart("Messages").createWebPart()),
                      getDefaultModuleSet(), null);
    }

    @Override
    public String getStartPageLabel(ViewContext ctx)
    {
       Container c = ctx.getContainer();
        if (c.equals(ContainerManager.getHomeContainer()))
            return AppProps.getInstance().getSystemShortName();
        else
            return c.getName();
    }
}
