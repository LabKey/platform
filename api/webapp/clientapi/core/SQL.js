(function(LABKEY)
{
    LABKEY.Query.experimental = LABKEY.Query.experimental || {};

    var identity = function(x) {return x;};

    var control_chars =
    {
        nul : "\x00",
        rs  : "\x1E", // record separator
        us  : "\x1F"  // unit separator
    };

    var convertDate = function(s)
    {
        var number = parseFloat(s);
        return new Date(!isNaN(number) && isFinite(number) ? number : s);
    };

    var converters =
    {
        BOOLEAN: parseInt,
        TINYINT : parseInt,
        SMALLINT : parseInt,
        INTEGER : parseInt,
        BIGINT : parseInt,      // parseDouble?
        DOUBLE : parseFloat,
        REAL : parseFloat,
        NUMERIC : parseFloat,
        TIMESTAMP : convertDate
    };


    function parseRows(text,sep,eol)
    {
        console.log(new Date());
        var rows = text.split(eol);
        if ("" === rows[rows.length-1].trimRight())
            rows.pop();

        // names
        var names = rows[0].split(sep);

        // types
        var colConverters = [];
        var types = rows[1].split(sep);
        for (var i=0 ; i<types.length ; i++)
            colConverters[i] = converters[types[i]] || identity;

        // rows
        for (var r=2 ; r<rows.length ; r++)
        {
            var row = rows[r].split(sep);
            for (var c=0 ; c<row.length ; c++)
            {
                if ("" == row[c])
                    row[c] = null;
                else
                    row[c] = colConverters[c](row[c]);
            }
            rows[r-2] = row;
        }
        rows.pop(); rows.pop();
        return {names:names, types:types, rows:rows};
    }


    function asObjects(fields, rows)
    {
        var row = function(){};
        var p = {};
        for (var f=0 ; f<fields.length ; f++)
            p[fields[f]] = null;
        row.prototype = p;

        var result = [];
        for (var r=0 ; r<rows.length ; r++)
        {
            var arr = rows[r];
            var obj = new row();
            var l=Math.min(fields.length,arr.length);
            for (var c=0 ; c<l ; c++)
                obj[fields[c]] = arr[c];
            result.push(obj);
        }
        return result;
    }


    LABKEY.Query.experimental.SQL = new (function()
    {
        /* containerPath:"", schema:"", sql:"", parameters:{}, timeout:## */
        function execute(config)
        {
            if (!config.schema)
                throw "You must specify a schema!";

            if (!config.sql)
                throw "You must specify sql statement!";

            var sep = config.sep || (control_chars.us + '\t');
            var eol = config.eol || (control_chars.us + '\n');

            var requestConfig =
            {
                url : LABKEY.ActionURL.buildURL('sql', 'execute', config.containerPath),
                method : "POST",
                success: function(response, request)
                {
                    var result = parseRows(response.responseText, sep, eol);
                    LABKEY.Utils.getOnSuccess(config)(result);
                },
                failure: LABKEY.Utils.getOnFailure(config),
                jsonData :
                {
                    schema:config.schema,
                    sql:config.sql,
                    parameters:config.parameters,
                    sep: sep,
                    eol: eol
                }
            };

            if (LABKEY.Utils.isDefined(config.timeout))
                requestConfig.timeout = config.timeout;

            return LABKEY.Ajax.request(requestConfig);
        }

        return {
            execute : execute,
            asObjects : asObjects
        }
    });

})(LABKEY);