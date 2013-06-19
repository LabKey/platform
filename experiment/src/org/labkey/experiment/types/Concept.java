/*
 * Copyright (c) 2005-2013 LabKey Corporation
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

package org.labkey.experiment.types;

import org.apache.commons.lang3.StringUtils;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.data.ContainerManager;

import java.util.HashSet;

/**
 * User: mbellew
 * Date: Nov 16, 2005
 * Time: 9:37:30 AM
 *
 * Bean class used for importing vocabulary
 */
public class Concept
{
    private String _code;
    private String _name;
    private String _concept;
    private String _synonyms;
    private String _description;
    private String _semanticType;
    private String _umls;
    private String _rangeURI;
    private String _label;

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        this._description = StringUtils.trimToNull(description);
    }

    public String getSynonyms()
    {
        return _synonyms;
    }

    public void setSynonyms(String synonyms)
    {
        this._synonyms = StringUtils.trimToNull(synonyms);
    }

    public String getConcept()
    {
        return _concept;
    }

    public void setConcept(String concept)
    {
        this._concept = StringUtils.trimToNull(concept);
    }

	public String getParent()
	{
		return _concept;
	}

	public void setParent(String concept)
	{
		this._concept = StringUtils.trimToNull(concept);
	}

    public String getName()
    {
        return _name;
    }

    public void setName(String name)
    {
        this._name = StringUtils.trimToNull(name);
    }

    public String getCode()
    {
        return _code;
    }

    public void setCode(String code)
    {
        this._code = StringUtils.trimToNull(code);
    }


    static HashSet<String> excludedTerms = new HashSet<>();

    static
    {
        // just exclude all 1 and 2 letter words
        excludedTerms.add("and");
        excludedTerms.add("the");
        excludedTerms.add("for");
        excludedTerms.add("with");
    }

    PropertyDescriptor toPropertyDescriptor(String prefix)
    {
        PropertyDescriptor pd = new PropertyDescriptor();

        // Name
        pd.setName(this._name);

        // PropertyURI
        String propertyURI = prefix + '#' + _name;
        pd.setPropertyURI(propertyURI);

        // ConceptURI
        if (null != _concept && !"root_node".equals(_concept))
        {
            String concept = _concept;
            int len = concept.indexOf('|');
            pd.setConceptURI(prefix + '#' + (len == -1 ? concept : concept.substring(0, len)));
        }

        // Description
        pd.setDescription(_description);

        // Label
        if (null != _label || null != _synonyms)
        {
            String label = _label;
            if (label == null)
			{
				int len = _synonyms.indexOf('|');
				label = len == -1 ? _synonyms : _synonyms.substring(0, len);
			}
            pd.setLabel(label);
        }

        // SearchTerms
        if (null != _synonyms || null != _concept)
        {
            String words = _name + " " + StringUtils.trimToEmpty(_synonyms) + " " + StringUtils.trimToEmpty(_concept);
            words = words.toLowerCase();
            String[] terms = words.toLowerCase().split("[\\|\\_, ]");
            StringBuffer searchTerms = new StringBuffer();
            HashSet<String> set = new HashSet<>();
            for (String term : terms)
            {
                term = StringUtils.trimToEmpty(term);
                if (term.length() < 3 || excludedTerms.contains(term))
                    continue;
                if (set.add(term))
                {
                    searchTerms.append('|');
                    searchTerms.append(term);
                }
            }
            if (searchTerms.length() > 999)
                searchTerms.setLength(999);
            searchTerms.append('|');
            
            pd.setSearchTerms(searchTerms.toString());
        }

        // RangeURI
        String range = _rangeURI;
        if (null == range || 0 == range.length())
        	range = "xsd:nil";
        pd.setRangeURI(range);

        // SemanticType
        if (null != _semanticType)
		{
			String types = _semanticType;
			if (!types.startsWith("|"))
				types = "|" + types;
			if (!types.endsWith("|"))
				types = types + "|";
			pd.setSemanticType(types);
		}
        // Concepts are global only
        pd.setContainer(ContainerManager.getSharedContainer());

        return pd;
    }

    public String getSemanticType()
    {
        return _semanticType;
    }

    public void setSemanticType(String semanticType)
    {
        this._semanticType = semanticType;
    }

    public String getUmls()
    {
        return _umls;
    }

    public void setUmls(String umls)
    {
        this._umls = umls;
    }

	public String getRangeURI()
	{
		return _rangeURI;
	}

	public void setRangeURI(String _rangeURI)
	{
		this._rangeURI = _rangeURI;
	}

	public String getLabel()
	{
		return _label;
	}

	public void setLabel(String label)
	{
		this._label = label;
	}
}