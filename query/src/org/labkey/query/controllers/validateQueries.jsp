<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%
/*
 * Copyright (c) 2006-2009 LabKey Corporation
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
<style type="text/css">
    td.schema
    {
        background-color: #CCCCCC;
        border-bottom: 1px solid #AAAAAA;
        font-weight: bold;
    }
    td.valid-msg
    {
        background-color: #00FF00
    }
    td.invalid-msg
    {
        background-color: #FF4444
    }
</style>
<div id="vq-status" class="labkey-status-info"></div>
<table style="width: 100%">
    <tr>
        <td style="vertical-align:top"><button id="btn-validate" disabled="1">Validate Queries</button></td>
        <td><span style="font-weight:bold">Options:</span><br/>
            <input type="radio" name="includeAllColumns" id="rb-include-all" checked="1"><label for="rb-include-all">Test All Columns</label>
            <br/>
            <input type="radio" name="includeAllColumns" id="rb-include-defvis"><label for="rb-include-defvis">Test Default Visible Columns</label>
        </td>
    </tr>
</table>
<div id="vq-schemas"></div>
<script type="text/javascript">
    LABKEY.requiresScript("query/validateQueries.js", true);
</script>
