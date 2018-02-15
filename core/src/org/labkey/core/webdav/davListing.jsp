<%
/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="org.labkey.api.premium.PremiumService" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("File");
    }
%>
<%
    DavController.ListPage listpage = (DavController.ListPage) HttpView.currentModel();
    WebdavResource resource = listpage.resource;
    AppProps app = AppProps.getInstance();
%>
<script type="text/javascript">

    Ext4.onReady(function() {

        var returnUrl = encodeURIComponent(LABKEY.contextPath + document.URL.split(LABKEY.contextPath)[1]);

        var loginAction = new Ext4.Action({
            text : 'Login',
            handler : function () {
                window.location = LABKEY.contextPath + '/login/home/login.view?returnUrl=' + returnUrl;
            }
        });

        var logoutAction = new Ext4.Action({
            text : 'Logout',
            handler : function () {
                window.location = LABKEY.contextPath + '/login/home/logout.view?returnUrl=' + returnUrl;
            }
        });

        var htmlViewAction = new Ext4.Action({
            text : 'HTML View',
            handler : function() {
                window.location = <%=q(h(resource.getLocalHref(getViewContext())+"?listing=html"))%>;
            }
        });

        <%
        // check here to make sure the relativePath used to populate rootOffset does not include the root itself
        // because the rootPath will have the root. See issue 29993
        Path resourcePath = listpage.resource.getPath();
        Path relativePath = resourcePath;
        if (resourcePath.startsWith(listpage.root))
            relativePath = resourcePath.subpath(listpage.root.size(),relativePath.size());
        %>

        // the rootPath here is encoded but the rootOffset is not (but may have already been) - not sure if this is what we want
        var fileSystem = Ext4.create('File.system.Webdav', {
            rootPath: <%=q(Path.parse(request.getContextPath()).append(listpage.root).encode("/",null))%>,
            rootOffset: <%=q(relativePath.toString("/","/"))%>,
            rootName: <%=q(app.getServerName())%>
        });
        Ext4.create('Ext.container.Viewport', {
            layout : 'fit',
            items : [{
                xtype : 'filebrowser',
                itemId: 'browser',
                border : false,
                isWebDav  : true,
                useHistory: true,
                disableContextMenu: true,
                fileSystem : fileSystem,
                disableFileUpload: <%=PremiumService.get().isFileUploadDisabled()%>,
                gridConfig : {
                    selModel: {
                        type: 'rowmodel',
                        mode: 'MULTI'
                    }
                },
                actions: [
                    'folderTreeToggle', 'parentFolder', 'refresh',
                    'createDirectory', 'download', 'deletePath', 'upload',
                    '->', htmlViewAction, <%= getUser().isGuest() ? "loginAction" : "logoutAction" %>
                ],
                useServerActions: false
            }],
            listeners: {
                resize: function(vp) {
                    if (vp) {
                        var fb = vp.getComponent('browser');
                        if (fb) {
                            Ext4.defer(fb.detailCheck, 250, fb);
                        }
                    }
                }
            }
        });
    });
</script>
