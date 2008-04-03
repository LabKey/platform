<%@ page import="org.labkey.api.view.*" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.ArrayList" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%><%
    PopupMenuView me = (PopupMenuView) HttpView.currentView();
    NavTree navTree = me.getNavTree();
    String contextPath = request.getContextPath();
%><script type="text/javascript">
LABKEY.requiresMenu();
</script><script type="text/javascript">
(function(){
    var menu;
    var elementId = <%=q(me.getElementId())%>;
    var model = <%=renderMenuModel("menu" + me.getElementId(), navTree.getChildren())%>;

    var showEvent = function(event)
    {
        YAHOO.util.Event.stopPropagation(event);
        menu.render(document.body);
        onWindowResize(event);
        menu.show();
    }

    var hideEvent = function(event)
    {
        menu.hide();
    }

    var onWindowLoad = function(event)
    {
        menu = new YAHOO.widget.Menu(<%=q("menu" + me.getElementId())%>, {xy:[0,0], constraintoviewport:true, submenualignment:["tl", "tr"]});
        menu.addItems(model);
        onWindowResize(event);
        YAHOO.util.Event.addListener(<%=q(me.getElementId())%>, "mousedown", showEvent);
        YAHOO.util.Event.addListener(<%=q("menu" + me.getElementId())%>, "click", hideEvent);
    }

    var onWindowResize = function(event)
    {
        if (!menu.element)
            return;
        
        var elt = YAHOO.util.Dom.get(elementId);
        var y = YAHOO.util.Dom.getY(elt) + elt.offsetHeight;
    <%
        if (me.getAlign() == PopupMenuView.Align.RIGHT)
        {
    %>
        var x = YAHOO.util.Dom.getX(elt) + elt.offsetWidth - menu.element.offsetWidth;
    <%
        }
        else
        {
    %>
        var x = YAHOO.util.Dom.getX(elt);
    <%
        }
    %>
        menu.moveTo(x, y);
    }
    YAHOO.util.Event.addListener(window, "load", onWindowLoad);
    YAHOO.util.Event.addListener(window, "resize", onWindowResize);
})();

</script><%
    if (null != navTree.getKey())
    {
        %><img id="<%=h(me.getElementId())%>" style="cursor:pointer;" src="<%=contextPath%>/<%=h(navTree.getKey())%>.button?style=boldMenu" alt="<%=h(navTree.getKey())%>"><%
    }
%>
<%!
    String renderMenuModel(String id, NavTree[] trees)
    {
        List<StringBuilder> sections = new ArrayList<StringBuilder>();
        int submenuIndex = 1;
        String sep = "";
        StringBuilder sb = new StringBuilder();
        sections.add(sb);

        for (NavTree tree : trees)
        {
            if (tree == NavTree.MENU_SEPARATOR)
            {
                sb = new StringBuilder();
                sep = "";
                sections.add(sb);
                continue;
            }

            sb.append(sep);
            String title = tree.getKey();
            sb.append("{").append("text:").append(q(title));
            if (null != tree.getValue())
                sb.append(",").append("url:").append(q(tree.getValue()));
            if (null != tree.getChildren() && tree.getChildren().length > 0)
            {
                String subMenuId = id + "_" + submenuIndex++;
                sb.append(",\n submenu:{id:").append(q(subMenuId)).append(",")
                        .append("itemdata: ").append(renderMenuModel(subMenuId, tree.getChildren()))
                        .append("}\n");
            }
            sb.append("}\n");
            sep = ",";
        }

        if (sections.size() == 1)
            return "[" + sb + "]";

        sb = new StringBuilder("[");
        sep = "";
        for (StringBuilder section : sections)
        {
            sb.append(sep).append("[").append(section).append("]");
            sep = ",";
        }
        sb.append("]");

        return sb.toString();
    }
%>