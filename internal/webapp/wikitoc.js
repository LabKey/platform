
function locateSubTOC (node)
{
	var result = node.parentNode;
	result = result.nextSibling;
	result = result.childNodes.item(0);
    while (result && result.nodeName != "DIV")
	{
        result = result.nextSibling;
    }
    return result;
}

function ToggleTopic(topic)
{
	// Unfold the branch if it isn't visible
    var subTOC = locateSubTOC (topic);
	if (!subTOC) return;

	if (subTOC.style.display == 'none')
	{
		// Change the image (if there is an image)
		if (topic.childNodes.length > 0)
		{
			if (topic.childNodes.item(0).nodeName == "IMG")
			{
				topic.childNodes.item(0).src = LABKEY.imagePath + "/minus.gif";
			}
		}

		subTOC.style.display = 'block';
	}
	// Collapse the branch if it IS visible
	else
	{
		// Change the image (if there is an image)
		if (topic.childNodes.length > 0)
		{
			if (topic.childNodes.item(0).nodeName == "IMG")
			{
				topic.childNodes.item(0).src = LABKEY.imagePath + "/plus.gif";
			}
		}

		subTOC.style.display = 'none';
	}

}

function ToggleSubTOC (topic, expand)
{
    // get first TD of TR (i.e. topic)
    var subtopicImg = topic.childNodes.item(0);
    subtopicImg = subtopicImg.childNodes.item(0);
    while (subtopicImg && subtopicImg.nodeName != "IMG")
	{
        subtopicImg = subtopicImg.nextSibling;
    }
    if (!subtopicImg) {
        subtopicImg = topic.childNodes.item(0);
        subtopicImg = subtopicImg.childNodes.item(0);
        while (subtopicImg && subtopicImg.nodeName != "A")
	    {
            subtopicImg = subtopicImg.nextSibling;
        }
        subtopicImg = subtopicImg.childNodes.item(0);
        while (subtopicImg && subtopicImg.nodeName != "IMG")
	    {
            subtopicImg = subtopicImg.nextSibling;
        }
    }

	var subtopic = topic.childNodes.item(1);
	subtopic = subtopic.childNodes.item(0);
    while (subtopic && subtopic.nodeName != "DIV")
	{
        subtopic = subtopic.nextSibling;
    }
    if (subtopic)
    {
        if (subtopicImg)
        {
            if (expand) {
        	    subtopicImg.src = LABKEY.imagePath + "/minus.gif";
            } else {
        	    subtopicImg.src = LABKEY.imagePath + "/plus.gif";
            }
        }

    	subtopic.style.display = expand ? 'block' : 'none';
        subtopic = subtopic.childNodes.item(0);
        if (subtopic)
        {
            ToggleTOCTable (subtopic, expand);
        }
    }

}

function ToggleTOCTable (tocTable, expand)
{
    if (tocTable)
    {
        if (0 == tocTable.childNodes.length)
            return false;

        var topics = tocTable.childNodes.item(0);
        while (topics && topics.nodeName != "TBODY")
        {
            topics = topics.nextSibling;
        }

        if (!topics)
            return false;
        
        for (var i = 0; i < topics.childNodes.length; i++)
        {
            var topic = topics.childNodes.item(i);
            if (topic.nodeName == "TR")
            {
                ToggleSubTOC (topic, expand);
            }
        }
    }
}

function ToggleTOC (node, tocName)
{
    var tocTable = document.getElementById (tocName);
    if (tocTable)
    {
        var linkText = node.childNodes.item (0);
        if (linkText.nodeValue == 'expand all') {
            ToggleTOCTable (tocTable, true);
            linkText.nodeValue = 'collapse all';
        } else {
            ToggleTOCTable (tocTable, false);
            linkText.nodeValue = 'expand all';
        }
    }

    return false;
}

