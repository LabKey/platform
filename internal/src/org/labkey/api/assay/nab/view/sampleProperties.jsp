<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.assay.dilution.DilutionAssayRun" %>
<%@ page import="org.labkey.api.assay.nab.RenderAssayBean" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.exp.PropertyDescriptor" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<RenderAssayBean> me = (JspView<RenderAssayBean>) HttpView.currentView();
    RenderAssayBean bean = me.getModelBean();
    Container c = getContainer();
//    DilutionAssayRun assay = bean.getAssay();
    // the data for the sample properties table
    List<Map<PropertyDescriptor, Object>> sampleData = new ArrayList<>();
    Set<String> pdsWithData = new HashSet<>();

    for (DilutionAssayRun.SampleResult result : bean.getSampleResults())
    {
        Map<PropertyDescriptor, Object> sampleProps = new LinkedHashMap<>(result.getSampleProperties());

        Pair<PropertyDescriptor, Object> virusPair = bean.getVirusName(result, c);
        if (virusPair != null)
            sampleProps.put(virusPair.getKey(), virusPair.getValue());

        Pair<PropertyDescriptor, Object> fitErrorPair = bean.getFitError(result, c);
        if (fitErrorPair != null)
            sampleProps.put(fitErrorPair.getKey(), fitErrorPair.getValue());

        Pair<PropertyDescriptor, Object> stdDev = bean.getStandardDev(result, c);
        if (stdDev != null)
            sampleProps.put(stdDev.getKey(), stdDev.getValue());

        Pair<PropertyDescriptor, Object> aucPair = bean.getAuc(result, c);
        if (aucPair != null)
            sampleProps.put(aucPair.getKey(), aucPair.getValue());

        Pair<PropertyDescriptor, Object> paucPair = bean.getPositiveAuc(result, c);
        if (paucPair != null)
            sampleProps.put(paucPair.getKey(), paucPair.getValue());

        sampleData.add(sampleProps);

        // calculate which columns have data
        for (Map.Entry<PropertyDescriptor, Object> entry : sampleProps.entrySet())
        {
            if (entry.getValue() != null && !pdsWithData.contains(entry.getKey().getName()))
                pdsWithData.add(entry.getKey().getName());
        }
    }

%>
            <%
                if (sampleData.size() > 0)
                {
            %>
                <table class="labkey-data-region-legacy labkey-show-borders">
                    <colgroup><%

                        for (PropertyDescriptor pd : sampleData.get(0).keySet())
                        {
                            if (!pdsWithData.contains(pd.getName()))
                                continue;
                            %>
                            <col>
                            <%
                        }

                    %></colgroup>
                    <tr class="labkey-col-header">
                    <%


                        for (PropertyDescriptor pd : sampleData.get(0).keySet())
                        {
                            if (!pdsWithData.contains(pd.getName()))
                                continue;

                    %>
                        <th><%= h(StringUtils.isBlank(pd.getLabel()) ? pd.getName() : pd.getLabel()) %></th>
                    <%
                        }
                    %>
                    </tr>
                    <%
                        int rowNumber = 0;
                        for (Map<PropertyDescriptor, Object> row : sampleData)
                        {
                            rowNumber++;
                    %>
                        <tr <%=text(rowNumber % 2 == 1 ? "class=\"labkey-alternate-row\"" : "class=\"labkey-row\"")%>>
                    <%
                        for (Map.Entry<PropertyDescriptor, Object> entry : row.entrySet())
                        {
                            PropertyDescriptor pd = entry.getKey();
                            if (!pdsWithData.contains(pd.getName()))
                                continue;

                            Object value = bean.formatValue(pd, entry.getValue());
                    %>
                            <td><%= h(value) %></td>
                    <%
                            }
                    %>
                        </tr>
                    <%
                        }
                    %>
                </table>
            <%
                }
                else
                {
            %>
            <span class="labkey-error">No samples well groups were specified in the selected plate template.</span>
            <%
                }
            %>