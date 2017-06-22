/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
/**
 * A new implementation of OlapExplorer that support multiple level using tree as underlying data structure
 */
Ext.define('LABKEY.app.model.OlapExplorer2', {

    extend: 'LABKEY.app.model.OlapExplorer',

    fields: [
        {name : 'lvlDepth', type: 'int', defaultValue: 0},
        {name : 'isSelected', type : 'boolean', defaultValue: false},
        {name : 'isLeafNode', type : 'boolean', defaultValue: false}
    ]
});

Ext.define('LABKEY.app.store.OlapExplorer2', {
    extend: 'LABKEY.app.store.OlapExplorer',
    alternateClassName: 'LABKEY.olapStore2',
    allRecordTree: null,
    statics: {
        /**
         * These sort functions assume sorting an Array of LABKEY.app.model.OlapExplorer2 nodes
         */
        nodeSorters: {
            sortAlphaNum : function(recA, recB) {
                return LABKEY.app.model.Filter.sorters.alphaNum(recA.record.get('label'), recB.record.get('label'));
            },
            sortAlphaNumRange : function(recA, recB) {
                return LABKEY.app.model.Filter.sorters.alphaNum(recA.record.get('label').split('-')[0], recB.record.get('label').split('-')[0]);
            },
            sortNatural : function(recA, recB) {
                return LABKEY.app.model.Filter.sorters.natural(recA.record.get('label'), recB.record.get('label'));
            }
        }
    },

    requestsComplete : function(response) {

        // unlock for requests for other dimensions
        this.locked = false;
        if (this.eventsSuspended) {
            this.resumeEvents();
        }

        // first check for 'stale'
        if (Ext.isObject(this.stale)) {
            this.load(this.stale.dimension, this.stale.hIndex, this.stale.selections, this.stale.showEmpty);
            return;
        }

        var hierarchy = this.dim.getHierarchies()[this.hIndex],
                baseResult = response.baseResult,
                dims = baseResult.metadata.cube.dimensions,
                selectionResult = response.selectionResult,
                targetLevels = dims.length > 1 ? dims[1].hierarchies[0].levels : hierarchy.levels,
                max = this.totals[hierarchy.getUniqueName()],
                target,
                pos = baseResult.axes[1].positions,
                activeGroup = '',
                isGroup = false,
                groupTarget,
                hasSubjectLevel = targetLevels[targetLevels.length-1].name === this.subjectName,
                hasGrpLevel = targetLevels.length > (hasSubjectLevel ? 3 : 2),
                grpLevelID = targetLevels[1] ? targetLevels[1].id : null,
                subPosition,
                customGroups = {},
        //
        // Support for 'sortStrategy' being declared on the MDX.Level. See this app's cube metadata documentation
        // to see if this app supports the 'sortStrategy' be declared.
        //
                sortStrategy = 'SERVER',
                sortLevelUniqueName,
                sortLevel;

        if (hasGrpLevel) {
            Ext.each(targetLevels, function(level) {
                if (level.id === grpLevelID) {
                    sortLevelUniqueName = level.uniqueName;
                    return false;
                }
            });
        }
        else {
            sortLevelUniqueName = targetLevels[targetLevels.length-1].uniqueName;
        }

        sortLevel = response.mdx.getLevel(sortLevelUniqueName);
        if (sortLevel && !Ext.isEmpty(sortLevel.sortStrategy)) {
            sortStrategy = sortLevel.sortStrategy;
        }
        var sortFn = this._resolveSortFunction(sortStrategy);

        // use (All) as root
        var rootPosition = pos[0][0];
        var nodeName = rootPosition.uniqueName.replace(rootPosition.name, '').replace('.[]', '');
        var rootNode = Ext.create('LABKEY.app.util.OlapExplorerNode', {data: {uniqueName: nodeName, lvlDepth: 0}});
        this.allRecordTree = Ext.create('LABKEY.app.util.OlapExplorerTree', rootNode);

        var groupOnly = true;

        for (var x=1; x < pos.length; x++) {
            subPosition = pos[x][0];

            // Subjects should not be listed so do not roll up
            if ((!this.showEmpty && baseResult.cells[x][0].value === 0)
                    || (subPosition.level.name === this.subjectName)
                    || subPosition.name == '#null') {
                continue;
            }

            isGroup = false;
            if (hasGrpLevel && subPosition.level.id == grpLevelID) {
                activeGroup = subPosition.name;
                isGroup = true;
            }

            if (!this.shouldIncludeMember(hierarchy.getUniqueName(), subPosition.level.uniqueName, subPosition.uniqueName))
                continue;

            target = {
                label: LABKEY.app.model.Filter.getMemberLabel(subPosition.name),
                uniqueName: subPosition.uniqueName,
                count: baseResult.cells[x][0].value,
                maxcount: max,
                value: subPosition.name,
                hierarchy: hierarchy.getUniqueName(),
                isGroup: isGroup,
                level: subPosition.name,
                lvlDepth: (subPosition.uniqueName.match(/\].\[/g) || []).length,
                ordinal: subPosition.ordinal,
                levelUniqueName: subPosition.level.uniqueName,
                collapsed: activeGroup && pos.length > 15 ? true : false,
                btnShown: false,
                hasSelect: response.useSelection === true,
                isSelected: this.isRecordSelected(subPosition.uniqueName)
            };

            if (response.useSelection) {
                target.subcount = this._calculateSubcount(selectionResult, target.uniqueName);
            }

            var instance = Ext.create('LABKEY.app.model.OlapExplorer2', target);

            if (target.isGroup) {
                groupTarget = instance;
                if (!customGroups[target.level]) {
                    customGroups[target.level] = [];
                }
            }
            else {
                instance.set('level', activeGroup);
                if (!customGroups[activeGroup]) {
                    customGroups[activeGroup] = [];
                }
                customGroups[activeGroup].push(instance);
                groupOnly = false;
            }

            this.allRecordTree.add(instance, sortFn);

            var collapse = this.checkCollapse(instance.data);
            instance.set('collapsed', collapse);

            if (groupTarget) {
                groupTarget.set('collapsed', collapse);
            }
        }

        this.allRecordTree.updateLeafNodes();
        var allRecords = this.allRecordTree.getAllRecords();
        var allInstances = [];
        Ext.each(allRecords, function(rec){
            allInstances.push(rec.record);
        }, this);


        if (groupOnly) {
            max = 0;
            this.removeAll();
        }
        else {
            this.loadRecords(allInstances);
        }

        this.customGroups = customGroups;
        this.maxCount = max;

        if (response.useSelection) {
            this.fireEvent('selectrequest');
        }
    },
    _resolveSortFunction : function(strategy) {
        switch (strategy) {
            case 'ALPHANUM':
                return LABKEY.olapStore2.nodeSorters.sortAlphaNum;
            case 'ALPHANUM-RANGE':
                return LABKEY.olapStore2.nodeSorters.sortAlphaNumRange;
            case 'NATURAL':
                return LABKEY.olapStore2.nodeSorters.sortNatural;
            case 'SERVER':
            default:
                return false;
        }
    },
    /**
     * Determine if a level is considered selected based on current active selections.
     * This is intended to be override by subclass to provide a concrete implementation.
     * @param uniqueName
     * @returns {boolean}
     */
    isRecordSelected : function(uniqueName) {
        return false;
    },

    setLoadSelection: function(rec, cellset) {
        rec.set({
            subcount: this._calculateSubcount(cellset, rec.get('uniqueName')),
            hasSelect: true,
            isSelected: this.isRecordSelected(rec.get('uniqueName'))
        });
        return true;
    },

    clearSelection : function() {
        if (this.enableSelection) {
            this.suspendEvents(true);
            this.queryBy(function(rec) {
                rec.set({
                    subcount: 0,
                    hasSelect: false,
                    isSelected: false
                });
                return true;
            }, this);
            this.resumeEvents();
            this.fireEvent('subselect', this);
        }
    }

});

