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
%>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.LinkedList" %>
<%@ page import="org.labkey.core.admin.writer.FolderSerializationRegistryImpl" %>
<%@ page import="org.labkey.api.admin.FolderWriter" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.writer.Writer" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
ViewContext context = HttpView.currentContext();
Container c = context.getContainer();
%>

<labkey:errors/>
<div id="form"></div>

<script type="text/javascript">

var formItems = [];

formItems.push({xtype: "label", text: "Folder objects to copy:"});
<%
    Collection<FolderWriter> writers = new LinkedList<FolderWriter>(FolderSerializationRegistryImpl.get().getRegisteredFolderWriters());
    boolean showFormatOptions = false;
    for (FolderWriter writer : writers)
    {
        String parent = writer.getSelectionText();
        if (null != parent && writer.show(c))
        {
            %>formItems.push({xtype: "checkbox", hideLabel: true, boxLabel: "<%=parent%>", name: "types", itemId: "<%=parent%>", inputValue: "<%=parent%>", checked: true, objectType: "parent"});<%

            Set<Writer> children = writer.getChildren();
            if (null != children && children.size() > 0)
            {
                for (Writer child : children)
                {
                    if (null != child.getSelectionText())
                    {
                        String text = child.getSelectionText();
                        %>
                        formItems.push({xtype: "checkbox", style: {marginLeft: "20px"}, hideLabel: true, boxLabel: "<%=text%>", name: "types", itemId: "<%=text%>",
                            inputValue: "<%=text%>", checked: true, objectType: "child", parentId: "<%=parent%>"});
                        <%
                    }
                }
            }
        }
    }
%>

var form = new LABKEY.ext.FormPanel({
    border: false,
    standardSubmit: true,
    items:formItems,
    buttons:[{text:'Create', type:'submit', handler:submit}],
    buttonAlign:'left'
});

function submit()
{
    form.getForm().submit();
}

Ext.onReady(function() {
    form.render('form');

    // add listeners to each of the parent checkboxes
    var parentCbs = form.find("objectType", "parent");
    Ext.each(parentCbs, function(cb) {
        cb.on("check", function(cmp, checked) {
            var children = form.find("parentId", cb.getItemId());
            Ext.each(children, function(child) {
                child.setValue(checked);
                child.setDisabled(!checked);
            });
        });
    });
});

</script>

