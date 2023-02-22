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
        String[] search = new String[]{"NucSequence", "ProtSequence", "Moleculeset", "RawMaterials", "Ingredients", "Mixtures"};
        String[] replacements = new String[]{"Nucleotide sequence", "Protein sequence", "Molecule set", "Raw material", "Ingredient", "Mixture"};

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
