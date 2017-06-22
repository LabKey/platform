/*
 * Copyright (c) 2012-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// Contains helpers that aren't specific to plot, layer, geom, etc. and are used throughout the API.

if(!LABKEY){
	var LABKEY = {};
}

if(!LABKEY.vis){
    /**
     * @namespace The namespace for the internal LabKey visualization library. Contains classes within
     * {@link LABKEY.vis.Plot}, {@link LABKEY.vis.Layer}, and {@link LABKEY.vis.Geom}.
     */
	LABKEY.vis = {};
}

LABKEY.vis.makeLine = function(x1, y1, x2, y2){
    //Generates a path between two coordinates.
    return "M " + x1 + " " + y1 + " L " + x2 + " " + y2;
};

LABKEY.vis.makePath = function(data, xAccessor, yAccessor){
    var pathString = '';

    for(var i = 0; i < data.length; i++){
        var x = xAccessor(data[i]);
        var y = yAccessor(data[i]);
        if(!LABKEY.vis.isValid(x) || !LABKEY.vis.isValid(y)){
            continue;
        }
        
        if(pathString == ''){
            pathString = pathString + 'M' + x + ' ' + y;
        } else {
            pathString = pathString + ' L' + x + ' ' + y;
        }
    }
    return pathString;
};

LABKEY.vis.createGetter = function(aes){
    if(typeof aes.value === 'function'){
        aes.getValue = aes.value;
    } else {
        aes.getValue = function(row){
            if(row instanceof Array) {
                /*
                 * For Path geoms we pass in the entire array of values for the path to the aesthetic. So if the user
                 * provides only a string for an Aes value we'll assume they want the first object in the path array to
                 * determing the value.
                */
                if(row.length > 0) {
                    row = row[0];
                } else {
                    return null;
                }
            }
            return row[aes.value];
        };
    }
};

LABKEY.vis.convertAes = function(aes){
    var newAes= {};
    for(var aesthetic in aes){
        var newAesName = (aesthetic == 'y') ? 'yLeft' : aesthetic;
        newAes[newAesName] = {};
        newAes[newAesName].value = aes[aesthetic];
    }
    return newAes;
};

LABKEY.vis.mergeAes = function(oldAes, newAes) {
    newAes = LABKEY.vis.convertAes(newAes);
    for(var attr in newAes) {
        if(newAes.hasOwnProperty(attr)) {
            if (newAes[attr].value != null) {
                LABKEY.vis.createGetter(newAes[attr]);
                oldAes[attr] = newAes[attr];
            } else {
                delete oldAes[attr];
            }
        }
    }
};

/**
 * Groups data by the groupAccessor, and subgroupAccessor if provided, passed in.
 *    Ex: A set of rows with participantIds in them, would return an object that has one attribute
 *    per participant id. Each attribute will be an array of all of the rows the participant is in.
 * @param data Array of data (likely result of selectRows API call)
 * @param groupAccessor Function defining how to access group data from array rows
 * @param subgroupAccessor Function defining how to access subgroup data from array rows
 * @returns {Object} Map of groups, and subgroups, to arrays of data for each
 */
LABKEY.vis.groupData = function(data, groupAccessor, subgroupAccessor)
{
    var groupedData = {},
        hasSubgroupAcc = subgroupAccessor != undefined && subgroupAccessor != null;

    for (var i = 0; i < data.length; i++)
    {
        var value = groupAccessor(data[i]);
        if (!groupedData[value])
            groupedData[value] = hasSubgroupAcc ? {} : [];

        if (hasSubgroupAcc)
        {
            var subvalue = subgroupAccessor(data[i]);
            if (!groupedData[value][subvalue])
                groupedData[value][subvalue] = [];

            groupedData[value][subvalue].push(data[i]);
        }
        else
        {
            groupedData[value].push(data[i]);
        }
    }
    return groupedData;
};

