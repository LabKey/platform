/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var issueTmpl =
[
		'<table class="report" cellspacing=0><thead>',
		'<tpl for="rows">',
			'<tpl if="values.breakLevel==0">',
				'<tr><td class="break-spacer">&nbsp;</td></tr>',
				'<tr><td class="break-0" colspan=3>{[this.getHtml(values.asObject.AssignedTo)]}</tr>',
				'<tr><td>&nbsp;</td>',
				'<tpl for="parent.fields">',
					'<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
				'</tpl>',
				'</tr>',
				'{[this.resetGrid()]}',
			'</tpl>',
			'<tr class="{[this.getGridRowClass()]}">',
			'<tpl if="values.breakLevel&gt;=0">',
				'<td class="break-1">{[this.getHtml(values.asObject.Status)]}</td>',
			'</tpl>',
			'<tpl if="values.breakLevel&lt;0">',
				'<td class="break-1">&nbsp;</td>',
			'</tpl>',
				'<tpl for="asArray">',
					'<td>{[this.getHtml(values)]}</td>',
				'</tpl>',
			'</tr>',
		'</tpl>',
		'</table>'
].join("");

var rowspanTmpl3 =
[
		'<table class="report">',
		'<thead>',
				'<tpl for="fields">',
					'<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
				'</tpl>',
		'</thead>',
		'<tpl for="rows">',
		        '<tr class="{[this.getGridRowClass()]}">',
				'<tpl for="asArray">{[this.getGridCellHtml(values)]}</tpl>',
    			'</tr>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
].join("");


var pageTmpl1 =
[
		'<table class="report" cellspacing=0>',
        '<tpl for="pages">',
            '{[this.resetGrid(),""]}',
// PAGE TEMPLATE
            '<tr><td class="break-spacer">&nbsp;<br>&nbsp;</td></tr>',
            '<tr><td colspan="{[this.data.fields.length]}">',
                '<div style="border:solid 1px #eeeeee; padding:5px; margin:10px;">',
                '<table>',
                    '<tr><td colspan=2 style="padding:5px; font-weight:bold; font-size:1.3em; text-align:center;">{[ this.getHtml(values.headerValue) ]}</td></tr>',
// note nested <tpl>, this will make values==datavalue and parent==field
                    '<tpl for="this.data.pageFields"><tpl for="parent.first.asArray[values.index]">',
                        '<tr><td align=right>{[this.getCaptionHtml(parent)]}:&nbsp;</td><td align=left style="{parent.style}">{[this.getHtml(values)]}</td></tr>',
                    '</tpl></tpl>',
                '</table>',
                '</div>',
            '</td></tr>',
// GRID TEMPLATE
            '<tr>',
                '<tpl for="this.data.gridFields">',
                    '<th class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
                '</tpl>',
            '</tr>',
            '<tpl for="rows">',
                '<tr class="{[this.getGridRowClass()]}">',
// again nested tpl
                '<tpl for="this.data.gridFields"><tpl for="parent.asArray[values.index]">',
                    '{[ this.getGridCellHtml(values) ]}',
                '</tpl></tpl>',
                '</tr>',
            '</tpl>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
].join("");




var SIMPLE_PAGE_TEMPLATE =
{
    template :
    [
		'<table class="report" cellspacing=0>',
        '<tpl for="pages">',
            '{[this.resetGrid(),""]}',
// PAGE TEMPLATE
            '<tr><td class="break-spacer">&nbsp;<br>&nbsp;</td></tr>',
            '<tr><td colspan="{[this.data.fields.length]}">',
                '<div style="border:solid 1px #eeeeee; padding:5px; margin:10px;">',
                '<table>',
                    '<tr><td colspan=2 style="padding:5px; font-weight:bold; font-size:1.3em; text-align:center;">{[ this.getHtml(values.headerValue) ]}</td></tr>',
// note nested <tpl>, this will make values==datavalue and parent==field
                    '<tpl for="this.data.pageFields"><tpl for="parent.first.asArray[values.index]">',
                        '<tr><td align=right>{[this.getCaptionHtml(parent)]}:&nbsp;</td><td align=left style="{parent.style}">{[this.getHtml(values)]}</td></tr>',
                    '</tpl></tpl>',
                '</table>',
                '</div>',
            '</td></tr>',
// GRID TEMPLATE
            '<tr>',
                '<tpl for="this.data.gridFields">',
                    '<th style="padding-right: 10px;" class="labkey-column-header">{[this.getCaptionHtml(values)]}</th>',
                '</tpl>',
            '</tr>',
            '<tpl for="rows">',
                '<tr class="{[this.getGridRowClass()]}">',
// again nested tpl
                '<tpl for="this.data.gridFields"><tpl for="parent.asArray[values.index]">',
                    '{[ this.getGridCellHtml(values) ]}',
                '</tpl></tpl>',
                '</tr>',
            '</tpl>',
		'</tpl>',
		'</table>',
		'{[((new Date()).valueOf() - this.start)/1000.0]}'
    ],
    on :
    {
        dataload : function(rpt, data)
        {
        },
        afterdatatransform : function(rpt, data)
        {
            // set headerValue field for each page
            var index = data.pageFields[0].index;
            for (var p=0 ; p<data.pages.length ; p++)
            {
                var page = data.pages[p];
                page.headerValue = page.first.asArray[index];
            }
        }
    }
};



function testIssues(el)
{
    var helper = new LABKEY.TemplateReport(
    {
        pageFields:['AssignedTo', {name:'AssignedTo/UserId', style:"color:purple;"}],
        pageBreakInfo:[{name:'AssignedTo', rowspans:false}],
        gridFields:['Status', 'IssueId', 'Created', 'Priority', 'Title', 'Type', 'CreatedBy', 'Area', 'Milestone'],
        rowBreakInfo:[{name:'Status', rowspans:true}],
        reportTemplate : SIMPLE_PAGE_TEMPLATE,
        renderTo : el,
        queryConfig : {
            requiredVersion: 12.1,
            schemaName: 'issues',
            queryName: 'Issues',
            columns: 'AssignedTo,Status,XY,IssueId,Created,Priority,Title,Type,AssignedTo/DisplayName,AssignedTo/UserId,CreatedBy,Area,Milestone,Triage',
            sort: 'AssignedTo/DisplayName,Status,-IssueId',
            includeStyle : true
        }
    });
}
