/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

LABKEY.vis.groupData = function(data, groupAccessor){
    /*
        Groups data by the groupAccessor passed in.
        Ex: A set of rows with participantIds in them, would return an object that has one attribute
         per participant id. Each attribute will be an array of all of the rows the participant is in.
     */
    var groupedData = {};
    for(var i = 0; i < data.length; i++){
        var value = groupAccessor(data[i]);
        if(!groupedData[value]){
            groupedData[value] = [];
        }
        groupedData[value].push(data[i]);
    }
    return groupedData;
};

LABKEY.vis.groupCountData = function(data, groupAccessor){
    /*
        Groups data by the groupAccessor passed in and returns the number of occurances for that group.
        Most commonly used for processing data for a bar plot.
     */
    var groupName, groupedData, count, counts = [], total = 0;

    groupedData = LABKEY.vis.groupData(data, groupAccessor);

    for (groupName in groupedData) {
        if (groupedData.hasOwnProperty(groupName)) {
            count = groupedData[groupName].length;
            total += count;
            counts.push({
                name: groupName,
                count: count,
                total: total,
                rawData: groupedData[groupName]
            });
        }
    }

    return counts;
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
    return !(value == undefined || value == null || (typeof value == "number" && isNaN(value)));
};
