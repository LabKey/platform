<%
    /*
     * Copyright (c) 2015-2017 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
        dependencies.add("query/olap.js");
    }
%>
<%
    String configId = "Study:/specimens";
    String schemaName = "Study";
    String cubeName = "SpecimenCube";
    int uid =  getRequestScopedUID();
    String pivotDesignerId = "pivotDesigner" + uid;
    String cellsetId = "cellset" + uid;
%>
<script type="text/javascript">
var cube;
var starttime;

(function()
{
    //var cube;
    var mdx;
    var configId = <%=q(configId)%>;
    var schemaName = <%=q(schemaName)%>;
    var cubeName = <%=q(cubeName)%>;
    var pivotDesignerId = <%=q(pivotDesignerId)%>;
    var cellsetId = <%=q(cellsetId)%>;
    var pivotDesignerFilter;
    var pivotDesignerPages;
    var pivotDesignerColumns;
    var pivotDesignerRows;
    var pivotDesignerMeasures;

    Ext4.onReady(function ()
    {
        cube = LABKEY.query.olap.CubeManager.getCube(
                {
                    name:cubeName,
                    configId:configId,
                    schemaName:schemaName
                });
        cube.on('onready', function (m)
        {
            mdx = m;
            renderUI();
        });
    });

    var model = {onRows:[], onColumns:[], filter:[], measures:[], filterValues:{}};


    function renderUI()
    {
        pivotDesignerFilter = Ext4.get(pivotDesignerId+".filter");
        pivotDesignerPages = Ext4.get(pivotDesignerId+".pages");
        pivotDesignerColumns = Ext4.get(pivotDesignerId+".columns");
        pivotDesignerRows = Ext4.get(pivotDesignerId+".rows");
        pivotDesignerMeasures = Ext4.get(pivotDesignerId+".measures");

        var pagesHtml = [];
        var filterHtml = [];
        var columnsHtml = [];
        var rowsHtml = [];

        // render select for each hierarchy
        function renderHierarchyFilter(h)
        {
            var select = ['<select onchange="return run()" class="filter" data-hierarchy="' + h.uniqueName + '">'];
            // TODO list every member in tree order
            for (var l=0 ; l<2 ; l++)
            {
                var level = h.levels[l];
                for (var m=0 ; m<level.members.length ; m++)
                {
                    var member = level.members[m];
                    if (member.name === "#notnull") continue;
                    select.push('<option value="' + member.uniqueName + '">' + member.name + '</option>');
                }
            }
            select.push("</select>");
            Ext4.get(pivotDesignerId+".filterArea").insertHtml("beforeEnd", select.join(''));
        }


        var dimensions = cube.getDimensions();
        for (var d=0 ; d<dimensions.length ; d++)
        {
            var dimension = dimensions[d];
            if ("Measures" == dimension.name)
                continue;
            var hierarchies = dimension.getHierarchies();
            for (var h=0 ; h<hierarchies.length ; h++)
            {
                var hierarchy = hierarchies[h];
                filterHtml.push('<div style="font-family:verdana; font-size:12pt; padding:2pt;" class="hierarchy" data-hierarchy="' + hierarchy.uniqueName + '">' + hierarchy.name +'</div>');
                renderHierarchyFilter(hierarchy);
            }
        }

        var measures = cube.levelMap.MeasuresLevel.members;
        var measureHtml = [];
        for (var m=0 ; m<measures.length ; m++)
        {
            var measure = measures[m];
            measureHtml.push('<div style="font-family:verdana; font-size:12pt; padding:2pt;"><input onclick="run()" type="checkbox" ' + (m==0?"checked":"") + ' name="chooseMeasure' + measure.name + '" value="measures:' + measure.uniqueName + '">' + measure.name + '</div>');
        }

        pivotDesignerPages.update(pagesHtml.join(''));
        pivotDesignerFilter.update(filterHtml.join(''));
        pivotDesignerColumns.update(columnsHtml.join(''));
        pivotDesignerRows.update(rowsHtml.join(''));
        pivotDesignerMeasures.update(measureHtml.join(''));

        // DRAG AND DROP
        var overrides = {
            // Called the instance the element is dragged.
            b4StartDrag: function ()
            {
                // Cache the drag element
                if (!this.el)
                {
                    this.el = Ext4.get(this.getEl());
                }

                //Cache the original XY Coordinates of the element, we'll use this later.
                this.originalXY = this.el.getXY();
            },
            // Called when element is dropped in a spot without a dropzone, or in a dropzone without matching a ddgroup.
            onInvalidDrop: function ()
            {
                // Set a flag to invoke the animated repair
                this.invalidDrop = true;
            },
            // Called when the drag operation completes
            endDrag: function ()
            {
                // Invoke the animation if the invalidDrop flag is set to true
                if (this.invalidDrop === true)
                {
                    // Remove the drop invitation
                    this.el.removeCls('dropOK');

                    // Create the animation configuration object
                    var animCfgObj = {
                        easing: 'elasticOut',
                        duration: 1,
                        scope: this,
                        callback: function ()
                        {
                            // Remove the position attribute
                            this.el.dom.style.position = 'static';
                            this.el.setLeftTop(0,0);
                        }
                    };

                    // Apply the repair animation
                    this.el.dom.style.position='relative';
                    // callback never gets called (ext version confusion?)
//                    this.el.setXY(this.originalXY[0], this.originalXY[1], animCfgObj);
                    this.el.dom.style.position = 'static';
                    this.el.setLeftTop(0,0);
                    delete this.invalidDrop;
                }
            }, // Called upon successful drop of an element on a DDTarget with the same
            onDragDrop : function(evtObj, targetElId)
            {
                // Wrap the drop target element with Ext.Element
                var dropEl = Ext4.get(targetElId);

                // Perform the node move only if the drag element's
                // parent is not the same as the drop target
                if (this.el.dom.parentNode.id != targetElId)
                {

                    // Move the element
                    dropEl.appendChild(this.el);

                    // Remove the drag invitation
                    this.onDragOut(evtObj, targetElId);

                    // Clear the styles
                    this.el.dom.style.position = '';
                    this.el.dom.style.top = '';
                    this.el.dom.style.left = '';

                    run();
                }
                else
                {
                    // This was an invalid drop, initiate a repair
                    this.onInvalidDrop();
                }
            },
            // Only called when the drag element is dragged over the a drop target with the same dd group
            onDragEnter : function(evtObj, targetElId) {
                /* Colorize the drag target if the drag node's parent is not the same as the drop target */
                if (targetElId != this.el.dom.parentNode.id) {
                    this.el.addCls('dropOK');
                }
                else {
                    // Remove the invitation
                    this.onDragOut();
                }
            },
            // Only called when element is dragged out of a dropzone with the same ddgroup
            onDragOut : function(evtObj, targetElId) {
                this.el.removeCls('dropOK');
            }
        };
        var draggableDivs = Ext4.DomQuery.select("DIV.hierarchy");
        draggableDivs.forEach(function(el)
        {
            var dd = Ext4.create('Ext.dd.DD',el,'hierarchyGroup');
            Ext4.apply(dd,overrides);
        });
        // targets
        // Instantiate instances of DDTarget for the rented and repair drop target elements
        var pagesDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerPages, 'hierarchyGroup');
        var filterDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerFilter, 'hierarchyGroup');
        var rowsDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerRows, 'hierarchyGroup');
        var columnsDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerColumns, 'hierarchyGroup');
    }

    function run()
    {
        updateModel();
        updateFilterUI();
        executeQuery();
    }

    window.run = run;


    function updateFilterUI()
    {
        var filterMap = {};
        model.filter.forEach(function (uniqueName) {filterMap[uniqueName] = true;});
        var selects = Ext4.DomQuery.select("SELECT.filter");
        selects.forEach(function (s) {
            if (filterMap[s.dataset.hierarchy])
                Ext4.fly(s).removeCls("x-hidden");
            else
                Ext4.fly(s).addCls("x-hidden");
        });
        // call after updateModel
    }


    function updateModel()
    {
        model = {onRows:[], onColumns:[], filter:[], measures:[], filterValues:[]};

        // draggable elements
        var draggableDivs = Ext4.DomQuery.select("DIV.hierarchy");
        draggableDivs.forEach(function(div){
            var parent = div.parentElement;
            var hierarchyName = div.dataset.hierarchy;
            var axisName = parent.dataset.axis;
            model[axisName].push(hierarchyName);
        });

        // checkbox elements
        var elements = Ext4.get(pivotDesignerId).select("INPUT").elements;
        for (var e=0 ; e<elements.length ; e++)
        {
            var input = elements[e];
            console.log("value="+input.value+" checked="+input.checked);
            if (!input.checked)
                continue;
            var value = input.value;
            var split = value.indexOf(':');
            var where = value.substring(0,split);
            var uniqueName = value.substring(split+1);
            console.log(where + "||" + uniqueName);
            model[where].push(uniqueName);
        }

        // active filters
        var filterMap = {};
        model.filter.forEach(function (uniqueName) {filterMap[uniqueName] = true;});
        var selects = Ext4.DomQuery.select("SELECT.filter");
        selects.forEach(function (s)
        {
            var hierarchy = filterMap[s.dataset.hierarchy];
            if (!filterMap[s.dataset.hierarchy])
                return;
            var value = Ext4.get(s).getValue();
            if (Ext4.String.endsWith(value,".[(All)]"))
                return;
            model.filterValues.push(value);
        });

        console.log("model=" + JSON.stringify(model));
    }


    function executeQuery()
    {
        if (model.measures.length === 0)
            return;
        var measureexpr = setFromMembers(model.measures);

        var query = "SELECT\n";

        if (model.onColumns.length === 0)
        {
            query += measureexpr + " ON COLUMNS\n";
        }
        else
        {
            var colexpr = crossExprFromHierarchies(model.onColumns,true);
            query += "NON EMPTY CROSSJOIN(" + colexpr + "," + measureexpr + ") ON COLUMNS\n";
        }

        if (model.onRows.length !== 0)
        {
            var rowexpr = crossExprFromHierarchies(model.onRows,false);
            query += ", NON EMPTY " + rowexpr + " ON ROWS\n";
        }

        query += "FROM " + cubeName + "\n";

        if (model.filterValues.length !== 0)
        {
            query += " WHERE (";
            var comma = "";
            model.filterValues.forEach(function(s){
                query += comma + s;
                comma = ", ";
            });
            query += ")\n";
        }

        console.log(query);

        Ext4.getBody().mask();
        var config = {
            configId:configId,
            schemaName:schemaName,
            query:query,
            success:function(cs){renderCellSet(cs,cellsetId);},
            failure:failed
        };
        LABKEY.query.olap.CubeManager.executeOlapQuery(config);
    }


    function crossExprFromHierarchies(hierarchies)
    {
        if (hierarchies.length == 1)
        {
            return hierarchies[0] + ".members";
        }
        else if (hierarchies.length > 1)
        {
            return "NONEMPTYCROSSJOIN(" + hierarchies[0] + ".members," + hierarchies[1] + ".members)";
        }
        else
        {
            var u = hierarchies.pop();
            var p = hierarchies.pop();
            var join = "NONEMPTYCROSSJOIN(" + p + ".members," + u + ".members)";
            while (hierarchies.length > 0)
            {
                var h = hierarchies.pop();
                join = "NONEMPTYCROSSJOIN(" + h + ".members," + join + ")";
            }
            return join;
        }
    }


    function setFromMembers(members)
    {
        return members[0];
    }


    function failed(json,response,options)
    {
        Ext4.getBody().unmask();
        var msg = response.statusText;
        if (json && json.exception)
            msg = json.exception;
        alert(msg);
    }


    function renderCellSet(cs, el)
    {
        var duration = new Date().getTime() - starttime;
        Ext4.getBody().unmask();
        var h = Ext4.util.Format.htmlEncode;
        el = Ext4.get(el||'cellset');
        var html = [];
        html.push('<table class="labkey-data-region-legacy labkey-show-borders"><tr>');
        if (cs.axes.length>1)
            html.push('<td>&nbsp;</td>');
        for (var col=0 ; col<cs.axes[0].positions.length ; col++)
        {
            // only showing first member (handle hierarchy)
            html.push('<td class="labkey-column-header">' + h(cs.axes[0].positions[col][0].uniqueName) + "</td>");
        }
        html.push("</tr>");

        if (cs.axes.length == 1)
        {
            //for (var row=0 ; row<cs.axes[1].positions.length ; row++)
            {
                html.push('<tr>');
                for (var col=0 ; col<cs.axes[0].positions.length ; col++)
                {
                    var cell = cs.cells[col];
                    var value = Ext4.isObject(cell) ? cell.value : cell;
                    html.push("<td align=right>" + (null==value?"&nbsp;":value) + "</td>");
                }
                html.push('</tr>');
            }
            html.push("</table>");
            html.push("<p><b>" + (duration/1000) + "</b></p>");        }
        else if (cs.axes.length == 2)
        {
            for (var row=0 ; row<cs.axes[1].positions.length ; row++)
            {
                html.push('<tr>');
                var pos = cs.axes[1].positions[row];
                for (var p=0; p < pos.length; p++)
                {
                    // only showing first member (handle hierarchy)
                    html.push('<td class="labkey-column-header">' + h(cs.axes[1].positions[row][p].uniqueName) + "</td>");
                }

                for (var col=0 ; col<cs.axes[0].positions.length ; col++)
                {
                    var cell = cs.cells[row][col];
                    var value = Ext4.isObject(cell) ? cell.value : cell;
                    html.push("<td align=right>" + (null==value?"&nbsp;":value) + "</td>");
                }
                html.push('</tr>');
            }
            html.push("</table>");
            html.push("<p><b>" + (duration/1000) + "</b></p>");
        }

        el.update(html.join(""));
    }


})();
</script>
<style>
    .dropOK
    {
        border:solid 2px green;
    }
