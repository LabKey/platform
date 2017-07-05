package org.labkey.api.markdown;

/**
 * User: Jim Piper
 * Date: Jun 28, 2017
 * Time: 10:15:52 AM
 */
public interface MarkdownService
{
    /**
     * @param mdText
     * @return the html string that will render the content described by the markdown text of the input string
     */
    String mdToHtml(String mdText);
}
