<%@ page import="org.labkey.biotrue.controllers.ServerForm" %>
<%@ page import="org.labkey.biotrue.objectmodel.BtServer" %>
<%@ page import="org.labkey.biotrue.datamodel.BtManager" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.data.*" %>
<%@ page import="org.labkey.biotrue.controllers.BtController" %>
<%@ page import="org.labkey.api.view.DetailsView" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<% ServerForm form = (ServerForm) __form;
    BtServer server = form.getServer();
    DataRegion region = new DataRegion();
    TableInfo table = BtManager.get().getTinfoServer();
    region.setColumns(new ColumnInfo[]{
            table.getColumn("Name"),
            table.getColumn("WsdlURL"),
            table.getColumn("ServiceNamespaceURI"),
            table.getColumn("ServiceLocalPart"),
            table.getColumn("Username"),
            table.getColumn("PhysicalRoot")
    });
    ButtonBar bb = new ButtonBar();
    if (getContainer().hasPermission(getUser(), ACL.PERM_UPDATE))
    {
        bb.add(new ActionButton("Synchronize", server.urlFor(BtController.Action.synchronizeServer)));
    }
    region.setButtonBar(bb);
    DetailsView view = new DetailsView(region, server.getRowId());
    include(view, out);
%>


