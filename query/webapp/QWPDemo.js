function testSchemaOnly() {
    new LABKEY.QueryWebPart({
        title: 'List out all queries in Samples schema',
        schemaName: 'Samples',
        renderTo: 'testRegion1',
        success: function() {
            //TODO validate result
        },
        failure: function() {
           alert('Failed test: List out all queries in schema');
        }
    });
}


function testQueryOnly() {
    new LABKEY.QueryWebPart({
        title: 'Show default view for query sampleDataTest1',
        schemaName: 'Samples',
        queryName: 'sampleDataTest1',
        renderTo: 'testRegion2',
        success: function() {
            //TODO
    },
    failure: function() {
        alert('Failed test: Show default view for query sampleDataTest');
    }
    });
}

function testFilterArray() {
    new LABKEY.QueryWebPart({
        title: 'Filter by Tag = blue',
        schemaName: 'Samples',
        queryName: 'sampleDataTest1',
        filterArray: [LABKEY.Filter.create('tag', 'blue', LABKEY.Filter.Types.EQUAL)],
        renderTo: 'testRegion3',
        success: function() {
            //TODO
        },
        failure: function() {
            alert('Failed test: Filter by Tag = blue');
        }
    });
}

function testSort() {
    new LABKEY.QueryWebPart({
        title: 'Sort by Tag',
        schemaName: 'Samples',
        queryName: 'sampleDataTest1',
        sort: 'tag',
        renderTo: 'testRegion4',
        success: function() {
            //TODO
        },
        failure: function() {
            alert('Failed test: Sort by Tag');
        }
    });
}

function testHideButtons() {
    new LABKEY.QueryWebPart({
        title: 'Hide buttons',
        schemaName: 'Samples',
        queryName: 'sampleDataTest1',
        showExportButtons: false,
        showInsertNewButton: false,
        showPagination: false,
        allowChooseQuery: false,
        allowChooseView: false,
        showDeleteButton: false,
        renderTo: 'testRegion5',
        success: function() {
            //TODO
        },
        failure: function() {
            alert('Failed test: Hide buttons');
        }
    });
}

function testHideColumns() {
    new LABKEY.QueryWebPart({
        title: 'Hide Edit and Details columns',
        schemaName: 'Samples',
        queryName: 'sampleDataTest1',
        showDetailsColumn: false,
        showUpdateColumn: false,
        renderTo: 'testRegion6',
        success: function() {
            //TODO
        },
        failure: function() {
            alert('Failed test: Hide Edit and Details columns');
        }
    });
}

var MAX_ROWS = 3;
function testSetPaging() {
    new LABKEY.QueryWebPart({
        title: 'Set Paging to 2 with API',
        schemaName: 'Samples',
        queryName: 'sampleDataTest1',
        renderTo: 'testRegion7',
        maxRows: MAX_ROWS,
        failure: function() {
            alert('Failed test: Failed test: Set Paging to 2 with API');
        },
        listeners: {
            render: function(dr) {
                if (dr.maxRows !== MAX_ROWS) {
                    throw new Error('Failed to apply maxRows');
                }
                else if (MAX_ROWS != 2) {
                    // change the maxRows
                    MAX_ROWS = 2;
                    dr.setMaxRows(MAX_ROWS);
                }
            }
        }
    });
}

function runTests()
{
    testSchemaOnly();
    testQueryOnly();
    testFilterArray();
    testSort();
    testHideButtons();
    testHideColumns();
    testSetPaging();
}