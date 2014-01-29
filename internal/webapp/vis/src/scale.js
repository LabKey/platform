/*
 * Copyright (c) 2012-2014 LabKey Corporation
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
 * An alternate darker color scale. Not currently used.
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
        return 'M0-6.8c-3.7,0-6.8,3.1-6.8,6.8S-3.7,6.8,0,6.8S6.8,3.7,6.8,0S3.7-6.8,0-6.8z M0,4.9c-2.7,0-4.9-2.3-4.9-4.9S-2.7-4.9,0-4.9S4.9-2.8,4.9,0C4.9,2.7,2.7,4.9,0,4.9z';
    };
    var shape02 = function(s){
        return 'M2.3-2.2v-6.5h-4.5v6.5h-6.5v4.4h6.5v6.5h4.4V2.2h6.5v-4.4H2.3z';
    };
    var shape03 = function(s){
        return 'M-6.9,3.9l3,3L0,3l3.9,3.9l3-3L3,0l3.9-3.9l-3-3L0-3l-3.9-3.9l-3,3L-3,0L-6.9,3.9z';
    };
    var shape04 = function(s){
        return 'M-6.7-6.7V6.7H6.7V-6.7H-6.7z M3.3,3.3h-6.7v-6.7h6.7V3.3z';
    };
    var shape05 = function(s){
        return 'M6.5-2.4l-1-1.5L1-1.5v-3.8h-2.1v3.8L-5.6-4l-0.9,1.6L-2,0l-4.5,2.2l0.9,1.5l4.5-2.2v3.8H1V1.5l4.5,2.2l1-1.5L1.8,0L6.5-2.4z';
    };
    var shape06 = function(s){
        return 'M2.9-4.8V0c0,1.5-1.4,2.9-2.9,2.9S-2.9,1.5-2.9,0v-4.8h-1.9V0c0,2.6,2.1,4.8,4.8,4.8S4.8,2.7,4.8,0v-4.8H2.9z';
    };
    var shape07 = function(s){
        return 'M0-5.5l-5.9,11h1.7H5.9L0-5.5z M3.1,3.8h-6.2L0-2.1L3.1,3.8z';
    };
    var shape08 = function(s){
        return 'M0-8.1l-0.7,0.7L-8.1,0L0,8.1L8.1,0L0-8.1z M-3.2,0L0-3.2L3.2,0L0,3.2L-3.2,0z';
    };
    var shape09 = function(s){
        return 'M4-4v-2.5h-8V-4h2.7v8H-4v2.5h8V4H1.3v-8H4z';
    };
    var shape10 = function(s){
        return 'M4.6,0c0-0.8-0.2-1.6-0.6-2.2l2.2-2.3L4.5-6.2L2.3-4C1.6-4.4,0.8-4.7,0-4.7S-1.6-4.4-2.3-4l-2.2-2.2l-1.7,1.7L-4-2.3C-4.4-1.6-4.6-0.9-4.6,0c0,0.8,0.2,1.6,0.6,2.3l-2.2,2.2l1.7,1.7L-2.3,4c0.7,0.4,1.4,0.6,2.2,0.6S1.5,4.4,2.1,4l2.2,2.2l1.8-1.8L3.9,2.2C4.4,1.6,4.6,0.8,4.6,0z M-3.2,0c0-1.8,1.4-3.2,3.2-3.2S3.2-1.8,3.2,0S1.8,3.2,0,3.2S-3.2,1.7-3.2,0z';
    };
    var shape11 = function(s){
        return 'M-0.1-6.7L-6-1.3v8H6v-8L-0.1-6.7z M-3.7,4.4v-4.5l3.6-3.5l3.6,3.5v4.5H-3.7z';
    };
    var shape12 = function(s){
        return 'M5.2-5.2C4.6-6.2,3.5-6.6,2.6-6.6C1.9-6.6,1.4-6.2,1-5.8C0.6-5.6,0.3-5.1,0-4.8c-0.3-0.4-0.6-0.8-1-1.1c-0.4-0.4-0.9-0.7-1.6-0.7c-1,0-2,0.4-2.6,1.3s-0.9,2-0.9,3.6c0,0.9,0.4,1.8,1,2.6c1.6,2.5,4.5,5.3,4.5,5.3c0.4,0.4,1,0.4,1.3,0c0,0,1.3-1.2,2.7-2.8C4.1,2.6,4.7,1.8,5.2,1C5.8,0,6.1-0.7,6.1-1.6C6.1-3.2,5.8-4.3,5.2-5.2z M3.5,0C2.8,1.1,1.8,2.3,0.9,3.3C0.6,3.6,0.2,4,0,4.2l-0.1-0.1c-0.7-0.7-1.8-1.9-2.6-3c-0.5-0.7-0.9-1.3-1.1-1.8c-0.3-0.5-0.4-0.9-0.4-1c0-1.3,0.3-2,0.6-2.4c0.3-0.4,0.6-0.5,1-0.5h0.1c0.2,0,0.7,0.6,1,1.2C-1.3-3-1.2-2.9-1.1-2.7L-1-2.5v0.1C-0.8-2-0.5-1.8-0.1-1.8S0.6-2,0.8-2.4v-0.1c0.1-0.2,0.4-0.9,0.8-1.4C1.8-4.2,2-4.4,2.2-4.5l0.3-0.2l0,0c0.5,0,0.7,0.1,1,0.5c0.3,0.4,0.6,1.2,0.6,2.4C4.2-1.5,4-0.9,3.5,0z';
    };
    var shape13 = function(s){
        return 'M6.1-6H-6.1v2.4h4.8V6h2.4v-9.6h4.8V-6H6.1z';
    };
    var shape14 = function(s){
        return 'M6.2,1.7c0,1-1.7,1.9-3.8,1.9c-1,0-1.9-0.1-2.6-0.4C-0.3,4.8-1,6-1.9,6c-1,0-1.9-1.7-1.9-3.8c0-0.9,0.1-1.7,0.4-2.2c-1.7-0.3-2.8-1-2.8-1.9c0-1,1.7-1.9,3.8-1.9c0.9,0,1.7,0.1,2.3,0.5C0-4.9,0.8-6,1.8-6s1.9,1.4,1.9,3.2c0,1-0.3,1.9-0.8,2.6C4.7-0.1,6.2,0.8,6.2,1.7z';
    };
    var shape15 = function(s){
        return 'M0,5.5l5.9-11H4.2H-5.9L0,5.5z M-3.1-3.8h6.2L0,2.1L-3.1-3.8z';
    };
    var shape16 = function(s){
        return 'M-1.2-3.5V-6h-2.3v7.2H-6v2.4h7.2V6h2.4v-7.2H6v-2.3H-1.2z M1.4,1.4h-2.6v-2.6h2.6V1.4z';
    };
    var shape17 = function(s){
        return 'M0.1,6.7L6,1.3v-8H-6v8L0.1,6.7z M3.7-4.4v4.5L0.1,3.6l-3.6-3.5v-4.5H3.7z';
    };
    var shape18 = function(s){
        return 'M-2.4-6.5l-1.5,1L-1.5-1h-3.8v2.1h3.8l-2.4,4.5l1.5,0.9L0,2l2.2,4.5l1.5-0.9L1.5,1.1h3.8v-2H1.5l2.2-4.5L2.2-6.5L0-1.8L-2.4-6.5z';
    };
    var shape19 = function(s){
        return 'M2.8,0C4-0.6,5.2-1.7,5.8-2.9L4.5-3.7C3.7-1.9,1.9-0.8,0.1-0.8s-3.6-1-4.5-2.9l-1.4,0.8C-5-1.7-4-0.6-2.7,0C-3.9,0.6-5,1.7-5.8,2.9l1.3,0.8c1-1.9,2.7-2.9,4.6-2.9s3.5,1.1,4.4,2.9l1.2-0.8C5.2,1.7,4.2,0.6,2.8,0z';
    };
    var shape20 = function(s){
        return 'M5.5,1.1L5.5,1.1L0-6.2l-5.5,7.3l1.8,1.4l2.5-3.4v7.1h2.4v-7.1l2.5,3.4L5.5,1.1z';
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