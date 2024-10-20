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
    int uid = getRequestScopedUID();
    String pivotDesignerId = "pivotDesigner" + uid;
    String cellsetId = "cellset" + uid;
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">
    let cube;
    let starttime;

    (function()
    {
        let mdx;
        const configId = <%=q(configId)%>;
        const schemaName = <%=q(schemaName)%>;
        const cubeName = <%=q(cubeName)%>;
        const pivotDesignerId = <%=q(pivotDesignerId)%>;
        const cellsetId = <%=q(cellsetId)%>;
        let pivotDesignerFilter;
        let pivotDesignerPages;
        let pivotDesignerColumns;
        let pivotDesignerRows;
        let pivotDesignerMeasures;

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

        let model = {onRows:[], onColumns:[], filter:[], measures:[], filterValues:{}};


        function renderUI()
        {
            pivotDesignerFilter = Ext4.get(pivotDesignerId+".filter");
            pivotDesignerPages = Ext4.get(pivotDesignerId+".pages");
            pivotDesignerColumns = Ext4.get(pivotDesignerId+".columns");
            pivotDesignerRows = Ext4.get(pivotDesignerId+".rows");
            pivotDesignerMeasures = Ext4.get(pivotDesignerId+".measures");

            let pagesHtml = [];
            let filterHtml = [];
            let columnsHtml = [];
            let rowsHtml = [];

            // render select for each hierarchy
            function renderHierarchyFilter(h)
            {
                let select = ['<select class="hierarchy" class="filter" data-hierarchy="' + Ext4.htmlEncode(h.uniqueName) + '">'];
                // TODO list every member in tree order
                for (let l=0 ; l<2 ; l++)
                {
                    let level = h.levels[l];
                    for (let m=0 ; m<level.members.length ; m++)
                    {
                        let member = level.members[m];
                        if (member.name === "#notnull") continue;
                        select.push('<option value="' + Ext4.htmlEncode(member.uniqueName) + '">' + Ext4.htmlEncode(member.name) + '</option>');
                    }
                }
                select.push("</select>");
                Ext4.get(pivotDesignerId+".filterArea").insertHtml("beforeEnd", select.join(''));
                LABKEY.Utils.attachEventHandlerForQuerySelector("SELECT.hierarchy", "click", run, true);
            }


            let dimensions = cube.getDimensions();
            for (let d=0 ; d<dimensions.length ; d++)
            {
                let dimension = dimensions[d];
                if ("Measures" == dimension.name)
                    continue;
                let hierarchies = dimension.getHierarchies();
                for (let h=0 ; h<hierarchies.length ; h++)
                {
                    let hierarchy = hierarchies[h];
                    filterHtml.push('<div style="font-family:verdana; font-size:12pt; padding:2pt;" class="hierarchy" data-hierarchy="' + Ext4.htmlEncode(hierarchy.uniqueName) + '">' + Ext4.htmlEncode(hierarchy.name) +'</div>');
                    renderHierarchyFilter(hierarchy);
                }
            }

            let measures = cube.levelMap.MeasuresLevel.members;
            let measureHtml = [];
            for (let m=0 ; m<measures.length ; m++)
            {
                let measure = measures[m];
                measureHtml.push('<div style="font-family:verdana; font-size:12pt; padding:2pt;"><input class="measure" type="checkbox" ' + (m===0?"checked":"") + ' name="chooseMeasure' + Ext4.htmlEncode(measure.name) + '" value="measures:' + Ext4.htmlEncode(measure.uniqueName) + '">' + Ext4.htmlEncode(measure.name) + '</div>');
            }

            pivotDesignerPages.update(pagesHtml.join(''));
            pivotDesignerFilter.update(filterHtml.join(''));
            pivotDesignerColumns.update(columnsHtml.join(''));
            pivotDesignerRows.update(rowsHtml.join(''));
            pivotDesignerMeasures.update(measureHtml.join(''));
            LABKEY.Utils.attachEventHandlerForQuerySelector("INPUT.measure", "click", run, true);

            // DRAG AND DROP
            let overrides = {
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
                        let animCfgObj = {
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
                    let dropEl = Ext4.get(targetElId);

                    // Perform the node move only if the drag element's
                    // parent is not the same as the drop target
                    if (this.el.dom.parentNode.id !== targetElId)
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
                    if (targetElId !== this.el.dom.parentNode.id) {
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
            let draggableDivs = Ext4.DomQuery.select("DIV.hierarchy");
            draggableDivs.forEach(function(el)
            {
                let dd = Ext4.create('Ext.dd.DD',el,'hierarchyGroup');
                Ext4.apply(dd,overrides);
            });
            // targets
            // Instantiate instances of DDTarget for the rented and repair drop target elements
            let pagesDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerPages, 'hierarchyGroup');
            let filterDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerFilter, 'hierarchyGroup');
            let rowsDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerRows, 'hierarchyGroup');
            let columnsDDTarget = Ext4.create('Ext.dd.DDTarget', pivotDesignerColumns, 'hierarchyGroup');
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
            let filterMap = {};
            model.filter.forEach(function (uniqueName) {filterMap[uniqueName] = true;});
            let selects = Ext4.DomQuery.select("SELECT.filter");
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
            let draggableDivs = Ext4.DomQuery.select("DIV.hierarchy");
            draggableDivs.forEach(function(div){
                let parent = div.parentElement;
                let hierarchyName = div.dataset.hierarchy;
                let axisName = parent.dataset.axis;
                model[axisName].push(hierarchyName);
            });

            // checkbox elements
            let elements = Ext4.get(pivotDesignerId).select("INPUT").elements;
            for (let e=0 ; e<elements.length ; e++)
            {
                let input = elements[e];
                console.log("value="+input.value+" checked="+input.checked);
                if (!input.checked)
                    continue;
                let value = input.value;
                let split = value.indexOf(':');
                let where = value.substring(0,split);
                let uniqueName = value.substring(split+1);
                console.log(where + "||" + uniqueName);
                model[where].push(uniqueName);
            }

            // active filters
            let filterMap = {};
            model.filter.forEach(function (uniqueName) {filterMap[uniqueName] = true;});
            let selects = Ext4.DomQuery.select("SELECT.filter");
            selects.forEach(function (s)
            {
                if (!filterMap[s.dataset.hierarchy])
                    return;
                let value = Ext4.get(s).getValue();
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
            let measureexpr = setFromMembers(model.measures);

            let query = "SELECT\n";

            if (model.onColumns.length === 0)
            {
                query += measureexpr + " ON COLUMNS\n";
            }
            else
            {
                let colexpr = crossExprFromHierarchies(model.onColumns,true);
                query += "NON EMPTY CROSSJOIN(" + colexpr + "," + measureexpr + ") ON COLUMNS\n";
            }

            if (model.onRows.length !== 0)
            {
                let rowexpr = crossExprFromHierarchies(model.onRows,false);
                query += ", NON EMPTY " + rowexpr + " ON ROWS\n";
            }

            query += "FROM " + cubeName + "\n";

            if (model.filterValues.length !== 0)
            {
                query += " WHERE (";
                let comma = "";
                model.filterValues.forEach(function(s){
                    query += comma + s;
                    comma = ", ";
                });
                query += ")\n";
            }

            console.log(query);

            Ext4.getBody().mask();
            let config = {
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
            if (hierarchies.length === 1)
            {
                return hierarchies[0] + ".members";
            }
            else if (hierarchies.length > 1)
            {
                return "NONEMPTYCROSSJOIN(" + hierarchies[0] + ".members," + hierarchies[1] + ".members)";
            }
            else
            {
                let u = hierarchies.pop();
                let p = hierarchies.pop();
                let join = "NONEMPTYCROSSJOIN(" + p + ".members," + u + ".members)";
                while (hierarchies.length > 0)
                {
                    let h = hierarchies.pop();
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
            let msg = response.statusText;
            if (json && json.exception)
                msg = json.exception;
            alert(msg);
        }


        function renderCellSet(cs, el)
        {
            const duration = new Date().getTime() - starttime;
            Ext4.getBody().unmask();
            el = Ext4.get(el||'cellset');
            let html = [], row, col, cell, value;

            html.push('<table class="labkey-data-region-legacy labkey-show-borders"><tr>');
            if (cs.axes.length>1)
                html.push('<td>&nbsp;</td>');
            for (col=0 ; col<cs.axes[0].positions.length ; col++)
            {
                // only showing first member (handle hierarchy)
                html.push('<td class="labkey-column-header">' + Ext4.htmlEncode(cs.axes[0].positions[col][0].uniqueName) + "</td>");
            }
            html.push("</tr>");

            if (cs.axes.length === 1)
            {
                //for (let row=0 ; row<cs.axes[1].positions.length ; row++)
                {
                    html.push('<tr>');
                    for (col=0 ; col<cs.axes[0].positions.length ; col++)
                    {
                        cell = cs.cells[col];
                        value = Ext4.isObject(cell) ? cell.value : cell;
                        html.push("<td align=right>" + (null==value?"&nbsp;":value) + "</td>");
                    }
                    html.push('</tr>');
                }
                html.push("</table>");
                html.push("<p><b>" + (duration/1000) + "</b></p>");        }
            else if (cs.axes.length === 2)
            {
                for (row=0 ; row<cs.axes[1].positions.length ; row++)
                {
                    html.push('<tr>');
                    let pos = cs.axes[1].positions[row];
                    for (let p=0; p < pos.length; p++)
                    {
                        // only showing first member (handle hierarchy)
                        html.push('<td class="labkey-column-header">' + Ext4.htmlEncode(cs.axes[1].positions[row][p].uniqueName) + "</td>");
                    }

                    for (col=0 ; col<cs.axes[0].positions.length ; col++)
                    {
                        cell = cs.cells[row][col];
                        value = Ext4.isObject(cell) ? cell.value : cell;
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
<button class="run">RUN</button>
<table id="<%=unsafe(pivotDesignerId)%>">
    <tr>
        <td rowspan="2"><fieldset style="height:100%"><legend>pages (NYI)</legend><div class="drop" id="<%=unsafe(pivotDesignerId)%>.pages" data-axis="pages" style="min-height:200pt; min-width:100pt;">&nbsp;</div></fieldset></td>
        <td><fieldset height=100%><legend>filters</legend><div class="drop" id="<%=unsafe(pivotDesignerId)%>.filter" data-axis="filter" style="min-height:100pt; min-width:100pt;">&nbsp;</div></fieldset></td>
        <td><fieldset height=100%><legend>columns</legend><div class="drop" id="<%=unsafe(pivotDesignerId)%>.columns" data-axis="onColumns" style="min-height:100pt; min-width:100pt;">&nbsp;</div></fieldset></td>
    </tr>
    <tr>
        <td><fieldset height=100%><legend>rows</legend><div class="drop" id="<%=unsafe(pivotDesignerId)%>.rows" data-axis="onRows" style="min-height:100pt; min-width:100pt;"></div></fieldset></td>
        <td><fieldset height=100%><legend>measures</legend><div id="<%=unsafe(pivotDesignerId)%>.measures" style="min-height:100pt; min-width:100pt;">&nbsp;</div></fieldset></td></tr>
</table>


<select><option>filter me</option></select>
<hr>
<div id="<%=unsafe(pivotDesignerId)%>.filterArea"></div>
<h3>I'm a page header</h3>
<div id="<%=unsafe(cellsetId)%>">
</div>
<labkey:script>
    LABKEY.Utils.attachEventHandlerForQuerySelector("BUTTON.run", "click", run, true);
</labkey:script>
