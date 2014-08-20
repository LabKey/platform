<%
/*
 * Copyright (c) 2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal.WebPart" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController.ReportConfigurationBean" %>
<%@ page import="org.labkey.study.controllers.specimen.SpecimenController.SpecimenReportWebPartFactory" %>
<%@ page import="org.labkey.study.specimen.report.SpecimenVisitReportParameters" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<WebPart> me = (JspView<WebPart>) HttpView.currentView();
    WebPart webpart = me.getModelBean();
    String currentReportName = webpart.getPropertyMap().get(SpecimenReportWebPartFactory.REPORT_TYPE_PARAMETER_NAME);

    ReportConfigurationBean bean = new ReportConfigurationBean(getViewContext());
%>
<labkey:form method="post">
<table>
    <tr>
        <td>
            Choose the specimen report to display in this webpart<br>
        </td>
    </tr>
    <tr>
        <td>
            Type
            <label>
                <select name="<%=text(SpecimenReportWebPartFactory.REPORT_TYPE_PARAMETER_NAME)%>"><%
                    for (String category : bean.getCategories())
                    {
                        for (SpecimenVisitReportParameters factory : bean.getFactories(category))
                        {
                            String name = factory.getReportType(); %>
                            <option value="<%=h(name)%>"<%=selected(name.equals(currentReportName))%>><%=h(factory.getLabel())%></option><%
                        }
                    }
                %>
                </select>
            </label>
        </td>
    </tr>
    <tr>
        <td align="left">
            <table>
                <tr>
                    <td align="left">
                        <%= button("Submit").submit(true) %>
                        <%= button("Cancel").href(getContainer().getStartURL(getUser())) %>
                    </td>
                </tr>
            </table>
        </td>
    </tr>
</table>
</labkey:form>
