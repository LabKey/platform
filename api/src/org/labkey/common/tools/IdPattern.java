/*
 * Copyright (c) 2007-2008 LabKey Corporation
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * User: peter@labkey.com
 * Date: Nov 18, 2007
 * Time: 11:52:56 PM
 *
 * this class implements a regular expression-based recognition of identifiers parsed from the fasta files.
 *
 *
 */
public class IdPattern
{
    private String _typeName;
    private Pattern _pattern;
    private String _strReplace;
    private String _typeAfter;
    private SortedMap<Integer, Pattern> _mapReplPatterns =null;

    // poplulate the map of id patterns
    public static final Map<String,IdPattern> ID_PATTERN_MAP = new HashMap<String,IdPattern>();
    public static final List<String> UNTYPED_ID_PATTERN_LIST = new ArrayList<String>();
    public static final List<String> TYPED_ID_PATTERN_LIST = new ArrayList<String>();
    public static final List<String> WHOLE_HEADER_ID_PATTERN_LIST = new ArrayList<String>();
    static
    {
        addUntypedIdPattern("IPI", "IPI..*");
        addUntypedIdPattern("COG", "COG[0-9][0-9][0-9][0-9][0-9]");
        addUntypedIdPattern("SwissProt", "[A-Z,0-9]{3,6}_[A-Z,0-9]{3,5}");
        addUntypedIdPattern("SwissProtAccn", "[A-Z][0-9][A-Z,0-9][A-Z,0-9][A-Z,0-9][0-9]", null, "SwissProt");
        addUntypedIdPattern("UniRef100", "UniRef100_([A-Z][0-9][A-Z,0-9][A-Z,0-9][A-Z,0-9][0-9])", "$1", null);

        //TypedID patterns may still need tranformation
        addTypedIdPattern("SI", "([A-Z,0-9]+)_.*", "$1");

        addWholeHeaderIdPattern("ENSEMBL", "^([YRQ][-A-Z,0-9]{4,9})[ ][A-Z]{1}[A-Z,0-9,-]{3,9}[ ]SGDID:[S][0-9]{9}.*","$1");
        // According to Phil, SGD gene names may have a trailing letter or a trailing dash and letter
        addWholeHeaderIdPattern("SGD_GN", "^[YRQ][-A-Z,0-9]{4,9}[ ]([A-Z]{3}[0-9]+-?[A-Z]?)[ ]SGDID:[S][0-9]{9}.*","$1");
        addWholeHeaderIdPattern("SGDID", "^[YRQ][-A-Z,0-9]{4,9}[ ][A-Z]{3}[0-9]+-?[A-Z]?[ ]SGDID:([S][0-9]{9}).*","$1");
        addWholeHeaderIdPattern("GN", ".*Gene_Symbol=([^ ]*).*","$1");

    }

    /**
     * This object handles "unqualified" tokens in the fasta header line; ie those that aren't prefaced
     * with a <identtype>| that is in the Protein.IdentTypeMap.  It could also be used to verify
     * qualified token values, but it would need to handle semi-colon delimited id sets and also handle
     * two-part tokens differntly.
     *
     * @param type:  the type name for the identifier, from the set of values of the Protein.IdentTypeMap
     * @param match  a regular expression which matches identifiers of that type
     * @param replace an optional replacement string for use when the identifer needs to be extracted or built up
     *              from the string being tested.  Can use regular expression capture groups $0 through $9, where
     *              $0 matches the entire identifier token
     * @param following  an optional type name of an identifier that normally follows this type of identifer.  Used
     *                  for example for Swissprot syntax of <accessionId>|<sprot_name>
     *
     * @throws PatternSyntaxException
     */
    public IdPattern(String type, String match, String replace, String following) throws PatternSyntaxException
    {
        _typeName = type;
        _pattern = Pattern.compile(match);
        _strReplace = replace;
        _typeAfter = following;
        if (null != _strReplace)
        {
            _mapReplPatterns = new TreeMap<Integer,Pattern>();
            int c=0;
            while (c <= 9)
            {
                String exp = "\\$" + c;
                Pattern r = Pattern.compile(exp);
                if (c>0 && !r.matcher(_strReplace).find())
                    break;

                _mapReplPatterns.put(c, r);
                c++;
            }
        }
    }

