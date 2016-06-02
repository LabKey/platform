(function($) {

    // Document Ready
    $(function() {
        var RENDERTO = "qwpDiv";
        var REGIONS = {
            testSchemaOnly: testSchemaOnly,
            testQueryOnly: testQueryOnly,
            testFilterArray: testFilterArray,
            testSort: testSort,
            testHideButtons: testHideButtons,
            testHideColumns: testHideColumns,
            testPagingConfig: testPagingConfig,
            testSetPaging: testSetPaging,
            test25337: test25337
        };

        var tabsSel = '.qwp-demo .tab',
                activeCls = 'active-tab';

        $(tabsSel).click(function() {
            $(tabsSel).removeClass(activeCls);
            $(this).addClass(activeCls);
        });

        function onHashChange(initial)
        {
            var hash = location.hash;
            if (initial === true) {
                if (hash) {
                    hash = hash.split('#')[1];
                }
            }
            else {
                hash = hash.split('#')[1];
            }

            if (hash && REGIONS.hasOwnProperty(hash)) {
                $('#qwpDiv').html('');
                LABKEY.Domain.get(function() {
                    LABKEY.Domain.get(function() {
                        LABKEY.Domain.get(function() {
                            REGIONS[hash]();
                        }, function() {
                            alert('Test data is not populated!');
                        }, 'Samples', 'sampleDataTest3');
                    }, function() {
                        alert('Test data is not populated!');
                    }, 'Samples', 'sampleDataTest2');
                }, function() {
                    alert('Test data is not populated!');
                }, 'Samples', 'sampleDataTest1');

                if (initial === true) {
                    $(tabsSel + ' a[href="' + '#' + hash + '"]').parent().addClass(activeCls);
                }
            }
        }

        window.addEventListener('hashchange', onHashChange, false);
        onHashChange(true);

        function testSchemaOnly() {
            new LABKEY.QueryWebPart({
                title: 'List out all queries in schema',
                schemaName: 'Samples',
                renderTo: RENDERTO,
                success: function() {
                    var results = $("a:contains('sampleDataTest')");
                    if (!results || results.length < 3) {
                        alert('Failed to list out all queries in Samples schema');
                    }
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
                renderTo: RENDERTO,
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
                renderTo: RENDERTO,
                success: function() {
                    var results = $('tr.labkey-alternate-row, tr.labkey-row');
                    if (results && results.length > 0) {
                        for (var i = 0; i < results.length; i++) {
                            if (results[i].lastChild.innerHTML !== 'blue') {
                                alert('Failed test: Filter by Tag = blue');
                            }
                        }
                    }
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
                renderTo: RENDERTO,
                success: function() {
                    var results = $('tr.labkey-alternate-row, tr.labkey-row');
                    if (results && results.length > 5) {
                        var result1 = results[0].lastChild.innerHTML;
                        var result2 = results[1].lastChild.innerHTML;
                        var result3 = results[2].lastChild.innerHTML;

                        if (result1.localeCompare(result2) > 0 || result2.localeCompare(result3) > 0) {
                            alert('Failed test: Sort by Tag');
                        }
                    }
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
                renderTo: RENDERTO,
                success: function() {
                    var results = $("a.labkey-menu-button");
                    if (results && results.length > 0) {
                        alert('Failed to hide buttons');
                    }
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
                renderTo: RENDERTO,
                success: function() {
                    var editLinks = $("a.labkey-text-link:contains('edit')");
                    var detailsLinks = $("a.labkey-text-link:contains('details')");
                    if ((editLinks && editLinks.length > 0) || (detailsLinks && detailsLinks.length > 0)) {
                        alert('Failed test: Hide Edit and Details columns');
                    }
                },
                failure: function() {
                    alert('Failed test: Hide Edit and Details columns');
                }
            });
        }

        function testPagingConfig() {
            new LABKEY.QueryWebPart({
                title: 'Set Paging to 3 with config',
                schemaName: 'Samples',
                queryName: 'sampleDataTest1',
                renderTo: RENDERTO,
                maxRows: 3,
                success: function(dr) {
                    if (dr.maxRows !== 3) {
                        alert('Failed test: Set Paging to 3 with maxRows config');
                    }
                    var results = $("a:contains('sampleDataTest1')");
                    if (!results || results.length != 3) {
                        alert('Failed to set Paging to 3 with maxRows config');
                    }
                },
                failure: function() {
                    alert('Failed test: Set Paging to 3 with config');
                }
            });
        }

        function testSetPaging() {
            new LABKEY.QueryWebPart({
                title: 'Set Paging to 2 with API',
                schemaName: 'Samples',
                queryName: 'sampleDataTest1',
                renderTo: RENDERTO,
                failure: function() {
                    alert('Failed test: Set Paging to 2 with API');
                },
                listeners: {
                    render: function(dr) {
                        if (dr.maxRows != 2) {
                            dr.setMaxRows(2);
                        }
                        else {
                            var results = $("a:contains('sampleDataTest1')");
                            if (!results || results.length != 2) {
                                alert('Failed to set Paging to 2 with API');
                            }
                        }
                    }
                }
            });
        }

        function test25337() {
            var check = false;
            new LABKEY.QueryWebPart({
                title: 'Regression #25337',
                schemaName: 'Samples',
                queryName: 'sampleDataTest1',
                renderTo: RENDERTO,
                removeableFilters: [
                    LABKEY.Filter.create('Name', undefined, LABKEY.Filter.Types.NONBLANK)
                ],
                failure: function() {
                    alert('Failed test: Regression #25337');
                },
                listeners: {
                    render: function(qwp) {
                        if (check) {
                            // confirm results
                            var filters = qwp.getUserFilterArray();
                            if (filters.length !== 3) {
                                alert('Failed test: Regression #25337. Expected removeableFilters + two filters of the same column/type to be applied');
                            }
                        }
                        else {
                            check = true;
                            var twoSameFilters = [
                                LABKEY.Filter.create('tag', 'blue', LABKEY.Filter.Types.CONTAINS),
                                LABKEY.Filter.create('tag', 'black', LABKEY.Filter.Types.CONTAINS)
                            ];
                            qwp.replaceFilters(twoSameFilters, {fieldKey: 'tag'});
                        }
                    }
                }
            });
        }

    });

})(jQuery);