/*
 * Copyright (c) 2012-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

if(!LABKEY.vis.Scale){
    /**
     * @namespace Namespace used for scales in {@link LABKEY.vis.Plot} objects.
     */
    LABKEY.vis.Scale = {};
}

/**
 * The default color scale used in plots.
 */
LABKEY.vis.Scale.ColorDiscrete = function(){
    // Used for discrete color scales (color assigned to categorical data)
    return [ "#66C2A5", "#FC8D62", "#8DA0CB", "#E78AC3", "#A6D854", "#FFD92F", "#E5C494", "#B3B3B3"];
};

/**
 * An alternate darker color scale.
 */
LABKEY.vis.Scale.DarkColorDiscrete = function(){
    // Used for discrete color scales (color assigned to categorical data)
    return ["#378a70", "#f34704", "#4b67a6", "#d53597", "#72a124", "#c8a300", "#d19641", "#808080"];
};

/**
 * Function that returns a discrete scale used to determine the shape of points in {@link LABKEY.vis.Geom.BoxPlot} and
 * {@link LABKEY.vis.Geom.Point} geoms.
 */
LABKEY.vis.Scale.Shape = function(){
    var circle = function(s){
        return "M0," + s + "A" + s + "," + s + " 0 1,1 0," + -s + "A" + s + "," + s + " 0 1,1 0," + s + "Z";
    };
    var square = function(s){
        return "M" + -s + "," + -s + "L" + s + "," + -s + " " + s + "," + s + " " + -s + "," + s + "Z";
    };
    var diamond = function(s){
        var r = (Math.sqrt(1.5 * Math.pow(s * 2, 2))) / 2;
        return 'M0 ' + r + ' L ' + r + ' 0 L 0 ' + -r + ' L ' + -r + ' 0 Z';
    };
    var triangle = function(s){
        return 'M0,' + s + 'L' + s + ',' + -s + 'L' + -s + ',' + -s + ' Z';
    };
    var x = function(s){
        var r = s / 2;
        return 'M0,' + r + 'L' + r + ',' + (2*r) + 'L' + (2*r) + ',' + r +
                'L' + r + ',0L' + (2*r) + ',' + -r +
                'L' + r + ',' + (-2*r) + 'L0,' + -r +
                'L' + -r + ',' + (-2*r) + 'L' + (-2*r) + ',' + -r +
                'L' + -r + ',0L' + (-2*r) + ',' + r +
                'L' + -r + ',' + (2*r) + 'L0,' + r + 'Z';
    };

    return [circle, triangle, square, diamond, x];
};

