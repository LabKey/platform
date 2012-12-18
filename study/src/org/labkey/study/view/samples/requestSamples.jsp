<%
/*
 * Copyright (c) 2006-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView"%>
<%@ page import="org.labkey.api.view.ViewContext"%>
<%@ page import="org.labkey.study.SampleManager"%>
<%@ page import="org.labkey.study.controllers.samples.SpecimenController"%>
<%@ page import="org.labkey.study.model.SiteImpl"%>
<%@ page import="org.labkey.study.model.Specimen"%>
<%@ page import="org.labkey.study.model.StudyManager"%>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SpecimenController.NewRequestBean> me = (JspView<SpecimenController.NewRequestBean>) HttpView.currentView();
    SpecimenController.NewRequestBean bean = me.getModelBean();
    ViewContext context = me.getViewContext();
    List<SiteImpl> sites = StudyManager.getInstance().getValidRequestingLocations(context.getContainer());
    boolean shoppingCart = SampleManager.getInstance().isSpecimenShoppingCartEnabled(context.getContainer());
    Specimen[] specimens = bean.getSamples();
    SampleManager.SpecimenRequestInput[] inputs = bean.getInputs();
%>
<span class="labkey-error">
    <%
        BindException errors = bean.getErrors();
        if (errors != null)
        {
            for (ObjectError e : (List<ObjectError>) errors.getAllErrors())
            {
                %><%=h(HttpView.currentContext().getMessage(e))%><br><%
            }
        }
    %>
</span>

<script type="text/javascript">
var LastSetValues = new Object();
var DefaultValues = new Object();
    <%
    for (int i = 0; i < inputs.length; i++)
    {
        SampleManager.SpecimenRequestInput input = inputs[i];
        if (input.isRememberSiteValue())
        {
    %>
LastSetValues['input<%= i %>'] = '';
DefaultValues['input<%= i %>'] = new Object();
    <%
            Map<Integer, String> defaults = input.getDefaultSiteValues(context.getContainer());
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
    var siteId = document.getElementById('destinationSite').value;
    for (var elementId in DefaultValues)
    {
        var elem = document.getElementById(elementId);
        var value = DefaultValues[elementId][siteId];
        if (value && (!elem.value || elem.value == LastSetValues[elementId]))
        {
            elem.value = value;
            LastSetValues[elementId] = elem.value;
        }
    }
    return true;
}
</script>
<form name="CreateSampleRequest" action="<%=h(buildURL(SpecimenController.HandleCreateSampleRequestAction.class))%>" method="POST">
    <input type="hidden" name="returnUrl" value="<%= h(bean.getReturnUrl()) %>">
    <%
        if (specimens != null)
        {
            for (Specimen specimen : specimens)
            {
    %>
    <input type="hidden" name="sampleRowIds" value="<%= specimen.getRowId() %>">
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
                <select id='destinationSite' name="destinationSite" onChange="setDefaults()">
                    <option value="0"></option>
                    <%
                        for (SiteImpl site : sites)
                        {
                    %>
                    <option value="<%= site.getRowId() %>" <%= text(bean.getSelectedSite() == site.getRowId() ? "SELECTED" : "")%>>
                        <%= h(site.getDisplayName()) %>
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
                SampleManager.SpecimenRequestInput input = inputs[i];
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
                <%
                    boolean hasReturnURL = bean.getReturnUrl() != null && !bean.getReturnUrl().isEmpty();
                    if (hasReturnURL)
                    {
                %>
                <%= generateSubmitButton((shoppingCart ? "Create" : "Submit") + " and Return to Specimens")%>
                <%
                    }
                %>
                <%= text(buttonImg((shoppingCart ? "Create" : "Submit") + " and View Details",
                        "document.CreateSampleRequest." + SpecimenController.CreateSampleRequestForm.PARAMS.ignoreReturnUrl.name() + ".value='true'; return true;"))%>
                <%= text(hasReturnURL ? generateButton("Cancel", bean.getReturnUrl()) : generateButton("Cancel", SpecimenController.ViewRequestsAction.class))%>
            </td>
        </tr>


        <%
            if (specimens != null && specimens.length > 0)
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
</form>