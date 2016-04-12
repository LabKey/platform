/**
 * A new implementation of OlapExplorer that support multiple level using tree as underlying data structure
 */
Ext.define('LABKEY.app.store.OlapExplorer2', {
    extend: 'LABKEY.app.store.OlapExplorer',
    alternateClassName: 'LABKEY.olapStore2',
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
        var allRecordTree = Ext.create('LABKEY.app.util.OlapExplorerTree', rootNode);

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
                hasSelect: response.useSelection === true
            };

            if (response.useSelection) {
                target.subcount = this._calculateSubcount(selectionResult, target.uniqueName);
            }

            var instance = Ext.create('LABKEY.app.model.OlapExplorer', target);

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

            allRecordTree.add(instance, sortFn);

            var collapse = this.checkCollapse(instance.data);
            instance.set('collapsed', collapse);

            if (groupTarget) {
                groupTarget.set('collapsed', collapse);
            }
        }

        var allRecords = allRecordTree.getAllRecords();
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
                parent = this.findParent(this.root, child);

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
    findParent : function(currentNode, newChild) {
        if (currentNode.isDirectParentOf(newChild)) {
            return currentNode;
        }
        for (var i = 0, length = currentNode.childrenNodes.length; i < length; i++) {
            var found = this.findParent(currentNode.childrenNodes[i], newChild);
            if (found) {
                return found;
            }
        }
        return null;
    },
    getAllRecords: function() {
        return this.preOrderTraversal(this.root, [], true);
    },
    preOrderTraversal: function(currentNode, results, isRoot) {
        if (!isRoot) {
            results.push(currentNode);
        }
        for (var i = 0, length = currentNode.childrenNodes.length; i < length; i++) {
            this.preOrderTraversal(currentNode.childrenNodes[i], results, false);
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
        return node.record.data.lvlDepth - this.record.data.lvlDepth == 1 && node.record.data.uniqueName.indexOf(this.record.data.uniqueName) > -1;
    }
});


