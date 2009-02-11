<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    String url = (String)HttpView.currentModel();
%>
<iframe id="fileFrame" frameborder=0 src="<%=h(url)%>" width="100%">Your browser does not support inline frames, try Mozilla or Internet Explorer</iframe>
<script type="text/javascript">
    LABKEY.requiresExtJs(true);
</script>
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
