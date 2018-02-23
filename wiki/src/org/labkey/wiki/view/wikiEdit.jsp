<%
/*
 * Copyright (c) 2008-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.attachments.Attachment" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="org.labkey.wiki.model.WikiEditModel" %>
<%@ page import="org.labkey.wiki.model.WikiTree" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("tiny_mce/tiny_mce.js");
        dependencies.add("wiki/internal/wikiEdit.js");
    }
%>
<%
    JspView<WikiEditModel> me = (JspView<WikiEditModel>) HttpView.currentView();
    WikiEditModel model = me.getModelBean();
    final String ID_PREFIX = "wiki-input-";
    String sep;
%>
<script type="text/javascript">
    LABKEY._wiki.setProps({
        entityId: <%=model.getEntityId()%>,
        rowId: <%=model.getRowId()%>,
        name: <%=model.getName()%>,
        title: <%=model.getTitle()%>,
        body: <%=model.getBody()%>,
        parent: <%=model.getParent()%>,
        pageVersionId: <%=model.getPageVersionId()%>,
        rendererType: <%=model.getRendererType()%>,
        webPartId: <%=model.getWebPartId()%>,
        showAttachments: <%=model.isShowAttachments()%>,
        shouldIndex: <%=model.isShouldIndex()%>,
        isDirty: false,
        useVisualEditor: <%=model.useVisualEditor()%>
    });
    LABKEY._wiki.setAttachments([
        <%
        if (model.hasAttachments())
        {
            sep = "";
            for (Attachment att : model.getWiki().getAttachments())
            {
                ActionURL downloadURL = WikiController.getDownloadURL(getContainer(), model.getWiki(), att.getName());
                %>
                    <%=sep%>{name: <%=PageFlowUtil.jsString(att.getName())%>,
                             iconUrl: <%=PageFlowUtil.jsString(getViewContext().getContextPath() + att.getFileIcon())%>,
                             downloadUrl: <%=PageFlowUtil.jsString(downloadURL.toString())%>
                            }
                <%
                sep = ",";
            }
        }
        %>
    ]);
    LABKEY._wiki.setFormats({
        <%
            sep = "";
            for (WikiRendererType format : WikiRendererType.values())
            {
        %>
            <%=sep%>
            <%=format.name()%>: <%=PageFlowUtil.jsString(format.getDisplayName())%>
        <%
                sep = ",";
            }
        %>
    });
    LABKEY._wiki.setURLs(<%= model.getRedir() %>, <%= model.getCancelRedir() %>);
</script>
<div id="status" class="labkey-status-info" style="display: none; width: 99%;">(status)</div>
<table width=99%;>
    <tr>
        <td width="50%" align="left" style="white-space: nowrap;">
            <%= button("Save & Close").submit(true).onClick("LABKEY._wiki.onFinish();").id("wiki-button-finish") %>
            <%= button("Save").submit(true).onClick("LABKEY._wiki.onSave();").id("wiki-button-save") %>
            <%= button("Cancel").onClick("LABKEY._wiki.onCancel();") %>
        </td>
        <td width="50%" align="right" style="white-space: nowrap;">
            <% if (model.canUserDelete()) { %>
                <%= button("Delete Page").onClick("return false;").id(ID_PREFIX + "button-delete").enabled(false) %>
            <% } %>
            <%= button("Convert To...").onClick("LABKEY._wiki.showConvertWindow()").id(ID_PREFIX + "button-change-format") %>
            <%= button("Show Page Tree").onClick("LABKEY._wiki.showHideToc()").id(ID_PREFIX + "button-toc") %>
        </td>
    </tr>
</table>
<table width="99%">
    <tr>
        <td style="vertical-align:top;width:99%">
            <table class="lk-fields-table" style="width:99%">
                <tr>
                    <td class="labkey-form-label-nowrap"><label for="<%=h(ID_PREFIX)%>name">Name * <%= PageFlowUtil.helpPopup("Name", "This field is required") %></label></td>
                    <td width="99%">
                        <input type="text" name="name" id="<%=h(ID_PREFIX)%>name" size="80" maxlength="255"/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=h(ID_PREFIX)%>title">Title</label></td>
                    <td width="99%">
                        <input type="text" name="title" id="<%=h(ID_PREFIX)%>title" size="80" maxlength="255"/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label-nowrap"><label for="<%=h(ID_PREFIX)%>shouldIndex">Index <%= PageFlowUtil.helpPopup("Index", "Uncheck if the content on this page should not be searchable") %></label></td>
                    <td width="99%">
                        <input type="checkbox" name="shouldIndex" id="<%=h(ID_PREFIX)%>shouldIndex"/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=h(ID_PREFIX)%>parent">Parent</label></td>
                    <td width="99%">
                        <select name="parent" id="<%=text(ID_PREFIX)%>parent">
                            <option<%=selected(model.getParent() == -1)%> value="-1">[none]</option>
                            <%
                                for (WikiTree possibleParent : model.getPossibleParents())
                                {
                                    String indent = "";
                                    int depth = possibleParent.getDepth();
                                    String parentTitle = possibleParent.getTitle();
                                    while (depth-- > 0)
                                        indent = indent + "&nbsp;&nbsp;";
                                    %><option<%=selected(possibleParent.getRowId() == model.getParent())%> value="<%= possibleParent.getRowId() %>"><%=text(indent)%><%= h(parentTitle) %> (<%= possibleParent.getName() %>)</option><%
                                }
                            %>
                        </select>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=h(ID_PREFIX)%>body">Body</label>
                        <br/><span id="wiki-current-format"></span>
                    </td>
                    <td width="99%">
                        <div>
                          <!--
                             start the wiki-tab-strip tabs and spacer out hidden, so that it does not appear and then disappear
                             when wiki editor loads and hides it with javascript.
                          -->
                            <ul id="wiki-tab-strip" class="labkey-tab-strip">
                                <li id="wiki-tab-visual" class="labkey-tab-active" style="display: none;"><a href="#">Visual</a></li>
                                <li id="wiki-tab-source" class="labkey-tab-inactive" style="display: none;"><a href="#">Source</a></li>
                                <div class="x4-clear"></div>
                            </ul>
                            <div id="wiki-tab-strip-spacer" class="labkey-tab-strip-spacer" style="display: none;"></div>
                            <div id="wiki-tab-content" class="labkey-tab-strip-content" style="padding: 0;">
                                <labkey:form action="">
                                    <textarea rows="30" cols="80" style="width:100%; border:none;" id="<%=ID_PREFIX%>body" name="body"></textarea>
                                    <script type="text/javascript">LABKEY.Utils.tabInputHandler('#<%=ID_PREFIX%>body');</script>
                                </labkey:form>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=h(ID_PREFIX)%>showAttachments">Files</label></td>
                    <td width="99%">
                        <table>
                            <tr>
                                <td>
                                    <input type="checkbox" id="<%=ID_PREFIX%>showAttachments"/>
                                    Show Attached Files
                                </td>
                            </tr>
                        </table>
                        <labkey:form action="<%=h(buildURL(WikiController.AttachFilesAction.class))%>" method="POST" enctype="multipart/form-data" id="form-files">
                            <table id="wiki-existing-attachments"></table>
                            <table id="wiki-new-attachments"></table>
                            <a id="wiki-file-link"><img src="<%=getWebappURL("_images/paperclip.gif")%>">Attach a file</a>
                        </labkey:form>
                    </td>
                </tr>
            </table>
            <div id="wiki-help-HTML-visual" style="display:none">
                <table>
                    <tr>
                        <td colspan=2><b>HTML Formatting Guide:</b></td>
                    </tr>
                    <tr>
                        <td>Link to a wiki page</td>
                        <td>Select text and right click. Then select "Insert/edit link."
                         Type the name of the wiki page in "Link URL" textbox.</td>
                    </tr>
                    <tr>
                        <td>Link to an attachment</td>
                        <td>Select text and right click. Then select "Insert/edit link."
                         Type the name of the attachment with the file extension in "Link URL" textbox.</td>
                    </tr>
                </table>
            </div>
            <div id="wiki-help-HTML-source" style="display:none">
                <table>
                    <tr>
                        <td colspan=2><b>HTML Source Formatting Guide:</b></td>
                    </tr>
                    <tr>
                        <td>Link to a wiki page</td>
                        <td>&lt;a href="pageName"&gt;My Page&lt;/a&gt;</td>
                    </tr>
                    <tr>
                        <td>Link to an attachment</td>
                        <td>&lt;a href="attachment.doc"&gt;My Document&lt;/a&gt;</td>
                    </tr>
                    <tr>
                        <td>Show an attached image</td>
                        <td>&lt;img src="imageName.jpg"&gt;</td>
                    </tr>
                </table>
            </div>

            <div id="wiki-help-RADEOX-source" style="display:none">
                <table>
                    <tr>
                        <td colspan=2><b>Wiki Formatting Guide</b> (<%=helpLink("wikiSyntax", "more help")%>):</td>
                    </tr>
                    <tr>
                        <td>link to page in this wiki&nbsp;&nbsp;</td>
                        <td>[pagename] or [Display text|pagename]</td>
                    </tr>
                    <tr>
                        <td>external link</td>
                        <td>http://www.google.com or {link:Display text|http://www.google.com}</td>
                    </tr>
                    <tr>
                        <td>picture</td>
                        <td>[attach.jpg] or {image:http://www.website.com/somepic.jpg}</td>
                    </tr>
                    <tr>
                        <td>bold</td>
                        <td>**like this**</td>
                    </tr>
                    <tr>
                        <td>italics</td>
                        <td>~~like this~~</td>
                    </tr>
                    <tr>
                        <td>bulleted list</td>
                        <td>- list item</td>
                    </tr>
                    <tr>
                        <td>numbered List</td>
                        <td>1. list item</td>
                    </tr>
                    <tr>
                        <td>line break (&lt;br&gt;)</td>
                        <td>\\</td>
                    </tr>
                </table>
            </div>
            <div id="wiki-help-TEXT_WITH_LINKS-source" style="display:none">
                <table>
                    <tr>
                        <td><b>Plain Text Formatting Guide:</b></td>
                    </tr>
                    <tr>
                        <td>In plain text format, web addresses (http://www.labkey.com) will be automatically converted
                        into active hyperlinks when the page is shown, but all other text will appear as typed.</td>
                    </tr>
                </table>
            </div>
            <div id="wiki-help-MARKDOWN-source" style="display:none">
                <table>
                    <tr>
                        <td colspan=2><b>Markdown Formatting Guide</b> (<a href="https://markdown-it.github.io/" target="_blank">More Help</a>)</td>
                    </tr>
                    <tr>
                        <td>Headers</td>
                        <td># H1 | ## H2 | ### H3</td>
                    </tr>
                    <tr>
                        <td>Bold text</td>
                        <td>**use double asterisks**</td>
                    </tr>
                    <tr>
                        <td>Italics</td>
                        <td>_use underlines_</td>
                    </tr>
                    <tr>
                        <td>Links</td>
                        <td>[I'm an inline-style link with title](https://www.google.com "Google's Homepage")</td>
                    </tr>
                    <tr>
                        <td>Images</td>
                        <td>![I'm an attached image](logo.jpg)</td>
                    </tr>
                    <tr>
                        <td>Code</td>
                        <td>``` js
                            var foo = function (bar) {
                            return bar++;
                            };
                            ``` </td>
                    </tr>
                    <tr>
                        <td>Lists</td>
                        <td>Create a list by starting a line with '+', '-', or '*'</td>
                    </tr>
                </table>
            </div>
        </td>
        <td width="1%" style="vertical-align:top;">
            <div id="wiki-toc-tree" style="display: none;"></div>
        </td>
    </tr>
</table>
<div id="<%=ID_PREFIX%>window-change-format" class="x4-hidden">
    <table>
        <tr>
            <td>
                <span class="labkey-error">WARNING:</span>
                Changing the format of your page will change the way
                your page is interpreted, causing it to appear at least differently,
                if not incorrectly. In most cases, manual adjustment to the
                page content will be necessary. You should not perform this
                operation unless you know what you are doing.
            </td>
        </tr>
        <tr>
            <td>
                <label for="<%=ID_PREFIX%>window-change-format-to">Convert page format from <span id="<%=ID_PREFIX%>window-change-format-from" style="font-weight: bold;">(from)</span> to</label>
                <select id="<%=ID_PREFIX%>window-change-format-to"></select>
            </td>
        </tr>
    </table>
</div>
