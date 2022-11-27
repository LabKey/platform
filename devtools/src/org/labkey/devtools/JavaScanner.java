package org.labkey.devtools;

import org.labkey.api.util.BaseScanner;
import org.labkey.api.view.NotFoundException;

public class JavaScanner extends BaseScanner
{
    private static final String ESCAPED_BACKSLASH = "\\\\";   // Backslash + backslash
    protected static final String TEXT_BLOCK_DELIMITER = "\"\"\"";

    public JavaScanner(String text)
    {
        super(text);
    }

    /**
     * Scans Java code, skipping all block comments, single-line comments, double-quoted string constants, text blocks,
     * and single-quoted character constants (while correctly handling escaped quotes and escaped backslashes inside the
     * quotes), calling handler methods along the way.
     * @param fromIndex index from which to start the scan
     * @param handler Handler whose methods will be invoked
     */
    @Override
    public void scan(int fromIndex, Handler handler)
    {
        int i = fromIndex;

        while (i < _text.length())
        {
            char c = _text.charAt(i);
            String twoChars = null;
            String threeChars = null;

            if (i < (_text.length() - 1))
                twoChars = _text.substring(i, i + 2);

            if (i < (_text.length() - 2))
                threeChars = _text.substring(i, i + 3);

            next: if ("/*".equals(twoChars))
            {
                int endIndex = _text.indexOf("*/", i + 2) + 2;  // Skip to end of comment

                if (1 == endIndex)
                    throw new NotFoundException("Comment starting at position " + i + " was not terminated");

                if (!handler.comment(i, endIndex))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment ('/')
            }
            else if ("//".equals(twoChars))
            {
                int endIndex = _text.indexOf("\n", i + 2);

                if (-1 == endIndex)
                    endIndex = _text.length();

                if (!handler.comment(i, endIndex))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment (before cr)
            }
            else if (TEXT_BLOCK_DELIMITER.equals(threeChars))
            {
                // Java 15 text block. No special handling for escaping or leading whitespace, but that seems unnecessary.
                int startIndex = i;

                while (++i < _text.length())
                {
                    threeChars = null;

                    if (i < (_text.length() - 2))
                        threeChars = _text.substring(i, i + 3);

                    if (TEXT_BLOCK_DELIMITER.equals(threeChars))
                    {
                        // Call string() handler method for all text-block strings
                        if (!handler.string(startIndex, i + 3))
                            return;

                        i += 3;
                        break next;
                    }
                }

                throw new NotFoundException("Expected text block ending (" + TEXT_BLOCK_DELIMITER + ") was not found");
            }
            else if ('"' == c || '\'' == c)  // Handle double-quoted string and single-quoted character constants
            {
                String escapedQuote = "\\" + c;     // Backslash + opening quote
                int startIndex = i;

                while (++i < _text.length())
                {
                    char c2 = _text.charAt(i);
                    twoChars = null;

                    if (i < (_text.length() - 1))
                        twoChars = _text.substring(i, i + 2);

                    if (ESCAPED_BACKSLASH.equals(twoChars) || escapedQuote.equals(twoChars))
                    {
                        i++;
                    }
                    else if (c == c2)
                    {
                        // Call string() handler method for all double-quoted strings
                        if ('"' == c && !handler.string(startIndex, i + 1))
                            return;

                        break next;
                    }
                }

                throw new NotFoundException("Expected ending quote (" + c + ") was not found");
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
