<%
/*
 * Copyright (c) 2007-2009 LabKey Corporation
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
<%@ page import="org.labkey.biotrue.controllers.ServerForm" %>
<%@ page import="org.labkey.biotrue.objectmodel.BtServer" %>
<%@ page import="org.labkey.biotrue.datamodel.BtManager" %>
<%@ page import="org.labkey.api.security.ACL" %>
<%@ page import="org.labkey.api.data.*" %>
<%@ page import="org.labkey.biotrue.controllers.BtController" %>
<%@ page import="org.labkey.api.view.DetailsView" %>
<%@ page import="org.labkey.api.security.permissions.UpdatePermission" %>
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
    if (getContainer().hasPermission(getUser(), UpdatePermission.class))
    {
        bb.add(new ActionButton("Synchronize", server.urlFor(BtController.SynchronizeServerAction.class)));
    }
    region.setButtonBar(bb);
    DetailsView view = new DetailsView(region, server.getRowId());
    include(view, out);
%>