Ext.define('LABKEY.app.view.OlapExplorer2', {
    extend: 'LABKEY.app.view.OlapExplorer',
    alias : 'widget.olapexplorerview2',
    initTemplate : function() {

        var barTpl = this.getBarTemplate();
        var countTpl = this.getCountTemplate();

        //
        // This template is meant to be bound to a set of LABKEY.app.model.OlapExplorer2 instances
        //
        this.tpl = new Ext.XTemplate(
                '<div class="', this.baseChartCls, '">',
                '<div class="', this.baseGroupCls, '">',
                '<tpl for=".">',
                '<tpl if="isGroup === true">',
                '<div class="saeparent">',
                '<div class="saecollapse {#}-collapse" id="{#}-collapse">',
                '<p><tpl if="isLeafNode === true"><tpl else><tpl if="collapsed === true">+<tpl else>-</tpl></tpl></p>',
                '</div>',
                '<div class="', this.barCls, ' large">',
                '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                '</span>',
                '{[ this.renderCount(values) ]}',
                '{[ this.renderBars(values) ]}',
                '</div>',
                '</div>',
                '<tpl else>',
                '<div class="', this.barCls, ' small<tpl if="collapsed === true"> barcollapse</tpl>',
                '<tpl if="level.length &gt; 0"><tpl if="lvlDepth  &gt; 1"> saelevel{lvlDepth} </tpl> saelevel </tpl>">',
                '<span class="', this.barLabelCls, '">{label:htmlEncode}',
                (this.ordinal ? '&nbsp;({ordinal:htmlEncode})' : ''),
                '</span>',
                '{[ this.renderCount(values) ]}',
                '{[ this.renderBars(values) ]}',
                '</div>',
                '</tpl>',
                '</tpl>',
                '</div>',
                '</div>',
                {
                    renderBars : function(values) {
                        return barTpl.apply(values);
                    },
                    renderCount : function(values) {
                        return countTpl.apply(values);
                    }
                }
        );
    },
    getBarTemplate : function() {
        return new Ext.XTemplate(
                '<span class="{[ this.rowSelectedCls(values) ]} index {[ this.doAnimate() ]}" style="width: {[ this.calcWidth(values) ]}%"></span>',
                '<span class="{[ this.rowSelectedCls(values) ]} index-selected inactive {[ this.doAnimate() ]}" style="width: {[ this.calcSubWidth(values) ]}%"></span>',
                {
                    doAnimate : function() {
                        return LABKEY.app.view.OlapExplorer.APPLY_ANIMATE === true ? 'animator' : '';
                    },
                    calcWidth : function(v) {
                        if (v.maxcount == 0) {
                            return 0;
                        }
                        return (v.count / v.maxcount) * 100;
                    },
                    rowSelectedCls : function(v) {
                        if (v.isSelected) {
                            return 'saelevel-selected';
                        }
                        return '';
                    },
                    calcSubWidth : function(v) {
                        if (v.maxcount == 0) {
                            return 0;
                        }
                        var ps = (v.subcount / v.count);
                        var pt = (v.count / v.maxcount);
                        var pts;

                        if (isNaN(ps)) {
                            pts = 0;
                        }
                        else if (ps >= 1) {
                            pts = pt;
                        }
                        else {
                            pts = ps*pt;
                        }
                        return pts * 100;
                    }
                }
        );
    }
});

