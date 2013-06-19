/*
 * Copyright (c) 2010-2011 LabKey Corporation
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

package gwt.client.org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ChangeListenerCollection;
import com.google.gwt.user.client.ui.SourcesChangeEvents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Dec 14, 2006
 * Time: 11:47:11 AM
 */
public class GWTStudyDefinition implements SourcesChangeEvents, IsSerializable
{
    private transient ChangeListenerCollection listeners = new ChangeListenerCollection();

    private String grant;
    private String investigator;
    private String studyName;
    private String animalSpecies;
    private int cavdStudyId;
    private int revision;
    private GWTImmunizationSchedule immunizationSchedule = new GWTImmunizationSchedule();
    private GWTAssaySchedule assaySchedule = new GWTAssaySchedule();
    private List<GWTCohort> groups = new ArrayList<GWTCohort>();
    private List<GWTAssayDefinition> assays = new ArrayList<GWTAssayDefinition>();
    private List<GWTImmunogen> immunogens = new ArrayList<GWTImmunogen>();
    private List<GWTAdjuvant> adjuvants = new ArrayList<GWTAdjuvant>();
    private List<GWTSampleType> sampleTypes = new ArrayList<GWTSampleType>(Arrays.asList(GWTSampleType.DEFAULTS));

    private String description;
    public static String[] immunogenTypes = {"Adenovirus-5", "Adenovirus-6", "Canarypox", "MVA", "Fowlpox", "NYVAC", "Vaccinia", "VEE", "AAV-2", "DNA", "Subunit Protein", "Subunit Peptide"};
    public static String[] genes = {"Gag", "Pol", "Nef", "Env", "Tat", "Rev"};
    public static String[] routes = {"Intramuscular (IM)", "Subcutaneous (SC)", "Intradermal (ID)", "Mucosal"};
    public static String[] subTypes = {"Clade A", "Clade B", "Clade C", "Clade D", "Circulating Type"};

    public GWTStudyDefinition()
    {
    }

    public void addChangeListener(ChangeListener listener)
    {
        listeners.add(listener);
    }

    public void removeChangeListener(ChangeListener listener)
    {
        listeners.remove(listener);
    }

    public void fireChangeEvents()
    {
        listeners.fireChange(null);
    }

