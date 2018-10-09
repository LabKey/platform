/* reshape a bunch of array valued fields into a tsv */

function formDataToTSV(formDataIN, fieldNamesArray)
{
    var fieldNamesSet = {};
    var gridNames = [];
    var gridValues = [];
    var len = 0;
    var n;
    for (n=0 ; n<fieldNamesArray.length ; n++)
    {
        var name = fieldNamesArray[n];
        fieldNamesSet[name] = name;
        var values = formDataIN.getAll(name);
        if (values.length === 0)
            continue;
        if (len !== 0 && values.length !== len)
            throw "badly formed FORM, can't post";
        gridNames.push(name);
        gridValues.push(values);
        len = values.length;
    }
    var tsv = [];
    tsv.push(gridNames.join("\t"));
    var row;
    var width = gridValues.length;
    for (i=0 ; i<len ; i++)
    {
        row = [];
        for (n=0 ; n<width ; n++)
            row.push(gridValues[n][i]);
        tsv.push(row.join("\t"));
    }
    tsv = tsv.join("\n");

    // OK, pick up all the other values
    var formDataOUT = new FormData();
    formDataOUT.append("tsv", tsv);
    var entries = Array.from(formDataIN.entries());
    for (var pair of formDataIN.entries())
    {
        var key = pair[0];
        if (key.charAt(0) === '.')
            continue;
        if (key === fieldNamesSet[key])
            continue;
        formDataOUT.append(key, pair[1]);
    }
    return formDataOUT;
}


function assayPublish_onCopyToStudy(el, fieldNames)
{
    var formEl = jQuery(el).closest("FORM")[0];

    // unfortunately FormData strategy does not work for all browsers
    // var formData = new FormData(formEl);
    var formData = {};
    for ( var i = 0; i < formEl.elements.length; i++ )
    {
        var e = formEl.elements[i];
        if (!e.name || e.name.charAt(0) === '.')
            continue;
        if (e.checked || (e.type !== "radio" && e.type !== "checkbox"))
        {
            var array = formData[e.name];
            if (!array)
                array = formData[e.name] = [];
            array.push(e.value.replace("\t"," "));
        }
    }

    // rather than submit one query param pair per value, encode multiple values using tabs
    var p;
    for (p in formData)
    {
        if (formData.hasOwnProperty(p))
            formData[p] = formData[p].join('\t');
    }

    // create a new hidden form and submit it
    var hiddenForm = formEl.cloneNode(false);
    hiddenForm.style.display = 'none';
    for (p in formData)
    {
        if (formData.hasOwnProperty(p))
        {
            var input = document.createElement("INPUT");
            input.type = "hidden";
            input.name = p;
            input.value = formData[p];
            hiddenForm.appendChild(input);
        }
    }
    document.documentElement.appendChild(hiddenForm);
    LABKEY.setSubmit(true);
    hiddenForm.submit();
    return false;
}