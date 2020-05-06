/*
 * Copyright (c) 2016 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

// returns a list of pairs
//     [
//         {a:a1, b:b2, status:'MATCH'},
//         {a:a2, b:b2, status:'DIFF'},
//         {a:a3, null, status:'NOMATCH'}
//         {a:null, b:a4, status:'NOMATCH'}
// that matches properties between two domains

function diffDomainProperties(domainA, domainB)
{
    var NOTUNIQUE = new Object('marker value');
    var typePrefix = "http://www.w3.org/2001/XMLSchema#";

    function buildIndexMap(array, getter)
    {
        var map = {}, item, key;
        for (var i=0 ; i<array.length ; i++)
        {
            item = array[i];
            if (!item) continue;
            key = getter(item);
            if (!key) continue;
            if (key in map)
                map[key] = NOTUNIQUE;
            else
                map[key] = i;
        }
        return map;
    }
    function findMatches(fieldsA, fieldsB, getter, matches)
    {
        var mapA = buildIndexMap(fieldsA, getter);
        var mapB = buildIndexMap(fieldsB, getter);
        var key;
        for (key in mapA)
        {
            if (!(key in mapB) || mapA[key] == NOTUNIQUE || mapB[key] == NOTUNIQUE)
                    continue;
            matches.push({a:fieldsA[mapA[key]], b:fieldsB[mapB[key]]});
            fieldsA[mapA[key]] = fieldsB[mapB[key]] = null;
        }
    }

    // shallow copy
    var fieldsA = domainA.fields.slice(0,domainA.fields.length);
    var fieldsB = domainB.fields.slice(0,domainB.fields.length);
    var matches = [];
    findMatches(fieldsA, fieldsB, function(pd){return pd.conceptURI;}, matches);
    findMatches(fieldsA, fieldsB, function(pd){return pd.name;}, matches);
    fieldsA.forEach(function (pd){if (pd) matches.push({a:pd, b:null});});
    fieldsB.forEach(function (pd){if (pd) matches.push({a:null, b:pd});});

    // so now we have sets of proposed matches, let's add some helpful text
    matches.forEach(function(m)
    {
        if (m.a && m.b)
        {
            var nameMatches = m.a.name == m.b.name;
            var conceptMatches = m.a.conceptURI == m.b.conceptURI;

            var aType = m.a.rangeURI;
            if (aType.indexOf(typePrefix) == 0)
                aType = aType.substring(typePrefix.length);

            var bType = m.b.rangeURI;
            if (bType.indexOf(typePrefix) == 0)
                bType = bType.substring(typePrefix.length);

            var typeMatches = aType == bType;

            if (nameMatches)
            {
                var changes = [];
                if (!typeMatches)
                    changes.push("TYPE");
                if (!conceptMatches)
                    changes.push("CONCEPT");
                m.status = (changes.length == 0) ? "MATCH" : (changes.join(", ") + " CHANGED");
            }
            else
            {
                // concept must match
                m.status = 'RENAMED';
            }
        }
        else
        {
            m.status='NOMATCH';
        }
    });

    // TODO sort
    return matches;
}


function renderUpdateDomainView(target, templateInfo, editDomain, templateDomain)
{
    // domain is dirty if any columns are not saved
    var dirty = false;
    editDomain.fields.forEach(function(prop){
        if (!prop.propertyId)
            LABKEY.setDirty(true);
    });

    function addColumn(name)
    {
        templateDomain.fields.forEach(function(prop){
            if (prop.name === name)
            {
                editDomain.fields.push(prop);
            }
        });
        renderUpdateDomainView(target, templateInfo, editDomain, templateDomain);
    }
    function save()
    {
        LABKEY.Domain.save({
            success: function()
            {
                var url = LABKEY.ActionURL.getParameters()['returnUrl'] || LABKEY.ActionURL.getParameters()['cancelUrl'];
                url = url || LABKEY.ActionURL.buildURL("property","editdomain",null,{"domainId":editDomain.domainId});
                window.location = url;
            },
            domainDesign:editDomain,
            schemaName:editDomain.schemaName,
            queryName:editDomain.queryName
        });
    }

    var $ = jQuery;
    var h = LABKEY.Utils.encodeHtml;

    if (null == templateInfo)
    {
        $(target).html("No associated template found for this domain");
        return;
    }

    // TODO rewrite using React tempalte when it is supported in the main build
    var html = ["<div class=comparetemplate>"];
    html.push("<h3>template information</h3>");
    html.push("<table>");
    html.push("<tr><td class='labkey-form-label'>module</td><td class=value>" + h(templateInfo.module) + "</td></tr>");
    html.push("<tr><td class='labkey-form-label'>file</td><td class=value>domain-templates/" + h(templateInfo.group) + ".template.xml</td></tr>");
    html.push("<tr><td class='labkey-form-label'>name</td><td class=value>" + h(templateInfo.template) + "</td></tr>");
    html.push("</table><p>&nbsp;</p>");

    if (null == templateDomain)
    {
        html.push("<span class=labkey-error>Could not load template</span>");
    }
    else
    {
        var matches = diffDomainProperties(editDomain, templateDomain);
        html.push("<h3>Compare domain columns to template columns.</h3>");
        html.push("<table><thead><th>column</th><th>template</th><th>status</th><th>&nbsp;</th></thead>");
        for (var m = 0; m < matches.length; m++)
        {
            var match = matches[m];
            html.push("<tr>");
            if (match.a && match.b)
            {
                html.push("<td class='colA'>" + h(match.a.name) + "</td><td class='colB'>" + h(match.b.name) + "</td><td class='colStatus'>" + (match.a.propertyId?match.status:"hit save to create column") + "</td><td>&nbsp;</td>");
            }
            else if (match.a)
            {
                html.push("<td class='colA'>" + h(match.a.name) + "</td><td class='colB'>&nbsp;</td><td class='colStatus'>" + match.status + "</td>");
            }
            else if (match.b)
            {
                html.push("<td class='colA'>&nbsp;</td><td class='colB'>" + h(match.b.name) + "</td><td class='colStatus'><button class='addColumn' data-name='" + h(match.b.name) + "'>add column</button></td>");
            }
            html.push("</tr>");
        }
        html.push("</table>");
        html.push("<button id='saveChanges' " + (LABKEY.isDirty() ? "" : "disabled") + ">save</button>");
    }
    html.push("</div>");
    $(target).html(html.join(""));

    var btnSave = $("#saveChanges");
    btnSave.click(save);
    var btnListAdd = $("BUTTON.addColumn");
    btnListAdd.click(function(evt)
    {
        addColumn(evt.target.dataset.name);
    });
}
