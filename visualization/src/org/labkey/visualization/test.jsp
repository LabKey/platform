<textarea id="json" style="height:400px; width: 100%;">

</textarea>
<button onclick="getData()">get data</button>
<script>
if (Ext4||Ext)
{
    var resizer = new (Ext4||Ext).Resizable("json", {
        handles: 'se',
        minWidth: 200,
        minHeight: 100,
        maxWidth: 1200,
        maxHeight: 800,
        pinned: true
    });
}

function getData()
{
    var config = JSON.parse(document.getElementById("json").value);
    config.success = function(json, response)
    {
        var r = response.responseText;
        if (Ext4||Ext)
            r = (Ext4||Ext).util.Format.htmlEncode(r);
        document.getElementById("response").innerHTML = r;
    };
    LABKEY.Query.Visualization.getData(config);
}
</script>

<pre id="response" style="border:solid 1px grey; min-height:100px;">

</pre>