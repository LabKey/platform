package org.labkey.search.umls;

/**
* Created by IntelliJ IDEA.
* User: matthewb
* Date: Mar 10, 2010
* Time: 10:47:35 AM
*/
public enum Relationship
{
    AQ("AQ", "Allowed qualifier"),
    CHD("CHD", "has child relationship in a Metathesaurus source vocabulary"),
    DEL("DEL", "Deleted concept."),
    PAR("PAR", "has parent relationship in a Metathesaurus source vocabulary"),
    QC("QB", "can be qualified by"),
    RB("RB", "has a broader relationship"),
    RL("RL", "is similar"),
    RN("RN", "has a narrower relationship"),
    RO("RO", "has relationship other than synonymous, narrower, or broader"),
    RQ("RQ", "related and possibly synonymous"),
    RU("RU", "Related, unspecified"),
    SIB("SIB", "has sibling relationship in a Metathesaurus source vocabulary"),
    SUBX("SUBX", "Concept removed from current subset"),
    SY("SY", "source asserted synonymy"),
    XR("XR", "Not related, no mapping");

    public String code;
    public String description;
    public Relationship inverse;

    Relationship(String code, String description)
    {
        this.code = code;
        this.description = description;
    }

    static
    {
        CHD.inverse = PAR;
        PAR.inverse = CHD;
        SIB.inverse = SIB;
        RN.inverse = RB;
        RB.inverse = RN;
    }
}
