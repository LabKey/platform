<%@ page import="org.labkey.api.util.HelpTopic"%>

<table>
    <tr>
        <td colspan=2><b>Formatting Guide</b> (<a target="_blank" href="<%=(new HelpTopic("wikiSyntax", HelpTopic.Area.SERVER)).getHelpTopicLink()%>">more help</a>):</td>
    </tr>
    <tr>
        <td>link to page in this wiki&nbsp;&nbsp;</td>
        <td>[pagename] or [Display text|pagename]</td>
    </tr>
    <tr>
        <td>external link</td>
        <td>http://www.google.com or {link:Display text|http://www.google.com}</td>
    </tr>
    <tr>
        <td>picture</td>
        <td>[attach.jpg] or {image:http://www.website.com/somepic.jpg}</td>
    </tr>
    <tr>
        <td>bold</td>
        <td>**like this**</td>
    </tr>
    <tr>
        <td>italics</td>
        <td>~~like this~~</td>
    </tr>
    <tr>
        <td>bulleted list</td>
        <td>- list item</td>
    </tr>
    <tr>
        <td>numbered List</td>
        <td>1. list item</td>
    </tr>
    <tr>
        <td>line break (&lt;br&gt;)</td>
        <td>\\</td>
    </tr>
</table>