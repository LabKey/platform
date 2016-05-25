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
        alert('Failed test: Query sampleDataTest1');
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
            alert('Failed test: Query sampleDataTest1');
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
            alert('Failed test: Query sampleDataTest1');
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
            alert('Failed test: Query sampleDataTest1');
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
            alert('Failed test: Query sampleDataTest1');
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
}