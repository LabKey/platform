<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL"%>
<%@ page import="org.labkey.api.util.PageFlowUtil"%>
<%@ page import="org.labkey.api.settings.AppProps" %>
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