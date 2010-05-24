<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0                                                   m
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript" language="javascript">
    LABKEY.requiresScript("ActionsAdmin.js");
</script>

<script type="text/javascript">
    Ext.onReady(function(){
        var prefDlg = new LABKEY.EmailPreferencesPanel({renderTo: 'emailDiv'});
        prefDlg.show();
    });
</script>

<div id='emailDiv'/>