    public static GWTStudyDefinition getDefaultTemplate()
    {
        GWTStudyDefinition study = new GWTStudyDefinition();
        //Initialize Assays
        List<GWTAssayDefinition> assays = study.getAssays();
        String[] labs = new String[] {"Lab 1", "Lab 2"};
        assays.add(new GWTAssayDefinition("ELISPOT", new String[] {"Schmitz"}, new GWTSampleMeasure(2, GWTSampleMeasure.Unit.ML, GWTSampleType.SERUM)));
        assays.add(new GWTAssayDefinition("Neutralizing Antibodies Panel 1", new String[] {"Montefiori", "Seaman"}, new GWTSampleMeasure(2, GWTSampleMeasure.Unit.ML, GWTSampleType.SERUM)));
        assays.add(new GWTAssayDefinition("ICS", new String[] {"McElrath", "Schmitz"}, new GWTSampleMeasure(2.0e6, GWTSampleMeasure.Unit.CELLS, GWTSampleType.PBMC)));
        assays.add(new GWTAssayDefinition("ELISA", labs, new GWTSampleMeasure(2.5e6, GWTSampleMeasure.Unit.CELLS, GWTSampleType.PBMC)));
        study.setAssays(assays);

        study.getGroups().add(new GWTCohort("Vaccine", "First Group", 30));
        study.getGroups().add(new GWTCohort("Placebo", "Second Group", 30));

        study.getAdjuvants().add(new GWTAdjuvant("Adjuvant1", null, null));
        study.getAdjuvants().add(new GWTAdjuvant("Adjuvant2", null, null));

        GWTImmunogen immunogen = new GWTImmunogen("Cp1", "1.5e10 Ad vg", "Canarypox", routes[0]);
        immunogen.getAntigens().add(new GWTAntigen("A1", genes[0], "Clade B", null, null));
        study.getImmunogens().add(immunogen);


        immunogen = new GWTImmunogen("gp120", "1.6e8 Ad vg", "Subunit Protein", routes[0]);
        immunogen.getAntigens().add(new GWTAntigen("Env", "Env", null, null, null));
        study.getImmunogens().add(immunogen);

        GWTTimepoint tp1 = new GWTTimepoint(null, 0, null);
        study.getImmunizationSchedule().addTimepoint(tp1);
        GWTTimepoint tp2 = new GWTTimepoint(null, 28, GWTTimepoint.DAYS);
        study.getImmunizationSchedule().addTimepoint(tp2);

        GWTImmunization immunization1 = new GWTImmunization();
        immunization1.adjuvants.add(study.getAdjuvants().get(0));
        immunization1.immunogens.add(study.getImmunogens().get(0));
        study.getImmunizationSchedule().setImmunization(study.getGroups().get(0), tp1, immunization1);

        GWTImmunization immunization2 = new GWTImmunization();
        immunization2.adjuvants.add(study.getAdjuvants().get(0));
        immunization2.immunogens.add(study.getImmunogens().get(1));
        study.getImmunizationSchedule().setImmunization(study.getGroups().get(0), tp2, immunization2);

        GWTImmunization immunization3 = new GWTImmunization();
        immunization3.adjuvants.add(study.getAdjuvants().get(0));
        study.getImmunizationSchedule().setImmunization(study.getGroups().get(1), tp1, immunization3);

        GWTImmunization immunization4 = new GWTImmunization();
        immunization4.adjuvants.add(study.getAdjuvants().get(0));
        study.getImmunizationSchedule().setImmunization(study.getGroups().get(1), tp2, immunization4);

        for (int i = 0; i < study.getAssays().size(); i++)
            study.getAssaySchedule().addAssay(study.getAssays().get(i));

        return study;
    }

//    public Document toXML()
//    {
//        Document doc = XMLParser.createDocument();
//        doc.appendChild(toElement(doc));
//        return doc;
//    }
//
//    public Element toElement(Document doc)
//    {
//        Element root = createTag(doc, "grant", grant, "investigator", investigator, "animalSpecies", animalSpecies);
//        Element immunogensElement = doc.createElement(new GWTImmunogen().pluralTagName());
//        for (int i = 0; i < immunogens.size(); i++)
//        {
//            GWTImmunogen immunogen = (GWTImmunogen) immunogens.get(i);
//            immunogensElement.appendChild(immunogen.toElement(doc));
//        }
//        root.appendChild(immunogensElement);
//        Element adjuvantsElement = doc.createElement(new GWTAdjuvant().pluralTagName());
//        for (int i = 0; i < adjuvants.size(); i++)
//        {
//            GWTAdjuvant adjuvant = (GWTAdjuvant) adjuvants.get(i);
//            adjuvantsElement.appendChild(adjuvant.toElement(doc));
//        }
//        root.appendChild(adjuvantsElement);
//
//        Element assaysElement = doc.createElement(new GWTAssayDefinition().pluralTagName());
//        for (int i = 0; i < assays.size(); i++)
//        {
//            GWTAssayDefinition a = (GWTAssayDefinition) assays.get(i);
//            assaysElement.appendChild(a.toElement(doc));
//        }
//        root.appendChild(assaysElement);
//        Element groupsElement = doc.createElement(new GWTCohort().pluralTagName());
//        for (int i = 0; i < groups.size(); i++)
//        {
//            GWTCohort g = (GWTCohort) groups.get(i);
//            groupsElement.appendChild(g.toElement(doc));
//        }
//        root.appendChild(groupsElement);
//        root.appendChild(immunizationSchedule.toElement(doc));
//        root.appendChild(assaySchedule.toElement(doc));
//
//        return root;
//    }
//
//    public GWTStudyDefinition(Document doc, int cavdStudyId, int revision)
//    {
//        Element elDef = doc.getDocumentElement();
//        //doc.normalize();
//        grant = elDef.getAttribute("grant");
//        investigator = elDef.getAttribute("investigator");
//        animalSpecies = elDef.getAttribute("animalSpecies");
//        this.cavdStudyId = cavdStudyId;
//        this.revision = revision;
//        Element immunogensElement = XMLUtils.getChildElement(elDef, new GWTImmunogen().pluralTagName());
//        NodeList nlImmunogens = immunogensElement.getChildNodes();
//        for (int i = 0; i < nlImmunogens.getLength(); i++)
//            immunogens.add(new GWTImmunogen((Element) nlImmunogens.item(i)));
//        Element adjuvantsElement = XMLUtils.getChildElement(elDef, new GWTAdjuvant().pluralTagName());
//        NodeList nlAdjuvants = adjuvantsElement.getChildNodes();
//        for (int i = 0; i < nlAdjuvants.getLength(); i++)
//            adjuvants.add(new GWTAdjuvant((Element) nlAdjuvants.item(i)));
//        Element assaysElement = XMLUtils.getChildElement(elDef, new GWTAssayDefinition().pluralTagName());
//        if (null != assaysElement)
//        {
//            NodeList nl = elDef.getElementsByTagName(new GWTAssayDefinition().tagName());
//            for (int i = 0; i < nl.getLength(); i++)
//            {
//                Element elAssay = (Element) nl.item(i);
//                assays.add(new GWTAssayDefinition(elAssay));
//            }
//        }
//        Element groupsElement = XMLUtils.getChildElement(elDef, new GWTCohort().pluralTagName());
//        if (null != groupsElement)
//        {
//            NodeList nl = elDef.getElementsByTagName(new GWTCohort().tagName());
//            for (int i = 0; i < nl.getLength(); i ++)
//            {
//                Element elCohort = (Element) nl.item(i);
//                groups.add(new GWTCohort(elCohort));
//            }
//        }
//        Element immunizationScheduleElement = XMLUtils.getChildElement(elDef, new GWTImmunizationSchedule().tagName());
//        immunizationSchedule = new GWTImmunizationSchedule(immunizationScheduleElement, this);
//        Element assayScheduleElement = XMLUtils.getChildElement(elDef, new GWTAssaySchedule().tagName());
//        assaySchedule = new GWTAssaySchedule(assayScheduleElement, assays);
//
//    }

