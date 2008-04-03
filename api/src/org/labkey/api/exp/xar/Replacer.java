package org.labkey.api.exp.xar;

import org.labkey.api.exp.XarFormatException;

/**
 * User: jeckels
 * Date: Jan 13, 2006
 */
public interface Replacer
{
    public String getReplacement(String original) throws XarFormatException;

    public static class CompoundReplacer implements Replacer
    {
        private final Replacer _replacer1;
        private final Replacer _replacer2;

        public CompoundReplacer(Replacer replacer1, Replacer replacer2)
        {
            _replacer1 = replacer1;
            _replacer2 = replacer2;
        }

        public String getReplacement(String original) throws XarFormatException
        {
            String result = _replacer1.getReplacement(original);
            if (result != null)
            {
                return result;
            }
            return _replacer2.getReplacement(original);
        }
    }
}