/**
 * Groups data by the groupAccessor, and subgroupAccessor if provided, passed in and returns the number
 * of occurrences for that group/subgroup. Most commonly used for processing data for a bar plot.
 * @param data
 * @param groupAccessor
 * @param subgroupAccessor
 * @param propNameMap
 * @returns {Array}
 */
LABKEY.vis.groupCountData = function(data, groupAccessor, subgroupAccessor, propNameMap)
{
    var counts = [], total = 0,
        nameProp = propNameMap && propNameMap.name ? propNameMap.name : 'name',
        subnameProp = propNameMap && propNameMap.subname ? propNameMap.subname : 'subname',
        countProp = propNameMap && propNameMap.count ? propNameMap.count : 'count',
        totalProp = propNameMap && propNameMap.total ? propNameMap.total : 'total',
        hasSubgroupAcc = subgroupAccessor != undefined && subgroupAccessor != null,
        groupedData = LABKEY.vis.groupData(data, groupAccessor, subgroupAccessor);

    for (var groupName in groupedData)
    {
        if (groupedData.hasOwnProperty(groupName))
        {
            if (hasSubgroupAcc)
            {
                for (var subgroupName in groupedData[groupName])
                {
                    if (groupedData[groupName].hasOwnProperty(subgroupName))
                    {
                        var row = {rawData: groupedData[groupName][subgroupName]},
                            count = row['rawData'].length;
                        total += count;

                        row[nameProp] = groupName;
                        row[subnameProp] = subgroupName;
                        row[countProp] = count;
                        row[totalProp] = total;
                        counts.push(row);
                    }
                }
            }
            else
            {
                var row = {rawData: groupedData[groupName]},
                    count = row['rawData'].length;
                total += count;

                row[nameProp] = groupName;
                row[countProp] = count;
                row[totalProp] = total;
                counts.push(row);
            }
        }
    }

    return counts;
};

/**
 * Generate an array of aggregate values for the given groups/subgroups in the data array.
 * @param {Array} data The response data from selectRows.
 * @param {String} dimensionName The grouping variable to get distinct members from.
 * @param {String} subDimensionName The subgrouping variable to get distinct members from
 * @param {String} measureName The variable to calculate aggregate values over. Nullable.
 * @param {String} aggregate MIN/MAX/SUM/COUNT/etc. Defaults to COUNT.
 * @param {String} nullDisplayValue The display value to use for null dimension values. Defaults to 'null'.
 * @param {Boolean} includeTotal Whether or not to include the cumulative totals. Defaults to false.
 * @returns {Array} An array of results for each group/subgroup/aggregate
 */
LABKEY.vis.getAggregateData = function(data, dimensionName, subDimensionName, measureName, aggregate, nullDisplayValue, includeTotal)
{
    var results = [], subgroupAccessor,
        groupAccessor = typeof dimensionName === 'function' ? dimensionName : function(row){ return LABKEY.vis.getValue(row[dimensionName]);},
        hasSubgroup = subDimensionName != undefined && subDimensionName != null,
        hasMeasure = measureName != undefined && measureName != null,
        measureAccessor = hasMeasure ? function(row){ return LABKEY.vis.getValue(row[measureName]); } : null;

    if (hasSubgroup) {
        if (typeof subDimensionName === 'function') {
            subgroupAccessor = subDimensionName;
        } else {
            subgroupAccessor = function (row) { return LABKEY.vis.getValue(row[subDimensionName]); }
        }
    }

    var groupData = LABKEY.vis.groupCountData(data, groupAccessor, subgroupAccessor);

    for (var i = 0; i < groupData.length; i++)
    {
        var row = {label: groupData[i]['name']};
        if (row['label'] == null || row['label'] == 'null')
            row['label'] = nullDisplayValue || 'null';

        if (hasSubgroup)
        {
            row['subLabel'] = groupData[i]['subname'];
            if (row['subLabel'] == null || row['subLabel'] == 'null')
                row['subLabel'] = nullDisplayValue || 'null';
        }
        if (includeTotal) {
            row['total'] = groupData[i]['total'];
        }

        var values = measureAccessor != undefined && measureAccessor != null
                ? LABKEY.vis.Stat.sortNumericAscending(groupData[i].rawData, measureAccessor)
                : null;

        if (aggregate == undefined || aggregate == null || aggregate == 'COUNT')
        {
            row['value'] = values != null ? values.length : groupData[i]['count'];
        }
        else if (typeof LABKEY.vis.Stat[aggregate] == 'function')
        {
            try {
                row.value = LABKEY.vis.Stat[aggregate](values);
            } catch (e) {
                row.value = null;
            }
        }
        else
        {
            throw 'Aggregate ' + aggregate + ' is not yet supported.';
        }

        results.push(row);
    }

    return results;
};

