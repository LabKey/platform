/*
 * Copyright (c) 2009-2011 LabKey Corporation
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
package org.labkey.api.view.template;

import org.apache.commons.lang.ObjectUtils;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/*
* User: Mark Igra
* Date: Dec 31, 2008
* Time: 3:35:12 PM
*/
public class AppBar extends NavTree
{
    private String _pageTitle;
    private List<NavTree> _navTrail;

    public AppBar(String folderTitle, ActionURL titleURL, NavTree... buttons)
    {
        this(folderTitle, titleURL, Arrays.asList(buttons));
    }

    public AppBar(String folderTitle, ActionURL titleURL, List<NavTree> buttons)
    {
        super(folderTitle, titleURL);
        addChildren(buttons);
    }

    public String getFolderTitle()
    {
        return getKey();
    }

    public NavTree[] getButtons()
    {
        return getChildren();
    }

    public void setPageTitle(String pageTitle)
    {
        _pageTitle = pageTitle;
    }

    public String getPageTitle()
    {
        return _pageTitle;     
    }

    public NavTree getSelected()
    {
        for (NavTree button : getButtons())
            if (button.isSelected())
                return button;

        return null;
    }

    public List<NavTree> getNavTrail()
    {
        return _navTrail;
    }

    public List<NavTree> setNavTrail(List<NavTree> navTrail, ActionURL actionURL)
    {
        _navTrail = fixCrumbTrail(navTrail, actionURL);
        return _navTrail;
    }

//    // Keys for stashing away navtrails to use for nested modules
//    private static final String PARENT_TRAIL_INFO = HomeTemplate.class.getName() + ".PARENT_TRAIL_INFO";
//
//    private static class ParentTrailInfo
//    {
//        ActionURL url;
//        List<NavTree> links;
//
//        public ParentTrailInfo(ActionURL url, List<NavTree> links)
//        {
//            this.url = url;
//            this.links = links;
//        }
//    }

//    List<NavTree> navTrail = page.getNavTrail();
//    List<NavTree> extraChildren = new ArrayList<NavTree>();
//    ActionURL url = context.getActionURL();
//    String pageFlow = url.getPageFlow();
//    Module curModule = page.getModuleOwner();
//    if (curModule == null)
//        curModule = ModuleLoader.getInstance().getModuleForController(pageFlow);
//    NavTree[] trailExtras = null == navTrail ? new NavTree[0] : navTrail.toArray(new NavTree[navTrail.size()]);
//
//    boolean singleTabFolder = FolderType.NONE.equals(folderType) && context.getContainer().getActiveModules().size() == 1;
//    //If this is an old tabbed folder just show tabs, unless there's a single "tab" which we hide
//    if (FolderType.NONE.equals(folderType) && (!singleTabFolder || curModule.equals(container.getDefaultModule())))
//    {
//        extraChildren.addAll(Arrays.asList(trailExtras));
//    }
//    else   //Glue together a navtrail since we're not in default module and are not showing tabs
//    {
//        ActionURL ownerStartUrl;
//        String startPageLabel;
//        if (singleTabFolder)
//        {
//            startPageLabel = container.equals(ContainerManager.getHomeContainer()) ? LookAndFeelProperties.getInstance(container).getShortName() : container.getName();
//            ownerStartUrl = container.getDefaultModule().getTabURL(container, context.getUser());
//        }
//        else
//        {
//            startPageLabel =  folderType.getStartPageLabel(context);
//            ownerStartUrl = folderType.getStartURL(context.getContainer(), context.getUser());
//        }
//        boolean atStart = equalBaseUrls(url, ownerStartUrl);
//
//        if (!atStart)
//            extraChildren.add(new NavTree(startPageLabel, ownerStartUrl));
//
//        //No extra children at the top...
//        if (!atStart)
//        {
//            //If we are in the default module, trust any passed in trails (except use folder's dashboard link from above)
//            // assume length == 1 is title only, length > 1 means root,...,title
//            if (curModule.equals(folderType.getDefaultModule()))
//            {
//                if (trailExtras.length == 1)
//                {
//                    extraChildren.addAll(Arrays.asList(trailExtras));
//                }
//                else if (trailExtras.length > 1)
//                {
//                    extraChildren.addAll(Arrays.asList(trailExtras).subList(1, trailExtras.length));
//                }
//
//                //Stash away the current URL & trailExtras so that if we use nested module we can
//                //know what parent trail should be. Use the page title as the last link with special
//                //handling if non-link is in the navTrail (should get rid of these)
//                //But don't ever store post urls cause they won't work...
//                if (!"POST".equalsIgnoreCase(getViewContext().getRequest().getMethod()))
//                {
//                    List<NavTree> saveChildren = new ArrayList<NavTree>(extraChildren);
//                    NavTree lastChild = extraChildren.get(extraChildren.size() - 1);
//                    if (null == lastChild.second)
//                        saveChildren.set(saveChildren.size() - 1, new NavTree(lastChild.getKey(), url));
//                    context.getRequest().getSession().setAttribute(PARENT_TRAIL_INFO, new ParentTrailInfo(url, saveChildren));
//                }
//            }
//            else //In a "services" module. Add its links below the dashboard.
//            {
//                //If we have stashed away the parent's trail info AND it looks like it is right, use it
//                ParentTrailInfo pti = (ParentTrailInfo) context.getRequest().getSession().getAttribute(PARENT_TRAIL_INFO);
//                if (null != pti && pti.url.getExtraPath().equals(url.getExtraPath()))
//                    extraChildren = new ArrayList<NavTree>(pti.links);
//                extraChildren.addAll(Arrays.asList(trailExtras));
//            }
//        }
//        else
//        {
//            context.getRequest().getSession().removeAttribute(PARENT_TRAIL_INFO);
//        }

//    private boolean equalBaseUrls(ActionURL url1, ActionURL url2)
//    {
//        if (url1 == url2)
//            return true;
//        if(null == url1 || null == url2)
//            return false;
//        return url1.getExtraPath().equalsIgnoreCase(url2.getExtraPath()) && url1.getAction().equalsIgnoreCase(url2.getAction()) && url1.getPageFlow().equalsIgnoreCase(url2.getPageFlow());
//    }


