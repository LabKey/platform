<%
    /*
     * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.security.SecurityUrls" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ page import="org.labkey.issue.model.Issue" %>
<%@ page import="org.labkey.issue.model.IssueListDef" %>
<%@ page import="org.labkey.issue.model.IssueManager" %>
<%@ page import="org.labkey.issue.model.IssueManager.EntryTypeNames" %>
<%@ page import="org.labkey.issue.model.IssuePage" %>
<%@ page import="org.labkey.issue.view.IssuesListView" %>
<%@ page import="org.springframework.validation.BindException" %>
<%@ page import="org.springframework.validation.ObjectError" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.function.Function" %>
<%@ page import="java.util.stream.Stream" %>
<%@ page import="java.util.stream.Collectors" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
        dependencies.add("Ext4");
    }
%>
<%
    final JspView<IssuePage> me = (JspView<IssuePage>) HttpView.currentView();
    final IssuePage bean = me.getModelBean();
    final Issue issue = bean.getIssue();
    final ViewContext context = getViewContext();
    final Container c = getContainer();
    final User user = getUser();
    final String focusId = bean.isInsert() ? "title" : "comment";
    final int emailPrefs = IssueManager.getUserEmailPreferences(c, user.getUserId());
    final boolean newUI = PageFlowUtil.useExperimentalCoreUI();
    IssueListDef issueListDef = IssueManager.getIssueListDef(getContainer(), issue.getIssueDefName());
    if (issueListDef == null)
        issueListDef = IssueManager.getIssueListDef(issue);
    IssueManager.EntryTypeNames names = IssueManager.getEntryTypeNames(c, issueListDef.getName());


    BindException errors = bean.getErrors();
    String completionUrl = urlProvider(SecurityUrls.class).getCompleteUserReadURLPrefix(c);
    ActionURL cancelURL;

    if (bean.getReturnURL() != null)
    {
        cancelURL = bean.getReturnURL();
    }
    else if (issue.getIssueId() > 0)
    {
        cancelURL = IssuesController.issueURL(c, IssuesController.DetailsAction.class).addParameter("issueId", issue.getIssueId());
    }
    else
    {
        cancelURL = IssuesController.issueURL(c, IssuesController.ListAction.class).addParameter(IssuesListView.ISSUE_LIST_DEF_NAME, issue.getIssueDefName()).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
    }

    // create collections for additional custom columns and distribute them evenly in the form
    Map<String, DomainProperty> propertyMap = bean.getCustomColumnConfiguration().getPropertyMap();
    List<DomainProperty> column1Props = new ArrayList<>();
    List<DomainProperty> column2Props = new ArrayList<>();
    List<DomainProperty> extraColumns = new ArrayList<>();

    final String popup = getNotifyHelpPopup(emailPrefs, issue.getIssueId(), IssueManager.getEntryTypeNames(c, issueListDef.getName()));

    // todo: don't include if the lookup is empty (was previously IssuePage.hasKeywords)
    extraColumns.addAll(Stream.of("type", "area", "priority", "milestone")
            .filter(propertyMap::containsKey)
            .map((Function<String, DomainProperty>) propertyMap::get)
            .collect(Collectors.toList()));

    //this is the rowspan used for the 2nd and 3rd columns
    int rowSpan = 2 + extraColumns.size();

    int i=0;
    for (DomainProperty prop : bean.getCustomColumnConfiguration().getCustomProperties())
    {
        if ((i++ % 2) == 0)
            column1Props.add(prop);
        else
            column2Props.add(prop);
    }
%>

<script type="text/javascript">
    var showLess = false;

    function filterRe(e, input, re)
    {
        if (e.isSpecialKey())
            return true;

        var cc = String.fromCharCode(e.getCharCode());
        if (!cc)
            return true;

        if (!re.test(cc))
        {
            if (e.stopPropagation) {
                e.stopPropagation();
            } else {
                e.cancelBubble = true;
            }
            if (e.preventDefault) {
                e.preventDefault();
            } else {
                e.returnValue = false;
            }
            return false;
        }

        return true;
    }

    function filterCommaSepNumber(e, input)
    {
        return filterRe(e, input, /^[\d,\s]+$/);
    }

    function showMoreTimestamps() {
        var allStampsDiv = document.getElementById("allTimeStamps");
        var stampExpandIcon = document.getElementById("stampExpandIcon");

        if (!showLess) {
            stampExpandIcon.className = 'fa fa-caret-up';
            allStampsDiv.style.display = "block";
        } else {
            stampExpandIcon.className = 'fa fa-caret-down';
            allStampsDiv.style.display = "none";
        }

        showLess = !showLess;
    }
</script>
<% if (newUI) {%>
<script type="text/javascript">
    (function($){

        var extraFields = [];

        <%
        ArrayList<DomainProperty> propertyArr = new ArrayList<>(extraColumns);
        propertyArr.addAll(bean.getCustomColumnConfiguration().getCustomProperties());
        for (DomainProperty prop : propertyArr)
        {%>
            extraFields.push(<%=q(prop.getName().toLowerCase())%>);
        <%}%>

        $(function() {
            $("input[name='title']").attr("tabindex", "1");
            $("select[name='assignedTo']").attr("tabindex", "2");
            $("input[name='related']").attr("tabindex", "3");
            $("textarea[name='notifyListArea']").attr("tabindex", "4");

            for (var i=5; i < extraFields.length; i++){
                var e = $("[name=" + extraFields[i] + "]");
                if (e) {
                    e.attr("tabindex", i);
                }
            }
        });
    })(jQuery);
</script>
<%}
else
{%>
<script type="text/javascript">
    (function($){

        var column1 = [];
        var column2 = [];
        var startColOneIdx = 11;
        var startColTwoIdx = 21;

        <%
            for (DomainProperty prop : column1Props)
            {%>
        column1.push(<%=q(prop.getName().toLowerCase())%>);<%
            }
            for (DomainProperty prop : column2Props)
            {%>
        column2.push(<%=q(prop.getName().toLowerCase())%>);<%
            }
        %>

        $(function() {
            $("input[name='title']").attr("tabindex", "1");
            $("select[name='assignedTo']").attr("tabindex", "2");
            $("select[name='type']").attr("tabindex", "3");
            $("select[name='area']").attr("tabindex", "4");
            $("select[name='priority']").attr("tabindex", "5");
            $("select[name='milestone']").attr("tabindex", "6");
            $("textarea[name='comment']").attr("tabindex", "7");
            $("select[name='resolution']").attr("tabindex", "8");
            $("input[name='duplicate']").attr("tabindex", "9");
            $("input[name='related']").attr("tabindex", "10");

            for (var i=0; i < column1.length; i++){
                $("[name=" + column1[i] + "]").attr("tabindex", startColOneIdx++);
            }

            for (i=0; i < column2.length; i++){
                $("[name=" + column2[i] + "]").attr("tabindex", startColTwoIdx++);
            }
        });
    })(jQuery);
</script>
<%}%>
<labkey:form method="POST" onsubmit="LABKEY.setSubmit(true); return true;" enctype="multipart/form-data" layout="horizontal">
    <% if (!newUI) {%>


    <table><%
        if (null != errors && 0 != errors.getErrorCount())
        {
            for (ObjectError e : errors.getAllErrors())
            {%>
        <tr><td colspan=3><font class="labkey-error"><%=h(context.getMessage(e))%></font></td></tr><%
                }
            }
            if (!bean.getRequiredFields().isEmpty())
            {%>
        <tr><td>Fields marked with an asterisk * are required.</td></tr><%
            }
        %>
    </table>

    <table>
        <tr>
            <td align="right" valign="top"><%= button("Save").submit(true).attributes("name=\"" + bean.getAction() + "\"").disableOnClick(true) %><%= button("Cancel").href(cancelURL) %></td>
        </tr>
        <tr><%
            if (bean.isInsert())
            {%>
            <%=text(bean.renderLabel(propertyMap.get("Title"), getViewContext()))%><%
            }
            else
            {%>
            <td class="labkey-form-label">Issue <%=issue.getIssueId()%></td><%
                }%>
            <td colspan="3">
                <%=text(bean.writeInput("title", issue.getTitle(), "id=title style=\"width:100%;\""))%>
            </td></tr>
        <tr>
            <td class="labkey-form-label"><%=text(bean.getLabel("Status", true))%></td><td><%=h(issue.getStatus())%></td>
            <td rowspan="<%=h(rowSpan)%>" valign="top">
                <table>
                    <tr><td class="labkey-form-label"><%=text(bean.getLabel("Opened", true))%></td><td nowrap="true"><%=h(bean.writeDate(issue.getCreated()))%> by <%=h(issue.getCreatedByName(user))%></td></tr>
                    <tr><td class="labkey-form-label">Changed</td><td nowrap="true"><%=h(bean.writeDate(issue.getModified()))%> by <%=h(issue.getModifiedByName(user))%></td></tr>
                    <tr><td class="labkey-form-label"><%=text(bean.getLabel("Resolved", true))%></td><td nowrap="true"><%=h(bean.writeDate(issue.getResolved()))%><%=text(issue.getResolvedBy() != null ? " by " : "")%> <%=h(issue.getResolvedByName(user))%></td></tr>
                    <%=text(bean.renderColumn(propertyMap.get("resolution"), getViewContext(), bean.isVisible("resolution"), bean.isReadOnly("resolution")))%>
                    <%
                        if (bean.isVisible("resolution") || !"open".equals(issue.getStatus()))
                        {%>
                    <tr><td class="labkey-form-label">Duplicate</td><td><%
                        if (bean.isVisible("duplicate"))
                        {
                            if("Duplicate".equals(issue.getResolution()))
                            {
                                //Enabled duplicate field.%>
                        <%=text(bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "type=\"number\" min=\"1\""))%><%
                        }
                        else
                        {
                            //Disabled duplicate field.%>
                        <%=text(bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "disabled"))%><%
                            }
                        %>
                        <script type="text/javascript">
                            var duplicateInput = document.getElementsByName('duplicate')[0];
                            var duplicateOrig = duplicateInput.value;
                            var resolutionSelect = document.getElementsByName('resolution')[0];
                            function updateDuplicateInput()
                            {
                                // The options don't have an explicit value set, so look for the display text instead of
                                // the value
                                if (resolutionSelect.selectedIndex >= 0 &&
                                        resolutionSelect.options[resolutionSelect.selectedIndex].text == 'Duplicate')
                                {
                                    duplicateInput.disabled = false;
                                }
                                else
                                {
                                    duplicateInput.disabled = true;
                                    duplicateInput.value = duplicateOrig;
                                }
                            }
                            if (window.addEventListener)
                                resolutionSelect.addEventListener('change', updateDuplicateInput, false);
                            else if (window.attachEvent)
                                resolutionSelect.attachEvent('onchange', updateDuplicateInput);
                        </script><%
                        }
                        else
                        {
                            if(issue.getDuplicate() != null)
                            {%>
                        <a href="<%=IssuesController.getDetailsURL(c, issue.getDuplicate(), false)%>"><%=issue.getDuplicate()%></a><%
                                }
                            }%>
                    </td></tr><%
                    }%>
                    <tr><td class="labkey-form-label"><%=text(bean.getLabel("Related", false))%></td><td>
                                <%=text(bean.writeInput("related", issue.getRelated() == null ? null : issue.getRelated(), "id=related"))%>

                        <script type="text/javascript">
                            Ext4.EventManager.on(document.getElementsByName('related')[0], 'keypress', filterCommaSepNumber);
                        </script><%

                    for (DomainProperty prop : column1Props)
                    {%>
                                <%=text(bean.renderColumn(prop, getViewContext()))%><%
                    }%>
                </table>
            </td>
            <td valign="top" rowspan="<%=h(rowSpan)%>"><table style="width: 100%;">
                <tr><td class="labkey-form-label">Closed</td><td><%=text(bean.writeDate(issue.getClosed()))%><%=text(issue.getClosedBy() != null ? " by " : "")%><%=h(issue.getClosedByName(user))%></td></tr><%
                if (bean.isVisible("notifyList"))
                {%>
                <tr>
                    <td class="labkey-form-label-nowrap"><%=text(bean.getLabel("NotifyList", true))%><%=text(popup)%><br/><br/><%
                        if (!user.isGuest())
                        {
                            if (bean.isInsert())
                            {%>
                        <%= textLink("email prefs", IssuesController.issueURL(c, IssuesController.EmailPrefsAction.class))%><%
                        }
                        else
                        {%>
                        <%= textLink("email prefs", IssuesController.issueURL(c, IssuesController.EmailPrefsAction.class).addParameter("issueId", issue.getIssueId()))%><%
                                }
                            }%>
                    </td>
                    <td>
                        <labkey:autoCompleteTextArea name="notifyList" id="notifyList" url="<%=h(completionUrl)%>" rows="4" tabindex="20" cols="40" value="<%=h(bean.getNotifyListString(false))%>"/>
                    </td>
                </tr><%
            }
            else
            {%>
                <tr><td class="labkey-form-label">Notify</td><td><%=text(bean.getNotifyList())%></td></tr><%
                }
                for (DomainProperty prop : column2Props)
                {%>
                <%=text(bean.renderColumn(prop, getViewContext()))%><%
                }%>
            </table></td>
        </tr>
        <%=text(bean.renderColumn(propertyMap.get("assignedTo"), getViewContext(), bean.isVisible("assignedTo"), bean.isReadOnly("assignedTo")))%>
        <%
            for (DomainProperty prop : extraColumns)
            {%>
        <%=text(bean.renderColumn(prop, getViewContext()))%><%
        }%>
        <tr><td class="labkey-form-label"><%=bean.getLabel("Comment", bean.isInsert())%></td>
            <td colspan="3">
                <textarea id="comment" name="comment" cols="150" rows="20" style="width: 99%;" onchange="LABKEY.setDirty(true);return true;"><%=h(bean.getBody())%></textarea>
            </td></tr>
        <tr>
            <td align="right" valign="top"><%= button("Save").submit(true).attributes("name=\"" + bean.getAction() + "\"").disableOnClick(true) %><%= PageFlowUtil.button("Cancel").href(cancelURL)%></td>
        </tr>
    </table>

    <table>
        <tr><td><table id="filePickerTable"></table></td></tr>
        <tr><td><a href="javascript:addFilePicker('filePickerTable','filePickerLink')" id="filePickerLink"><img src="<%=getWebappURL("_images/paperclip.gif")%>">Attach a file</a></td></tr>
    </table>

<%}
else
{%>
    <table><%
        if (null != errors && 0 != errors.getErrorCount())
        {
            for (ObjectError e : errors.getAllErrors())
            {%>
                <tr><td colspan=3><span class="labkey-error"><%=h(context.getMessage(e))%></span></td></tr><%
            }
        }
        if (!bean.getRequiredFields().isEmpty())
        {%>
            <tr><td class="help-block">Fields marked with an asterisk * are required.</td></tr><%
        }
        %>
    </table>
    <br>
    <% String placeHolderString = bean.getIssue().getIssueId() == 0 ? "Name this " + names.singularName: " "; %>

    <div class="row">
        <div class="col-sm-8">
            <div class="form-group">
                <label for="title" class="control-label col-md-1" style="padding-right: 5px;">Title *</label>
                <div class="col-md-11">
                    <input class="form-control" name="title" id="title" value="<%=h(issue.getTitle() == null ? "" : issue.getTitle())%>" placeholder="<%=h(placeHolderString)%>" tabindex="1">
                </div>
            </div>
        </div>
        <div class="col-sm-4">
            <div class="form-group">
                <label class="col-md-3 control-label">Status</label>
                <div class="col-md-9"  style="padding-top: 5px"><span><%=text(issue.getStatus())%></span></div>
            </div>
        </div>
    </div>
    <div class="row">
        <div class="col-sm-5">
            <%=text(bean.renderColumn(propertyMap.get("assignedTo"), getViewContext(), bean.isVisible("assignedTo"), bean.isReadOnly("assignedTo")))%>
            <%if (bean.isVisible("resolution"))
            {%>
                <%=text(bean.renderColumn(propertyMap.get("resolution"), getViewContext(), true, bean.isReadOnly("resolution")))%>
            <%}
            if (issue.getIssueId() != 0)
            {
                Issue.IssueEvent m = issue.getMostRecentEvent(user);
                String lastUpdatedStr = "";
                String lastUpdatedTitleStr = "";
                if (null != m)
                {
                    lastUpdatedStr = m.toString();
                    lastUpdatedTitleStr = m.getFullTimestamp();
                }

            %>

            <div style="margin: 10px 0;">
                <div id="recentTimeStamp" title="<%=h(lastUpdatedTitleStr)%>"><div><strong>Recent Activity</strong></div><%=h(lastUpdatedStr)%>
                    <a id="timestampsToggle" onclick="showMoreTimestamps()">
                        <i id="stampExpandIcon" title="See all" class="fa fa-caret-down" style="cursor: pointer;"></i>
                    </a>
                </div>

                <div id="allTimeStamps" style="display: none;">
                    <%
                       ArrayList<Issue.IssueEvent> eventArray = issue.getOrderedEventArray(user);

                        for (int j = 1; j < eventArray.size(); j++)
                        {
                            Issue.IssueEvent e = eventArray.get(j);
                            String stampString = e.toString();
                    %>
                    <div title="<%=h(e.getFullTimestamp())%>"><%=h(stampString)%></div>
                    <%
                        }
                    %>
                </div>
            </div>
            <%}%>
        </div>
        <div class="col-sm-3">
            <div class="form-group">
                <label class="col-md-5 col-lg-4 control-label">
                    Related
                </label>
                <div class="col-md-7 col-lg-8">
                    <%=text(bean.writeInput("related", issue.getRelated(), "id=\"related\" placeholder=\"ID #\""))%>
                </div>
            </div>

            <%if (bean.isVisible("duplicate"))
            {%>
            <div class="form-group">
                <label class="col-md-5 col-lg-4 control-label">
                    Duplicate ID
                </label>
                <div class="col-md-7 col-lg-8">
                <%if("Duplicate".equals(issue.getResolution()))
                {
                    //Enabled duplicate field.%>
                    <%=text(bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "type=\"number\" min=\"1\" placeholder=\"ID #\""))%>
                <%}
                else
                {
                    //Disabled duplicate field.%>
                    <%=text(bean.writeInput("duplicate", issue.getDuplicate() == null ? null : String.valueOf(issue.getDuplicate()), "disabled"))%>
            <%}%>
                </div>
            </div>
            <script type="text/javascript">
                var duplicateInput = document.getElementsByName('duplicate')[0];
                var duplicateOrig = duplicateInput.value;
                var resolutionSelect = document.getElementsByName('resolution')[0];
                function updateDuplicateInput()
                {
                    // The options don't have an explicit value set, so look for the display text instead of
                    // the value
                    if (resolutionSelect.selectedIndex >= 0 &&
                            resolutionSelect.options[resolutionSelect.selectedIndex].value === 'Duplicate')
                    {
                        duplicateInput.disabled = false;
                    }
                    else
                    {
                        duplicateInput.disabled = true;
                        duplicateInput.value = duplicateOrig;
                    }
                }
                if (window.addEventListener)
                    resolutionSelect.addEventListener('change', updateDuplicateInput, false);
                else if (window.attachEvent)
                    resolutionSelect.attachEvent('onchange', updateDuplicateInput);
            </script><%
            }
            else
            {
            if(issue.getDuplicate() != null)
            {%>
            <a href="<%=IssuesController.getDetailsURL(c, issue.getDuplicate(), false)%>"><%=issue.getDuplicate()%></a><%
            }
            }%>
        </div>
        <div class="col-sm-4">
            <div class="form-group">
                <label for="notifyList" class="control-label col-md-3">Notify List</label>
                <div class="col-md-9" style="padding-right: 15px">
                    <labkey:autoCompleteTextArea name="notifyList" id="notifyList" url="<%=h(completionUrl)%>" rows="3" tabindex="20" cols="40" value="<%=h(bean.getNotifyListString(false))%>"/>
                </div>
            </div>
        </div>
    </div>
    <hr>
    <%
        ArrayList<DomainProperty> propertyArr = new ArrayList<>(extraColumns);
        propertyArr.addAll(bean.getCustomColumnConfiguration().getCustomProperties());
        for (int j = 0; j < propertyArr.size(); j++)
        {
            DomainProperty prop = propertyArr.get(j);
            if (j % 3 == 0)
            { //begin row div
    %>
            <div class="row form-row">
    <%       }%>

    <div class="col-sm-4 form-large-label">
        <%=text(bean.renderColumn(prop, getViewContext()))%>
    </div>

    <%
            if (j % 3 == 2)
            { //end row div
    %>
            </div>
    <%
            }
    }
    if (propertyArr.size() % 3 != 0)
    {%>
       </div>
    <%}%>
    <div class="row" style="padding: 0 15px">
        <label class="control-label" for="commentArea">Comment</label>
        <div>
            <textarea id="commentArea" class="form-control" name="comment" cols="150" rows="8" onchange="LABKEY.setDirty(true);return true;"><%=h(bean.getBody())%></textarea>
        </div>
    </div>
    <table style="display: inline-table">
        <tr><td><table id="filePickerTableHead"></table></td></tr>
        <tr><td><a href="javascript:addFilePicker('filePickerTableHead','filePickerLinked')" id="filePickerLinked"><img src="<%=getWebappURL("_images/paperclip.gif")%>">Attach a file</a></td></tr>
    </table>
    <div style="float: right; padding-top: 10px; display: inline-table;">
        <%= button("Cancel").href(cancelURL) %>
        <%= button("Save").submit(true).attributes("name=\"" + bean.getAction() + "\"").disableOnClick(true) %>
    </div>
    <% if (!issue.getComments().isEmpty()) {%>
        <hr>
    <%}%>
    <%
        if (bean.getCallbackURL() != null)
        {
    %>
    <input type="hidden" name="callbackURL" value="<%=h(bean.getCallbackURL())%>"/>
    <%
        }

        if (bean.getReturnURL() != null)
        {
    %>
    <input type="hidden" name="returnUrl" value="<%=h(bean.getReturnURL())%>"/>
    <%
        }%>
        <%if (issue.getIssueId() != 0 && !issue.getComments().isEmpty())
        {
        %>
        <labkey:panel className="labkey-portal-container">
            <%
                for (Issue.Comment comment : issue.getComments())
                {
            %>
            <div class="currentIssue" style="display: inline">
                <strong class=".comment-created-by">
                    <%=h(comment.getCreatedByName(user))%>
                </strong>
                <br>
                <strong class=".comment-created" title="<%=h(comment.getCreatedFullString())%>">
                    <%=h(bean.writeDate(comment.getCreated()))%>
                </strong>
                <%
                if (!issue.getComments().contains(comment))
                {%>
                    <div style="font-weight:bold;">Related #<%=comment.getIssue().getIssueId()%> </div><%
                }%>
                <%=comment.getComment()%>
                <%=bean.renderAttachments(context, comment)%>
                <hr>
            </div>
            <%}%>
        </labkey:panel>
        <%}
    }%>
<%
    if (!newUI)
    {
        for (Issue.Comment comment : issue.getComments())
        {%>
    <hr>
    <table width="100%"><tr><td align="left"><b>
        <%=h(bean.writeDate(comment.getCreated()))%>
    </b></td><td align="right"><b>
        <%=h(comment.getCreatedByName(user))%>
    </b></td></tr>
    </table>
    <%=text(comment.getComment())%>
    <%=text(bean.renderAttachments(context, comment))%><%
    }
    }%>

    <input type="hidden" name=".oldValues" value="<%=PageFlowUtil.encodeObject(bean.getPrevIssue())%>">
    <input type="hidden" name="action" value="<%=h(bean.getAction().getName())%>">
    <input type="hidden" name="issueId" value="<%=issue.getIssueId()%>">
    <input type="hidden" name="issueDefName" value="<%=h(StringUtils.trimToEmpty(issue.getIssueDefName()))%>">
</labkey:form>
<script type="text/javascript" for="window" event="onload">try {document.getElementById(<%=q(focusId)%>).focus();} catch (x) {}</script>
<script type="text/javascript">

    var origComment = document.getElementById("comment") === null ?
            document.getElementById("commentArea").value :
            document.getElementById("comment").value;
    var origNotify = <%=q(bean.getNotifyListString(false).toString())%>;

    function isDirty()
    {
        var comment = document.getElementById("comment");
        if (comment && origComment != comment.value)
            return true;
        var notify = document.getElementById("notifyList");
        if (notify && origNotify != notify.value)
            return true;
        return false;
    }

    window.onbeforeunload = LABKEY.beforeunload(isDirty);
</script>

<%!
    String getNotifyHelpPopup(int emailPrefs, int issueId, EntryTypeNames names)
    {
        String indefArticle = names.getIndefiniteSingularArticle();
        String description = h(indefArticle) + " " + h(names.singularName);

        StringBuilder sb = new StringBuilder();
        sb.append("Email notifications can be controlled via this notification list (one email address per line)");

        if (!getUser().isGuest())
        {
            sb.append(" or your user <a href=\"").append(h(buildURL(IssuesController.EmailPrefsAction.class)));
            if (issueId != 0)
            {
                sb.append("issueId=").append(issueId);
            }
            sb.append("\">email preferences</a>. ");
        }
        if (emailPrefs != 0)
        {
            sb.append("Your current preferences are to receive notification emails when:<br>");
            sb.append("<ul>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_OPEN) != 0)
                sb.append("<li>").append(description).append(" is opened and assigned to you</li>");
            if ((emailPrefs & IssueManager.NOTIFY_ASSIGNEDTO_UPDATE) != 0)
                sb.append("<li>").append(description).append(" that's assigned to you is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_CREATED_UPDATE) != 0)
                sb.append("<li>").append(description).append(" you opened is modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SUBSCRIBE) != 0)
                sb.append("<li>any ").append(h(names.singularName)).append(" is created or modified</li>");
            if ((emailPrefs & IssueManager.NOTIFY_SELF_SPAM) != 0)
                sb.append("<li>you create or modify ").append(description).append("</li>");
            sb.append("</ul>");
        }
        return PageFlowUtil.helpPopup("Email Notifications", sb.toString(), true);
    }
%>
