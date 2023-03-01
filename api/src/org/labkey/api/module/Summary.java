package org.labkey.api.module;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;

/**
 * Used to represent concepts such as "2 Molecules" and "1 Notebook". Effort has been taken to maintain accuracy in
 * pluralization. Some given noun names are replaced by more reader-friendly versions.
 */
public class Summary
{
    private final int count;
    private final String nounSingular;
    private final String nounPlural;

    private String replaceNounTitles(String nounSingular) {
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
        return StringUtils.replaceEach(nounSingular, search, replacements);
    }

    public Summary(final int count, final String nounSingular, final String nounPlural)
    {
        this.count = count;
        this.nounSingular = nounSingular;
        this.nounPlural = nounPlural;
    }

    public Summary(final int count, final String nounSingular)
    {
        String nounName = replaceNounTitles(nounSingular);

        this.count = count;
        this.nounSingular = nounName;
        this.nounPlural = nounName.endsWith("s") ? nounName : nounName + "s";
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("count", count);
        json.put("noun", count > 1 ? nounPlural : nounSingular);
        return json;
    }
}