    /**
     * Merges an existing NavTrail into the app bar, allows us to highlight the appBar based on existing NavTree code
     * If the name or url of a tab is the last thing on the navTrail, don't show a page title (just folder title)
     * Otherwise, if the NavTrail is more than length 1, use the last thing on the navTrail as the page title
     */
    private List<NavTree> fixCrumbTrail(List<NavTree> crumbTrail, ActionURL actionURL)
    {
        NavTree[] buttons = getButtons();
        boolean hideTitle = false;

        NavTree selected = getSelected();
        if (null == selected && null != actionURL) //First try to match actionURL
        {
            for (NavTree button : buttons)
            {
                if (button.getValue().equals(actionURL.toString()))
                {
                    selected = button;
                    hideTitle = true;
                    break;
                }
            }
        }

        if (null == selected)
            for (NavTree crumb : crumbTrail)
            {
                for (NavTree button : buttons)
                    if (button.getValue().equalsIgnoreCase(crumb.getValue()) || button.getKey().equalsIgnoreCase(crumb.getKey()))
                        selected = button;
            }

        if (null != selected)
            selected.setSelected(true);

        if (hideTitle)
        {
            setPageTitle(null);
            return Collections.emptyList();
        }
        else if (crumbTrail.size() >= 1)
        {
            // Last item is page title, strip it off the crumb trail
            setPageTitle(crumbTrail.get(crumbTrail.size() - 1).getKey());

            List<NavTree> result = new ArrayList<NavTree>();
            boolean stopLooking = false;

            for (int i = 0; i < crumbTrail.size() - 1; i++)
            {
                String link = crumbTrail.get(i).getValue();
                boolean foundMatch = false;
                if (!stopLooking)
                {
                    // First check the folder title's link
                    if (ObjectUtils.equals(getValue(), link))
                    {
                        foundMatch = true;
                    }
                    // Then look at all of the tabs
                    for (NavTree button : buttons)
                    {
                        if (ObjectUtils.equals(button.getValue(), link) || (actionURL != null && ObjectUtils.equals(actionURL.getLocalURIString(), link)))
                        {
                            foundMatch = true;
                        }
                    }
                }
                if (!foundMatch)
                {
                    stopLooking = true;
                    result.add(crumbTrail.get(i));
                }
            }

            return result;
        }

        return crumbTrail;
    }
}
