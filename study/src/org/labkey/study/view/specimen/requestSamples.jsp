<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.view.HttpView"%>
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.api.view.template.ClientDependencies"%>
<%@ page import="org.labkey.study.SpecimenManager"%>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController"%>
<%@ page import="org.labkey.study.model.LocationImpl"%>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.Vial" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    JspView<SpecimenController.NewRequestBean> me = (JspView<SpecimenController.NewRequestBean>) HttpView.currentView();
    SpecimenController.NewRequestBean bean = me.getModelBean();
    ViewContext context = getViewContext();
    Container c = getContainer();
    List<LocationImpl> locations = StudyManager.getInstance().getValidRequestingLocations(c);
    boolean shoppingCart = SpecimenManager.getInstance().isSpecimenShoppingCartEnabled(c);
    boolean hasExtendedRequestView = SpecimenManager.getInstance().getExtendedSpecimenRequestView(context) != null;
    List<Vial> vials = bean.getVials();
    SpecimenManager.SpecimenRequestInput[] inputs = bean.getInputs();
%>
<span class="labkey-error">
    <%
        BindException errors = bean.getErrors();
        if (errors != null)
        {
            for (ObjectError e : errors.getAllErrors())
            {
                %><%=h(context.getMessage(e))%><br><%
            }
        }
    %>
</span>

<script type="text/javascript">
var LastSetValues = {};
var DefaultValues = {};
    <%
    for (int i = 0; i < inputs.length; i++)
    {
        SpecimenManager.SpecimenRequestInput input = inputs[i];
        if (input.isRememberSiteValue())
        {
    %>
LastSetValues['input<%= i %>'] = '';
DefaultValues['input<%= i %>'] = {};
    <%
            Map<Integer, String> defaults = input.getDefaultSiteValues(c);
            for (Map.Entry<Integer,String> entry : defaults.entrySet())
            {
    %>DefaultValues['input<%= i %>']['<%= entry.getKey() %>'] = <%= PageFlowUtil.jsString(entry.getValue()) %>;
    <%
            }
        }
    }
    %>

function setDefaults()
{
    var locationId = document.getElementById('destinationLocation').value;
    for (var elementId in DefaultValues)
    {
        if (DefaultValues.hasOwnProperty(elementId)) {
            var elem = document.getElementById(elementId);
            var value = DefaultValues[elementId][locationId];
            if (value && (!elem.value || elem.value == LastSetValues[elementId]))
            {
                elem.value = value;
                LastSetValues[elementId] = elem.value;
            }
        }
    }
    return true;
}
</script>
<labkey:form name="CreateSampleRequest" action="<%=h(buildURL(SpecimenController.HandleCreateSampleRequestAction.class))%>" method="POST">
    <input type="hidden" name="returnUrl" value="<%= h(bean.getReturnUrl()) %>">
    <%
        if (vials != null)
        {
            for (Vial vial : vials)
            {
    %>
    <input type="hidden" name="sampleRowIds" value="<%= vial.getRowId() %>">
    <%
            }
        }
    %>
    <table>
    <%
        if (shoppingCart)
        {
    %>
        <tr>
            <td>Please fill out this form to create a new specimen request.  You will have the chance to add or remove specimens before the request is submitted.</td>
        </tr>
    <%
        }
    %>
        <tr>
            <th align="left">Requesting Location (Required):</th>
        </tr>
        <tr>
            <td>
                <select id='destinationLocation' name="destinationLocation" onChange="setDefaults()">
                    <option value="0"></option>
                    <%
                        for (LocationImpl location : locations)
                        {
                    %>
                    <option value="<%= location.getRowId() %>"<%=selected(bean.getSelectedSite() == location.getRowId())%>>
                        <%= h(location.getDisplayName()) %>
                    </option>
                    <%
                        }
                    %>
                </select>

            </td>
        </tr>
        <%
            for (int i = 0; i < inputs.length; i++)
            {
                SpecimenManager.SpecimenRequestInput input = inputs[i];
        %>
        <tr>
            <th align="left">
                <input type="hidden" name="required" value="<%= input.isRequired() %>">
                <br><%= h(input.getTitle()) %> <%= text(input.isRequired() ? "(Required)" : "(Optional)") %>:
            </th>
        </tr>
        <tr>
            <td><%= h(input.getHelpText()) %></td>
        </tr>
        <tr>
            <td>
                <%
                    if (input.isMultiLine())
                    {
                %>
                <textarea rows="5" id="input<%= i %>" cols="50" name="inputs"><%= h(bean.getValue(i)) %></textarea>
                <%
                    }
                    else
                    {
                %>
                <input type="text" id="input<%= i %>" size="40" name="inputs" value="<%= h(bean.getValue(i)) %>">
                <%
                    }
                %>
            </td>
        </tr>
        <%
            }
        %>
        <tr>
            <td>
                <input type="hidden" name="<%= h(SpecimenController.CreateSampleRequestForm.PARAMS.ignoreReturnUrl.name()) %>" value="false">
                <input type="hidden" name="<%= h(SpecimenController.CreateSampleRequestForm.PARAMS.extendedRequestUrl.name()) %>" value="false">
                <%
                    boolean hasReturnURL = bean.getReturnUrl() != null && !bean.getReturnUrl().isEmpty();
                    if (hasExtendedRequestView)
                    {
                        %>
                        <%= button("Save & Continue").submit(true).onClick("document.CreateSampleRequest." + SpecimenController.CreateSampleRequestForm.PARAMS.extendedRequestUrl.name() + ".value='true'; return true;") %>
                        <%
                    }
                    else
                    {
                        if (hasReturnURL)
                        {
                            %>
                            <%= button((shoppingCart ? "Create" : "Submit") + " and Return to Specimens").submit(true) %>
                            <%
                        }
                     %>
                     <%= button((shoppingCart ? "Create" : "Submit") + " and View Details").submit(true)
                             .onClick("document.CreateSampleRequest." + SpecimenController.CreateSampleRequestForm.PARAMS.ignoreReturnUrl.name() + ".value='true'; return true;") %>
                <%
                    }
                %>
                <%= text(hasReturnURL ? button("Cancel").href(bean.getReturnUrl()).toString() : button("Cancel").href(SpecimenController.ViewRequestsAction.class, getContainer()).toString()) %>
            </td>
        </tr>


        <%
            if (vials != null && vials.size() > 0)
            {
        %>
            <tr>
                <th align="left">Selected Specimens:</th>
            </tr>
            <tr>
                <td><% me.include(bean.getSpecimenQueryView(), out); %></td>
            </tr>
        <%
            }
        %>

    </table>
</labkey:form>