    public boolean equals(GWTStudyDefinition d)
    {
        return isEqual(grant, d.grant) &&
                isEqual(investigator, d.investigator) &&
                isEqual(animalSpecies, animalSpecies) &&
                isEqual(assays, d.assays) &&
                isEqual(assaySchedule, d.assaySchedule) &&
                isEqual(immunizationSchedule, d.immunizationSchedule);
    }

    private boolean isEqual(Object o1, Object o2)
    {
        return o1 == o2 || (null != o1 && o1.equals(o2));
    }


    public GWTImmunizationSchedule getImmunizationSchedule()
    {
        return immunizationSchedule;
    }

    public void setImmunizationSchedule(GWTImmunizationSchedule immunizationSchedule)
    {
        this.immunizationSchedule = immunizationSchedule;
    }

    public GWTAssaySchedule getAssaySchedule()
    {
        return assaySchedule;
    }

    public void setAssaySchedule(GWTAssaySchedule assaySchedule)
    {
        this.assaySchedule = assaySchedule;
    }

    public List<GWTCohort> getGroups()
    {
        return groups;
    }

    public void setGroups(List<GWTCohort> groups)
    {
        this.groups = groups;
    }

    public List<GWTAssayDefinition> getAssays()
    {
        return assays;
    }

    public void setAssays(List<GWTAssayDefinition> assays)
    {
        this.assays = assays;
    }

    public List<GWTImmunogen> getImmunogens()
    {
        return immunogens;
    }

    public void setImmunogens(List<GWTImmunogen> immunogens)
    {
        this.immunogens = immunogens;
    }

    public List<GWTAdjuvant> getAdjuvants()
    {
        return adjuvants;
    }

    public void setAdjuvants(List<GWTAdjuvant> adjuvants)
    {
        this.adjuvants = adjuvants;
    }

    public String getGrant()
    {
        return grant;
    }

    public void setGrant(String grant)
    {
        this.grant = grant;
    }

    public String getInvestigator()
    {
        return investigator;
    }

    public void setInvestigator(String investigator)
    {
        this.investigator = investigator;
    }

    public String getStudyName()
    {
        return studyName;
    }

    public void setStudyName(String studyName)
    {
        this.studyName = studyName;
    }

    public String getAnimalSpecies()
    {
        return animalSpecies;
    }

    public void setAnimalSpecies(String animalSpecies)
    {
        this.animalSpecies = animalSpecies;
    }

    public int getCavdStudyId()
    {
        return cavdStudyId;
    }

    public void setCavdStudyId(int cavdStudyId)
    {
        this.cavdStudyId = cavdStudyId;
    }

    public int getRevision()
    {
        return revision;
    }

    public void setRevision(int revision)
    {
        this.revision = revision;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public String getDescription()
    {
        return description;
    }

    public List<GWTSampleType> getSampleTypes()
    {
        if (null == sampleTypes)
            sampleTypes = new ArrayList<GWTSampleType>(Arrays.asList(GWTSampleType.DEFAULTS));
        
        return sampleTypes;
    }

    public void setSampleTypes(List<GWTSampleType> sampleTypes)
    {
        this.sampleTypes = sampleTypes;
    }
}