    public Map<String, Set<String>> getIdFromPattern(String[] tokens, int idx)
    {
        String idValue=tokens[idx];
        Map<String, Set<String>> idAfter=null;

        Matcher matcher = _pattern.matcher(idValue);
        if (!matcher.matches())
            return null;

        if ((null!= _typeAfter) && (tokens.length-1 > idx))
        {
            idAfter = ID_PATTERN_MAP.get(_typeAfter).getIdFromPattern(tokens, idx + 1);
            if (null==idAfter)
                return null;
        }

        if (null!= _mapReplPatterns)
        {
            idValue = _strReplace;
            for (Integer ref : _mapReplPatterns.keySet())
            {

                Matcher matchRepl = _mapReplPatterns.get(ref).matcher(idValue);
                if (ref <= matcher.groupCount())
                    idValue = matchRepl.replaceAll(matcher.group(ref));
            }
        }
        return addIdMap(createIdMap(_typeName, idValue), idAfter);
    }

    /**
     * Adds the contents of one identifer map to another
     *
     * @param mapExisting an identifier map to be added to
     * @param mapNew an identifier map, may be null or empty
     * @return always returns a map, but may be empty.
     */
    public static Map<String, Set<String>> addIdMap(Map<String, Set<String>> mapExisting, Map<String, Set<String>> mapNew)
    {
        if (null != mapNew)
        {
            if (null == mapExisting || mapExisting.size()==0)
                return mapNew;

            for (String key : mapNew.keySet())
            {
                if (mapExisting.containsKey(key))
                {
                    Set<String> vals = mapExisting.get(key);
                    vals.addAll(mapNew.get(key));
                }
                else
                    mapExisting.put(key, mapNew.get(key));
            }
        }
        return mapExisting;
    }

    /**
     * Method to create an identifier map from a key, value pair.  Value can be
     * semicolon dilimited.
     *
     * @param key  type of the identifier, from the IdentMap for the type abbreviation key
     * @param value the value of the identifier, can be a semi-colon divided list
     * @return an idMapStructure.  does not return null, returns an empty map if val is null or blank.
     */
    public static Map<String, Set<String>> createIdMap(String key, String value)
    {
        Map<String, Set<String>> idMap = new HashMap<String, Set<String>>();
        Set<String> vals = new HashSet<String>();
        if (null!=value)
        {
            String[] valArray = value.split(";");
            for (String v : valArray)
            {
                v = v.trim();
                if (v.length() > 50)
                    v = v.substring(0, 50);
                if (v.length()>0)
                    vals.add(v);
            }
        if (vals.size() > 0)
            idMap.put(key, vals);
        }
        return idMap;
    }

    private static void addUntypedIdPattern(String typeName, String pattern)
    {
        addUntypedIdPattern(typeName, pattern, null, null);
    }

    private static void addUntypedIdPattern(String typeName, String pattern, String replace, String typeAfter)
    {
        addIdPattern(typeName, pattern, replace, typeAfter);
        UNTYPED_ID_PATTERN_LIST.add(typeName);
    }

    private static void addTypedIdPattern(String typeName, String pattern, String replace)
    {
        addIdPattern(typeName, pattern, replace, null);
        TYPED_ID_PATTERN_LIST.add(typeName);
    }

    private static void addWholeHeaderIdPattern(String typeName, String pattern, String replace)
    {
        addIdPattern(typeName, pattern, replace, null);
        WHOLE_HEADER_ID_PATTERN_LIST.add(typeName);
    }

    private static void addIdPattern(String typeName, String match, String replace, String typeAfter)
    {
        try
        {
            IdPattern idPattern = new IdPattern(typeName, match, replace, typeAfter);
            ID_PATTERN_MAP.put(typeName, idPattern);
        }
        catch (PatternSyntaxException e)
        {
            throw new RuntimeException(e);
        }
    }
}
