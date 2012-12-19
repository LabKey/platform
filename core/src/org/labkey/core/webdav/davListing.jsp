<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.webdav.WebdavResource" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
  public LinkedHashSet<ClientDependency> getClientDependencies()
  {
      LinkedHashSet<ClientDependency> resources = new LinkedHashSet<ClientDependency>();
      resources.add(ClientDependency.fromFilePath("File"));
      return resources;
  }
%>
<%
    DavController.ListPage listpage = (DavController.ListPage) HttpView.currentModel();
    WebdavResource resource = listpage.resource;
    AppProps.Interface app = AppProps.getInstance();
%>
<script type="text/javascript">

    Ext4.onReady(function() {

        var htmlViewAction = new Ext4.Action({
            text : 'HTML View',
            handler : function() {
                window.location = <%=PageFlowUtil.jsString(h(resource.getLocalHref(getViewContext())+"?listing=html"))%>;
            }
        });

        var fileSystem = Ext4.create('File.system.Webdav', {
            baseUrl  : <%=PageFlowUtil.jsString(Path.parse(request.getContextPath()).append(listpage.root).encode("/",null))%>,
            offsetUrl: <%=PageFlowUtil.jsString(listpage.resource.getPath().toString())%>,
            rootName : <%=PageFlowUtil.jsString(app.getServerName())%>
        });

        Ext4.create('Ext.container.Viewport', {
            layout : 'fit',
            items : [{
                xtype : 'filebrowser',
                adminUser : true,
                fileSystem : fileSystem,
                gridConfig : {
                    selType : 'rowmodel'
                },
                tbarItems : ['->', htmlViewAction]
            }]
        });

    });
</script>
