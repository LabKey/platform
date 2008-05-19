<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.query.controllers.SourceForm"%>
<%@ page import="org.labkey.api.query.QueryAction"%>
<%@ page import="org.labkey.query.controllers.MetadataForm"%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.query.controllers.Page" %>
<%
    MetadataForm form = (MetadataForm) __form;
    boolean canEdit = form.canEdit();
%>
<labkey:errors />
<form method="POST" action="<%=form.getQueryDef().urlFor(QueryAction.metadataQuery)%>">
<%=PageFlowUtil.getStrutsError(request, null)%>
<textarea rows="20" cols="80" wrap="off"
          name="ff_metadataText"<%=canEdit ? "" : " READONLY"%>><%=h(form.ff_metadataText)%></textarea><br>
<% if (canEdit)
{ %>
<input type="Submit" name="ff_action" value="Save Changes"/>
<% } %>
</form>