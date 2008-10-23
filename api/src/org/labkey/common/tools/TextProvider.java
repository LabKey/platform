/*
 * Copyright (c) 2006-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;

import java.util.*;

/**
 * Provides Java access to translated text in resource bundles
 */
public class TextProvider
{
    //singleton instance, so that accesses of _textBundle from multiple threads don't block
    protected static ResourceBundle _textBundle = null;

    //default values for the resourcebundle package and name
    protected static String _bundlePackage = "org.fhcrc.cpl.localization";
    protected static String _bundleName = "MSInspectText";

    //String that offsets a token from the rest of a text string
    public static final String TOKEN_INDICATOR="##";

    public TextProvider()
    {
        reloadTextBundle();
    }

    /**
     * load the ResourceBundle pointed to by the current bundle package and name,
     * using the default locale
     */
    public static void reloadTextBundle()
    {
         _textBundle = ResourceBundle.getBundle(_bundlePackage + "." +
                                                _bundleName);
    }


    /**
     * Return the appropriate string for the given text code
     * @param textCode
     * @return the appropriate string for the given text code, from the currently loaded
     * resource bundle.  If the string is not found, returns the text code itself
     */
    public static String getText(String textCode)
    {
         return getText(textCode,(HashMap) null);
    }

    /**
     * Convenience method.  Finds the token in the text string, Creates the appropriate HashMap with one
     * name-value pair, returns the appropriate string for the given text code, making substitutions
     * @param textCode
     * @param tokenValue
     * @return the appropriate string for the given text code, from the currently loaded
     * resource bundle, with any necessary token substitutions.  If the string is not found, returns the
     * text code itself
     */
    public static String getText(String textCode, String tokenValue)
    {
        String textStringNoSubs = getText(textCode);
        int tokenIndex = textStringNoSubs.indexOf(TOKEN_INDICATOR);
        if (tokenIndex == -1)
            return textStringNoSubs;
        String remainder = textStringNoSubs.substring(tokenIndex+TOKEN_INDICATOR.length());
        tokenIndex = remainder.indexOf(TOKEN_INDICATOR);
        if (tokenIndex == -1)
            return textStringNoSubs;
        String tokenName = remainder.substring(0,tokenIndex);
        return getText(textCode,tokenName,tokenValue);
    }

    /**
     * Convenience method.  Creates the appropriate HashMap of tokens, returns the appropriate string for
     * the given text code, making substitutions for three tokens
     * @param textCode
     * @param tokenName1
     * @param tokenValue1
     * @param tokenName2
     * @param tokenValue2
     * @param tokenName3
     * @param tokenValue3
     * @return the appropriate string for the given text code, from the currently loaded
     * resource bundle, with any necessary token substitutions.  If the string is not found, returns the
     * text code itself
     */
    public static String getText(String textCode, String tokenName1, String tokenValue1,
                                 String tokenName2, String tokenValue2,
                                 String tokenName3, String tokenValue3)
    {
        HashMap tokenMap = new HashMap(1);
        tokenMap.put(tokenName1,tokenValue1);
        tokenMap.put(tokenName2,tokenValue2);
        tokenMap.put(tokenName3,tokenValue3);
        return getText(textCode, tokenMap);
    }


    /**
     * Convenience method.  Creates the appropriate HashMap of tokens, returns the appropriate string for
     * the given text code, making substitutions for two tokens
     * @param textCode
     * @param tokenName1
     * @param tokenValue1
     * @param tokenName2
     * @param tokenValue2
     * @return the appropriate string for the given text code, from the currently loaded
     * resource bundle, with any necessary token substitutions.  If the string is not found, returns the
     * text code itself
     */
    public static String getText(String textCode, String tokenName1, String tokenValue1,
                                 String tokenName2, String tokenValue2)
    {
        HashMap tokenMap = new HashMap(1);
        tokenMap.put(tokenName1,tokenValue1);
        tokenMap.put(tokenName2,tokenValue2);
        return getText(textCode, tokenMap);
    }

    /**
     * Convenience method.  Creates the appropriate HashMap of tokens, returns the appropriate string for
     * the given text code, making substitutions for one token
     * @param textCode
     * @param tokenName
     * @param tokenValue
     * @return the appropriate string for the given text code, from the currently loaded
     * resource bundle, with any necessary token substitutions.  If the string is not found, returns the
     * text code itself
     */
    public static String getText(String textCode, String tokenName, String tokenValue)
    {
        HashMap tokenMap = new HashMap(1);
        tokenMap.put(tokenName,tokenValue);
        return getText(textCode, tokenMap);
    }

    /**
     * return the appropriate string for the given text code, making any necessary token substitutions
     * @param textCode
     * @param tokens
     * @return the appropriate string for the given text code, from the currently loaded
     * resource bundle, with any necessary token substitutions.  If the string is not found, returns the
     * text code itself
     */
    public static String getText(String textCode, HashMap tokens)
    {
        String postFix = "";
        String result = textCode;
        try
        {
            if (_textBundle == null)
              reloadTextBundle();
            result = _textBundle.getString(textCode);
            if (result == null)
            {
                if (textCode.endsWith("_DOTDOTDOT"))
                {
                    String newTextCode = textCode.substring(0, textCode.lastIndexOf("_DOTDOTDOT"));
                    result = _textBundle.getString(newTextCode);
                    if (result == null)
                        result = textCode;
                    else
                        postFix = "...";
                }
                else if (textCode.endsWith("_COLON"))
                {
                    String newTextCode = textCode.substring(0, textCode.lastIndexOf("_COLON"));
                    result = _textBundle.getString(newTextCode);
                    if (result == null)
                        result = textCode;
                    else
                        postFix = ":";
                }
            }
            if (tokens != null)
                result = substituteTokens(result,tokens);
        }
        catch (Exception e)
        {
            result = textCode;
        }
        return result;
    }

    /**
     * Make all token substitutions
     * @param inputText
     * @param tokens
     * @return the input string with all tokens substituted
     */
    protected static String substituteTokens(String inputText, HashMap tokens)
    {
        String result = inputText;

        Set keys = tokens.keySet();
        Iterator iter = keys.iterator();

        while (iter.hasNext())
        {
            String currentToken = (String) iter.next();
            result = substituteToken(result,currentToken,(String) tokens.get(currentToken));
        }
        return result;
    }

    /**
     * Make all necessary substitutions for one token
     * @param inputText
     * @param tokenName
     * @param tokenValue
     * @return the input string with the token substituted
     */
    protected static String substituteToken(String inputText, String tokenName, String tokenValue)
    {
        String outputText = new String();
        String fullToken = TOKEN_INDICATOR + tokenName + TOKEN_INDICATOR;
        String[] stringPieces = inputText.split(fullToken);

        //token substitution in the beginning and middle
        for (int i=0; i<stringPieces.length-1;i++)
        {

             outputText = outputText + stringPieces[i] + tokenValue;
        }
        outputText = outputText + stringPieces[stringPieces.length-1];

        //token substitution at the end
        if (inputText.endsWith(fullToken))
          outputText = outputText + tokenValue;

        return outputText;
    }


    public static String getBundlePackage()
    {
        return _bundlePackage;
    }

    /**
     * Set the package name to look in for the text bundle
     * @param bundlePackage
     */
    public static void setBundlePackage(String bundlePackage)
    {
        _bundlePackage = bundlePackage;
    }

    public static String getBundleName()
    {
        return _bundleName;
    }

    /**
     * Set the resource bundle name to use, within the package _bundlePackage
     */
    public void setBundleName(String bundleName)
    {
        _bundleName = bundleName;
    }
}
