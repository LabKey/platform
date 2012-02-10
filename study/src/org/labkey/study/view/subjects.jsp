<%
/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.view.SubjectsWebPart" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.Selector" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.BitSet" %>
<%@ page import="org.labkey.study.model.ParticipantCategory" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.api.study.Cohort" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<SubjectsWebPart.SubjectsBean> me = (JspView<SubjectsWebPart.SubjectsBean>) HttpView.currentView();
    SubjectsWebPart.SubjectsBean bean = me.getModelBean();
    Container container = bean.getViewContext().getContainer();
    User user = bean.getViewContext().getUser();
    String singularNoun = StudyService.get().getSubjectNounSingular(container);
    String pluralNoun = StudyService.get().getSubjectNounPlural(container);
    String colName = StudyService.get().getSubjectColumnName(container);
    ActionURL subjectUrl = new ActionURL(StudyController.ParticipantAction.class, container);
    subjectUrl.addParameter("participantId", "");
    String urlTemplate = subjectUrl.getEncodedLocalURIString();
    int rows = bean.getRows();
    DbSchema dbschema = StudySchema.getInstance().getSchema();
    final JspWriter _out = out;

    String divId = "participantsDiv" + getRequestScopedUID();
    String listDivId = "listDiv" + getRequestScopedUID();
    String groupsDivId = "groupsDiv" + getRequestScopedUID();

    String viewObject = "subjectHandler" + bean.getIndex();
%>
<style type="text/css">
ul.subjectlist li
{
    margin: 0;
    padding: 0;
    text-indent: 3px;
    list-style-type: none;
}

ul.subjectlist
{
    padding-left: 1em;
}

li.ptid a.highlight
{
/*    color:black; background-color: pink; */
}
li.ptid a.unhighlight
{
    color:#dddddd;
}

div.group.highlight
{
    background-color:pink;
}
div.group.unhighlight
{
}
</style>
<script>
    LABKEY.requiresExt4Sandbox(true);
</script>
<script type="text/javascript">
<%=viewObject%> = (function()
{
    var X = Ext4 || Ext;
    var $h = X.util.Format.htmlEncode;


    var _urlTemplate = '<%= urlTemplate %>';
    var _subjectColName = '<%= colName %>';
    var _singularNoun = '<%= singularNoun %>';
    var _pluralNoun = '<%= pluralNoun %>';
    var _divId = '<%= divId %>';

    // filters
    var _filterSubstring = null;
    var _filterSubstringMap = null;
    var _filterGroup = -1;
    var _filterGroupMap = null;


    var _initialRenderComplete = false;
    var _ptids = [<%
        final String[] commas = new String[]{""};
        final HashMap<String,Integer> ptidMap = new HashMap<String,Integer>();
        (new SqlSelector(dbschema, "SELECT participantId FROM study.participant WHERE container=? ORDER BY 1", container)).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            public void exec(ResultSet rs) throws SQLException
            {
                String ptid = rs.getString(1);
                ptidMap.put(ptid,ptidMap.size());
                try { _out.write(commas[0]); _out.write(q(ptid)); commas[0]=",\n"; } catch (IOException x) {}
            }
        });
        %>];
    var _groups = [<%
        commas[0] = "";
        int index = 0;

        // cohorts
        final HashMap<Integer,Integer> cohortMap = new HashMap<Integer,Integer>();
        CohortImpl[] cohorts = new CohortImpl[0];
        if (StudyManager.getInstance().showCohorts(container, user))
            cohorts = StudyManager.getInstance().getCohorts(container, user);
        for (Cohort co : cohorts)
        {
            cohortMap.put(((CohortImpl)co).getRowId(), index);
            %><%=commas[0]%>{index:<%=index%>, cohort:true, label:<%=q(co.getLabel())%>}<%
            commas[0]=",\n";
            index++;
        }

        // groups/categories
        final HashMap<Integer,Integer> groupMap = new HashMap<Integer,Integer>();
        ParticipantGroupManager m = ParticipantGroupManager.getInstance();
        ParticipantCategory[] categories = m.getParticipantCategories(container, user);
        for (ParticipantCategory cat : categories)
        {
            if (cat.isShared())
            {
                for (ParticipantGroup g : m.getParticipantGroups(container, user, cat))
                {
                    groupMap.put(g.getRowId(), index);
                    %><%=commas[0]%>{index:<%=index%>, shared:true, cohort:false, label:<%=q(g.getLabel())%>}<%
                    commas[0]=",\n";
                    index++;
                }
            }
        }
        for (ParticipantCategory cat : categories)
        {
            if (!cat.isShared())
            {
                for (ParticipantGroup g : m.getParticipantGroups(container, user, cat))
                {
                    groupMap.put(g.getRowId(), index);
                    %><%=commas[0]%>{index:<%=index%>, shared:false, cohort:false, label:<%=q(g.getLabel())%>}<%
                    commas[0]=",\n";
                    index++;
                }
            }
        }
        %>];
