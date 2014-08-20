<%
/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.experiment.types.TypesController" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%

//
// SEARCH FORM
//
TypesController.SearchForm form = (TypesController.SearchForm) HttpView.currentModel();
Map<String, Object>[] concepts = form.concepts;

String[] semanticTypes = TypesController.getSemanticTypes();

/*
TreeSet foundSemanticTypes = new TreeSet();
for (row in concepts)
	{
	String s = row.get("SemanticType");
	if (!s)
		continue;
	String[] types = s.split("\\|");
	for (type in types)
		{
		if (type)
			foundSemanticTypes.add(type);
		}
	}
for (found in foundSemanticTypes)
	out.println(found + "<br>");
*/
%>
<labkey:errors/>
<labkey:form action="<%=h(buildURL(TypesController.FindConceptsAction.class))%>" method="GET">
<table >
	<tr><td class="labkey-form-label">Search for</td><td><input name=query style="width:320;" value="<%=h(form.getQuery())%>"></td></tr>
	<tr><td class="labkey-form-label">Prefix match</td><td><input type=checkbox name=prefixMatch<%=checked(form.isPrefixMatch())%>></td></tr>
	<tr><td colspan=2><hr></td></tr>
	<tr><td class="labkey-form-label">Semantic Type</td><td><select name="semanticType"  style="width:320;">
	<option value=""<%=selected(form.getSemanticType() == null)%>>- any -</option><%
	for (String type : semanticTypes)
		{
		%><option<%=selected(form.getSemanticType() != null && form.getSemanticType().equals(type))%>><%=h(type)%></option><%
		}
	%></select></td></tr>
	<tr><td class="labkey-form-label">Concept</td><td><input name="concept" style="width:320;" value="<%=h(form.getConcept())%>"></td></tr>
	<tr><td><%= button("Search").submit(true) %></td><td></td></tr>
</table>
</labkey:form>
<p/>
<div><%

//
// SEARCH RESULTS
//

Map<String, Object> match = null;

if (concepts.length == 0)
	{
	%><b> No Results </b><%
	}
else
	{
	if (concepts.length == 1 || (form.getConcept() != null &&
			(form.getConcept().equals(concepts[0].get("PropertyURI")) ||
			 concepts[0].get("Name").equals(form.getConcept()))))
		{
		Map<String, Object> row = concepts[0];
		String uri = (String)row.get("PropertyURI");
		String name = (String)row.get("Name");
		String label = (String)row.get("Label");
		if (label == null)
			label = name;
		List<String> path = (List<String>)row.get("Path");
		String description = (String)row.get("Description");
		String semanticType = (String)row.get("SemanticType");
        if (semanticType != null)
            {
            if (semanticType.startsWith("|"))
                semanticType = semanticType.substring(1);
            if (semanticType.endsWith("|"))
                semanticType = semanticType.substring(0, semanticType.length()-1);
            semanticType = semanticType.replaceAll("\\|", ", ");
            }
        %><p/><b><%=h(uri)%></b><hr size=1>
		<table>
			<tr><th valign=top align=right nowrap>Name</th><td><%=h(name)%></td></tr>
			<tr><th valign=top align=right nowrap>Label</th><td><%=h(label)%></td></tr>
			<tr><th valign=top align=right nowrap>Semantic Type(s)</th><td><%=h(semanticType)%></td></tr>
			<tr><th valign=top align=right nowrap>Parent Concept</th><td><%=h(row.get("ConceptURI"))%></td></tr>
			<tr><th valign=top align=right nowrap>Description</th><td><%=h(description)%></td></tr>
		</table>
        <%=PageFlowUtil.button("select").onClick("javascript:select(" + PageFlowUtil.jsString(uri) + ")")%><%
		}

	%><p/><b>Search Results</b><hr size=1><%
	for (Map<String, Object> row : concepts)
		{
		String uri = (String)row.get("PropertyURI");
		String name = (String)row.get("Name");
		String label = (String)row.get("Label");
		if (label == null)
			label = name;
		List<String> path = (List<String>)row.get("Path");
		String description = (String)row.get("Description");

	    // concept
		%><b><a href="javascript:concept(<%=PageFlowUtil.jsString(uri)%>)"><%=h(name)%></a> : </b><%
		String and = "";
		for (String pathURI : path)
			{
			out.print(and);
			%><a href="javascript:concept(<%=PageFlowUtil.jsString(pathURI)%>)"><%=h(pathURI.substring(pathURI.lastIndexOf('#')+1))%></a><%
			and = " / ";
			}
		if (false)
		{
		%><%=and%><a href="javascript:concept(<%=PageFlowUtil.jsString(uri)%>)"><%=h(name)%></a><%
		}
		out.println("<br>");
		if (row == match)
			{
			out.println("&nbsp;&nbsp;Semantic Types " + h(row.get("SemanticType")) + "<br>");
			out.println("&nbsp;&nbsp;PropertyURI " + h(uri) + "<br>");
			if (row.get("Description") != null)
				out.println("&nbsp;&nbsp;&nbsp;" + h(description) + "<br>");
			}
		else
			{
			if (row.get("Description") != null)
				out.println("&nbsp;&nbsp;&nbsp;" + h(description) + "<br>");
			}
		}
	}
%></div>
<script type="text/javascript">
function select(uri)
	{
	window.alert(uri);
	}
function concept(uri)
	{
	window.location='?concept='+escape(uri);
	}
</script>
