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