</style>
<button onclick="run()">RUN</button>
<table id="<%=text(pivotDesignerId)%>">
<tr>
    <td rowspan="2"><fieldset style="height:100%"><legend>pages (NYI)</legend><div class="drop" id="<%=text(pivotDesignerId)%>.pages" data-axis="pages" style="min-height:200pt; min-width:100pt;">&nbsp;</div></fieldset></td>
    <td><fieldset height=100%><legend>filters</legend><div class="drop" id="<%=text(pivotDesignerId)%>.filter" data-axis="filter" style="min-height:100pt; min-width:100pt;">&nbsp;</div></fieldset></td>
    <td><fieldset height=100%><legend>columns</legend><div class="drop" id="<%=text(pivotDesignerId)%>.columns" data-axis="onColumns" style="min-height:100pt; min-width:100pt;">&nbsp;</div></fieldset></td>
</tr>
<tr>
    <td><fieldset height=100%><legend>rows</legend><div class="drop" id="<%=text(pivotDesignerId)%>.rows" data-axis="onRows" style="min-height:100pt; min-width:100pt;"></div></fieldset></td>
    <td><fieldset height=100%><legend>measures</legend><div id="<%=text(pivotDesignerId)%>.measures" style="min-height:100pt; min-width:100pt;">&nbsp;</div></fieldset></td></tr>
</table>


<select><option>filter me</option></select>
<hr>
<div id="<%=pivotDesignerId%>.filterArea"></div>
<h3>I'm a page header</h3>
<div id="<%=text(cellsetId)%>">
</div>
