/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.XmlError;
import gwt.client.org.labkey.study.designer.client.model.*;
import org.labkey.study.xml.*;
import org.json.JSONObject;

import java.util.List;
import java.util.ArrayList;/*
 * User: marki
 * Date: Jun 15, 2009
 * Time: 2:55:48 PM
 */

public class JSONSerializer
{
    private static Logger _log = Logger.getLogger(XMLSerializer.class);

    public static JSONObject toJSON(String xml)
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

        JSONObject j = new JSONObject();
        j.put("name", xdesign.getName());
        j.put("grantName", xdesign.getGrantName());
        j.put("investigator", xdesign.getInvestigator());
        j.put("animalSpecies", xdesign.getAnimalSpecies());
        if (xdesign.isSetDescription())
            j.put("description", xdesign.getDescription());

        for (Immunogen immunogen : xdesign.getImmunogens().getImmunogenArray())
        {
            JSONObject jimmunogen = new JSONObject();
            jimmunogen.put("name", immunogen.getName());
            jimmunogen.put("dose", immunogen.getDose());
            jimmunogen.put("type", immunogen.getType());
            jimmunogen.put("admin", immunogen.getAdmin());

            GWTImmunogen gimmunogen = new GWTImmunogen(immunogen.getName(), immunogen.getDose(), immunogen.getType(), immunogen.getAdmin());
            for (Antigen antigen : immunogen.getAntigens().getAntigenArray())
            {
                JSONObject jantigen = new JSONObject();
                jantigen.put("name", antigen.getName());
                jantigen.put("gene", antigen.getGene());
                jantigen.put("subtype", antigen.getSubtype());
                jantigen.put("genBankId", antigen.getGenBankId());
                jantigen.put("sequence", antigen.getSequence());
                jimmunogen.append("antigens", jantigen);
            }

            j.append("immunogens", jimmunogen);

        }

        for (Adjuvant adjuvant : xdesign.getAdjuvants().getAdjuvantArray())
        {
            JSONObject jadjuvant = new JSONObject();
            jadjuvant.put("name", adjuvant.getName());
            jadjuvant.put("dose", adjuvant.getDose());
            jadjuvant.put("admin", adjuvant.getAdmin());
            j.append("adjuvants", jadjuvant);
        }


        for (Cohort cohort : xdesign.getCohorts().getCohortArray())
        {
            JSONObject jcohort = new JSONObject();
            jcohort.put("name", cohort.getName());
            jcohort.put("count", cohort.getCount());
            j.append("cohorts", jcohort);
        }


        AssaySchedule xAssaySchedule = xdesign.getAssaySchedule();
        JSONObject jAssaySchedule = new JSONObject();
        j.put("assaySchedule", jAssaySchedule);
        if (null != xAssaySchedule.getDescription())
            jAssaySchedule.put("description", xAssaySchedule.getDescription());


        for (AssayEvent evt : xAssaySchedule.getAssayEventArray())
        {
            Timepoint tp = evt.getTimepoint();
            JSONObject jAssay = new JSONObject();
            jAssay.put("timepoint", createTimepoint(tp));
            jAssay.put("name", evt.getAssayName());
            jAssay.put("sampleMeasure", createSampleMeasure(evt.getSampleMeasure()));

            jAssaySchedule.append("assays", jAssay);
        }
    
        GWTImmunizationSchedule gImmunizationSchedule = new GWTImmunizationSchedule();
        for (ImmunizationEvent evt : xdesign.getImmunizationSchedule().getImmunizationEventArray())
        {

            Timepoint tp = evt.getTimepoint();
            GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
            GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays() / unit.daysPerUnit, GWTTimepoint.Unit.fromString(tp.getDisplayUnit()));
            JSONObject immunization = new JSONObject();
            for (ImmunogenRef immunogenRef : evt.getImmunization().getImmunogenRefArray())
                immunization.append("immunogens", immunogenRef.getName());
            for (AdjuvantRef adjuvantRef : evt.getImmunization().getAdjuvantRefArray())
                immunization.append("adjuvants", adjuvantRef.getName());

            immunization.put("groupName", evt.getGroupName());
            immunization.put("timepoint", createTimepoint(evt.getTimepoint()));

