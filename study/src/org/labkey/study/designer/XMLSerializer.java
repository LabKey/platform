/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.study.designer;

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.study.StudySchema;
import org.labkey.study.model.CohortImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.xml.*;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Mark Igra
 * Date: Feb 14, 2007
 * Time: 10:30:36 PM
 */
public class XMLSerializer
{
    private static Logger _log = Logger.getLogger(XMLSerializer.class);

    public static GWTStudyDefinition fromXML(String xml, User user, Container c)
    {
        return fromXML(xml, null, user, c);
    }

    public static GWTStudyDefinition fromXML(String xml, GWTStudyDefinition template, User user, Container c)
    {

        StudyDesignDocument doc;
        try
        {
            doc = StudyDesignDocument.Factory.parse(xml);
        } catch (XmlException e)
        {
            _log.error(e);
            throw new RuntimeException(e);
        }
        
        StudyDesign xdesign = doc.getStudyDesign();
        assert validate(doc);
        
        GWTStudyDefinition def = new GWTStudyDefinition();

        // Set the initial lookup values based on the Container/Project study design lookup tables
        def.setImmunogenTypes(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignImmunogenTypes()));
        def.setGenes(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignGenes()));
        def.setRoutes(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignRoutes()));
        def.setSubTypes(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignSubTypes()));
        def.setSampleTypes(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignSampleTypes()));
        def.setUnits(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignUnits()));
        def.setAssays(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignAssays()));
        def.setLabs(StudyDesignManager.get().getStudyDesignLookupValues(user, c, StudySchema.getInstance().getTableInfoStudyDesignLabs()));

        // Set the cohort choices based on the current study configuration
        if (StudyManager.getInstance().showCohorts(c, user))
            def.setCohorts(StudyDesignManager.get().getStudyCohorts(user, c));

        // set the study top level properties based on the saved info
        def.setStudyName(xdesign.getName());
        def.setGrant(xdesign.getGrantName());
        def.setInvestigator(xdesign.getInvestigator());
        def.setAnimalSpecies(xdesign.getAnimalSpecies());
        if (xdesign.isSetDescription())
            def.setDescription(xdesign.getDescription());

        List<GWTImmunogen> immunogens = new ArrayList<>();
        for (Immunogen immunogen : xdesign.getImmunogens().getImmunogenArray())
        {
            GWTImmunogen gimmunogen = new GWTImmunogen(immunogen.getName(), immunogen.getDose(), immunogen.getType(), immunogen.getAdmin());
            List<GWTAntigen> antigens = new ArrayList<>();
            for (Antigen antigen : immunogen.getAntigens().getAntigenArray())
                antigens.add(new GWTAntigen(antigen.getName(), antigen.getGene(), antigen.getSubtype(), antigen.getGenBankId(), antigen.getSequence()));
            gimmunogen.setAntigens(antigens);
            immunogens.add(gimmunogen);
        }
        def.setImmunogens(immunogens);

        List<GWTAdjuvant> adjuvants = new ArrayList<>();
        for (Adjuvant adjuvant : xdesign.getAdjuvants().getAdjuvantArray())
            adjuvants.add(new GWTAdjuvant(adjuvant.getName(), adjuvant.getDose(), adjuvant.getAdmin()));
        def.setAdjuvants(adjuvants);

        //TODO: XML for cohort descriptions
        for (Cohort cohort : xdesign.getCohorts().getCohortArray())
        {
            GWTCohort gwtCohort = new GWTCohort(cohort.getName(), null, cohort.getCount(), null);
            if (StudyManager.getInstance().showCohorts(c, user))
            {
                CohortImpl existingCohort = StudyManager.getInstance().getCohortByLabel(c, user, cohort.getName());
                if (existingCohort != null)
                    gwtCohort.setCohortId(existingCohort.getRowId());
            }

            def.getGroups().add(gwtCohort);
        }

        GWTAssaySchedule gAssaySchedule = new GWTAssaySchedule();
        AssaySchedule xAssaySchedule = xdesign.getAssaySchedule();
        if (null != xAssaySchedule.getDescription())
            gAssaySchedule.setDescription(xAssaySchedule.getDescription());

        AssaySchedule.Assays assays = xAssaySchedule.getAssays();
        if (null != assays)
        {
            for (AssayRef ref : assays.getAssayRefArray())
            {
                GWTAssayDefinition gad = new GWTAssayDefinition(ref.getAssayName(), ref.getLab());
                gAssaySchedule.getAssays().add(gad);
            }
        }

        //Note: timepoint storage is somewhat redundant for backward compatibility
        //The complete list of timepoints is retrieved here to support empty schdules
        //Assay events serialize their entire timepoint descriptor as well
        AssaySchedule.Timepoints timepoints = xAssaySchedule.getTimepoints();
        if (null != timepoints)
            for (Timepoint tp : timepoints.getTimepointArray())
            {
                GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
                GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays()/unit.daysPerUnit, unit);
                gAssaySchedule.addTimepoint(gtp);
            }

        for (AssayEvent evt : xAssaySchedule.getAssayEventArray())
        {
            Timepoint tp = evt.getTimepoint();
            GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
            GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays()/unit.daysPerUnit, unit);
            GWTAssayDefinition gad = findAssayDefinition(evt.getAssayName(), gAssaySchedule.getAssays());
            gAssaySchedule.setAssayPerformed(gad, gtp, new GWTAssayNote(createGWTSampleMeasure(evt.getSampleMeasure())));
        }
        def.setAssaySchedule(gAssaySchedule);

        GWTImmunizationSchedule gImmunizationSchedule = new GWTImmunizationSchedule();
        ImmunizationSchedule.Timepoints immunizationTimepoints = xdesign.getImmunizationSchedule().getTimepoints();
        if (null != immunizationTimepoints)
            for (Timepoint tp : immunizationTimepoints.getTimepointArray())
            {
                GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
                GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays()/unit.daysPerUnit, unit);
                gImmunizationSchedule.addTimepoint(gtp);
            }

        for (ImmunizationEvent evt : xdesign.getImmunizationSchedule().getImmunizationEventArray())
        {

            Timepoint tp = evt.getTimepoint();
            GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
            GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays() / unit.daysPerUnit, GWTTimepoint.Unit.fromString(tp.getDisplayUnit()));
            GWTImmunization immunization = new GWTImmunization();
            for (ImmunogenRef immunogenRef : evt.getImmunization().getImmunogenRefArray())
            {
                GWTImmunogen gImmunogen = findImmunogen(immunogenRef.getName(), def.getImmunogens());
                if (null != gImmunogen)
                    immunization.immunogens.add(gImmunogen);
            }
            for (AdjuvantRef adjuvantRef : evt.getImmunization().getAdjuvantRefArray())
            {
                GWTAdjuvant gAdjuvant = findAdjuvant(adjuvantRef.getName(), def.getAdjuvants());
                if (null != gAdjuvant)
                    immunization.adjuvants.add(gAdjuvant);
            }
            String groupName = evt.getGroupName();
            gImmunizationSchedule.setImmunization(findCohort(groupName, def.getGroups()), gtp, immunization);
        }
        def.setImmunizationSchedule(gImmunizationSchedule);

        return def;
    }

    public static StudyDesignDocument toXML(GWTStudyDefinition def)
    {
        try
        {
            StudyDesignDocument doc = StudyDesignDocument.Factory.newInstance();
            StudyDesign x = doc.addNewStudyDesign();
            x.setGrantName(def.getGrant());
            x.setInvestigator(def.getInvestigator());
            x.setName(def.getStudyName());
            x.setAnimalSpecies(def.getAnimalSpecies());
            if (null != def.getDescription())
                x.setDescription(def.getDescription());

            StudyDesign.Immunogens immunogens = x.addNewImmunogens();
            for (int i = 0; i < def.getImmunogens().size(); i++)
            {
                GWTImmunogen gImmunogen = def.getImmunogens().get(i);
                Immunogen xImmunogen = immunogens.addNewImmunogen();
                xImmunogen.setAdmin(gImmunogen.getAdmin());
                xImmunogen.setName(gImmunogen.getName());
                xImmunogen.setDose(gImmunogen.getDose());
                xImmunogen.setType(gImmunogen.getType());
                Immunogen.Antigens antigens = xImmunogen.addNewAntigens();
                for (Object o : gImmunogen.getAntigens())
                {
                    GWTAntigen gAntigen = (GWTAntigen) o;
                    Antigen antigen = antigens.addNewAntigen();
                    antigen.setGene(gAntigen.getGene());
                    antigen.setName(gAntigen.getName());
                    if (null != gAntigen.getSequence())
                        antigen.setSequence(gAntigen.getSequence());
                    if (null != gAntigen.getGenBankId())
                        antigen.setGenBankId(gAntigen.getGenBankId());
                    antigen.setSubtype(gAntigen.getSubtype());
                }
            }

            StudyDesign.Adjuvants adjuvants = x.addNewAdjuvants();
            for (int i = 0; i < def.getAdjuvants().size(); i++)
            {
                GWTAdjuvant gAdjuvant = def.getAdjuvants().get(i);
                Adjuvant xImmunogen = adjuvants.addNewAdjuvant();
                xImmunogen.setAdmin(gAdjuvant.admin);
                xImmunogen.setName(gAdjuvant.getName());
                xImmunogen.setDose(gAdjuvant.getDose());
            }


            StudyDesign.Cohorts cohorts = x.addNewCohorts();
            for (int i = 0; i < def.getGroups().size(); i++)
            {
                GWTCohort gwtCohort = def.getGroups().get(i);
                Cohort cohort = cohorts.addNewCohort();
                cohort.setName(gwtCohort.getName());
                cohort.setCount(gwtCohort.getCount());
            }

            ImmunizationSchedule xImmunizationSchedule = x.addNewImmunizationSchedule();
            GWTImmunizationSchedule gSchedule = def.getImmunizationSchedule();

            ImmunizationSchedule.Timepoints timepointsElem = xImmunizationSchedule.addNewTimepoints();
            List<Timepoint> immunizationTimepoints = new ArrayList<>();
            for (GWTTimepoint gtp : gSchedule.getTimepoints())
                immunizationTimepoints.add(createTimepoint(gtp));
            timepointsElem.setTimepointArray(immunizationTimepoints.toArray(new Timepoint[immunizationTimepoints.size()]));

            for (GWTCohort gCohort : def.getGroups())
            {
                for (GWTTimepoint gtp : gSchedule.getTimepoints())
                {
                    GWTImmunization gImmunization = gSchedule.getImmunization(gCohort, gtp);
                    if (null != gImmunization)
                    {
                        ImmunizationEvent evt = xImmunizationSchedule.addNewImmunizationEvent();
                        Immunization immunization = evt.addNewImmunization();
                        for (GWTImmunogen gImmunogen : gImmunization.immunogens)
                            immunization.addNewImmunogenRef().setName(gImmunogen.getName());

                        for (GWTAdjuvant gAdjuvant : gImmunization.adjuvants)
                            immunization.addNewAdjuvantRef().setName(gAdjuvant.getName());

                        evt.setGroupName(gCohort.getName());
                        evt.setTimepoint(createTimepoint(gtp));
                    }
                }
            }

            AssaySchedule xAssaySchedule = x.addNewAssaySchedule();
            AssaySchedule.Assays assays = xAssaySchedule.addNewAssays();
            AssaySchedule.Timepoints timepoints = xAssaySchedule.addNewTimepoints();

            GWTAssaySchedule gAssaySchedule = def.getAssaySchedule();
            if (null != gAssaySchedule.getDescription())
                xAssaySchedule.setDescription(gAssaySchedule.getDescription());

            List<Timepoint> scheduleTimepoints = new ArrayList<>();
            for (GWTTimepoint gtp : gAssaySchedule.getTimepoints())
                scheduleTimepoints.add(createTimepoint(gtp));
            timepoints.setTimepointArray(scheduleTimepoints.toArray(new Timepoint[scheduleTimepoints.size()]));

            for (GWTAssayDefinition gwtAssayDefinition : gAssaySchedule.getAssays())
            {
                AssayRef assayRef = assays.addNewAssayRef();
                assayRef.setAssayName(gwtAssayDefinition.getAssayName());
                assayRef.setLab(gwtAssayDefinition.getLab());
                
                for (GWTTimepoint gwtTimepoint : gAssaySchedule.getTimepoints())
                {
                    GWTAssayNote gwtAssayNote = gAssaySchedule.getAssayPerformed(gwtAssayDefinition, gwtTimepoint);
                    if (null != gwtAssayNote)
                    {
                        AssayEvent evt = xAssaySchedule.addNewAssayEvent();
                        evt.setAssayName(gwtAssayDefinition.getAssayName());
                        evt.setTimepoint(createTimepoint(gwtTimepoint));
                        evt.setSampleMeasure(createSampleMeasure(gwtAssayNote.getSampleMeasure()));
                    }
                }
            }

            assert doc.validate();
            return doc;
        }
        catch (Exception e)
        {
            _log.error(e);
            throw new RuntimeException(e);
        }
    }


    static GWTSampleMeasure createGWTSampleMeasure(SampleMeasure measure)
    {
        return new GWTSampleMeasure(measure.getAmount(), measure.getUnit(), measure.getType());
    }

    static Timepoint createTimepoint(GWTTimepoint gtp)
    {
        Timepoint tp = Timepoint.Factory.newInstance();
        tp.setDays(gtp.getDays());
        tp.setDisplayUnit(gtp.getUnit().toString());
        if (null != gtp.getName())
            tp.setName(gtp.getName());

        return tp;
    }

    static SampleMeasure createSampleMeasure(GWTSampleMeasure gwtSampleMeasure)
    {
        SampleMeasure sm = SampleMeasure.Factory.newInstance();
        if (gwtSampleMeasure != null && !gwtSampleMeasure.isEmpty())
        {
            sm.setAmount(gwtSampleMeasure.getAmount());
            if (null != gwtSampleMeasure.getType())
                sm.setType(gwtSampleMeasure.getType());
            if (null != gwtSampleMeasure.getUnit())
                sm.setUnit(gwtSampleMeasure.getUnit());
        }
        return sm;
    }

    static GWTAssayDefinition findAssayDefinition(String assayName, List<GWTAssayDefinition> assays)
    {
        for (GWTAssayDefinition def : assays)
            if (def.getAssayName().equals(assayName))
                return def;

        return null;
    }

    static GWTImmunogen findImmunogen(String name, List<GWTImmunogen> immunogens)
    {
        for (GWTImmunogen i : immunogens)
            if (i.getName().equals(name))
                return i;

        return null;
    }

    static GWTAdjuvant findAdjuvant(String name, List<GWTAdjuvant> adjuvants)
    {
        for (GWTAdjuvant i : adjuvants)
            if (i.getName().equals(name))
                return i;

        return null;
    }

    static GWTCohort findCohort(String name, List<GWTCohort> cohorts)
    {
        for (GWTCohort c : cohorts)
            if (c.getName().equals(name))
                return c;

        return null;
    }

    static boolean validate(StudyDesignDocument doc)
    {
        XmlOptions opts = new XmlOptions();
        ArrayList<XmlError> errors = new ArrayList();
        opts.setErrorListener(errors);
        boolean valid = doc.validate(opts);
        return valid;
    }
}

