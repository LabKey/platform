/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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
            test25337: test25337,
            testPageOffset: testPageOffset,
            testParameterizedQueries: testParameterizedQueries,
            testRemovableFilters: testRemovableFilters,
            testHidePagingCount: testHidePagingCount,
            testShowAllTotalRows: testShowAllTotalRows
        };

        var PAGE_OFFSET = 4;
        var PAGE_OFFSET_SKIPP = false;

        function assertPagingCount(region, first, last, total) {
            var headerId = '#' + region.domId + '-headerbar';
            var pagingEl = $(headerId).find('.paging-widget > a');

            try {
                if (pagingEl.length !== 1) {
                    throw 'Unable to find paging widget for this region.';
                }
                else {
                    var compare = '' + first + ' - ' + last;
                    if (total !== undefined) {
                        compare += ' of ' + total;
                    }

                    if (compare !== pagingEl.text()) {
                        throw 'Failed paging count assertion.\nExpected : "' + compare + '"\nActual: "' + pagingEl.text() + '"';
                    }
                }

                return true;
            }
            catch (e) {
                console.log(e);
                alert(e);
                return false;
            }
        }

        function getRowCount(region) {
            if (!region) {
                return -1;
            }

            var id = '#' + region.domId;
            return $(id + ' tr.labkey-row').size() + $(id + ' tr.labkey-alternate-row').size();
        }

        function resetVariables() {
            PAGE_OFFSET = 4;
            PAGE_OFFSET_SKIPP = false;
        }

        var tabsSel = '.qwp-demo .tab',
            activeCls = 'active-tab';

        $(tabsSel).click(function() {
            $(tabsSel).removeClass(activeCls);
            $(this).addClass(activeCls);
        });

        function onHashChange(initial) {
            resetVariables();
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
                    else {
                        LABKEY.Utils.signalWebDriverTest("testSchemaOnly");
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
                success: function() {
                    LABKEY.Utils.signalWebDriverTest("testQueryOnly");
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
                renderTo: RENDERTO,
                success: function() {
                    var results = $('tr.labkey-alternate-row, tr.labkey-row');
                    var failed = false;
                    if (results && results.length > 0) {
                        for (var i = 0; i < results.length; i++) {
                            if (results[i].lastChild.innerHTML !== 'blue') {
                                alert('Failed test: Filter by Tag = blue');
                                failed = true;
                            }
                        }
                    }
                    if (!failed) {
                        LABKEY.Utils.signalWebDriverTest("testFilterArray");
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
                        else {
                            LABKEY.Utils.signalWebDriverTest("testSort");
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
                showReports: false,
                showDeleteButton: false,
                renderTo: RENDERTO,
                success: function() {
                    var results = $("a.labkey-menu-button");
                    if (results && results.length > 0) {
                        alert('Failed to hide buttons');
                    }
                    else {
                        LABKEY.Utils.signalWebDriverTest("testHideButtons");
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
                    else {
                        LABKEY.Utils.signalWebDriverTest("testHideColumns");
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
                        alert('Failed test: Set Paging to 3 with maxRows config. Expected maxRows to be 3. Max rows is ' + dr.maxRows);
                    }

                    if (getRowCount(dr) !== 3) {
                        alert('Failed to set Paging to 3 with maxRows config. Expected 3 rows to be in the region.');
                    }
                    else {
                        LABKEY.Utils.signalWebDriverTest('testPagingConfig');
                    }
                },
                failure: function() {
                    alert('Failed test: Set Paging to 3 with config. Failed to load.');
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
                            if (getRowCount(dr) !== 2) {
                                alert('Failed to set Paging to 2 with API. Expected 2 rows to be in the region.');
                            }
                            else {
                                LABKEY.Utils.signalWebDriverTest('testSetPaging');
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
                            else {
                                LABKEY.Utils.signalWebDriverTest("test25337");
                            }
                        }
                        else {
                            check = true;
                            var twoSameFilters = [
                                LABKEY.Filter.create('tag', 'blue', LABKEY.Filter.Types.CONTAINS),
                                LABKEY.Filter.create('tag', 'black', LABKEY.Filter.Types.CONTAINS)
                            ];
                            qwp.replaceFilters(twoSameFilters);
                        }
                    }
                }
            });
        }

        function testPageOffset() {
            new LABKEY.QueryWebPart({
                title: 'Change Page Offset',
                schemaName: 'Samples',
                queryName: 'sampleDataTest1',
                renderTo: RENDERTO,
                maxRows: 2,
                offset: PAGE_OFFSET,
                failure: function() {
                    alert('Failed test: Set Paging to 2 with API');
                },
                listeners: {
                    render: function(dr) {
                        var prevPageLink = $('button:not(.disabled)').has('i[class="fa fa-chevron-left"]');
                        if (PAGE_OFFSET === 4) {
                            var firstPageLink = $('li').has('a:contains("Show first")');
                            if (!firstPageLink || firstPageLink.length == 0 || !prevPageLink || prevPageLink.length == 0) {
                                alert('Failed to set Offset to 4 and MaxRows to 2 with config');
                            }
                            else {
                                PAGE_OFFSET = 0;
                                dr.setPageOffset(PAGE_OFFSET);
                            }
                        }
                        else if (!PAGE_OFFSET_SKIPP) {
                            PAGE_OFFSET_SKIPP = true;
                            var nextPageLink = $('button:not(.disabled)').has('i[class="fa fa-chevron-right"]');
                            if (prevPageLink.length === 0 && nextPageLink.length > 0) {
                                LABKEY.Utils.signalWebDriverTest("testPageOffset");
                            }
                            else {
                                alert('Failed to set Offset to 0 with API');
                            }
                        }
                    }
                }
            });
        }

        function testParameterizedQueries() {
            var loadCount = 0;
            new LABKEY.QueryWebPart({
                renderTo: RENDERTO,
                title: 'Parameterized Queries',
                schemaName: 'Samples',
                sql: "PARAMETERS (TAG_START VARCHAR) SELECT id, tag FROM sampleDataTest1 WHERE tag LIKE TAG_START || '%'",
                parameters: {
                    TAG_START: 'b' // blue, black, blue
                },
                failure: function() {
                    alert('Failed test: Parameterized Queries.');
                },
                listeners: {
                    render: function(qwp) {
                        var params = qwp.getParameters();
                        var rowCount = getRowCount(qwp);

                        if (loadCount === 0) {
                            if (params['TAG_START'] !== 'b') {
                                alert('Failed test: Parameterized Queries. Expected initial parameters to be returned.');
                                return;
                            }
                            else if (rowCount !== 3) {
                                alert('Failed test: Parameterized Queries. Expected initial load of 3 row. There are ' + rowCount + ' rows.');
                                return;
                            }

                            qwp.setParameters({
                                TAG_START: 'r' // red
                            });
                        }
                        else if (loadCount === 1) {
                            if (params['TAG_START'] !== 'r') {
                                alert('Failed test: Parameterized Queries. Expected setParameters to set parameter to "r". Was: \"' + params['TAG_START'] + '\"');
                                return;
                            }
                            else if (rowCount !== 1) {
                                alert('Failed test: Parameterized Queries. Expected setParameters to "r" to load of 1 row. There are ' + rowCount + ' rows.');
                                return;
                            }

                            qwp.clearAllParameters();
                        }
                        else if (loadCount === 2) {
                            var paramName = qwp.dataRegionName + '.param.TAG_START';

                            // expect form to set parameters
                            if ($('input[name="' + paramName + '"]').size() !== 1) {
                                alert('Failed test: Parameterized Queries. Expected input form for parameter TAG_START.');
                                return;
                            }

                            qwp.setParameters([
                                [paramName, 'yellow']
                            ]);
                        }
                        else if (loadCount === 3) {
                            if (params['TAG_START'] !== 'yellow') {
                                alert('Failed test: Parameterized Queries. Expected setParameters to set parameter to "yellow". Was: \"' + params['TAG_START'] + '\"');
                                return;
                            }
                            else if (rowCount !== 1) {
                                alert('Failed test: Parameterized Queries. Expected setParameters to "yellow" to load of 1 row. There are ' + rowCount + ' rows.');
                                return;
                            }

                            LABKEY.Utils.signalWebDriverTest("testParameterizedQueries");
                        }

                        loadCount++;
                    }
                }
            });
        }

        function testRemovableFilters() {
            var initialLoad = true;
            new LABKEY.QueryWebPart({
                title: 'Keep Removable Filters',
                schemaName: 'Samples',
                queryName: 'sampleDataTest1',
                renderTo: RENDERTO,
                removeableFilters: [
                    LABKEY.Filter.create('tag', 'yellow')
                ],
                failure: function() {
                    alert('Failed test: Keep Removable Filters.');
                },
                listeners: {
                    render: function(qwp) {
                        if (initialLoad) {
                            initialLoad = false;
                            qwp.refresh();
                        }
                        else {
                            var filters = qwp.getUserFilterArray();
                            if (filters.length !== 1) {
                                alert('Failed test: Keep Removable Filters. Expected removeableFilters to be maintained.');
                            }
                            else {
                                LABKEY.Utils.signalWebDriverTest("testRemovableFilters");
                            }
                        }
                    }
                }
            });
        }

        function testHidePagingCount() {
            var loadCount = 0;
            var schema = 'Samples';
            var query = 'sampleDataTest1';

            LABKEY.Query.selectRows({
                schemaName: schema,
                queryName: query,
                success: function(ssr) {
                    var TOTAL_EXPECTED_ROWS = ssr.rowCount;

                    new LABKEY.QueryWebPart({
                        title: 'Hide Paging Count (#32206)',
                        schemaName: schema,
                        queryName: query,
                        maxRows: 2,
                        renderTo: RENDERTO,
                        failure: function() {
                            alert('Failed test: Hide Paging Count.');
                        },
                        listeners: {
                            render: function(dr) {
                                // timeout is used here to defer displaying error until after region is rendered.
                                // Less confusing when comparing error against current region state.
                                setTimeout(function() {
                                    if (loadCount === 0) {
                                        loadCount++;
                                        if (assertPagingCount(dr, 1, 2, TOTAL_EXPECTED_ROWS)) {
                                            dr.showPaginationCount = false;
                                            dr.refresh();
                                        }
                                    }
                                    else if (loadCount === 1) {
                                        loadCount++;
                                        if (assertPagingCount(dr, 1, 2)) {
                                            dr.showAllRows(); // should remain hidden if all rows are shown
                                        }
                                    }
                                    else if (loadCount === 2) {
                                        loadCount++;
                                        if (assertPagingCount(dr, 1, TOTAL_EXPECTED_ROWS)) {
                                            dr.setPageOffset(2); // should remain hidden if offset changes
                                        }
                                    }
                                    else if (loadCount === 3) {
                                        loadCount++;
                                        if (assertPagingCount(dr, 3, 4)) {
                                            LABKEY.Utils.signalWebDriverTest('testHidePagingCount');
                                        }
                                    }
                                }, 100);
                            }
                        }
                    });
                }
            });
        }

        function testShowAllTotalRows() {
            new LABKEY.QueryWebPart({
                title: 'Show All Rows',
                schemaName: 'Samples',
                queryName: 'sampleDataTest1',
                renderTo: RENDERTO,
                failure: function() {
                    alert('Failed test: Show All Rows');
                },
                listeners: {
                    render: function(dr) {
                        if (dr.maxRows != -1) {
                            dr.showAllRows();
                        }
                        else {
                            if (!dr.totalRows)
                            {
                                alert('Failed test: Show All Rows. totalRows is not set correctly with Show All.');
                            }
                            else {
                                LABKEY.Utils.signalWebDriverTest("testShowAllTotalRows");
                            }
                        }
                    }
                }
            });
        }

    });

})(jQuery);