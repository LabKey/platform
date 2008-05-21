/*
 * Copyright (c) 2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
LABKEY.writeApplet = function (applet)
{
	var tag = 'applet';
    var p;
//	if (navigator.appName.substr(0,8) == 'Netscape')
//		tag = 'embed';
//	if (navigator.appName.substr(0,9) == 'Microsoft')
//		tag = 'object';
	if (tag == 'embed')
	{
		document.write(
			'<EMBED id="'+ applet.id + '"',
			' code="' + applet.code + '"',
			' archive="' + applet.archive + '"',
			' width="200"',
			' height="200"',
			' type="application/x-java-applet;version=1.5.0"');
//		    ' pluginspage="http://java.sun.com/j2se/1.5.0/download.html">');
		for (p in applet.params)
			document.write('<PARAM name="' + p + '" value="' + applet.params[p] + '">\n');
		document.write('<NOEMBED>Applet not supported</NOEMBED></EMBED>');
	}
	else if (tag == 'object')
	{
		document.write(
			'<OBJECT id="' + applet.id + '"',
			' classid="clsid:8AD9C840-044E-11D1-B3E9-00805F499D93"',
			' width="200"',
			' height="200"',
			' codebase="http://java.sun.com/products/plugin/autodl/jinstall-1_5_0-windows-i586.cab#Version=1,5,0,0">',
			'<PARAM name="code" value="' + applet.code + '">',
			'<PARAM name="arhive" value="' + applet.archive + '">');
		for (p in applet.params)
			document.write('<PARAM name="' + p + '" value="' + applet.params[p] + '">\n');
		document.write('Applet not supported</OBJECT>');
	}
	else if (tag == 'applet')
	{
		document.write(
			'<APPLET id="' + applet.id + '"',
			' code="' + applet.code + '"',
			' archive="' + applet.archive + '"',
			' height="200" width="200" MAYSCRIPT="true" SCRIPTABLE="true">\n');
		for (p in applet.params)
			document.write('<PARAM name="' + p + '" value="' + applet.params[p] + '">\n');
		document.write('This feature requires the Java browser plug-in.</APPLET>');
	}
}