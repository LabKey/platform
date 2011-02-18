/*
 * Copyright (c) 2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

var Logger = org.apache.log4j.Logger;

var logger = Logger.getLogger(org.labkey.api.script.ScriptService.Console);

function formatPart(o)
{
    if (typeof (o) == 'undefined')
        o = "undefined";
    else if (o == null)
        o = "null";
    else if (typeof o == "string" || typeof o == "number" || typeof o == "function")
        // Don't JSON.stringify these types
        ;
    else
    {
        try
        {
            o = JSON.stringify(o);
        }
        catch (e) {
            o = ""+o;
        }
    }

    // create a primitive string
    return String(o);
}

function formatMessage(args)
{
    var parts = [];
    for (var i = 0; i < args.length; i++)
        parts.push(formatPart(args[i]));

    return parts.join(" ");
}

exports.log = function ()
{
    logger.info(formatMessage(arguments));
};

exports.debug = function ()
{
    logger.debug(formatMessage(arguments));
};

exports.info = function ()
{
    logger.info(formatMessage(arguments));
};

exports.warn = function ()
{
    logger.warn(formatMessage(arguments));
};

exports.error = function ()
{
    logger.error(formatMessage(arguments));
};

exports.assert = function () { };
exports.dir = function (o) { };
exports.trace = function () { };

