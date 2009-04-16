/*
 * Copyright (c) 2008-2009 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.writeApplet = function (applet, renderTo)
{
	var tag = 'applet';
    var p;
    var html = '';
//	if (navigator.appName.substr(0,8) == 'Netscape')
//		tag = 'embed';
//	if (navigator.appName.substr(0,9) == 'Microsoft')
//		tag = 'object';
	if (tag == 'embed')
	{
		html +=
			'<EMBED id="'+ applet.id + '"' +
			' code="' + applet.code + '"' +
			' archive="' + applet.archive + '"' +
			' width=' + (applet.width ? applet.width : 200) +
			' height=' + (applet.height ? applet.height : 200) +
			' type="application/x-java-applet;version=1.5.0"';
//		    ' pluginspage="http://java.sun.com/j2se/1.5.0/download.html">');
		for (p in applet.params)
			html += '<PARAM name="' + p + '" value="' + applet.params[p] + '">\n';
		html += '<NOEMBED>Applet not supported</NOEMBED></EMBED>';
	}
	else if (tag == 'object')
	{
		html +=
			'<OBJECT id="' + applet.id + '"' +
			' classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"' +
            ' width=' + (applet.width ? applet.width : 200) +
            ' height=' + (applet.height ? applet.height : 200) +
			' codebase="http://java.sun.com/products/plugin/autodl/jinstall-1_5_0-windows-i586.cab#Version=1,5,0,0">' +
			'<PARAM name="code" value="' + applet.code + '">' +
			'<PARAM name="arhive" value="' + applet.archive + '">';
		for (p in applet.params)
			html += '<PARAM name="' + p + '" value="' + applet.params[p] + '">\n';
		html += 'Applet not supported</OBJECT>';
	}
	else if (tag == 'applet')
	{
		html +=
			'<APPLET id="' + applet.id + '"' +
			' code="' + applet.code + '"' +
			' archive="' + applet.archive + '"' +
            ' width=' + (applet.width ? applet.width : 200) +
            ' height=' + (applet.height ? applet.height : 200) +
			' MAYSCRIPT="true" SCRIPTABLE="true">\n';
		for (p in applet.params)
			html += '<PARAM name="' + p + '" value="' + applet.params[p] + '">\n';
		html += 'This feature requires the Java browser plug-in.</APPLET>';
	}

    if (renderTo)
        Ext.get(renderTo).insertHtml("BeforeEnd", html);
    else
        document.write(html);
};