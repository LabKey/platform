/*
 * Copyright (c) 2025-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.api.util;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.view.NotFoundException;

/**
 * Detects /* {@literal *}/ block comments, but with rules that deviate slightly from Java syntax because we need to
 * allow valid CSP syntax like "http://*". A block comment therefore starts with:
 * - "/*" when these are the first two characters of the string (char[0], char[1])
 * - " /*" after char[0]. All other comments must be proceeded by a space.
 * Other whitespace characters (\n, \r, \t) in the incoming text must be replaced with spaces for comments following
 * them to be detected.
 **/
public class CspCommentScanner extends BaseScanner
{
    public CspCommentScanner(String text)
    {
        super(text);
    }

    @Override
    public void scan(int fromIndex, Handler handler)
    {
        // Simple cheat to handle beginning-of-line special case: insert a space as the very first character.
        // Indexes that are handed to callback methods (comment(), character()) are then adjusted -1 to compensate.
        String text = " " + _text;
        int i = fromIndex;

        while (i < text.length())
        {
            char c = text.charAt(i);
            String threeChars = null;

            if (i < (text.length() - 2))
                threeChars = text.substring(i, i + 3);

            if (" /*".equals(threeChars))
            {
                int endIndex = text.indexOf("*/", i + 3) + 2;  // Skip to end of comment

                if (1 == endIndex)
                    throw new NotFoundException("Comment starting at position " + i + " was not terminated");

                // Comment index starts at the character after the space, which is i + 1 but then -1 to compensate = i
                if (!handler.comment(i, endIndex - 1))
                    return;

                i = endIndex - 1; // Leave i at the last character of the comment ('/')
            }
            else
            {
                // Don't handle the first character, the cheater space we added
                if (i > 0 && !handler.character(c, i - 1))
                    return;
            }

            i++;
        }
    }

    public static final class TestCase extends Assert
    {
        @Test
        public void testScanner()
        {
            succeed("", "");
            succeed("/", "/");
            succeed("/**/", "");
            succeed("/*/*/", "");
            succeed("/* /*/", "");
            succeed(" /* */", " ");
            succeed("This/* is not a comment since there's no space preceding it */", "This/* is not a comment since there's no space preceding it */");
            succeed("http://* https://* /* only one legal comment here */", "http://* https://* ");
            succeed("/* only one legal comment here *//* not a legal comment *//* also not a legal comment */", "/* not a legal comment *//* also not a legal comment */");

            failStrip("/*");
            failStrip("/**");
            failStrip("/*/");
            failStrip("/*/*");
            failStrip("This is longer text with an unterminated comment /* and then some more text");
            failStrip("And here's an unterminated comment at the very end /*");
        }

        private void succeed(String text, String expected)
        {
            Assert.assertEquals(expected, new CspCommentScanner(text).stripComments().toString());
        }

        private void failStrip(String text)
        {
            try
            {
                new CspCommentScanner(text).stripComments();
                fail("Expected an exception for unterminated comment for: " + text);
            }
            catch (NotFoundException e)
            {
                String message = e.getMessage();

                // We expect a "not terminated" exception. Throw if it's anything but that.
                if (!message.contains("was not terminated"))
                    throw e;

                int idx = message.indexOf("position ") + 9;
                int pos = Integer.valueOf(message.substring(idx, message.indexOf(" ", idx)));
                Assert.assertEquals('/', text.charAt(pos));
                Assert.assertEquals('*', text.charAt(pos + 1));
            }
        }
    }
}
