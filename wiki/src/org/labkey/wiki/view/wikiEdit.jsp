<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.HtmlStringBuilder" %>
<%@ page import="org.labkey.api.util.element.Option.OptionBuilder" %>
<%@ page import="org.labkey.api.util.element.Select.SelectBuilder" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.api.wiki.WikiRendererType" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="org.labkey.wiki.WikiController.AttachFilesAction" %>
<%@ page import="org.labkey.wiki.model.WikiEditModel" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("tiny_mce/tiny_mce.js");
        dependencies.add("wiki/internal/wikiEdit.js");
    }
%>
<%
    JspView<WikiEditModel> me = (JspView<WikiEditModel>) HttpView.currentView();
    WikiEditModel model = me.getModelBean();
    final boolean existingWiki = null != model.getEntityId();
    final String ID_PREFIX = "wiki-input-";
    final HtmlString H_ID_PREFIX = h("wiki-input-");
    String sep;
%>
<script type="text/javascript">
    LABKEY._wiki.setProps({
        entityId: <%=q(model.getEntityId())%>,
        rowId: <%= model.getRowId() %>,
        name: <%=q(model.getName())%>,
        title: <%=q(model.getTitle())%>,
        body: <%=q(model.getBody())%>,
        parent: <%=model.getParent()%>,
        pageVersionId: <%=model.getPageVersionId()%>,
        rendererType: <%=q(model.getRendererType())%>,
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
                    <%=h(sep)%>
                    {
                        name: <%=q(att.getName())%>,
                        iconUrl: <%=q(getViewContext().getContextPath() + att.getFileIcon())%>,
                        downloadUrl: <%=q(downloadURL)%>
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
            <%=h(sep)%>
            <%=format%>: <%=q(format.getDisplayName())%>
        <%
                sep = ",";
            }
        %>
    });
    LABKEY._wiki.setURLs(<%= q(model.getRedir()) %>, <%= q(model.getCancelRedir()) %>);
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
                    <td class="labkey-form-label-nowrap"><label for="<%=H_ID_PREFIX%>name">Name<%=text(existingWiki ? helpPopup("Name", "Wiki pages can be renamed on the Manage page").toString() : " * " + helpPopup("Name", "This field is required"))%></label></td>
                    <td width="99%">
                        <input type="text" name="name" id="<%=H_ID_PREFIX%>name" size="80" maxlength="255"<%=text(existingWiki ? " class=\"labkey-form-label\" style=\"text-align:left;padding:1px 2px;\" readonly=\"readonly\"" : "")%>/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=H_ID_PREFIX%>title">Title</label></td>
                    <td width="99%">
                        <input type="text" name="title" id="<%=H_ID_PREFIX%>title" size="80" maxlength="255"/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label-nowrap"><label for="<%=H_ID_PREFIX%>shouldIndex">Index <%= helpPopup("Index", "Uncheck if the content on this page should not be searchable") %></label></td>
                    <td width="99%">
                        <input type="checkbox" name="shouldIndex" id="<%=H_ID_PREFIX%>shouldIndex"/>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=H_ID_PREFIX%>parent">Parent</label></td>
                    <td width="99%">
                    <%
                        SelectBuilder parentBuilder = new SelectBuilder()
                            .name("parent")
                            .id(ID_PREFIX + "parent")
                            .addStyle("width:600px");
                        parentBuilder.addOption(new OptionBuilder().value("-1").label("[none]").selected(model.getParent() == -1).build());
                        model.getPossibleParents().forEach(pp->{
                            StringBuilder indent = new StringBuilder();
                            int depth = pp.getDepth();
                            while (depth-- > 0)
                                indent.append("&nbsp;&nbsp;");

                            HtmlString label = HtmlStringBuilder.of(HtmlString.unsafe(indent.toString())).append(pp.getTitle() + " (" + pp.getName() + ")").getHtmlString();

                            parentBuilder.addOption(new OptionBuilder()
                                .value(String.valueOf(pp.getRowId()))
                                .label(label)
                                .selected(pp.getRowId() == model.getParent())
                                .build()
                            );
                        });
                    %>
                    <%=parentBuilder%>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=H_ID_PREFIX%>body">Body</label>
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
                                    <textarea rows="30" cols="80" style="width:100%; border:none;" id="<%=H_ID_PREFIX%>body" name="body"></textarea>
                                    <script type="text/javascript">LABKEY.Utils.tabInputHandler('#<%=H_ID_PREFIX%>body');</script>
                                </labkey:form>
                            </div>
                        </div>
                    </td>
                </tr>
                <tr>
                    <td class="labkey-form-label"><label for="<%=H_ID_PREFIX%>showAttachments">Files</label></td>
                    <td width="99%">
                        <table>
                            <tr>
                                <td>
                                    <label>
                                        <input type="checkbox" id="<%=H_ID_PREFIX%>showAttachments"/>
                                        Show Attached Files
                                    </label>
                                </td>
                            </tr>
                        </table>
                        <labkey:form action="<%=urlFor(AttachFilesAction.class)%>" method="POST" enctype="multipart/form-data" id="form-files">
                            <table id="wiki-existing-attachments"></table>
                            <br>
                            <table id="wiki-new-attachments"></table>
                            <a href="javascript:addFilePicker('wiki-new-attachments','filePickerLink')" id="filePickerLink"><img src="<%=getWebappURL("_images/paperclip.gif")%>">&nbsp;Attach a file</a>
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
                        <td colspan=2><b>Markdown Formatting Guide</b> (<a href="https://markdown-it.github.io/" target="_blank" rel="noopener noreferrer">More Help</a>)</td>
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
<div id="<%=H_ID_PREFIX%>window-change-format" class="x4-hidden">
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
                <label for="<%=H_ID_PREFIX%>window-change-format-to">Convert page format from <span id="<%=H_ID_PREFIX%>window-change-format-from" style="font-weight: bold;">(from)</span> to</label>
                <select id="<%=H_ID_PREFIX%>window-change-format-to"></select>
            </td>
        </tr>
    </table>
</div>
