package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

public final class CommandLineTokenizer
{
    /**
     * Tokenize a command line string into arguments.
     *
     * Splits on whitespace, preserves single- and double-quoted arguments as well
     * arguments surrounded by ${ and }.  Backslash escapes the next character.
     *
     * @param str
     * @return List of command line tokens.
     * @throws IllegalArgumentException If the quotes are unbalanced.
     */
    @NotNull
    public static List<String> tokenize(String str)
    {
        if (str == null || str.isBlank())
            return Collections.emptyList();

        List<String> tokens = new ArrayList<>(10);

        StringBuilder current = new StringBuilder();

        // true when we are backslash escaped
        boolean escaped = false;
        State state = State.NORMAL;
        int startQuote = -1;

        for (int i = 0; i < str.length(); i++)
        {
            char c = str.charAt(i);
            if (escaped)
            {
                // Previous character was backslash, just append the current char
                escaped = false;
                current.append(c);
            }
            else
            {
                switch (state)
                {
                    case SINGLE_QUOTE:
                        if (c == '\'')
                        {
                            // closing single-quote; continue until next whitespace is seen
                            state = State.NORMAL;
                            startQuote = -1;
                        }
                        else
                        {
                            current.append(c);
                        }
                        break;

                    case DOUBLE_QUOTE:
                        if (c == '"')
                        {
                            // closing double-quote; continue until next whitespace is seen
                            state = State.NORMAL;
                            startQuote = -1;
                        }
                        else if (c == '\\')
                        {
                            // backslash within double-quote string
                            // peek ahead for backslash or double quote char and append the literal character
                            if (i < str.length() && (str.charAt(i+1) == '\\' || str.charAt(i+1) == '"'))
                            {
                                i++;
                                current.append(str.charAt(i));
                            }
                            else
                            {
                                current.append(c);
                            }
                        }
                        else
                        {
                            current.append(c);
                        }
                        break;

                    case DOLLAR_CURLY:
                        if (c == '}')
                        {
                            // closing curly; continue until next whitespace is seen
                            state = State.NORMAL;
                            startQuote = -1;
                            current.append(c);
                        }
                        else
                        {
                            current.append(c);
                        }
                        break;

                    case NORMAL:
                    default:
                        if (c == '\\')
                        {
                            // backslash escape
                            escaped = true;
                        }
                        else if (c == '\'')
                        {
                            // starting single quote
                            state = State.SINGLE_QUOTE;
                            startQuote = i;
                        }
                        else if (c == '"')
                        {
                            // starting double quote
                            state = State.DOUBLE_QUOTE;
                            startQuote = i;
                        }
                        else if (c == '$')
                        {
                            // peek ahead to see if we have '{'
                            if (i < str.length() && str.charAt(i+1) == '{')
                            {
                                i++;
                                state = State.DOLLAR_CURLY;
                                startQuote = i;
                                current.append("${");
                            }
                            else
                            {
                                current.append(c);
                            }
                        }
                        else if (!Character.isWhitespace(c))
                        {
                            // any other character
                            current.append(c);
                            state = State.NORMAL;
                        }
                        else
                        {
                            // whitespace ends the token
                            if (current.length() > 0)
                                tokens.add(current.toString());
                            current = new StringBuilder();
                            state = State.NORMAL;
                        }
                        break;
                }
            }
        }

        // throw exception if quotes are unbalanced
        if (state != State.NORMAL)
        {
            assert startQuote != -1;
            throw new IllegalArgumentException("Mismatched " + state.name() + " starting at " + startQuote + ": " + str);
        }

        // append the dangling backslash
        if (escaped)
            current.append('\\');

        if (current.length() > 0)
            tokens.add(current.toString());

        return tokens;
    }

    private enum State {
        NORMAL,
        SINGLE_QUOTE,
        DOUBLE_QUOTE,
        DOLLAR_CURLY
    }

    public static class TestCase
    {
        @Test
        public void testEmpty()
        {
            assertThat(tokenize(null), equalTo(List.of()));
            assertThat(tokenize(""), equalTo(List.of()));
            assertThat(tokenize("   "), equalTo(List.of()));
            assertThat(tokenize("\t\n "), equalTo(List.of()));
        }

        @Test
        public void testSimple()
        {
            assertThat(tokenize("a b c"), equalTo(List.of("a", "b", "c")));
            assertThat(tokenize("abc"), equalTo(List.of("abc")));

            // backslash escaped character
            assertThat(tokenize("a \\b c"), equalTo(List.of("a", "b", "c")));
            assertThat(tokenize("a\\ b c"), equalTo(List.of("a b", "c")));
            assertThat(tokenize("\\\\"), equalTo(List.of("\\")));

            // trailing backslash included
            assertThat(tokenize("\\"), equalTo(List.of("\\")));
            assertThat(tokenize("c:\\\\path\\\\"), equalTo(List.of("c:\\path\\")));
        }

        @Test
        public void testSingleQuote()
        {
            assertThat(tokenize("'a b c'"), equalTo(List.of("a b c")));

            // quotes can be used within an argument
            assertThat(tokenize("tr -d' '"), equalTo(List.of("tr", "-d ")));

            // whitespace and double-quote within single-quote
            assertThat(tokenize("'a  \"  b'"), equalTo(List.of("a  \"  b")));

            // backslash within single-quote string
            assertThat(tokenize("'\\'"), equalTo(List.of("\\")));

            // mismatched quote
            try
            {
                tokenize("Conan O'Brien");
                fail("Expected mismatched quote exception");
            }
            catch (IllegalArgumentException e)
            {
                assertThat(e.getMessage(), startsWith("Mismatched SINGLE_QUOTE starting at 7:"));
            }
        }

        @Test
        public void testDoubleQuote()
        {
            assertThat(tokenize("\"a b c\""), equalTo(List.of("a b c")));

            // whitespace and single-quote within double-quote string
            assertThat(tokenize("\"a  '  b\""), equalTo(List.of("a  '  b")));

            // whitespace and curly-quote within double-quote string
            assertThat(tokenize("\"${foo bar}\""), equalTo(List.of("${foo bar}")));
            assertThat(tokenize("\"test ${foo bar}\" blee"), equalTo(List.of("test ${foo bar}", "blee")));

            // backslash escaped quote inside double-quote string
            assertThat(tokenize("\"a \\\"b c\""), equalTo(List.of("a \"b c")));

            // backslash escaped backslash inside double-quote string
            assertThat(tokenize("\"a \\\\b c\""), equalTo(List.of("a \\b c")));

            // mismatched quote
            try
            {
                tokenize("\"foo");
                fail("Expected mismatched quote exception");
            }
            catch (IllegalArgumentException e)
            {
                assertThat(e.getMessage(), startsWith("Mismatched DOUBLE_QUOTE starting at 0"));
            }
        }

        @Test
        public void testCurlyQuote()
        {
            // bare $ passed through
            assertThat(tokenize("a$b c"), equalTo(List.of("a$b", "c")));

            assertThat(tokenize("foo ${pipeline, username} bar"),
                    equalTo(List.of("foo", "${pipeline, username}", "bar")));

            // whitespace and quotes within double-quote string
            assertThat(tokenize("${a  '  b}"), equalTo(List.of("${a  '  b}")));

            // closing curly
            assertThat(tokenize("a}b"), equalTo(List.of("a}b")));

            // mismatched quote
            try
            {
                tokenize(" ${ ");
                fail("Expected mismatched quote exception");
            }
            catch (IllegalArgumentException e)
            {
                assertThat(e.getMessage(), startsWith("Mismatched DOLLAR_CURLY starting at 2"));
            }
        }
    }

}