LABKEY.vis.getColumnAlias = function(aliasArray, measureInfo) {
    /*
     Lookup the column alias (from the getData response) by the specified measure information
     aliasArray: columnAlias array from the getData API response
     measureInfo: 1. a string with the name of the column to lookup
                  2. an object with a measure alias OR measureName
                 3. an object with both measureName AND pivotValue
    */
    if (!aliasArray)
        aliasArray = [];

    if (typeof measureInfo != "object")
        measureInfo = {measureName: measureInfo};
    for (var i = 0; i < aliasArray.length; i++)
    {
        var arrVal = aliasArray[i];

        if (measureInfo.measureName && measureInfo.pivotValue)
        {
            if (arrVal.measureName == measureInfo.measureName && arrVal.pivotValue == measureInfo.pivotValue)
                return arrVal.columnName;
        }
        else if (measureInfo.alias)
        {
            if (arrVal.alias == measureInfo.alias)
                return arrVal.columnName;
        }
        else if (measureInfo.measureName && arrVal.measureName == measureInfo.measureName)
            return arrVal.columnName;
    }
    return null;
};

LABKEY.vis.isValid = function(value) {
    return !(value == undefined || value == null || (typeof value == "number" && !isFinite(value)));
};

LABKEY.vis.arrayObjectIndexOf = function(myArray, searchTerm, property) {
    for (var i = 0; i < myArray.length; i++) {
        if (myArray[i][property] === searchTerm) return i;
    }
    return -1;
};

LABKEY.vis.discreteSortFn = function(a,b) {
    // Issue 23015: sort categorical x-axis alphabetically with special case for "Not in X" and "[Blank]"
    var aIsEmptyCategory = a && (a.indexOf("Not in ") == 0 || a == '[Blank]'),
        bIsEmptyCategory = b && (b.indexOf("Not in ") == 0 || b == '[Blank]');

    if (aIsEmptyCategory)
        return 1;
    else if (bIsEmptyCategory)
        return -1;
    else if (a != b)
        return LABKEY.vis.naturalSortFn(a,b);

    return 0;
};

LABKEY.vis.naturalSortFn = function(aso, bso) {
    // http://stackoverflow.com/questions/19247495/alphanumeric-sorting-an-array-in-javascript
    var a, b, a1, b1, i= 0, n, L,
        rx=/(\.\d+)|(\d+(\.\d+)?)|([^\d.]+)|(\.\D+)|(\.$)/g;
    if (aso === bso) return 0;
    a = aso.toLowerCase().match(rx);
    b = bso.toLowerCase().match(rx);

    L = a.length;
    while (i < L) {
        if (!b[i]) return 1;
        a1 = a[i]; b1 = b[i++];
        if (a1 !== b1) {
            n = a1 - b1;
            if (!isNaN(n)) return n;
            return a1 > b1 ? 1 : -1;
        }
    }
    return b[i] ? -1 : 0;
};

LABKEY.vis.getValue = function(obj) {
    if (typeof obj == 'object')
        return obj.hasOwnProperty('displayValue') ? obj.displayValue : obj.value;

    return obj;
};
