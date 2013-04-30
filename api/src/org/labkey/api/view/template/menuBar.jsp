<%
/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.settings.LookAndFeelProperties" %>
<%@ page import="org.labkey.api.util.FolderDisplayMode" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartFactory" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.api.view.template.MenuBarView" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
        resources.add(ClientDependency.fromFilePath("ext3"));
        return resources;
    }
%>
<%
    List<Portal.WebPart> menus = ((MenuBarView) HttpView.currentView()).getModelBean();
    ViewContext currentContext = HttpView.currentContext();
    Container c = currentContext.getContainer();

    FolderDisplayMode folderMode = LookAndFeelProperties.getInstance(c).getFolderDisplayMode();
    boolean showFolderNavigation = c != null && !c.isRoot() && c.getProject() != null;
    Container p = c.getProject();
    folderMode.isShowInMenu();
%>
<div id="menubar" class="labkey-main-menu">
    <ul>
<%
    if (currentContext.isShowFolders())
    {
%>
        <li id="projectBar" class="menu-projects"> </li>
<%
    }

    if (showFolderNavigation)
    {
%>
        <li id="folderBar" class="menu-folders"><%=p.getName()%></li>
<%
    }
%>
        <%
            if(menus.size() > 0)
            {
                for (Portal.WebPart part : menus)
                {
                    String menuCaption = part.getName();
                    String menuName = part.getName() + part.getIndex();
                    try
                    {
                        WebPartFactory factory = Portal.getPortalPart(part.getName());
                        if (null == factory)
                            continue;
                        WebPartView view = factory.getWebPartView(currentContext, part);
                        if (view.isEmpty())
                            continue;       // Don't show folder/query if nothing to show
                        if (null != view.getTitle())
                            menuCaption = view.getTitle();
                    }
                    catch(Exception e)
                    {
                        //Use the part name...
                    }
        %>
        <li id="<%=h(menuName)%>$Header" class="labkey-main-menu-item">
            <a class="labkey-main-menu-link" href="#">
                <%=h(menuCaption)%>
            </a>
        </li>
        <%
                }
            }
        %>
    </ul>
</div>
<script type="text/javascript">
    Ext4.onReady(function() {

        Ext4.define('HoverNavigation', {
            mixins : {
                observable : 'Ext.util.Observable'
            },

            statics : {
                visiblePopup : false
            },

            showDelay : 500,

            hideDelay : 500,

            hoverCls : 'selected',

            constructor : function(config) {

                // will apply config to this
                this.mixins.observable.constructor.call(this, config);

                this.hoverEl = Ext4.get(config.hoverElem);
                if (!this.hoverEl) {
                    return;
                }

                var loader = Ext4.DomHelper.insertAfter('menubar', {
                    id  : config.hoverElem + '_menu',
                    tag : 'div',
                    cls : 'labkey-webpart-menu',
                    children : [{
                        tag : 'div',
                        cls : 'loading-indicator',
                        style : 'width: 100px; height: 100px;'
                    }]
                });

                this.popup = new Ext4.Layer({zindex : 1000, constrain : false }, loader);
                this.popup.alignTo(this.hoverEl);
                this.popup.hide();

                // Configure hover element list
                this.hoverEl.hover(this.onTargetOver, this.delayCheck, this);
                this.popup.hover(this.cancelHide, this.delayCheck, this);
            },

            cancelShow : function() {
                if (this.showTimeout) {
                    clearTimeout(this.showTimeout);
                    this.showTimeout = false;
                }
            },

            cancelHide : function() {
                if (this.hideTimeout) {
                    clearTimeout(this.hideTimeout);
                    this.hideTimeout = false;
                }
            },

            onTargetOver : function(e) {
                this.cancelHide();
                this.render();

                // show immediately if we already have a menu up
                // Otherwise, make sure that someone hovers for a while
                HoverNavigation.visiblePopup ? this.show() : this.delayShow();
            },

            notFocused : function(e) {
                return !this.hoverEl.getRegion().contains(e.getPoint()) || !this.popup.getRegion().contains(e.getPoint());
            },

            delayCheck : function(e) {
                if (this.notFocused(e)) {
                    this.delayHide();
                }
            },

            delayShow : function() {
                if (!this.showTimeout) {
                    this.showTimeout = Ext4.defer(this.show, this.showDelay, this);
                }
            },

            delayHide : function() {
                this.cancelHide();
                this.cancelShow();
                this.hideTimeout = Ext4.defer(this.hide, this.hideDelay, this);
            },

            render : function() {
                if (!this.rendered) {
                    var p = new LABKEY.WebPart({
                        renderTo : this.popup.id,
                        partName : this.webPartName,
                        frame    : 'none',
                        partConfig : this.partConfig,
                        failure  : function(err) {if (window.console && window.console.log) { window.console.log(err);}},
                        scope    : this
                    });
                    p.render();
                    this.rendered = true;
                }
            },

            show : function() {

                if (HoverNavigation.visiblePopup) {
                    if (HoverNavigation.visiblePopup == this) {
                        return;
                    }
                    HoverNavigation.visiblePopup.hide();
                }

                this.hoverEl.addCls(this.hoverCls);

                this.render();

                this.popup.show();
                this.popup.alignTo(this.hoverEl); // default: tl-bl
                HoverNavigation.visiblePopup = this;
            },

            hide : function() {
                this.hoverEl.removeCls(this.hoverCls);
                this.popup.hide();
                if (HoverNavigation.visiblePopup == this) {
                    HoverNavigation.visiblePopup = false;
                }
            }
        });

        HoverNavigation.Parts = {};
        HoverNavigation._project = new HoverNavigation({hoverElem : 'projectBar', webPartName : 'projectnav' });
<%
    if (showFolderNavigation)
    {
%>
        HoverNavigation._folder = new HoverNavigation({hoverElem : 'folderBar',  webPartName : 'foldernav'});
<%
    }

    for (Portal.WebPart part : menus)
    {
        if (null == Portal.getPortalPartCaseInsensitive(part.getName()))
            continue;

        String menuName = part.getName() + part.getIndex();
%>
        HoverNavigation.Parts["_<%=menuName%>"] = new HoverNavigation({hoverElem:"<%=menuName%>$Header", webPartName: "<%=part.getName()%>",
            partConfig: { <%
                    String sep = "";
                    for (Map.Entry<String,String> entry : part.getPropertyMap().entrySet())
                    { %>
                        <%=sep%><%=PageFlowUtil.jsString(entry.getKey())%>:<%=PageFlowUtil.jsString(entry.getValue())%><%
                        sep = ",";
                    }%>
            }});
<%
    }
%>
    });
</script>
