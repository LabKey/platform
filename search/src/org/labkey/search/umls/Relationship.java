/*
 * Copyright (c) 2010-2013 LabKey Corporation
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
package org.labkey.search.umls;

/**
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
