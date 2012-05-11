/*
 * Copyright (c) 2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// Contains helpers that aren't specific to plot, layer, geom, etc. and are used throughout the API.

LABKEY.vis.makeLine = function(x1, y1, x2, y2){
    //Generates a path between two coordinates.
    return "M " + x1 + " " + y1 + " L " + x2 + " " + y2;
};


LABKEY.vis.createGetter = function(aes){
    if(typeof aes.value === 'function'){
        aes.getValue = function(row){
            return aes.value(row);
        };
    } else if(!isNaN(parseFloat(aes.value)) && isFinite(aes.value)){
        // This is for size aesthetics which can specify just a number.
        aes.getValue = function(){
            return aes.value;
        }
    } else {
        aes.getValue = function(row){
            return row[aes.value];
        };
    }
};

LABKEY.vis.convertAes = function(aes){
    var newAes= {};
    for(var aesthetic in aes){
        newAes[aesthetic] = {};
        newAes[aesthetic].value = aes[aesthetic];
    }

    return newAes;
};