<%
        final BitSet[] memberSets = new BitSet[index];
        for (int i=0 ; i<memberSets.length ; i++)
            memberSets[i] = new BitSet();
        (new SqlSelector(dbschema, "SELECT currentcohortid, participantid FROM study.participant WHERE container=?", container)).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            public void exec(ResultSet rs) throws SQLException
            {
                Integer icohortid = cohortMap.get(rs.getInt(1));
                Integer iptid = ptidMap.get(rs.getString(2));
                if (null!=icohortid && null!=iptid)
                    memberSets[icohortid].set(iptid);
            }
        });
        (new SqlSelector(dbschema, "SELECT groupid, participantid FROM study.participantgroupmap WHERE container=?", container)).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            public void exec(ResultSet rs) throws SQLException
            {
                Integer igroup = groupMap.get(rs.getInt(1));
                Integer iptid = ptidMap.get(rs.getString(2));
                if (null!=igroup && null!=iptid)
                    memberSets[igroup].set(iptid);
            }
        });
%>
    var _ptidGroupMap = [<%
        String comma = "\n";
        for (BitSet bs : memberSets)
        {
            %><%=comma%>"<%
            int s = bs.length();
            for (int i=0 ; i<s; i+=16)
            {
                int u = 0;
                for (int b=0 ; b<16 ; b++)
                    u |= (bs.get(i+b)?1:0) << b;
                writeUnicodeChar(_out,u);
            }
            %>"<%
            comma = ",\n";
        }
    %>];
    <% int ptidsPerCol = Math.min(Math.max(20,groupMap.size()), Math.max(6, ptidMap .size()/6));%>
    var _ptidPerCol = <%=ptidsPerCol%>;

    var h, i, ptid;
    for (i=0 ; i<_ptids.length ; i++)
    {
        ptid = _ptids[i];
        h = $h(ptid);
        _ptids[i] = {index:i, ptid:ptid, html:(h==ptid?ptid:h)};
    }

    function test(s, p)
    {
        if (p<0 || p/16 >= s.length)
            return false;
        var i = (typeof s == "string") ? s.charCodeAt(p/16) : s[p/16];
        return i >> (p%16) & 1;
    }

    function testGroupPtid(g,p)
    {
        if (g<0||g>=_ptidGroupMap.length||p<0)
            return false;
        var s=_ptidGroupMap[g];
        return test(s,p);
    }

    var _highlightGroup = -1;
    var _highlightGroupTask = null;

    function highlightPtidsInGroup(index)
    {
        if (index == _highlightGroup) return;
        _highlightGroup = index;
        if (null == _highlightGroupTask)
            _highlightGroupTask = new Ext.util.DelayedTask(_highlightPtidsInGroup);
        _highlightGroupTask.delay(50);
    }

    function _highlightPtidsInGroup()
    {
        var list = Ext.DomQuery.select("LI.ptid",_divId);
        <%-- note removeClass() and addClass() are pretty expensive when used on a lot of elements --%>
        Ext4.Array.each(list, function(li){
            var a = li.firstChild;
            if (_highlightGroup == -1)
                a.className = '';
            else if (testGroupPtid(_highlightGroup,parseInt(li.attributes.index.value)))
                a.className = 'highlight';
            else
                a.className = 'unhighlight';
        });
    }

    var _highlightPtid = -1;
    var _highlightPtidTask = null;

    function highlightGroupsForPart(index)
    {
        if (index == _highlightPtid) return;
        _highlightPtid = index;
        if (null == _highlightPtidTask)
            _highlightPtidTask = new Ext.util.DelayedTask(_highlightGroupsForPart);
        _highlightPtidTask.delay(50);
    }

    function _highlightGroupsForPart()
    {
        var p = _highlightPtid;
        var list = Ext.DomQuery.select("DIV.group",'<%=groupsDivId%>');
        for (var i=0 ; i<list.length ; i++)
        {
            var div = Ext.get(list[i]);
            div.removeClass(['highlight', 'unhighlight']);
            if (p == -1) continue;
            g = parseInt(div.dom.attributes.index.value);
            var inGroup = testGroupPtid(g,p);
            if (inGroup)
                div.addClass('highlight');
            else
                div.addClass('unhighlight');
        }
    }

    function renderGroups()
    {
        var el = Ext.get('<%=groupsDivId%>');
        var html = [];
        html.push('<div class="group" index="-1" style="white-space:nowrap;">show all</div>&nbsp;');

        var g, group;
        var countCohort = 0;
        for (g=0 ; g<_groups.length ; g++)
        {
            group = _groups[g];
            if (group.cohort)
            {
                if (countCohort == 0)
                    html.push('<fieldset><legend>cohorts</legend>');
                html.push('<div class="group" index=' + g + ' rowid="' + group.rowid + '" style="white-space:nowrap;">');
                html.push($h(group.label));
                html.push("</div>");
                countCohort++;
            }
        }
        if (countCohort)
            html.push('</fieldset>&nbsp;');

        var countGroup = 0;
        for (g=0 ; g<_groups.length ; g++)
        {
            group = _groups[g];
            if (!group.cohort)
            {
                if (countGroup == 0)
                    html.push('<fieldset><legend>groups</legend>');
                html.push('<div class="group" index=' + g + ' rowid="' + group.rowid + '" style="white-space:nowrap;">');
                html.push($h(group.label));
                html.push("</div>");
                countGroup++;
            }
        }
        if (countGroup)
            html.push('</fieldset>');

        el.update(html.join(''));
    }


    function renderSubjects()
    {
        if (_ptids.length == 0)
        {
            document.getElementById(_divId).innerHTML = 'No ' + _pluralNoun.toLowerCase() + " were found in this study.  " +
                    _singularNoun + " IDs will appear here after specimens or datasets are imported.";
            return;
        }

        var html = [];
        html.push('<table><tr><td valign="top"><ul class="subjectlist">');
        var count = 0;
        for (var subjectIndex = 0; subjectIndex < _ptids.length; subjectIndex++)
        {
            var p = _ptids[subjectIndex];
            if ((!_filterSubstringMap || test(_filterSubstringMap,subjectIndex)) && (!_filterGroupMap || test(_filterGroupMap,subjectIndex)))
            {
                if (++count > 1 && count % _ptidPerCol == 1)
                    html.push('</ul></td><td  valign="top"><ul class="subjectlist">');
                html.push('<li class="ptid" index=' + subjectIndex + ' ptid="' + p.html + '" ><a href="' + _urlTemplate + p.html + '">' + (LABKEY.demoMode?LABKEY.id(p.ptid):p.html) + '</a></li>\n');
            }
        }

        html.push('</ul></td></tr></table>');
        html.push('<div style="clear:both;">');
        var message = "";
        if (count > 0)
        {
            if (_filterSubstringMap || _filterGroupMap)
                message = 'Found ' + count + ' ' + (count > 1 ? _pluralNoun.toLowerCase() : _singularNoun.toLowerCase()) + ' of ' + _ptids.length + '.';
            else
                message = 'Showing all ' + count + ' ' + (count > 1 ? _pluralNoun.toLowerCase() : _singularNoun.toLowerCase()) + '.';
        }
        else
            message = 'No ' + _singularNoun.toLowerCase() + ' IDs contain \"' + _filterSubstring + '\".';
        html.push('</div>');

        Ext.get(<%=q(listDivId)%>).update(html.join(''));
        Ext.get(<%=q(divId + ".status")%>).update(message);
    }


    function filterPtidContains(substring)
    {
        _filterSubstring = substring;
        if (!substring)
        {
            _filterSubstringMap = null;
        }
        else
        {
            var a = new Array(Math.floor((_ptids.length+15)/16));
            for (var i=0 ; i<_ptids.length ; i++)
            {
                var ptid = _ptids[i].ptid;
                if (ptid.indexOf(_filterSubstring) >= 0)
                    a[i/16] |= 1 << (i%16);
            }
            _filterSubstringMap = a;
        }
        renderSubjects();
    }


    function filterGroup(g)
    {
        _filterGroup = g;
        _filterGroupMap = g<0 ? null : _ptidGroupMap[g];
        renderSubjects();
    }


    function render()
    {
        var inp = Ext.get('<%=divId%>.filter');
        inp.on('keyup', function(a){filterPtidContains(a.target.value);}, null, {buffer:200});
         <%--onKeyUp="<%= viewObject %>.updateSubjects(this.value); return false;"--%>
        doAdjustSize();
        renderGroups();
        renderSubjects();

        var ptidDiv = Ext.get(<%=q(listDivId)%>);
        ptidDiv.on('mouseover', function(e,dom)
        {
            var indexAttr = dom.attributes.index || dom.parentNode.attributes.index;
            if (Ext.isDefined(indexAttr))
                highlightGroupsForPart(parseInt(indexAttr.value));
        });
        ptidDiv.on('mouseout', function(e,dom)
        {
            highlightGroupsForPart(-1);
        });
        var groupsDiv = Ext.get(<%=q(groupsDivId)%>);
        groupsDiv.on('mouseover', function(e,dom)
        {
            var indexAttr = dom.attributes.index || dom.parentNode.attributes.index;
            if (Ext.isDefined(indexAttr))
                highlightPtidsInGroup(parseInt(indexAttr.value));
        });
        groupsDiv.on('mouseout', function(e,dom)
        {
            highlightPtidsInGroup(-1);
        });
        groupsDiv.on('click', function(e,dom)
        {
            var indexAttr = dom.attributes.index || dom.parentNode.attributes.index;
            if (Ext.isDefined(indexAttr))
                filterGroup(parseInt(indexAttr.value));
        });

        // we don't want ptidDiv to change height as it filters, so set height explicitly after first layout
        ptidDiv.setHeight(ptidDiv.getHeight());
    }


    function doAdjustSize()
    {
        // CONSIDER: register for window resize
        var listDiv = Ext.get(<%=q(listDivId)%>);
        var rightAreaWidth = 0;
        try {rightAreaWidth = Ext.fly(Ext.select(".labkey-side-panel").elements[0]).getWidth();} catch (x){}
        var padding = 35;
        var viewWidth = Ext.getBody().getViewSize().width;
        var right = viewWidth - padding - rightAreaWidth;
        var x = listDiv.getXY()[0];
        var width = Math.max(400, right-x);
        listDiv.setWidth(width);
    }


    var ret =
    {
        render : render
    };
    return ret;
})();


Ext.onReady(<%=viewObject%>.render, <%=viewObject%>);

</script>


<div style="">
 <table id="<%= divId %>" width="100%">
    <tr><td style="padding:5px; margin:5px; border:solid 1px #eeeeee;" width=200 valign=top>
    <div style="min-width:200px;"  id="<%=groupsDivId%>">&nbsp;</div>
    </td>
    <td style="padding:5px; margin:5px; border:solid 1px #eeeeee;" valign=top>
     <div style="" >Filter <input id="<%=divId%>.filter" type="text" size="15">&nbsp;&nbsp;<span id="<%=divId%>.status">Loading...</span></div>
     <div style="overflow-x:auto; min-height:<%=Math.round(1.2*(ptidsPerCol+3))%>em"  id="<%= listDivId %>"></div>
    </td></tr>
</table>
</div>


<%!
void writeUnicodeChar(JspWriter out, int i) throws IOException
{
    if (i==0)
        out.write("\\");
    else if (i<16)
        out.write("\\x0");
    else if (i<256)
        out.write("\\x");
    else if (i<4096)
        out.write("\\u0");
    else
        out.write("\\u");
    out.write(Integer.toHexString(i));
}
%>
