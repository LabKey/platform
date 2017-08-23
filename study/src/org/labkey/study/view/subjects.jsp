<%
/*
 * Copyright (c) 2010-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.DbSchema" %>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.Selector" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.Cohort" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.WebTheme" %>
<%@ page import="org.labkey.api.view.WebThemeManager" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="org.labkey.study.model.CohortImpl" %>
<%@ page import="org.labkey.study.model.ParticipantCategoryImpl" %>
<%@ page import="org.labkey.study.model.ParticipantGroup" %>
<%@ page import="org.labkey.study.model.ParticipantGroupManager" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.query.StudyQuerySchema" %>
<%@ page import="org.labkey.study.view.SubjectsWebPart" %>
<%@ page import="java.io.IOException" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.BitSet" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("study/ParticipantFilter");
        dependencies.add("study/ParticipantList.css");
    }
%>
<%
    JspView<SubjectsWebPart.SubjectsBean> me = (JspView<SubjectsWebPart.SubjectsBean>) HttpView.currentView();
    SubjectsWebPart.SubjectsBean bean = me.getModelBean();

    Container container  = getContainer();
    User user            = getUser();
    Study study = StudyService.get().getStudy(container);
    StudyManager manager = StudyManager.getInstance();
    StudyQuerySchema schema = StudyQuerySchema.createSchema((StudyImpl)study, user, true);
    String subjectTableName = StudyService.get().getSubjectTableName(container);
    String subjectColumnname = StudyService.get().getSubjectColumnName(container);

    String singularNoun  = StudyService.get().getSubjectNounSingular(container);
    String pluralNoun    = StudyService.get().getSubjectNounPlural(container);
    TableInfo tableParticipants = schema.getTable(subjectTableName);
    ColumnInfo columnParticipant = tableParticipants.getColumn(subjectColumnname);
    ColumnInfo columnCurrentCohortId = tableParticipants.getColumn("cohort");


    ActionURL subjectUrl = new ActionURL(StudyController.ParticipantAction.class, container);
    String urlTemplate   = subjectUrl.getEncodedLocalURIString();
    DbSchema dbschema    = StudySchema.getInstance().getSchema();
    final JspWriter _out = out;

    String divId        = "participantsDiv" + getRequestScopedUID();
    String listDivId    = "listDiv" + getRequestScopedUID();
    String groupsDivId  = "groupsDiv" + getRequestScopedUID();
    String groupsPanelId  = "groupsPanel" + getRequestScopedUID();

    String viewObject = "subjectHandler" + bean.getIndex();
    WebTheme theme    = WebThemeManager.getTheme(container);
%>

<script type="text/javascript">
<%=viewObject%> = (function()
{
    var $h = Ext4.util.Format.htmlEncode;
    var first = true;

    var _urlTemplate = '<%= urlTemplate %>';
    var _singularNoun = '<%= singularNoun %>';
    var _pluralNoun = '<%= pluralNoun %>';
    var _divId = '<%= divId %>';

    // filters
    var _filterSubstring;
    var _filterSubstringMap;
    var _filterGroupMap;

    var _isWide = <%= bean.getWide()%>;
    var _ptids = [<%
        final String[] commas = new String[]{"\n"};
        final HashMap<String,Integer> ptidMap = new HashMap<>();

        SQLFragment sqlf = new SQLFragment("SELECT DISTINCT ")
            .append(columnParticipant.getValueSql("P")).append(" AS participantId FROM ")
            .append(tableParticipants.getFromSQL("P"))
            .append(" ORDER BY 1");

        new SqlSelector(dbschema, sqlf).forEach(new Selector.ForEachBlock<ResultSet>()
        {
            public void exec(ResultSet rs) throws SQLException
            {
                String ptid = rs.getString(1);
                ptidMap.put(ptid,ptidMap.size());
                try { _out.write(commas[0]); _out.write(q(ptid)); commas[0]=(0==ptidMap.size()%10?",\n":","); } catch (IOException x) {}
            }
        });
        %>];
    var _groups = [<%
        commas[0] = "\n";
        int index = 0;
        boolean hasCohorts = false;
        boolean hasUnenrolledCohorts = false;
        int nocohortIndex = -1;
        int unenrolledIndex = -1;
        final List<CohortImpl> cohorts = new ArrayList<>();
        final HashMap<Integer,Integer> cohortIndexMap = new HashMap<>();

        // cohorts
        if (!study.isDataspaceStudy())
        {
            if (StudyManager.getInstance().showCohorts(container, user))
                cohorts.addAll(StudyManager.getInstance().getCohorts(container, user));
            hasCohorts = cohorts.size() > 0;
            if (hasCohorts)
            {
                if (study != null && StudyManager.getInstance().getParticipantIdsNotInCohorts(study).length > 0)
                    hasUnenrolledCohorts = true;

                for (Cohort co : cohorts)
                {
                    cohortIndexMap.put(co.getRowId(), index);
                    %><%=commas[0]%>{id:<%=co.getRowId()%>, index:<%=index%>, type:'cohort', label:<%=q(co.getLabel())%>, enrolled:<%=co.isEnrolled()%>}<%
                    commas[0]=",\n";
                    index++;
                    if (!co.isEnrolled())
                    {
                        hasUnenrolledCohorts = true;
                    }
                }

                unenrolledIndex = index;
                cohortIndexMap.put(-2, index);
                %><%=commas[0]%>{id:-2, index:<%=index%>, type:'cohort', label:'{unenrolled}', enrolled:false}<%
                index++;
            }
            // 'no cohort place holder
            nocohortIndex = index;
            cohortIndexMap.put(-1, index);
            %><%=commas[0]%>{id:-1, index:<%=index%>, type:'cohort', label:'{no cohort}', enrolled:false}<%
            commas[0]=",\n";
            index++;
        }
        %>

        <%
        // groups/categories
        final HashMap<Integer,Integer> groupMap = new HashMap<>();
        ParticipantGroupManager m = ParticipantGroupManager.getInstance();
        List<ParticipantCategoryImpl> categories = m.getParticipantCategories(container, user);
        boolean hasGroups = !categories.isEmpty();
        int nogroupIndex = -1;
        int firstGroupIndex = index;
        if (hasGroups)
        {
            for (int isShared=1 ; isShared>=0 ; isShared--)
            {
                for (ParticipantCategoryImpl cat : categories)
                {
                    if ((isShared == 1) == cat.isShared())
                    {
                        for (ParticipantGroup g : m.getParticipantGroups(container, user, cat))
                        {
                            groupMap.put(g.getRowId(), index);
                            // UNDONE: groupid vs categoryid???
                            %><%=commas[0]%>{categoryId:<%=cat.getRowId()%>, id:<%=g.getRowId()%>, index:<%=index%>, shared:true, type:'participantGroup', label:<%=q(g.getLabel())%>}<%
                            commas[0]=",\n";
                            index++;
                        }
                    }
                }
            }
            nogroupIndex = index;
            groupMap.put(-1, index);
            // UNDONE: groupid vs categoryid???
            %><%=commas[0]%>{categoryId:-1, id:-1, index:<%=index%>, shared:false, type:'participantGroup', label:'{no group}'}<%
            commas[0]=",\n";
            index++;
        }
        %>];

        var hasCohorts = <%=text(hasCohorts?"true":"false")%>;

<%
        int size = index;
        final BitSet[] memberSets = new BitSet[size];
        for (int i=0 ; i<size ; i++)
            memberSets[i] = new BitSet();
        if (hasCohorts)
        {
            sqlf = new SQLFragment("SELECT ")
                .append(columnCurrentCohortId.getValueSql("P")).append(" as currentcohortid, ")
                .append(columnParticipant.getValueSql("P")).append(" AS participantid FROM ")
                .append(tableParticipants.getFromSQL("P"));

            new SqlSelector(dbschema, sqlf).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                public void exec(ResultSet rs) throws SQLException
                {
                    Integer icohortid = cohortIndexMap.get(rs.getInt(1));
                    Integer iptid = ptidMap.get(rs.getString(2));
                    if (null!=icohortid && null!=iptid)
                        memberSets[icohortid].set(iptid);
                }
            });
            BitSet setNoCohort = memberSets[nocohortIndex];
            setNoCohort.set(0,ptidMap.size());
            for (int i=0 ; i<cohorts.size() ; i++)
            {
                setNoCohort.andNot(memberSets[i]);
            }
            BitSet setUnenrolled = memberSets[unenrolledIndex];
            setUnenrolled.set(0,ptidMap.size());
            for (int i=0 ; i<cohorts.size() ; i++)
            {
                if (cohorts.get(i).isEnrolled())
                   setUnenrolled.andNot(memberSets[i]);
            }
        }
        if (hasGroups)
        {
            SQLFragment sqlGroups = new SQLFragment("SELECT groupid, participantid FROM study.participantgroupmap WHERE groupid IN (select PG.rowid from study.participantgroup PG where PG.container=").append(container).append(")");
            (new SqlSelector(dbschema, sqlGroups)).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                public void exec(ResultSet rs) throws SQLException
                {
                    Integer igroup = groupMap.get(rs.getInt(1));
                    Integer iptid = ptidMap.get(rs.getString(2));
                    if (null!=igroup && null!=iptid)
                    {
                        memberSets[igroup].set(iptid);
                    }
                }
            });
            BitSet setNoGroup = memberSets[nogroupIndex];
            setNoGroup.set(0,ptidMap.size());
            for (int i=firstGroupIndex ; i<nogroupIndex ; i++)
            {
                setNoGroup.andNot(memberSets[i]);
            }
        }


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
    var _unenrolled = <%=unenrolledIndex%>;
    <% int ptidsPerCol = Math.min(Math.max(20,groupMap.size()), Math.max(6, ptidMap.size()/6));%>
    <% if (!bean.getWide()) {
        ptidsPerCol = ptidMap.size()/2;
    } %>
    var _ptidPerCol = <%=ptidsPerCol%>;
    var _hasUnenrolledCohorts = <%=hasUnenrolledCohorts%>;

    var h, i, ptid;
    for (i=0 ; i<_ptids.length ; i++)
    {
        ptid = _ptids[i];
        h = $h(ptid);
        _ptids[i] = {
            index:i,
            ptid:ptid,
            html:(h==ptid?ptid:h),
            urlParam: Ext4.Object.toQueryString({participantId: ptid})
        };
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
            _highlightGroupTask = new Ext4.util.DelayedTask(_highlightPtidsInGroup);
        _highlightGroupTask.delay(50);
    }

    function _highlightPtidsInGroup()
    {
        var list = Ext4.DomQuery.select("LI.ptid",_divId);
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
            _highlightPtidTask = new Ext4.util.DelayedTask(_highlightGroupsForPart);
        _highlightPtidTask.delay(50);
    }

    function _highlightGroupsForPart()
    {
        var p = _highlightPtid;
        var list = Ext4.DomQuery.select("DIV.lk-filter-panel-label",'<%=groupsDivId%>');
        for (var i=0 ; i<list.length ; i++)
        {
            var div = Ext4.get(list[i]);
            div.removeCls(['highlight', 'unhighlight']);
            if (p == -1) continue;
            var id = intAttribute(div,'data-id',-99);
            var type = stringAttribute(div,'data-type',null);
            if (id == -99)
                continue;
            var g = getIndexForGroup({id:id, type:type});
            var inGroup = g>=0 && testGroupPtid(g,p);
            if (inGroup)
                div.addCls('highlight');
            else
                div.addCls('unhighlight');
        }
    }

    function stringAttribute(el,name,def)
    {
        el = Ext4.get(el);
        if (!el)
            return def;
        var dom = el.dom;
        var attr = dom.attributes[name];
        if (!attr || !attr.value)
            return def;
        return attr.value;
    }
    function intAttribute(el,name,def)
    {
        try
        {
            var v = stringAttribute(el,name,null);
            return v===null ? def : parseInt(v);
        }
        catch (err)
        {
            return def;
        }
    }

    function filter(selected)
    {
        Ext4.Ajax.request({
            url      : LABKEY.ActionURL.buildURL('participant-group', 'getSubjectsFromGroups.api'),
            method   : 'POST',
            jsonData : Ext4.encode({
                groups : selected
            }),
            success  : function(response)
            {
                var json = Ext4.decode(response.responseText);
                _filterGroupMap = {};
                for (var i=0; i < json.subjects.length; i++)
                    _filterGroupMap[json.subjects[i]] = true;
                renderSubjects();
            },
            failure  : function(response){
                Ext4.Msg.alert('Failure', Ext4.decode(response.responseText));
            },
            scope : this
        });
    }


    function getIndexForGroup(data)
    {
        var g=-1;
        for (var i=0 ; i<_groups.length ; i++)
            if (_groups[i].id==data.id && _groups[i].type==data.type)
                g = i;
        return g;
    }


    function renderGroups()
    {
        var filterTask = new Ext4.util.DelayedTask(filter);

        <% if (bean.getWide()) { %>
        var ptidPanel = Ext4.create('LABKEY.study.ParticipantFilterPanel',
        {
            id : <%=q(groupsPanelId)%>,
            renderTo : Ext4.get(<%=q(groupsDivId)%>),
            title : 'Show',
            border : true,
            cls : 'themed-panel iScroll',
            bodyStyle : 'overflow-x: hidden !important;',
            width : 260,
            height : 350,
            autoScroll : true,
            normalWrap : true,
            allowAll  : true,
            listeners : {
                itemmouseenter : function(v,r,item,idx)
                {
                    var g=getIndexForGroup(r.data);
                    highlightPtidsInGroup(g);
                },
                itemmouseleave : function()
                {
                    highlightPtidsInGroup(-1);
                },
                selectionchange : function(model, selected)
                {
                    var json = [];
                    var filters = ptidPanel.getFilterPanel().getSelection(true);
                    for (var f=0; f < filters.length; f++)
                    {
                        json.push(filters[f].data);
                    }
                    filterTask.delay(400, null, null, [json]);
                },
                initSelectionComplete: function(count, list)
                {
                    // setup our initial selection list to only
                    // included enrolled cohorts.  If all cohorts
                    // are enrolled, then let the list engage
                    // in default behavior

                    var hasUnenrolled = false;
                    var filterPanels = list.getFilterPanels();

                    for (var i = 0; i < filterPanels.length; i++)
                    {
                        var store = filterPanels[i].getGrid().store;
                        var select = [];

                        for (var j = 0; j < store.getCount(); j++)
                        {
                            var rec = store.getAt(j);

                            if (rec.data.type == 'cohort')
                            {
                                if (rec.data.enrolled)
                                {
                                    select.push(rec.data);
                                }
                                else
                                {
                                    hasUnenrolled = true;
                                }
                            }
                        }

                        if (hasUnenrolled && select.length > 0)
                        {
                            filterPanels[i].getGrid().selection = select;
                            filterPanels[i].deselectAll();

                            for (var k = 0; k < select.length; k++)
                                filterPanels[i].select(select[k].id, true);
                        }
                    }

                    if (hasUnenrolled)
                    {
                        select = [];
                        Ext4.each(list.getSelection(true), function(rec){select.push(rec.data);});

                        // ensure we apply the group filter as well
                        filterTask.delay(50, null, null, [select])
                    }
                }
            }
        });

        Ext4.create('Ext.resizer.Resizer', {
            // default handles are east, south, and souteast
            target: ptidPanel,
            dynamic: false,
            minWidth: 260,
            minHeight: 350,
            listeners: {
                resize: function(cmp, width) {
                    ptidPanel.getGroupPanel().setWidth(width - 10);
                }
            }
        });
        <% } %>

        function scrollHorizontal(evt) {
            Ext4.get(<%=q(listDivId)%>).scroll((evt.getWheelDeltas().y > 0) ? 'r' : 'l', 20);
            evt.stopEvent();
        }

        Ext4.get(<%=q(listDivId)%>).on(Ext4.supports.MouseWheel ? 'mousewheel' : 'DOMMouseScroll', scrollHorizontal);
    }


    function renderSubjects()
    {
        // don't render the initial list if we have unenrolled cohorts; wait until we have a filterGroupMap
        // (unless this is the narrow participant list which doesn't have a filterGroupMap)
        if (_isWide && _hasUnenrolledCohorts && !_filterGroupMap)
        {
            return;
        }
        first = false;

        var html = [];
        html.push('<table><tr><td valign="top"><ul class="subjectlist">');
        var count = 0;
        var showEnrolledText = _hasUnenrolledCohorts;

        for (var subjectIndex = 0; subjectIndex < _ptids.length; subjectIndex++)
        {
            var p = _ptids[subjectIndex];
            if ((!_filterSubstringMap || test(_filterSubstringMap,subjectIndex)) && (!_filterGroupMap || _filterGroupMap[p.ptid]))
            {
                // For the wide participant list, we have a _filterGroupMap we can use.  For the narrow participant list
                // there is no group filter but we still only want to show enrolled participants so do the filter manually.
                if (_isWide || isParticipantEnrolled(subjectIndex))
                {
                    if (++count > 1 && count % _ptidPerCol == 1)
                        html.push('</ul></td><td valign="top"><ul class="subjectlist">');
                    html.push('<li class="ptid" index=' + subjectIndex + ' ptid="' + p.html + '" style="white-space:nowrap;"><a href="' + _urlTemplate + p.urlParam + '">' + (LABKEY.demoMode?LABKEY.id(p.ptid):p.html) + '</a></li>\n');
                }

                if (_isWide && showEnrolledText)
                {
                    showEnrolledText = isParticipantEnrolled(subjectIndex);
                }
            }
        }

        html.push('</ul></td></tr></table>');
        html.push('<div style="clear:both;">');
        var message = "";
        // if we have no unenrolled cohorts in the study, then no need to call out that a participant belongs to an enrolled cohort
        // if the filter only includes enrolled participants, then use 'enrolled' to describe them
        // if the filter includes both enrolled and unenrolled cohorts, then don't use 'enrolled'
        // if there are no participants because they all belong to unenrolled cohorts, then say "no matching enrolled..."
        var enrolledText = showEnrolledText ? " enrolled " : " ";
        if (count > 0)
        {
            if (_filterSubstringMap || _filterGroupMap || _hasUnenrolledCohorts)
                message = 'Found ' + count + enrolledText + (count > 1 ? _pluralNoun.toLowerCase() : _singularNoun.toLowerCase()) + ' of ' + _ptids.length + '.';
            else
                message = 'Showing all ' + count + enrolledText + (count > 1 ? _pluralNoun.toLowerCase() : _singularNoun.toLowerCase()) + '.';
        }
        else {
            if (_filterSubstring && _filterSubstring.length > 0)
                message = 'No ' + _singularNoun.toLowerCase() + ' IDs contain \"' + _filterSubstring + '\".';
            else
                message = 'No matching' + enrolledText + _pluralNoun + '.';
        }
        html.push('</div>');

        Ext4.get(<%=q(listDivId)%>).update(html.join(''));
        Ext4.get(<%=q(divId + ".status")%>).update(message);
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


    function generateToolTip(p)
    {
        var part = _ptids[p];
        var html = ["<div style='font-weight:bold;font-size:11px;'>" + (LABKEY.demoMode?LABKEY.id(part.ptid):part.html) + "</div>"];
        for (var g=0 ; g<_groups.length ; g++)
        {
            if (_groups[g].id != -1 && testGroupPtid(g,p))
                html.push('<div style="white-space:nowrap;font-size:11px;">' + $h(_groups[g].label) + '</div>');
        }
        return html.join("");
    }


    function isParticipantEnrolled(subjectIndex)
    {
        return _unenrolled < 0 || !testGroupPtid(_unenrolled,subjectIndex);
    }


    function render()
    {
        if (_ptids.length == 0 && first)
        {
            // Issue 16372
            // Need to check for ptids here instead of later, otherwise internal Ext errors occur which prevent other
            // Ext items (i.e. Admin / Help menus) from being rendered.

            document.getElementById(_divId).innerHTML = 'No ' + _pluralNoun.toLowerCase() + " were found in this study.  " +
                    _singularNoun + " IDs will appear here after specimens or datasets are imported.";
            first = false;
            return;
        }

        renderGroups();
        renderSubjects();

        Ext4.create('Ext.tip.ToolTip',
        {
            target     : <%=q(listDivId)%>,
            delegate   : "LI.ptid",
            trackMouse : true,
            listeners  :
            {
                beforeshow: function(tip)
                {
                    var dom = tip.triggerElement || tip.target;
                    var indexAttr = dom.attributes.index || dom.parentNode.attributes.index;
                    if (!Ext4.isDefined(indexAttr))
                        return false;
                    var html = generateToolTip(parseInt(indexAttr.value));
                    tip.update(html);
                }
            }
        });

        /* filter events */

        var inp = Ext4.get('<%=divId%>.filter');
        inp.on('keyup', function(a){filterPtidContains(a.target.value);}, null, {buffer:200});
        inp.on('change', function(a){filterPtidContains(a.target.value);}, null, {buffer:200});

        /* ptids events */

        var ptidDiv = Ext4.get(<%=q(listDivId)%>);
        ptidDiv.on('mouseover', function(e,dom)
        {
            var indexAttr = dom.attributes.index || dom.parentNode.attributes.index;
            if (Ext4.isDefined(indexAttr))
                highlightGroupsForPart(parseInt(indexAttr.value));
        });
        ptidDiv.on('mouseout', function(e,dom)
        {
            highlightGroupsForPart(-1);
        });

        // we don't want ptidDiv to change height as it filters, so set height explicitly after first layout
        ptidDiv.setHeight(ptidDiv.getHeight());

        // the groups panel starts with a height of 350, but make that bigger to match the ptid div
        var groupsPanel = Ext4.ComponentQuery.query('participantfilter[id=<%=(groupsPanelId)%>]');
        if (groupsPanel.length == 1 && groupsPanel[0].getHeight() < ptidDiv.getHeight())
            groupsPanel[0].setHeight(ptidDiv.getHeight());
    }

    return { render : render };
})();


Ext4.onReady(<%=viewObject%>.render, <%=viewObject%>);
</script>

<div style="">
    <table id=<%= q(divId) %> class="lk-participants-list-table">
        <tr>
            <% if (bean.getWide()) { %>
            <td valign=top>
                <div id=<%=q(groupsDivId)%>></div>
            </td>
            <% } %>
            <td style="padding-left: 10px;" valign=top class="iScroll">
                <table><tr>
                    <td><div style="" >Filter:&nbsp;<input id="<%=divId%>.filter" type="text" size="15" style="border:solid 1px #<%=theme.getWebPartColor()%>"></div></td>
                    <%--<td>&nbsp;<%if (hasCohorts){%><input type=checkbox>&nbsp;by&nbsp;cohort (NYI)<%}%></td>--%>
                </tr></table>
                <hr style="height:1px; border:0; background-color:#<%=theme.getWebPartColor()%>; color:#<%=theme.getWebPartColor()%>;">
                <div><span id="<%=divId%>.status">Loading...</span></div>
                <div style="overflow-x:auto; min-height:<%=Math.round(1.2*(ptidsPerCol+3))%>em;" id="<%= listDivId %>"></div>
            </td>
        </tr>
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