            j.append("immunizations", immunization);
         }

        return j;
    }

    public static JSONObject toJSON(GWTStudyDefinition def)
    {
        try
        {
            JSONObject j = new JSONObject();
            j.put("grantName", def.getGrant());
            j.put("investigator", def.getInvestigator());
            j.put("name", def.getStudyName());
            j.put("animalSpecies", def.getAnimalSpecies());
            if (null != def.getDescription())
                j.put("description", def.getDescription());

            for (int i = 0; i < def.getImmunogens().size(); i++)
            {
                GWTImmunogen gImmunogen = def.getImmunogens().get(i);

                JSONObject jImmunogen = new JSONObject();
                jImmunogen.put("admin", gImmunogen.getAdmin());
                jImmunogen.put("name", gImmunogen.getName());
                jImmunogen.put("dose", gImmunogen.getDose());
                jImmunogen.put("type", gImmunogen.getType());

                for (Object o : gImmunogen.getAntigens())
                {
                    GWTAntigen gAntigen = (GWTAntigen) o;
                    JSONObject jAntigen = new JSONObject();
                    jAntigen.put("gene", gAntigen.getGene());
                    jAntigen.put("name", gAntigen.getName());
                    if (null != gAntigen.getSequence())
                        jAntigen.put("sequence", gAntigen.getSequence());
                    if (null != gAntigen.getGenBankId())
                        jAntigen.put("genBankId", gAntigen.getGenBankId());
                    jAntigen.put("subtype", gAntigen.getSubtype());

                    jImmunogen.append("antigens", jAntigen);
                }
                j.append("immunogens", jImmunogen);
            }

            for (int i = 0; i < def.getAdjuvants().size(); i++)
            {
                GWTAdjuvant gAdjuvant = def.getAdjuvants().get(i);
                JSONObject jAdjuvant = new JSONObject();
                jAdjuvant.put("admin", gAdjuvant.admin);
                jAdjuvant.put("name", gAdjuvant.getName());
                jAdjuvant.put("dose", gAdjuvant.getDose());
                j.append("adjuvants", jAdjuvant);
            }


            for (int i = 0; i < def.getGroups().size(); i++)
            {
                GWTCohort gwtCohort = def.getGroups().get(i);
                JSONObject jCohort = new JSONObject();
                jCohort.put("name", gwtCohort.getName());
                jCohort.put("count", gwtCohort.getCount());
                j.append("cohorts", jCohort);
            }

            GWTImmunizationSchedule gSchedule = def.getImmunizationSchedule();
            for (GWTCohort gCohort : def.getGroups())
            {
                for (GWTTimepoint gtp : gSchedule.getTimepoints())
                {
                    GWTImmunization gImmunization = gSchedule.getImmunization(gCohort, gtp);
                    if (null != gImmunization)
                    {
                        JSONObject jevt = new JSONObject();
                        jevt.put("groupName", gCohort.getName());
                        jevt.put("timepoint", createTimepoint(gtp));
                        for (GWTImmunogen gImmunogen : gImmunization.immunogens)
                            jevt.append("immunogens", gImmunogen.getName());

                        for (GWTAdjuvant gAdjuvant : gImmunization.adjuvants)
                            jevt.append("adjuvants", gAdjuvant.getName());

                        j.append("immunizations", jevt);
                    }
                }
            }

            JSONObject jAssaySchedule = new JSONObject();
            GWTAssaySchedule gAssaySchedule = def.getAssaySchedule();
            if (null != gAssaySchedule.getDescription())
                jAssaySchedule.put("description", gAssaySchedule.getDescription());


            for (GWTAssayDefinition gwtAssayDefinition : gAssaySchedule.getAssays())
            {
                boolean wasArrayEmitted = false;
                for (GWTTimepoint gwtTimepoint : gAssaySchedule.getTimepoints())
                {
                    GWTAssayNote gwtAssayNote = gAssaySchedule.getAssayPerformed(gwtAssayDefinition, gwtTimepoint);
                    if (null != gwtAssayNote)
                    {
                        JSONObject jAssay = new JSONObject();
                        jAssay.put("name", gwtAssayDefinition.getAssayName());
                        jAssay.put("timepoint", createTimepoint(gwtTimepoint));
                        jAssay.put("sampleMeasure", createSampleMeasure(gwtAssayNote.getSampleMeasure()));
                        jAssay.put("lab", gwtAssayDefinition.getLab());
                        jAssaySchedule.append("assays", jAssay);
                        wasArrayEmitted = true;
                    }
                }
                if (!wasArrayEmitted)
                {
                    JSONObject jAssay = new JSONObject();
                    jAssay.put("name", gwtAssayDefinition.getAssayName());
                    jAssay.put("lab", gwtAssayDefinition.getLab());
                    jAssaySchedule.append("assays", jAssay);
                }
            }
            j.put("assaySchedule", jAssaySchedule);

            return j;

        }
        catch (Exception e)
        {
            _log.error(e);
            throw new RuntimeException(e);
        }
    }


    static JSONObject createJSONSampleMeasure(SampleMeasure measure, GWTStudyDefinition parent)
    {
        JSONObject jSampleMeasure = new JSONObject();
        jSampleMeasure.put("amount", measure.getAmount());
        jSampleMeasure.put("type", measure.getType());
        jSampleMeasure.put("unit", measure.getUnit());

        return jSampleMeasure;
    }

    static JSONObject createTimepoint(GWTTimepoint gtp)
    {
        JSONObject tp = new JSONObject();
        tp.put("days", gtp.getDays());
        tp.put("displayUnit", gtp.getUnit().toString());
        if (null != gtp.getName())
            tp.put("name", gtp.getName());
        else
            tp.put("name", gtp.toString());

        return tp;
    }

    static JSONObject createTimepoint(Timepoint tp)
    {

        GWTTimepoint.Unit unit = GWTTimepoint.Unit.fromString(tp.getDisplayUnit());
        GWTTimepoint gtp = new GWTTimepoint(tp.getName(), tp.getDays()/unit.daysPerUnit, unit);
        return createTimepoint(gtp);
    }

    static JSONObject createSampleMeasure(GWTSampleMeasure gwtSampleMeasure)
    {
        JSONObject sm = new JSONObject();
        sm.put("type", gwtSampleMeasure.getType());
        sm.put("amount", gwtSampleMeasure.getAmount());
        sm.put("unit", gwtSampleMeasure.getUnit());

        return sm;
    }


    static JSONObject createSampleMeasure(SampleMeasure sampleMeasure)
    {
        JSONObject sm = new JSONObject();
        sm.put("type", sampleMeasure.getType());
        sm.put("amount", sampleMeasure.getAmount());
        sm.put("unit", sampleMeasure.getUnit());

        return sm;
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

