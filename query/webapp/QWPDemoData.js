function sampleSetDomainTypeTest(queryName, rows, description) {

    function getSuccessHandler(dd, queryName) {
        console.log('The Sample Set \'' + queryName + '\' already exists.', true);
    }

    function getErrorHandler() {
        console.log('Did not find the \'' + queryName + '\' Sample Set.');

        // Try to create the domain
        var domainDesign = {
            name : queryName,
            description : description,
            fields : [{
                name : 'id',
                rangeURI : 'int'
            },{
                name : 'tag',
                rangeURI : 'string'
            }]
        };

        var domainOptions = {
            idCols : [0]
        };

        function createSuccessHandler() {
            console.log('Successfully created the \'' + queryName + '\' Sample Set.');
            LABKEY.Domain.get(function(_dd){

                LABKEY.Query.insertRows({
                    containerPath: LABKEY.containerPath,
                    schemaName: 'Samples',
                    queryName: queryName,
                    rows: rows,
                    successCallback: function() {
                        location.reload();
                    }
                });

            }, function(response) {
                console.log('Failed to update the \'' + queryName + '\' Sample Set');
            }, 'Samples', queryName);
        }

        function createErrorHandler(response) {
            console.log('Failed to create the \'' + queryName + '\' Sample Set');
        }

        LABKEY.Domain.create(createSuccessHandler, createErrorHandler,
                'sampleset', domainDesign, domainOptions, LABKEY.containerPath);
    }

    // See if the domain already exists
    LABKEY.Domain.get(getSuccessHandler, getErrorHandler, 'Samples', queryName);
}
function setUpDomains()
{
    var rows1 =[{"Alias":  'alias 1', "Id": 1, "Tag": 'blue'},
                {"Alias":  'alias 2', "Id": 2, "Tag": 'red'},
                {"Alias":  'alias 3', "Id": 3, "Tag": 'yellow'},
                {"Alias":  'alias 4', "Id": 4, "Tag": 'white'},
                {"Alias":  'alias 5', "Id": 5, "Tag": 'black'},
                {"Alias":  'alias 6', "Id": 6, "Tag": 'blue'}];
    var rows2 =[ {"Alias":  'alias 2-1', "Id": 201, "Tag": 'square'}];
    var rows3 =[ {"Alias":  'alias 3-1', "Id": 301, "Tag": 'Hispanic'}];
    sampleSetDomainTypeTest('sampleDataTest1', rows1, 'A sample set with color tags');
    sampleSetDomainTypeTest('sampleDataTest2', rows2, 'A sample set with shape tags');
    sampleSetDomainTypeTest('sampleDataTest3', rows3, 'A sample set with race tags');
}
function dropDomains()
{
    var dropSuccess = function() {
        console.log('dropped');
        location.reload(); // add page refresh for automated test
    };
    var dropFailure = function() {
        console.log('drop failed');
        location.reload(); // add page refresh for automated test
    };
    LABKEY.Domain.drop({success: dropSuccess, failure: dropFailure, schemaName: 'Samples', queryName: 'sampleDataTest1'});
    LABKEY.Domain.drop({success: dropSuccess, failure: dropFailure, schemaName: 'Samples', queryName: 'sampleDataTest2'});
    LABKEY.Domain.drop({success: dropSuccess, failure: dropFailure, schemaName: 'Samples', queryName: 'sampleDataTest3'});
}
