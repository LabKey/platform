<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.util.AppProps" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<% 
   ActionURL urlSet = new ActionURL("Experiment", "setFlag", "");
    urlSet.addParameter("flagSessionId", request.getSession().getId());
%>

<script type="text/javascript">
    var defaultComment = "Flagged for review";
    function setFlag(flagId)
    {
        var urlSet = <%=q(urlSet.toString())%> + '&lsid=' + flagId;
        var images = [];
        var allImages = document.images;
        var allImageCount = allImages.length;
        for (var i = 0; i < allImageCount; i ++)
        {
            var image = allImages[i];
            if (image.getAttribute("flagId") == flagId)
            {
                images[images.length] = image;
            }
        }
        if (images.length == 0)
            return false;
        var comment = images[0].getAttribute("title");
        if (!comment)
            comment = defaultComment;
        for (var i = 0; i < images.length; i ++)
        {
            var img = images[i];
            img.src = <%=q(AppProps.getInstance().getContextPath() + "/Experiment/flagWait.gif")%>;
        }
        var x,y,fNS;
        if (window.pagexOffset != undefined)
        {
            fNS = true;
            x = window.pagexOffset;
            y = window.pageyOffset;
        }
        else
        {
            x = document.body.scrollLeft;
            y = document.body.scrollTop;
        }
        var newComment = window.prompt("Enter a comment", comment ? comment : defaultComment);
        function restoreScrollPosition() {
            window.scrollTo(x, y);
        }
        window.setTimeout(restoreScrollPosition, 1);
        if (newComment != null)
        {
            if (newComment)
                defaultComment = newComment;
            urlSet += "&comment=" + escape(newComment) + "&unique=" + new Date().getTime();
        }
        for (var i = 0; i < images.length; i ++)
        {
            var img = images[i];
            img.src = urlSet;
            if (newComment == null)
            {
                // no change
            }
            else if (newComment)
            {
                img.setAttribute("title", newComment);
            }
            else
            {
                img.removeAttribute("title");
            }
        }
        return false;
    }
</script>