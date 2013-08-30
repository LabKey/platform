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

package gwt.client.org.labkey.study.designer.client.model;

import com.google.gwt.user.client.rpc.IsSerializable;
import com.google.gwt.user.client.ui.ChangeListener;
import com.google.gwt.user.client.ui.ChangeListenerCollection;
import com.google.gwt.user.client.ui.SourcesChangeEvents;

import java.util.ArrayList;
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
    private List<String> groupsToDelete = new ArrayList<String>();
    private List<GWTImmunogen> immunogens = new ArrayList<GWTImmunogen>();
    private List<GWTAdjuvant> adjuvants = new ArrayList<GWTAdjuvant>();

    private List<String> assays = new ArrayList<String>();
    private List<String> labs = new ArrayList<String>();
    private List<String> sampleTypes = new ArrayList<String>();
    private List<String> units = new ArrayList<String>();
    private List<String> immunogenTypes = new ArrayList<String>();
    private List<String> genes = new ArrayList<String>();
    private List<String> routes = new ArrayList<String>();
    private List<String> subTypes = new ArrayList<String>();
    private List<String> cohorts = new ArrayList<String>();

    private String description;

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
        return study;
    }

    public static GWTStudyDefinition getDefaultTemplateWithValues()
    {
        GWTStudyDefinition study = new GWTStudyDefinition();

        List<String> assays = study.getAssays();
        assays.add("ELISPOT");
        assays.add("Neutralizing Antibodies Panel 1");
        assays.add("ICS");
        assays.add("ELISA");
        study.setAssays(assays);

        List<String> labs = study.getLabs();
        labs.add("Lab 1");
        labs.add("Lab 2");
        study.setLabs(labs);

        List<String> units = study.getUnits();
        units.add("ml");
        units.add("ul");
        units.add("cells");
        study.setUnits(units);

        List<String> sampleTypes = study.getSampleTypes();
        sampleTypes.add("Plasma");
        sampleTypes.add("Serum");
        sampleTypes.add("PBMC");
        sampleTypes.add("Vaginal Mucosal");
        sampleTypes.add("Nasal Mucosal");
        study.setSampleTypes(sampleTypes);

        study.getGroups().add(new GWTCohort("Vaccine", "First Group", 30, null));
        study.getGroups().add(new GWTCohort("Placebo", "Second Group", 30, null));

        study.getAdjuvants().add(new GWTAdjuvant("Adjuvant1", null, null));
        study.getAdjuvants().add(new GWTAdjuvant("Adjuvant2", null, null));

        GWTImmunogen immunogen = new GWTImmunogen("Cp1", "1.5e10 Ad vg", "Canarypox", "Intramuscular (IM)");
        immunogen.getAntigens().add(new GWTAntigen("A1", "Gag", "Clade B", null, null));
        study.getImmunogens().add(immunogen);

        immunogen = new GWTImmunogen("gp120", "1.6e8 Ad vg", "Subunit Protein", "Intramuscular (IM)");
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
        {
            GWTSampleMeasure measure = new GWTSampleMeasure(2, study.getUnits().get(0), study.getSampleTypes().get(0));
            GWTAssayDefinition assayDef =  new GWTAssayDefinition(study.getAssays().get(i), "Lab 1");
            study.getAssaySchedule().addAssay(assayDef);
        }

        return study;
    }

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

    public List<String> getAssays()
    {
        return assays;
    }

    public void setAssays(List<String> assays)
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

    public List<String> getSampleTypes()
    {
        return sampleTypes;
    }

    public void setSampleTypes(List<String> sampleTypes)
    {
        this.sampleTypes = sampleTypes;
    }

    public List<String> getImmunogenTypes()
    {
        return immunogenTypes;
    }

    public void setImmunogenTypes(List<String> immunogenTypes)
    {
        this.immunogenTypes = immunogenTypes;
    }

    public List<String> getGenes()
    {
        return genes;
    }

    public void setGenes(List<String> genes)
    {
        this.genes = genes;
    }

    public List<String> getRoutes()
    {
        return routes;
    }

    public void setRoutes(List<String> routes)
    {
        this.routes = routes;
    }

    public List<String> getSubTypes()
    {
        return subTypes;
    }

    public void setSubTypes(List<String> subTypes)
    {
        this.subTypes = subTypes;
    }

    public List<String> getLabs()
    {
        return labs;
    }

    public void setLabs(List<String> labs)
    {
        this.labs = labs;
    }

    public List<String> getUnits()
    {
        return units;
    }

    public void setUnits(List<String> units)
    {
        this.units = units;
    }

    public List<String> getGroupsToDelete()
    {
        return groupsToDelete;
    }

    public void clearGroupsToDelete()
    {
        this.groupsToDelete = new ArrayList<String>();
    }

    public void addGroupToDelete(String groupName)
    {
        this.groupsToDelete.add(groupName);
    }

    public List<String> getCohorts()
    {
        return cohorts;
    }

    public void setCohorts(List<String> cohorts)
    {
        this.cohorts = cohorts;
    }
}
