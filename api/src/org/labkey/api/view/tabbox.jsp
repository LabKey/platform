<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.TabBoxView" %>
<%@ page import="org.springframework.web.servlet.ModelAndView" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.gwt.client.util.StringUtils" %>
<%@page extends="org.labkey.api.jsp.JspBase"%>
<%
    TabBoxView me = (TabBoxView) HttpView.currentModel();
%>
<div id="tabBoxDiv" class="extContainer"></div>
<script type="text/javascript">
Ext.onReady(function(){
    var p = new Ext.TabPanel({
        activeItem : 0,
        border : false,
        defaults: {style : {padding:'5px'}},
        items : [<%
            String comma = "";
            int index = 0;
            for (ModelAndView v : me.getViews())
            {
                String title = (v instanceof WebPartView) ? ((WebPartView)v).getTitle() : ("Tab " + index);
                if (StringUtils.trimToNull(title) == null)
                    title = "Tab " + index;
                String id = "tabWebPart" + index;
                %><%=comma%>{title:<%=PageFlowUtil.jsString(title)%>, contentEl:<%=PageFlowUtil.jsString(id)%>, autoHeight:true}<%
                comma = ","; index++;
            }
        %>]
    });

    p.render('tabBoxDiv');
    p.strip.applyStyles({'background':'#ffffff'});
//    p.stripWrap.applyStyles({'background':'#ffffff'});
//    p.stripWrap.parent().applyStyles({'background':'#ffffff'});
});
</script>