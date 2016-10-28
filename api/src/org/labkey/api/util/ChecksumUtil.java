/*
 * Copyright (c) 2016 LabKey Corporation
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
import org.labkey.api.test.TestWhen;

/**
 * Class for creating and validating a Luhn mod n checksum character for a given string using a given alphabet.
 * The alphabet must not be empty and the character string to compute a checksum for should also not be empty.
 */
public class ChecksumUtil
{
    private String _validInputChars;
    private Integer _n;

    public ChecksumUtil(String chars)
    {
        if (chars == null || chars.isEmpty())
            throw new IllegalArgumentException("Valid character set cannot be empty or null.");
        _validInputChars = chars;
        _n = _validInputChars.length();
    }

    private int codePointFromCharacter(char character)
    {
        return _validInputChars.indexOf(character);
    }

    private char characterFromCodePoint(int codePoint)
    {
        return _validInputChars.charAt(codePoint);
    }

    private int codePointTotal(String input, boolean withChecksum)
    {
        int factor = withChecksum ? 1 : 2;
        int sum = 0;

        // Starting from the right and working leftwards is easier since
        // the initial "factor" will always be "2"
        for (int i = input.length() - 1; i >= 0; i--) {
            int codePoint = codePointFromCharacter(input.charAt(i));
            if (codePoint < 0)
                throw new IllegalArgumentException("Input string '" + input + "' not from valid input character set '" + _validInputChars + "'");
            int addend = factor * codePoint;

            // Alternate the "factor" that each "codePoint" is multiplied by
            factor = (factor == 2) ? 1 : 2;

            // Sum the digits of the "addend" as expressed in base "n"
            addend = (addend / _n) + (addend % _n);
            sum += addend;
        }
        return sum;
    }

    /**
     * Given a string from the chosen alphabet, computes the checksum character
     * @param input the string for which the check character is being computed
     * @return the computed character
     * @throws IllegalArgumentException if the input string contains characters not from the chosen alphabet.
     */
    public char getValue(String input)
    {
        if (input.isEmpty())
            throw new IllegalArgumentException("Input string must contain at least one character.");

        int sum = codePointTotal(input, false);

        // Calculate the number that must be added to the "sum"
        // to make it divisible by "n"
        int remainder = sum % _n;
        int checkCodePoint = (_n - remainder) % _n;

        return characterFromCodePoint(checkCodePoint);
    }

    /**
     * Determines if the input string provided has a valid checksum character
     * @param input the string to be validated
     * @return true if the input string's checksum character is valid; false otherwise
     * @throws IllegalArgumentException if the input string contains characters not from the chosen alphabet.
     */
    public boolean isValid(String input)
    {
        return (!input.isEmpty() && codePointTotal(input, true) % _n == 0);
    }

    //
    //JUnit TestCase
    //
    @TestWhen(TestWhen.When.BVT)
    public static class TestCase extends Assert
    {
        @Test
        public void testGetValue()
        {
            String alphabet = "abcdefghijklmnop";
            ChecksumUtil util = new ChecksumUtil(alphabet);
            char value = util.getValue("lp");
            Assert.assertTrue("checksum character not in valid alphabet", alphabet.indexOf(value) >= 0);
            Assert.assertEquals("Checksum value not as expected", 'g', value);
        }

        @Test
        public void testGetValueEmptyString()
        {
            String alphabet = "12345";
            ChecksumUtil util = new ChecksumUtil(alphabet);
            try
            {
                util.getValue("");
                fail("Empty string as input should not be valid.");
            }
            catch (IllegalArgumentException e)
            {
                // Do nothing.  This is where we expect to be.
            }
        }

        @Test
        public void testIsValid()
        {
            String alphabet = "123XYZ";
            ChecksumUtil util = new ChecksumUtil(alphabet);
            String input = "XYX";
            Assert.assertTrue("Input with checksum appended is not valid", util.isValid(input + util.getValue(input)));
            Assert.assertFalse("Input with invalid checksum appended should not be valid", util.isValid(input + '2'));
        }

        @Test
        public void testNotIsValidEmptyString()
        {
            String alphabet = "AEIOU";
            ChecksumUtil util = new ChecksumUtil(alphabet);
            Assert.assertFalse("The empty string should not be seen as a valid checksum string", util.isValid(""));
        }

        @Test
        public void testGetValueBadInput()
        {
            String alphabet = "ABCLMN";
            ChecksumUtil util = new ChecksumUtil(alphabet);
            try
            {
                util.getValue("a");
                fail("Getting checksum value for a string outside of valid alphabet should throw an exeception.");
            }
            catch (IllegalArgumentException e)
            {
                // Do nothing.  This is where we should end up.
            }
        }

        @Test
        public void testIsValidBadInput()
        {
            String alphabet = "ABCLMN";
            ChecksumUtil util = new ChecksumUtil(alphabet);
            try
            {
                util.isValid("ab");
                fail("Getting checksum value for a string outside of valid alphabet should throw an exeception.");
            }
            catch (IllegalArgumentException e)
            {
                // Do nothing.  This is where we should end up.
            }
        }
    }
}
