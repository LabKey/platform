package org.labkey.api.util;

import org.labkey.api.view.NotFoundException;

/**
 * A very stripped down version of JavaScanner that handles only Java-style block comments (does not handle line
 * comments, double-quoted strings, or text blocks)
 **/
public class JavaBlockCommentScanner extends BaseScanner
{
    public JavaBlockCommentScanner(String text)
    {
        super(text);
    }

    @Override
    public void scan(int fromIndex, Handler handler)
    {
        int i = fromIndex;

        while (i < _text.length())
        {
            char c = _text.charAt(i);
            String twoChars = null;

            if (i < (_text.length() - 1))
                twoChars = _text.substring(i, i + 2);

            if ("/*".equals(twoChars))
            {
                int endIndex = _text.indexOf("*/", i + 2) + 2;  // Skip to end of comment

                if (1 == endIndex)
                    throw new NotFoundException("Comment starting at position " + i + " was not terminated");

                if (!handler.comment(i, endIndex))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment ('/')
            }
            else
            {
                if (!handler.character(c, i))
                    return;
            }

            i++;
        }
    }
}
