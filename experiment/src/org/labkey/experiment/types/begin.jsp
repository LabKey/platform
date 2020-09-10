<%
/*
 * Copyright (c) 2009-2019 LabKey Corporation
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
<%@ page import="org.labkey.experiment.types.TypesController.CheckResolveAction" %>
<%@ page import="org.labkey.experiment.types.TypesController.TypesAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<div>
<p>This is the admin console for defining types, such as SampleTypes or Forms.
Note that creating a type is typically done in the context of another module.
This page is for troubleshooting and testing purposes.
</p>

[&nbsp;<a href="<%=h(buildURL(TypesController.TypesAction.class))%>">View&nbsp;Types</a>&nbsp;]<br>
[&nbsp;<a href="<%=h(buildURL(TypesController.CheckResolveAction.class))%>">Resolve LSIDs</a>&nbsp;]<br>
</div>