Ext.define('LABKEY.app.util.OlapExplorerTree', {
    root: null,
    constructor: function(root) {
        if (root) {
            this.root = root;
        }
    },
    add: function(data, sortFn) {
        var child = new LABKEY.app.util.OlapExplorerNode(data),
                parent = this.findNode(this.root, child, function(node, newChild){
                    return node.isDirectParentOf(newChild);
                });

        if (parent) {
            parent.childrenNodes.push(child);
            if (sortFn) {
                parent.childrenNodes = parent.childrenNodes.sort(sortFn);
            }
            child.parent = parent;
        } else {
            console.log('Parent node not found.');
        }
    },
    findNode: function(currentNode, uniqueName, matchFn) {
        if (matchFn.call(this, currentNode, uniqueName))
            return currentNode;
        for (var i = 0, length = currentNode.childrenNodes.length; i < length; i++) {
            var found = this.findNode(currentNode.childrenNodes[i], uniqueName, matchFn);
            if (found) {
                return found;
            }
        }
        return null;
    },
    getAllRecords: function() {
        return this.preOrderTraversal(this.root, [], true);
    },
    /**
     * Detemine if a node is a leaf node with no children
     */
    updateLeafNodes: function() {
        this.updateLeafNodesInfo(this.root);
    },
    updateLeafNodesInfo: function(parentNode) {
        for (var i = 0, length = parentNode.childrenNodes.length; i < length; i++) {
            var curNode = parentNode.childrenNodes[i];
            if (curNode.childrenNodes.length == 0) {
                if (curNode.record.data) {
                    curNode.record.data.isLeafNode = true;
                }
            }
            else {
                this.updateLeafNodesInfo(curNode);
            }
        }
    },
    preOrderTraversal: function(currentNode, results, isRoot) {
        if (!isRoot) {
            results.push(currentNode);
        }
        for (var i = 0, length = currentNode.childrenNodes.length; i < length; i++) {
            this.preOrderTraversal(currentNode.childrenNodes[i], results, false);
        }
        return results;
    },
    /**
     * Get all descendant groups for a ancestor, excluding the line that's directly related to the disownedDescendant
     * @param {string} ancestor The uniqueName of the ancestor node
     * @param {string} disownedDescendant The uniqueName of the descendant node whose line will be pruned
     * @returns {string[]} an array of uniqueNames/members after the pruning
     */
    dissolve: function(ancestor, disownedDescendant) {
        var ancestorNode = this.findNode(this.root, ancestor, function(node, uniqueName){
            return node.record.data.uniqueName === uniqueName;
        });
        if (ancestorNode) {
            return this.getDescendantGroups(ancestorNode, disownedDescendant, [], true)
        } else {
            console.log('Ancestor node not found.');
        }
    },
    getDescendantGroups: function(currentNode, disownedDescendant, results, isRoot) {
        if (disownedDescendant === currentNode.record.data.uniqueName) {
            return results;
        }
        if (disownedDescendant.indexOf(currentNode.record.data.uniqueName) == 0) {
            for (var i = 0, length = currentNode.childrenNodes.length; i < length; i++) {
                this.getDescendantGroups(currentNode.childrenNodes[i], disownedDescendant, results, false);
            }
        }
        else {
            if (!isRoot) {
                results.push({uniqueName: currentNode.record.data.uniqueName});
            }
        }
        return results;
    }
});

Ext.define('LABKEY.app.util.OlapExplorerNode', {
    record: null,
    parent: null,
    childrenNodes: [],

    constructor: function(data) {
        if (data) {
            this.record = data;
            this.parent = null;
            this.childrenNodes = [];
        }
    },
    isDirectParentOf: function(node) {
        return node.record.data.lvlDepth - this.record.data.lvlDepth == 1 && node.record.data.uniqueName.indexOf(this.record.data.uniqueName) == 0;
    }
});


