package org.labkey.api.exp.xar;

import java.util.Map;
import java.util.HashMap;

/**
 * User: jeckels
 * Date: Jan 19, 2006
 */
public class MapReplacer implements Replacer
{
    private final Map<String, String> _replacements;

    public MapReplacer()
    {
        _replacements = new HashMap<String, String>();
    }

    public MapReplacer(Map<String, String> replacements)
    {
        _replacements = new HashMap<String, String>(replacements);
    }

    public String getReplacement(String original)
    {
        return _replacements.get(original);
    }

    public void addReplacement(String original, String replacement)
    {
        _replacements.put(original, replacement);
    }
}
