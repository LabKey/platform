package org.labkey.api.module;

import org.json.JSONObject;

public class Summary
{
    private final int count;
    private final String nounSingular;
    private final String nounPlural;

    public Summary(final int count, final String nounSingular, final String nounPlural)
    {
        this.count = count;
        this.nounSingular = nounSingular;
        this.nounPlural = nounPlural;
    }

    public Summary(final int count, final String nounSingular)
    {
        this.count = count;
        this.nounSingular = nounSingular;
        this.nounPlural = nounSingular + "s";
    }

    public JSONObject toJSON()
    {
        JSONObject json = new JSONObject();
        json.put("count", count);
        json.put("noun", count > 1 ? nounPlural : nounSingular);
        return json;
    }
}
