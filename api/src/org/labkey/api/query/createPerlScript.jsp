<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
%><%@ page import="org.labkey.api.query.CreateJavaScriptModel" %><%@ page import="org.labkey.api.view.JspView" %><%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.query.CreatePerlScriptModel" %><%
    JspView<CreatePerlScriptModel> me = (JspView<CreatePerlScriptModel>) HttpView.currentView();
    CreatePerlScriptModel model = me.getModelBean();
    me.getViewContext().getResponse().setContentType("text/plain");
%>my $results = LABKEY::Query::selectRows(
<%=model.getStandardScriptParameters(4, true)%>
);

#output the results in tab-delimited format
my @fields;
foreach my $field (@{$results->{metaData}->{fields}}){
    push(@fields, $field->{name});
}
print join("\t", @fields) . "\n";

foreach my $row (@{$results->{rows}}){
    my @line;
    foreach (@fields){
        if ($row->{$_}){
            push(@line, $row->{$_});
        }
        else {
            push(@line, "");
        }
    }
    print join("\t", @line);
    print "\n";
};

