/*
 * Copyright (c) 2023-2026 LabKey Corporation
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
package org.labkey.api.module;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * Used to represent concepts such as "2 Molecules" and "1 Notebook". Effort has been taken to maintain accuracy in
 * pluralization. Some given noun names are replaced by more reader-friendly versions.
 */
public class Summary
{
    private final long count;
    private final String noun;

    private String replaceNounTitles(String noun) {
        String[] search = new String[] {
                "ExpressionSystem",
                "Ingredients",
                "Mixtures",
                "MolecularSpecies",
                "MoleculeSet",
                "NucSequence",
                "ProtSequence",
                "RawMaterials"
        };
        String[] replacements = new String[] {
                "Expression system",
                "Ingredient",
                "Mixture",
                "Molecular species",
                "Molecule set",
                "Nucleotide sequence",
                "Protein sequence",
                "Raw material"
        };
        return StringUtils.replaceEach(noun, search, replacements);
    }

    public Summary(final long count, final String noun)
    {
        String nounName = replaceNounTitles(noun);

        this.count = count;
        this.noun = nounName;
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("count", count);
        json.put("noun", noun);
        return json;
    }
}
