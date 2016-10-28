/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


LABKEY.Utils.onReady(function()
{
    var $ = $ || jQuery;
    function QUERY_onClick()
    {
        var sql = sqlTextArea.val();
        if (!sql)
        {
            dr.text('no results');
            return;
        }
        new LABKEY.QueryWebPart2({
            renderTo: 'testDataRegionDiv',
            title: 'results',
            schemaName: 'core',
            sql: sql,
            buttonBarPosition: 'none'
        });
    }
    var div = $('#testQueryDiv');
    div.html("<div id='testQueryErrors' class='labkey-error'></div><textarea id='sql' style='min-width:700px;min-height:200px'></textarea><br><button id='submitQueryButton'>QUERY</button><p/><div id='testDataRegionDiv'></div>");
    var errors = $('#testQueryErrors');
    var sqlTextArea = $('#sql');
    var dr = $('#testDataRegionDiv');
    $('#submitQueryButton').on('click',QUERY_onClick);
});