LABKEY.vis.Scale.DataspaceShape = function(){
    var shape01 = function(s){
        return 'M0-2.6c-1.5,0-2.6,1.1-2.6,2.6S-1.4,2.6,0,2.6 c1.5,0,2.6-1.2,2.6-2.6C2.6-1.5,1.5-2.6,0-2.6z M0,1.9c-1.1,0-1.9-0.8-1.9-1.9S-1-1.9,0-1.9C1.1-1.9,1.9-1,1.9,0 C1.9,1.1,1.1,1.9,0,1.9z';
    };
    var shape02 = function(s){
        return 'M3.1-0.6H0.6v-2.5h-1.2v2.5h-2.5v1.2h2.5v2.5h1.2V0.6h2.5C3.1,0.6,3.1-0.6,3.1-0.6z';
    };
    var shape03 = function(s){
        return 'M2.8-2.2L2.2-2.8L0-0.6l-2.2-2.2l-0.6,0.6L-0.6,0l-2.2,2.2l0.6,0.6L0,0.6l2.2,2.2l0.6-0.6L0.6,0L2.8-2.2z';
    };
    var shape04 = function(s){
        return 'M1.2-2.6h-2.6h-1.2v1.3v2.5v1.3h1.3h2.5h1.3V1.2v-2.6v-1.2H1.2z M1.2,1.2h-2.6v-2.6h2.5v2.6H1.2z';
    };
    var shape05 = function(s){
        return 'M3-1.1L2.6-1.9L0.5-0.8v-1.8h-1v1.8l-2.1-1.1L-3-1.1L-0.9,0L-3,1.1l0.4,0.7l2.1-1.1v1.9h1V0.7l2.1,1.1L3,1.1 L0.9,0L3-1.1z';
    };
    var shape06 = function(s){
        return 'M1.9-3.1V0C1.9,1,1,1.9,0,1.9C-1,1.9-1.9,1-1.9,0v-3.1h-1.3V0c0,1.7,1.4,3.1,3.1,3.1c1.7,0,3.1-1.4,3.1-3.1 v-3.1H1.9z';
    };
    var shape07 = function(s){
        return 'M0-3.5l-3.7,7h1h6.3L0-3.5z M2,2.3l-4,0l2-3.8L2,2.3z';
    };
    var shape08 = function(s){
        return 'M0-3.6l-0.3,0.3L-3.6,0L0,3.6L3.6,0L0-3.6z M-2.4,0L0-2.4L2.4,0L0,2.4L-2.4,0z';
    };
    var shape09 = function(s){
        return 'M1.9,-1.9 L1.9,-3.1 L0.6,-3.1 L-0.6,-3.1 L-1.9,-3.1 L-1.9,-1.9 L-0.6,-1.9 L-0.6,1.9 L-1.9,1.9 L-1.9,3.1 L-0.6,3.1 L0.6,3.1 L1.9,3.1 L1.9,1.9 L0.6,1.9 L0.6,-1.9z';
    };
    var shape10 = function(s){
        return 'M2.5,0c0-0.4-0.1-0.8-0.3-1.2l1.2-1.2L2.4-3.4L1.2-2.2C0.9-2.4,0.5-2.5,0-2.5c-0.5,0-0.9,0.1-1.2,0.3 l-1.2-1.2l-0.9,0.9l1.2,1.2C-2.4-0.9-2.5-0.5-2.5,0c0,0.5,0.1,0.9,0.3,1.2l-1.2,1.2l0.9,0.9l1.2-1.2C-0.8,2.3-0.4,2.5,0,2.5 c0.4,0,0.8-0.1,1.2-0.3l1.2,1.2l0.9-0.9L2.2,1.2C2.4,0.8,2.5,0.4,2.5,0z M-1.7,0C-1.7-1-1-1.7,0-1.7C1-1.7,1.7-1,1.7,0 c0,1-0.8,1.7-1.7,1.7C-1,1.7-1.7,0.9-1.7,0z';
    };
    var shape11 = function(s){
        return 'M2.9-0.8L0-3.6l-3.1,2.9v4.2h6.3v-4.2L2.9-0.8z M-1.9,2.3v-2.4L0-1.9l1.9,1.9v2.4H-1.9z';
    };
    var shape12 = function(s){
        return 'M2.7-2.7C2.4-3.1,1.8-3.4,1.3-3.4C1-3.4,0.7-3.2,0.5-3C0.3-2.8,0.1-2.6,0-2.4C-0.1-2.6-0.3-2.8-0.5-3 C-0.7-3.2-1-3.4-1.3-3.4c-0.5,0-1,0.2-1.3,0.7C-3-2.2-3.2-1.6-3.2-0.8c0,0.5,0.2,0.9,0.5,1.3c0.8,1.3,2.3,2.7,2.3,2.7 c0.2,0.2,0.5,0.2,0.7,0c0,0,0.7-0.6,1.4-1.4c0.3-0.4,0.7-0.8,1-1.3c0.3-0.4,0.5-0.9,0.5-1.3C3.2-1.6,3-2.2,2.7-2.7z M1.8,0 C1.5,0.5,0.9,1.2,0.5,1.7C0.3,1.9,0.1,2,0,2.2c0,0,0,0-0.1-0.1c-0.4-0.4-0.9-1-1.4-1.6C-1.6,0.3-1.8,0-2-0.3 c-0.1-0.3-0.2-0.5-0.2-0.6c0-0.7,0.1-1.1,0.3-1.3c0.2-0.2,0.3-0.3,0.5-0.3l0,0c0.1,0,0.4,0.3,0.5,0.6c0.1,0.1,0.2,0.3,0.2,0.4 l0.1,0.1l0,0C-0.4-1-0.2-0.9,0-0.9S0.4-1,0.5-1.2l0-0.1C0.5-1.4,0.7-1.7,0.9-2C1-2.1,1.1-2.2,1.2-2.3l0.1-0.1l0,0 c0.2,0,0.4,0.1,0.5,0.3C2-1.9,2.2-1.5,2.2-0.8C2.2-0.7,2-0.4,1.8,0z';
    };
    var shape13 = function(s){
        return 'M3.1,-3.1 L0.6,-3.1 L-0.6,-3.1 L-3.1,-3.1 L-3.1,-1.9 L-0.6,-1.9 L-0.6,3.1 L0.6,3.1 L0.6,-1.9 L3.1,-1.9z';
    };
    var shape14 = function(s){
        return 'M1.9,0.5c-0.3,0-0.6,0-0.9,0.1C1.2,0.4,1.3,0.2,1.3,0c0-0.1,0-0.3-0.1-0.4c0.1,0,0.1,0,0.2,0 c0.5,0,1-0.7,1-1.6s-0.4-1.6-1-1.6s-1,0.7-1,1.6c0,0.3,0.1,0.7,0.2,0.9C0.4-1.2,0.2-1.2,0.1-1.2c-0.1,0-0.2,0-0.3,0.1 c0-0.1,0-0.1,0-0.2c0-0.5-0.8-1-1.8-1s-1.8,0.4-1.8,1s0.8,1,1.8,1c0.4,0,0.7-0.1,1-0.2C-1.1-0.4-1.1-0.2-1.1,0c0,0,0,0.1,0,0.1 C-1.2,0-1.3,0-1.4,0c-0.5,0-1,0.8-1,1.8c0,1,0.4,1.8,1,1.8s1-0.8,1-1.8c0-0.3,0-0.6-0.1-0.9c0.2,0.1,0.4,0.2,0.6,0.2 c0,0,0.1,0,0.1,0C0.1,1.2,0.1,1.3,0.1,1.4c0,0.5,0.8,1,1.9,1s1.9-0.4,1.9-1S3,0.5,1.9,0.5z';
    };
    var shape15 = function(s){
        return 'M0,3.5l3.7-7h-1h-6.3L0,3.5z M-2-2.3l4,0L0,1.5L-2-2.3z';
    };
    var shape16 = function(s){
        return 'M-0.6-1.9v-1.3h-1.3v3.8h-1.3v1.3h3.8v1.3h1.3v-3.8h1.3v-1.3H-0.6z M0.6,0.6h-1.3v-1.3h1.3V0.6z';
    };
    var shape17 = function(s){
        return 'M-2.9,0.8L0,3.6l3.1-2.9v-4.2h-6.3v4.2L-2.9,0.8z M1.9-2.3v2.4L0,1.9l-1.9-1.9v-2.4H1.9z';
    };
    var shape18 = function(s){
        return 'M-1.4,-3.7 L-2.3,-3.2 L-0.9,-0.6 L-3.1,-0.6 L-3.1,0.6 L-0.9,0.6 L-2.3,3.2 L-1.4,3.7 L0,1.1 L1.3,3.7 L2.2,3.2 L0.9,0.6 L3.1,0.6 L3.1,-0.6 L0.9,-0.6 L2.2,-3.2 L1.3,-3.7 L0,-1.1z';
    };
    var shape19 = function(s){
        return 'M1.7,0c0.7-0.4,1.4-1,1.8-1.8L2.8-2.2C2.1-1.1,1.1-0.4,0-0.4c-1.1,0-2.1-0.7-2.8-1.8l-0.8,0.4 C-3.1-1-2.5-0.4-1.7,0c-0.7,0.4-1.4,1-1.8,1.8l0.8,0.4C-2.1,1.1-1.1,0.4,0,0.4c1.1,0,2.1,0.7,2.8,1.8l0.8-0.4C3.1,1,2.5,0.4,1.7,0z';
    };
    var shape20 = function(s){
        return 'M3,0.6 L3,0.6 L0,-3.4 L-3,0.6 L-2,1.3 L-0.6,-0.5 L-0.6,3.4 L0.6,3.4 L0.6,-0.5 L2,1.3z';
    };

    return [
        shape01,
        shape02,
        shape03,
        shape04,
        shape05,
        shape06,
        shape07,
        shape08,
        shape09,
        shape10,
        shape11,
        shape12,
        shape13,
        shape14,
        shape15,
        shape16,
        shape17,
        shape18,
        shape19,
        shape20
    ];
};

LABKEY.vis.Scale.DataspaceColor = function(){
    return [
        '#010101',
        '#52B700',
        '#B30FAC',
        '#FF9400',
        '#0069F9',
        '#FF1200',
        '#0D6201',
        '#964904',
        '#FF33E5',
        '#964904',
        '#000000',
        '#52B700',
        '#FF9400',
        '#FF1200',
        '#B30FAC',
        '#0D6201',
        '#FF9400',
        '#FF9400',
        '#000000',
        '#FF33E5'
    ];
};