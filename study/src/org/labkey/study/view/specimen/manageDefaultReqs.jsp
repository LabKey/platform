<%
/*
 * Copyright (c) 2006-2014 LabKey Corporation
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
<%@ page import="org.labkey.study.model.SpecimenRequestActor" %>
<%@ page import="org.labkey.study.model.SpecimenRequestRequirement" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController"%>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.ManageReqsBean> me = (JspView<SpecimenController.ManageReqsBean>) HttpView.currentView();
    SpecimenController.ManageReqsBean bean = me.getModelBean();
    SpecimenRequestRequirement[] providerRequirements = bean.getProviderRequirements();
    SpecimenRequestRequirement[] originatingRequirements = bean.getOriginatorRequirements();
    SpecimenRequestRequirement[] receiverRequirements = bean.getReceiverRequirements();
    SpecimenRequestRequirement[] generalRequirements = bean.getGeneralRequirements();
    SpecimenRequestActor[] actors = bean.getActors();
    ActionURL deleteDefaultRequirement = new ActionURL(SpecimenController.DeleteDefaultRequirementAction.class, getContainer()).addParameter("id",0);
%>
<script type="text/javascript">
function verifyNewRequirement(prefix)
{
    var actorElement = document.getElementById(prefix + "Actor");
    var descriptionElement = document.getElementById(prefix + "Description");
    var actorOption = actorElement.options[actorElement.selectedIndex];
    var actorNull = !actorOption.value || actorOption.value < 0;
    var descriptionNull = !descriptionElement.value;
    if (actorNull && descriptionNull)
        return false;

    if ((actorNull && !descriptionNull) || (!actorNull && descriptionNull))
    {
        alert("Actor and description are required for each default requirement.");
        return false;
    }
    return true;
}
</script>
<form action="<%=h(buildURL(SpecimenController.ManageDefaultReqsAction.class))%>" name="manageDefaultReqs" method="POST">
    <table class="labkey-manage-default-reqs">
    <tr class="labkey-wp-header">
        <th align="left">Requirements of Each Originating Lab</th>
    </tr>
    <tr>
        <td>
            <table width=100% class="labkey-data-region">
                <tr>
                    <th align="left">&nbsp;</th>
                    <th align="left">Actor</th>
                    <th align="left" colspan="2">Description</th>
                </tr>
                <%
                    for (SpecimenRequestRequirement requirement : originatingRequirements)
                    {
                %>
                <tr>
                    <td><%= textLink("Delete", deleteDefaultRequirement.replaceParameter("id", String.valueOf(requirement.getRowId())))%></td>
                    <td><%= h(requirement.getActor().getLabel()) %></td>
                    <td
                            colspan="2"><%= requirement.getDescription() != null ? h(requirement.getDescription()) : "&nbsp;" %></td>
                </tr>
                <%
                    }
                %>
                <tr>
                    <td colspan="2">
                        <select id="originatorActor" name="originatorActor">
                            <option value="-1"></option>
                            <%
                                for (SpecimenRequestActor actor : actors)
                                {
                                    if (actor.isPerSite())
                                    {
                            %>
                            <option value="<%= actor.getRowId() %>"><%= h(actor.getLabel()) %></option>
                            <%
                                    }
                                }
                            %>
                        </select>
                    </td>
                    <td><input type="text" id="originatorDescription" name="originatorDescription" size="50"></td>
                    <td><%= button("Add Requirement").submit(true).onClick("return verifyNewRequirement('originator');") %></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr class="labkey-wp-header">
        <th align="left">Requirements of Each Providing Lab</th>
    </tr>
    <tr>
        <td>
            <table width=100% class="labkey-data-region">
                <tr>
                    <th align="left">&nbsp;</th>
                    <th align="left">Actor</th>
                    <th align="left" colspan="2">Description</th>
                </tr>
                <%
                    for (SpecimenRequestRequirement requirement : providerRequirements)
                    {
                %>
                <tr>
                    <td><%= textLink("Delete", deleteDefaultRequirement.replaceParameter("id", String.valueOf(requirement.getRowId())))%></td>
                    <td><%= h(requirement.getActor().getLabel()) %></td>
                    <td
                            colspan="2"><%= requirement.getDescription() != null ? h(requirement.getDescription()) : "&nbsp;" %></td>
                </tr>
                <%
                    }
                %>
                <tr>
                    <td colspan="2">
                        <select id="providerActor" name="providerActor">
                            <option value="-1"></option>
                            <%
                                for (SpecimenRequestActor actor : actors)
                                {
                                    if (actor.isPerSite())
                                    {
                            %>
                            <option value="<%= actor.getRowId() %>"><%= h(actor.getLabel()) %></option>
                            <%
                                    }
                                }
                            %>
                        </select>
                    </td>
                    <td><input type="text" id="providerDescription" name="providerDescription" size="50"></td>
                    <td><%= button("Add Requirement").submit(true).onClick("return verifyNewRequirement('provider');") %></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr class="labkey-wp-header">
            <th align="left">Requirements of Receiving Lab</th>
        </tr>
        <tr>
            <td>
                <table width=100% class="labkey-data-region">
                    <tr>
                        <th align="left">&nbsp;</th>
                        <th align="left">Actor</th>
                        <th align="left" colspan="2">Description</th>
                    </tr>
                    <%
                        for (SpecimenRequestRequirement requirement : receiverRequirements)
                        {
                    %>
                    <tr>
                        <td><%= textLink("Delete", deleteDefaultRequirement.replaceParameter("id", String.valueOf(requirement.getRowId())))%></td>
                        <td><%= h(requirement.getActor().getLabel()) %></td>
                        <td colspan="2"><%= requirement.getDescription() != null ? h(requirement.getDescription()) : "&nbsp;" %></td>
                    </tr>
                    <%
                        }
                    %>
                    <tr>
                        <td colspan="2">
                            <select id="receiverActor" name="receiverActor">
                                <option value="-1"></option>
                                <%
                                    for (SpecimenRequestActor actor : actors)
                                    {
                                        if (actor.isPerSite())
                                        {
                                %>
                                <option value="<%= actor.getRowId() %>"><%= h(actor.getLabel()) %></option>
                                <%
                                        }
                                    }
                                %>
                            </select>
                        </td>
                        <td><input type="text" id="receiverDescription" name="receiverDescription" size="50"></td>
                        <td><%= button("Add Requirement").submit(true).onClick("return verifyNewRequirement('receiver');") %></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr class="labkey-wp-header">
            <th align="left">General Requirements</th>
        </tr>
        <tr>
            <td>
                <table width=100% class="labkey-data-region">
                    <tr>
                        <th align="left">&nbsp;</th>
                        <th align="left">Actor</th>
                        <th align="left" colspan="2">Description</th>
                    </tr>
                    <%
                        for (SpecimenRequestRequirement requirement : generalRequirements)
                        {
                    %>
                    <tr>
                        <td><%= textLink("Delete", deleteDefaultRequirement.replaceParameter("id", String.valueOf(requirement.getRowId())))%></td>
                        <td><%= h(requirement.getActor().getLabel()) %></td>
                        <td colspan="2"><%= requirement.getDescription() != null ? h(requirement.getDescription()) : "&nbsp;" %></td>
                    </tr>
                    <%
                        }
                    %>
                    <tr>
                        <td colspan="2">
                            <select id="generalActor" name="generalActor">
                                <option value="-1"></option>
                                <%
                                    for (SpecimenRequestActor actor : actors)
                                    {
                                        if (!actor.isPerSite())
                                        {
                                %>
                                <option value="<%= actor.getRowId() %>"><%= h(actor.getLabel()) %></option>
                                <%
                                        }
                                    }
                                %>
                            </select>
                        </td>
                        <td><input type="text" id="generalDescription" name="generalDescription" size="50"></td>
                        <td><%= button("Add Requirement").submit(true).onClick("return verifyNewRequirement('general');") %></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
    <input type="hidden" name="nextPage" value="<%=new ActionURL(SpecimenController.ManageDefaultReqsAction.class, getContainer()).getLocalURIString()%>">
</form>
<%= textLink("manage study", new ActionURL(StudyController.ManageStudyAction.class, getContainer())) %>