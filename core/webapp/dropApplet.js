/*
 * Copyright (c) 2008-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function getDropApplet()
{
    try
    {
        var applet = _id("dropApplet");
        if (applet && 'isActive' in applet && applet.isActive())
            return applet;
    }
    catch (x)
    {
    }
    return null;
}

function dropApplet_Update()
{
    asyncUpdateUI();
}

function dropApplet_DragEnter()
{
  _id("appletDiv").className = "labkey-nav-bordered labkey-bordered-heavy";
  _id("appletDiv").style.margin = "1px";
}

function dropApplet_DragExit()
{
    _id("appletDiv").className = "labkey-nav-bordered";
    _id("appletDiv").style.margin = "2px";
}

function browseFiles()
{
    try
    {
        var applet = getDropApplet();
        if (applet)
            applet.showFileChooser();
    }
    catch (ex)
    {
        window.alert(ex);
    }
}

var count_asyncUpdateUI = 0;      // debug info
var count_updateDropUI = 0;     // debug info
var appletUpdated = 1;

var asyncUpdateIntervalId = null;

function asyncUpdateUI()
{
    count_asyncUpdateUI++;
    appletUpdated++;
// this causes multi-threading problems on Firefox, just mark the applet dirty (appletUpdated++) and let
// 'foreground' thread do the work
//    if (asyncUpdateIntervalId)
//        return;
//    asyncUpdateIntervalId = window.setTimeout(asyncUpdateUI_handler,100);
}


function asyncUpdateUI_handler()
{
    window.clearTimeout(asyncUpdateIntervalId); asyncUpdateIntervalId = null;
    try
    {
        updateDropUI();
    }
    catch (x)
    {
        alert(x);
    }
}


function updateDropUI()
{
    if (0 == appletUpdated)
        return;
    appletUpdated = 0;
    _updateDropUI();
}



var INFO=0;
var SUCCESS=1;
var FAIL=-1;

// some variable to keep track of what has changed since last _updateDropUI
var consoleLinesRead = 0;
var consoleHtml = "";
var transfersTableInit = false;
var transfers = [];

function _updateDropUI()
{
    count_updateDropUI++;
    var i, t;
    var dropApplet = getDropApplet();
    var transferCount = transfers.length;
    var oldTransferCount = transfers.length;
    if (dropApplet)
    {
        transferCount = dropApplet.transfer_getFileCount();
        if (transferCount != oldTransferCount)
            showTransfers();
    }

    var ftpTransfers = _id("ftpTransfers");
    if (ftpTransfers && ftpTransfers.style.display != 'none')
    {
        if (!transfersTableInit)
        {
            ftpTransfers.innerHTML =
                "<table class='labkey-data-region' width='100%'>" +
                "<tr><th>&nbsp;<br><img src=\"" + LABKEY.contextPath + "/_.gif\" width=50 height=1></th><th width=150>file<br><img src=\"" + LABKEY.contextPath + "/_.gif\" width=150 height=1></th><th>modified<br><img src=\"" + LABKEY.contextPath + "/_.gif\" width=150 height=1></th><th width=100>size<br><img src=\"" + LABKEY.contextPath + "/_.gif\" width=100 height=1></th><th style=\"width:200px;\">status<br><img src=\"" + LABKEY.contextPath + "/_.gif\" width=210 height=1></th></tr>" +
                "</table>";
            transfersTableInit = true;
        };

        var ftpTransfersTable = ftpTransfers.getElementsByTagName("TABLE")[0];
        if (ftpTransfersTable)
        {
            if (oldTransferCount<transferCount)
            {
                var frag = document.createDocumentFragment();
                for (i=oldTransferCount ; i<transferCount; i++)
                {
                    transfers[i] = {};
                    t = transfers[i];
                    t.path = dropApplet.transfer_getPath(i);
                    t.name = dropApplet.transfer_getDisplayName(i);
                    t.length = dropApplet.transfer_getLength(i);
                    var m = dropApplet.transfer_getLastModified(i);
                    t.modified = m == -1 ? "-" : (new Date(m)).toLocaleString();
                    state = dropApplet.transfer_getState(i);
                    t.state = -100; // let next loop update state/status info to duplicate less code
                    t.status = dropApplet.transfer_getStatus(i);
                    t.percent = -1;
                    t.transfered = 0;

                    var trNew = _tr();
                    trNew.id = 'transfer[' + i + ']';
                    trNew.style.background = (i%2 == 0) ? "#eeeeee" : "#ffffff";
                    t.tdButton  = _td(_button("cancel", "btnCancel_onClick(" + i + ")"));
                    trNew.appendChild(t.tdButton);
                    var tdName = _td(_text(t.name));
                    trNew.appendChild(tdName);
                    var tdModified = _td(_text(t.modified));
                    tdModified.align="right";
                    trNew.appendChild(tdModified);
                    var tdLength = _td(_text(""+t.length));
                    tdLength.align="right";
                    trNew.appendChild(tdLength);
                    t.tdStatus = _td(_text(t.status));
                    t.tdStatus.style.color = state<=FAIL ? "red" : state>=SUCCESS ? "green" : "#000000";
                    trNew.appendChild(t.tdStatus);
                    frag.appendChild(trNew);
                }
                ftpTransfersTable.getElementsByTagName("TBODY")[0].appendChild(frag);
            }

            for (i=0 ; i<transferCount ; i++)
            {
                if (!dropApplet.transfer_getUpdated(i))
                    continue;
                t = transfers[i];
                var transfered = Math.max(0,dropApplet.transfer_getTransferred(i));
                var percent = dropApplet.transfer_getPercent(i);
                var state = dropApplet.transfer_getState(i);
                var status = dropApplet.transfer_getStatus(i);
                if ("tdStatus" in t && t.tdStatus)
                {
                    var tdStatus = t.tdStatus;
                    if (state == INFO && percent > 0 && percent < 100)
                    {
                        if (percent != t.percent)
                        {
                            var img = tdStatus.getElementsByTagName("IMG");
                            if (img && img[0] && img[1])
                            {
                                img[0].style.width = "" + (2*percent) + "px";
                                img[1].style.width = "" + (2*(100-percent)) + "px";
                            }
                            else
                            {
                                tdStatus.innerHTML = "<table border=1 cellpadding=0 cellspacing=0><tr><td><img src='" + LABKEY.contextPath + "/_.gif' style='background:black; width:" + (2*percent) + "; height:5; border-width:0px;'></td><td><img src='" + LABKEY.contextPath + "/_.gif' style='background;#202020; width:" + (2*(100-percent)) + "; height:5;; border-width:0px;'></td></tr></table>";
                            }
                        }
                    }
                    else
                    {
                        if (status != t.status || state != t.state)
                        {
                            removeChildren(tdStatus);
                            tdStatus.style.color = state < 0 ? "red" : state==SUCCESS ? "green" : "#000000";
                            tdStatus.appendChild(_text(status));
                        }
                    }
                }
                if ("tdButton" in t && t.tdButton)
                {
                    var td = t.tdButton;
                    if (t.state != state)
                    {
                        switch (state)
                        {
                            case -2:    // cancelled
                            case -1:    // failed
                                removeChildren(td);
                                td.appendChild(_button("retry", "btnRetry_onClick(" + i + ")"));
                                break;
                            case 1:     // success
                                removeChildren(td);
                                break;
                            case 0:
                                removeChildren(td);
                                td.appendChild(_button("cancel", "btnCancel_onClick(" + i + ")"));
                                break;
                        }
                    }
                }
                t.status = status;
                t.state = state;
                t.percent = percent;
                t.transfered = transfered;
            }

            if (transferCount != oldTransferCount && transfers[oldTransferCount] && transfers[oldTransferCount].tdStatus)
                transfers[oldTransferCount].tdStatus.parentNode.scrollIntoView();
        }
    }

    var bytesTransferred = 0;
    var filesTransferred = 0;
    var filesPending = 0;
    for (i=0 ; i<transfers.length ; i++)
    {
        t = transfers[i];
        bytesTransferred += t.transfered;
        if (t.state == SUCCESS && t.percent == 100)
            filesTransferred++;
        if (t.state == INFO)
            filesPending++;
    }

    var ftpBytesTransferred = _id("ftpBytesTransferred");
    if (ftpBytesTransferred)
        ftpBytesTransferred.innerHTML = "" + bytesTransferred;

    var ftpFilesTransferred = _id("ftpFilesTransferred");
    if (ftpFilesTransferred)
        ftpFilesTransferred.innerHTML = "" + filesTransferred;

    var ftpFilesPending = _id("ftpFilesPending");
    if (ftpFilesPending)
        ftpFilesPending.innerHTML = "" + filesPending;

    var ftpLocation = _id("ftpLocation");
    //if (ftpLocation && dropApplet)
    //    ftpLocation.innerHTML = h(dropApplet.getTargetURI());

    var ftpCountUpdate = _id("ftpCountUpdate");
    if (ftpCountUpdate)
        ftpCountUpdate.innerHTML = "" + count_UpdateDropUI + "/" + countUpdateDropUI;

    var ftpConsole = _id("ftpConsole");
    if (ftpConsole && dropApplet)
    {
        var count = dropApplet.console_getLineCount();
        if (consoleLinesRead<count)
        {
            for (; consoleLinesRead<count ; consoleLinesRead++)
                    consoleHtml += h(dropApplet.console_getLine(consoleLinesRead)) + "<br>";
            if (ftpConsole.style.display != 'none')
                ftpConsole.innerHTML = "<pre>" + consoleHtml + "</pre>";
        }
    }

    if (_id('ftpListing') && _id('ftpListing').style.display != 'none')
        displayFiles();
}


function pendingTransfers()
{
    var dropApplet = getDropApplet();
    if (!dropApplet)
        return 0;
    var count = dropApplet.transfer_getFileCount();
    var pending = 0;
    for (var i = 0 ; i<count ; i++)
    {
        var state = dropApplet.transfer_getState(i);
        if (state == INFO) // not success or fail yet
            pending++;
    }
    return pending;
}


function mkdir(path)
{
    try
    {
        var dropApplet = getDropApplet();
        if (dropApplet)
        {
            dropApplet.makeDirectory(path);
            dropApplet.changeTargetDirectory(path);
        }
        updateDropUI();
    }
    catch (ex)
    {
        window.alert(ex);
    }
}


function listFiles(async)
{
    try
    {
        var dropApplet = getDropApplet();
        if (!dropApplet)
            return;
        // don't do data connection in fg thread
        if (true)
        {
            dropApplet.asyncRefreshFiles();
        }
        else
        {
            dropApplet.refreshFiles();
            displayFiles();
        }
    }
    catch (ex)
    {
        window.alert(ex);
    }
}


function displayFiles()
{
    var dropApplet = getDropApplet();
    var listing = _id('ftpListing');
    if (!listing || !listing)
        return;
    var s = "";
    var count = dropApplet.server_getFileCount();
    for (var i=0 ; i<count ; i++)
    {
        s += h(dropApplet.server_getRawListing(i)) + "<br>";
    }
    listing.innerHTML = "<pre>" + s + "</pre>";
}


function listDirectories()
{
    try
    {
        var dropApplet = getDropApplet();
        if (dropApplet)
        {
            var a = dropApplet.listDirectories();
            var s = "";
            for (var i=0 ; i<a.length ; i++)
            {
                s += a[i].getRawListing() + "\n";
            }
            alert(s);
        }
    }
    catch (ex)
    {
        window.alert(ex);
    }
}

function btnCancel_onClick(i)
{
    if (i in transfers && transfers[i])
    {
        var t = transfers[i];
        getDropApplet().transfer_cancel(t.path);
    }
}

function btnRetry_onClick(i)
{
    if (i in transfers && transfers[i])
    {
        var t = transfers[i];
        getDropApplet().transfer_retry(t.path);
    }
}

function toggleVisible(show)
{
    if (_id(show)) _id(show).style.display="inline";
    for (var i=1 ; i<arguments.length ; i++)
        if (_id(arguments[i])) _id(arguments[i]).style.display="none";
}

function toggleSelected(select)
{
    if (_id(select)) _id(select).className="labkey-tab-selected";
    for (var i=1 ; i<arguments.length ; i++)
        if (_id(arguments[i])) _id(arguments[i]).className="labkey-tab labkey-tab-shaded";
}

function showConsole()
{
    if (_id("ftpConsole"))
        _id("ftpConsole").innerHTML = "<pre>" + consoleHtml + "</pre>";
    toggleVisible("ftpConsole", "ftpListing", "ftpTransfers");
    toggleSelected("consoleTab", "filesTab", "transfersTab");
}

function showFiles()
{
    listFiles(true);
    toggleVisible("ftpListing", "ftpConsole", "ftpTransfers");;
    toggleSelected("filesTab", "consoleTab", "transfersTab");
}

function showTransfers()
{
    toggleVisible("ftpTransfers", "ftpListing", "ftpConsole");
    toggleSelected("transfersTab", "filesTab", "consoleTab");
    asyncUpdateUI();                              
}

function h(s)
{
    if (!s) return "";
    if (-1 == s.indexOf("<") && -1 == s.indexOf("&"))
        return s;
    return _div(_text(s)).innerHTML;
}

function _id(s) {return document.getElementById(s);}
function _div(node) { var div = document.createElement("DIV"); if (node) div.appendChild(node); return div; }
function _tbody() { return document.createElement("TBODY"); }
function _tr() { return document.createElement("TR"); }
function _td(node) { var td = document.createElement("TD"); if (node) td.appendChild(node); return td;}
function _text(s) {return document.createTextNode(s);};
function _button(s,onclickFN)
{
//    var img = document.createElement("IMG");
//    img.src = LABKEY.contextPath + "/" + escape(s) + ".button";
//    img.onclick = function() {eval(onclickFN)};
//    return img;
    var a = document.createElement("A");
    a.href = "#";
    a.onclick = function() {eval(onclickFN);};
    a.appendChild(_text('['+s+']'));
    return a;
}
function removeChildren(e)
{
    while (e && e.firstChild)
        e.removeChild(e.firstChild);
}

var mkdirDialog;

function showMkdirDialog()
{
    if (!mkdirDialog)
        mkdirDialog = new LABKEY.widget.DialogBox("mkdirDialog",{width:"300px", height:"100px"});
    mkdirDialog.render();
    mkdirDialog.center();
    mkdirDialog.show();
}

// call from window.onload

var foregroundIntervalId = null;

function init()
{
    window.onbeforeunload = LABKEY.beforeunload(pendingTransfers);

    // style consistency
    dropApplet_DragExit();
    showTransfers();

    var mkdirDiv = _div();
    mkdirDiv.id = "mkdirDialog";
    mkdirDiv.style.display = "none";
    mkdirDiv.innerHTML = '<div class="hd">New Folder</div><div class="bd">' +
                         '<input id="folderName" name="folderName" value=""/><%=PageFlowUtil.generateButton("create", "")%>>';
    document.getElementsByTagName("BODY")[0].appendChild(mkdirDiv);

    //    updateDropUI();
    foregroundIntervalId = window.setInterval(updateDropUI,100);
}
