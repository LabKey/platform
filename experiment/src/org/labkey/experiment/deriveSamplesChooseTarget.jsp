<%@ page import="org.labkey.api.exp.api.ExpMaterial" %>
<%@ page import="org.labkey.api.exp.api.ExpSampleSet" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.experiment.controllers.exp.ExperimentController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib"%>
<%
    JspView<ExperimentController.DeriveSamplesChooseTargetBean> me = (JspView<ExperimentController.DeriveSamplesChooseTargetBean>) HttpView.currentView();
    ExperimentController.DeriveSamplesChooseTargetBean bean = me.getModelBean();
%>

<form action="describeDerivedSamples.view" method="get">

    <table>
        <tr>
            <td class="ms-searchform">Source materials:</td>
            <td>
                <table>
                    <tr>
                        <td valign="bottom" class="ms-searchform"><strong>Name</strong></td>
                        <td valign="bottom" class="ms-searchform"><strong>Role</strong><%= PageFlowUtil.helpPopup("Role", "Roles allow you to label an input as being used in a particular way. It serves to disambiguate the purpose of each of the input materials. Each input should have a unique role.")%></td>
                    </tr>
                <%
                int roleIndex = 0;
                for (ExpMaterial material : bean.getSourceMaterials().keySet())
                { %>
                    <tr>
                        <td><input type="hidden" name="rowIds" value="<%= material.getRowId()%>" /><%= h(material.getName())%></td>
                        <td><select name="inputRole<%= roleIndex %>" onchange="document.getElementById('customRole<%= roleIndex %>').disabled = this.value != '<%= ExperimentController.DeriveSamplesChooseTargetBean.CUSTOM_ROLE %>';">
                            <option value=""></option>
                            <% for (String inputRole : bean.getInputRoles())
                            { %>
                                <option value="<%= h(inputRole)%>"><%= h(inputRole) %></option>
                            <% } %>
                            <option value="<%= ExperimentController.DeriveSamplesChooseTargetBean.CUSTOM_ROLE %>">Add a new role...</option>
                        </select> <input name="customRole<%= roleIndex %>" disabled="true" id="customRole<%= roleIndex %>"/></td>
                    </tr>
                <%
                    roleIndex++;
                }
                %>
                </table>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform">Number of derived samples:</td>
            <td colspan="2">
                <select name="outputCount">
                    <% for (int i = 1; i <= 20; i++)
                    { %>
                        <option <% if (bean.getSampleCount() == i) { %>selected<% } %> value="<%= i %>"><%= i %></option>
                    <% } %>
                </select>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform">Target sample set:</td>
            <td colspan="2">
                <select name="targetSampleSetId">
                    <option value="0">Not a member of a sample set</option>
                    <%
                    for (ExpSampleSet ss : bean.getSampleSets())
                    { %>
                        <option value="<%= ss.getRowId() %>"><%= h(ss.getName())%> in <%= h(ss.getContainer().getPath()) %></option>
                    <% } %>
                </select>
            </td>
        </tr>
        <tr>
            <td></td>
            <td><labkey:button text="Next" /></td>
        </tr>
    </table>
</form>