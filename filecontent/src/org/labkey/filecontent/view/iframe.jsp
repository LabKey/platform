<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String url = (String)HttpView.currentModel();
%>
<iframe id="fileFrame" frameborder=0 src="<%=h(url)%>" width="100%">Your browser does not support inline frames, try Mozilla or Internet Explorer</iframe>
<script type="text/javascript">
var top = 100;
function resizeFrame()
{
    var frame = Ext.get("fileFrame");

    var viewHeight = Ext.lib.Dom.getViewportHeight();
    var top = frame.getY();
    frame.setHeight(viewHeight-top-6);
}
Ext.onReady(resizeFrame);
Ext.EventManager.onWindowResize(resizeFrame);
</script>
