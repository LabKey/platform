/*
 * Copyright (c) 2016-2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function sampleSetDomainTypeTest(queryName, rows, description, additionalCallBack, xmlMetadata) {

    function getSuccessHandler(dd, queryName) {
        console.log('The Sample Type \'' + queryName + '\' already exists.', true);
        additionalCallBack.call();
    }

    function getErrorHandler() {
        console.log('Did not find the \'' + queryName + '\' Sample Type.');

        // Try to create the domain
        var domainDesign = {
            name : queryName,
            description : description,
            fields : [{
                name : 'id',
                rangeURI : 'int'
            },{
                name : 'sort',
                rangeURI: 'int'
            },{
                name : 'tag',
                rangeURI : 'string'
            }]
        };

        var domainOptions = {
            idCols : [0]
        };

        function createSuccessHandler() {
            saveQueryXML('Samples', queryName, xmlMetadata, function() {
                console.log('Successfully created the \'' + queryName + '\' Sample Type.');
                LABKEY.Domain.get(function() {
                    LABKEY.Query.insertRows({
                        containerPath: LABKEY.containerPath,
                        schemaName: 'Samples',
                        queryName: queryName,
                        rows: rows,
                        success: additionalCallBack,
                        failure: function(response) {
                            console.log('Failed to insert rows into the \'' + queryName + '\' Sample Type');
                        }
                    });
                }, function(response) {
                    console.log('Failed to create the \'' + queryName + '\' Sample Type');
                }, 'Samples', queryName);
            });
        }

        function createErrorHandler(response) {
            console.log('Failed to create the \'' + queryName + '\' Sample Type');
        }

        LABKEY.Domain.create(createSuccessHandler, createErrorHandler,
                'sampleset', domainDesign, domainOptions, LABKEY.containerPath);
    }

    // See if the domain already exists
    LABKEY.Domain.get(getSuccessHandler, getErrorHandler, 'Samples', queryName);
}

function saveQueryXML(schemaName, queryName, xmlMetadata, cb) {
    if (xmlMetadata) {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'saveSourceQuery.api'),
            method: 'POST',
            jsonData: {
                ff_metadataText: xmlMetadata,
                schemaName: schemaName,
                queryName: queryName
            },
            success: LABKEY.Utils.getCallbackWrapper(function() { cb(); }),
            failure: LABKEY.Utils.getCallbackWrapper(undefined, undefined, /* isErrorCallback */ true)
        })
    }
    else {
        cb();
    }
}

function setUpDomains() {
    var rows1 =[{"Alias":  'alias 1', "Id": 1, "Sort": 100, "Tag": 'blue'},
                {"Alias":  'alias 2', "Id": 2, "Sort": 90,  "Tag": 'red'},
                {"Alias":  'alias 3', "Id": 3, "Sort": 80,  "Tag": 'yellow'},
                {"Alias":  'alias 4', "Id": 4, "Sort": 70,  "Tag": 'white'},
                {"Alias":  'alias 5', "Id": 5, "Sort": 60,  "Tag": 'black'},
                {"Alias":  'alias 6', "Id": 6, "Sort": 50,  "Tag": 'blue'}];
    var rows2 =[ {"Alias":  'alias 2-1', "Id": 201, "Sort": 1000, "Tag": 'square'}];
    var rows3 =[ {"Alias":  'alias 3-1', "Id": 301, "Sort": 500,  "Tag": 'Hispanic'}];
    sampleSetDomainTypeTest('sampleDataTest1', rows1, 'A sample type with color tags', function() {
        sampleSetDomainTypeTest('sampleDataTest2', rows2, 'A sample type with shape tags', function(){
            sampleSetDomainTypeTest('sampleDataTest3', rows3, 'A sample type with race tags', function(){
                location.reload();
            });
        });
    }, ([
    '<tables xmlns="http://labkey.org/data/xml">',
        '<table tableName="sampleDataTest1" tableDbType="TABLE">',
            '<buttonBarOptions includeStandardButtons="true">',
                '<includeScript>QWPDemoBar.js</includeScript>',
                '<onRender>QWPDemoBar.confirm.render</onRender>',
            '</buttonBarOptions>',
        '</table>',
    '</tables>'
    ].join('\n').trim()));
}

function dropDomains() {
    var completeCt = 0;
    var queries = ["sampleDataTest1", "sampleDataTest2", "sampleDataTest3"];
    var dropSuccess = function() {
        dropComplete('dropped');
    };
    var dropFailure = function() {
        dropComplete('drop failed');
    };
    var dropComplete = function(msg) {
        console.log(queries[completeCt] + ": " + msg);
        completeCt++;
        if (completeCt >= queries.length)
            location.reload(); // add page refresh for automated test
        else
            drop(queries[completeCt]);
    };
    var drop = function(query)
    {
        LABKEY.Domain.drop({success: dropSuccess, failure: dropFailure, schemaName: 'Samples', queryName: query});
    };
    drop(queries[0]);
}
