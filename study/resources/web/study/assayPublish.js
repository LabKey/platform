/*
 * Copyright (c) 2018 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

/*
 * basically, change a=1&a=2=&a=3 to a=1\t2\t3 and resubmit the form
 */
function assayPublish_onCopyToStudy(el, fieldNames)
{
    var formEl = jQuery(el).closest("FORM")[0];

    // unfortunately FormData strategy does not work for all browsers
    // var formData = new FormData(formEl);
    var formData = {};
    for ( var i = 0; i < formEl.elements.length; i++ )
    {
        var e = formEl.elements[i];
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