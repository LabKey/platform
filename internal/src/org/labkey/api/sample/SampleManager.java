/*
 * Copyright (c) 2003-2005 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.sample;

import org.apache.log4j.Logger;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.security.User;
import org.labkey.api.util.DateUtil;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: migra
 * Date: Jul 28, 2005
 * Time: 1:26:32 PM
 */
public class SampleManager
{
    private static final String SAMPLE_TABLE_NAME = "Sample";
    private static final String LOCATION_TABLE_NAME = "Location";
    private static final String SAMPLE_TYPE_TABLE_NAME = "SampleType";
    private static final String SLIDE_TABLE_NAME = "Slide";
    private static final DbSchema schema = MouseSchema.getSchema();
    private static final String _sampleDateFormatString = "MMddyy";
    private static Logger _log = Logger.getLogger(SampleManager.class);

    public static String formatSampleDate(Date date)
    {
        return DateUtil.formatDateTime(date, _sampleDateFormatString);
    }

    public static Date parseSampleDate(String s) throws ParseException
    {
        return DateUtil.parseDateTime(s, _sampleDateFormatString);
    }

    public static void beginTransaction() throws SQLException
    {
        schema.getScope().beginTransaction();
    }

    public static void commitTransaction() throws SQLException
    {
        schema.getScope().commitTransaction();
    }

    public static void rollbackTransaction()
    {
        schema.getScope().rollbackTransaction();
    }

    public static boolean isTransactionActive()
    {
        return schema.getScope().isTransactionActive();
    }

    public static Location insert(User user, Location location) throws SQLException
    {
        return Table.insert(user, getTinfoLocation(), location);
    }

    public static Location update(User user, Location location) throws SQLException
    {
        return Table.update(user, getTinfoLocation(), location, location.getSampleLSID(), null);
    }

    public static SampleType insert(User user, SampleType sampleType) throws SQLException
    {
        return Table.insert(user, getTinfoSampleType(), sampleType);
    }

    public static Sample insert(User user, Sample sample) throws SQLException
    {
        ExpMaterial mat = ExperimentService.get().createExpMaterial();
        mat.setLSID(sample.getLSID());
        mat.setName(sample.getSampleId());
        mat.setContainer(ContainerManager.getForId(sample.getContainer()));
        mat.insert(user);
        return Table.insert(user, getTinfoSample(), sample);
    }

    public static void deleteSample(String sampleLsid, Container c) throws SQLException
    {
        ExpMaterial mat = ExperimentService.get().getExpMaterial(sampleLsid);
        if (null != mat)
            ExperimentService.get().deleteMaterialByRowIds(c, mat.getRowId()) ;

        SimpleFilter filter = new SimpleFilter("LSID", sampleLsid);
        filter.addCondition("container", c.getId());
        Table.delete(getTinfoSample(), filter);

        SimpleFilter locationFilter = new SimpleFilter("SampleLSID", sampleLsid);
        Table.delete(getTinfoLocation(), locationFilter);
    }


    /**
     * SampleId's are encoded at the request of the mouse lab as
     * ModelName-Date-###[a-z]?. Where aliquots have a terminating letter.
     * We are trying to match all of those.
     */
    private static String _sampleIdSql = "SELECT SampleId FROM mousemod.sample WHERE collectionDate = ?";

    public static Integer getNextSampleId(Date collectionDate, int min, int max)
    {
        ResultSet rs;
        try
        {
            rs = Table.executeQuery(schema, _sampleIdSql, new Object[]{collectionDate});
            while (rs.next())
            {
                ParsedSampleId parsedId = new ParsedSampleId(rs.getString(1));
                if (parsedId.isParsed && parsedId.sampleNum >= min && parsedId.sampleNum < max)
                    min = parsedId.sampleNum + 1;
            }
            rs.close();
        }
        catch (Exception x)
        {
            _log.error("Retrieving next sample Id", x);
        }

        return min;
    }

    private static String moveBoxSql = "UPDATE mousemod.location set freezer=?, rack=?, box=? WHERE freezer=? AND rack=? and box=? AND SampleLSID IN (SELECT mousemod.Location.SampleLSID FROM mouseMod.Location JOIN mouseMod.Sample ON mouseMod.Location.SampleLSID = mouseMod.Sample.LSID WHERE mouseMod.Sample.Container = ?)";
    public static int moveBox(Container c, String oldFreezer, String newFreezer, String oldRack, String newRack, String oldBox, String newBox) throws SQLException
    {
        return Table.execute(MouseSchema.getSchema(), moveBoxSql, new Object[] {
                newFreezer, newRack, newBox, oldFreezer, oldRack, oldBox, c.getId()
        });
    }

    public static Sample update(User user, Sample sample) throws SQLException
    {
        return Table.update(user, MouseSchema.getSample(), sample, sample.getLSID(), null);
    }

    public static Sample[] getSampleFromBDIBarCode(String sampleId) throws SQLException
    {

        Pattern p;
        if (!sampleId.matches("[0-9]{6}"))
            return null;

        String likeStr = "%" + sampleId.substring(3);
        String month = sampleId.substring(0,2);
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(2005, 0, 1);
        cal.add(Calendar.MONTH, Integer.parseInt(month) -1);
        Calendar cal2 = (Calendar) cal.clone();
        cal2.add(Calendar.MONTH, 1);


        String sql = "SELECT * FROM mousemod.Sample WHERE SampleId " + schema.getSqlDialect().getCharClassLikeOperator() + " ? AND "
                + "CollectionDate >= ? AND CollectionDate <= ?"
            ;
        return Table.executeQuery(schema, sql, new Object[]{likeStr, cal.getTime(), cal2.getTime()}, Sample.class);
    }

    public static TableInfo getTinfoSample()
    {
        return schema.getTable(SAMPLE_TABLE_NAME);
    }

    public static TableInfo getTinfoSampleType()
    {
        return schema.getTable(SAMPLE_TYPE_TABLE_NAME);
    }

    public static TableInfo getTinfoLocation()
    {
        return schema.getTable(LOCATION_TABLE_NAME);
    }

    public static TableInfo getTinfoSlide()
    {
        return schema.getTable(SLIDE_TABLE_NAME);
    }

    public static Slide insertSlide(User u, Slide slide, List<AttachmentFile> files) throws SQLException, IOException, AttachmentService.DuplicateFilenameException
    {
        slide = Table.insert(u, getTinfoSlide(), slide);
        AttachmentService.get().addAttachments(u, slide, files);
        return slide;
    }

    public static void deleteSlide(Container c, Slide slide) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("EntityId", slide.getEntityId());
        filter.addCondition(getTinfoSlide().getColumn("Container"), c);
        Table.delete(getTinfoSlide(), filter);
        AttachmentService.get().deleteAttachments(slide);
    }

    public static Slide getSlide(Container c, String entityId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("EntityId", entityId);
        filter.addCondition(getTinfoSlide().getColumn("Container"), c);
        Slide[] slides = Table.select(getTinfoSlide(), Table.ALL_COLUMNS, filter, null,  Slide.class);
        if (null == slides || slides.length < 1)
            return null;

        return slides[0];
    }
    
    public static class ParsedSampleId
    {
        private final static Pattern FULL_SAMPLE_PATTERN = Pattern.compile("(\\d{6})-(\\d\\d\\d)([a-z]?)");
        String sampleId;
        boolean isParsed = false;
        String modelName;
        Date collectionDate;
        int sampleNum;
        char aliquot = 0;

        private ParsedSampleId(String str)
        {
            sampleId = str;
            Matcher matcher = FULL_SAMPLE_PATTERN.matcher(str);
            if (matcher.matches())
            {
                try
                {
                    collectionDate = parseSampleDate(matcher.group(1));
                }
                catch (Exception x)
                {
                    return;
                }
                isParsed = true;
                sampleNum = Integer.parseInt(matcher.group(2));
                if (matcher.group(3).length() > 0)
                    aliquot = matcher.group(3).charAt(0);

                isParsed = true;
            }
        }
    }

    public static Location getLocation(String sampleLSID) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("SampleLSID", sampleLSID);
        Location[] locations = Table.select(getTinfoLocation(), Table.ALL_COLUMNS, filter, null, Location.class);
        if (null == locations || locations.length == 0)
            return null;

        return locations[0];
    }

    public static Sample getSampleFromLocation(String freezer, String rack, String box, Integer cell) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter();
        if (null == freezer)
            filter.addCondition("Freezer", freezer, CompareType.ISBLANK);
        else
            filter.addCondition("Freezer", freezer);
        if (null == rack)
            filter.addCondition("Rack", rack, CompareType.ISBLANK);
        else
            filter.addCondition("Rack", rack);
        //BOX & CELL are NOT NULL
        filter.addCondition("Box", box);
        filter.addCondition("Cell", cell);

        Location loc = Table.selectObject(MouseSchema.getLocation(), filter, null, Location.class);
        if (null == loc)
            return null;

        return getSample(loc.getSampleLSID());
    }

    public static SampleType getSampleType(int sampleTypeId)
    {
        return Table.selectObject(getTinfoSampleType(), sampleTypeId, SampleType.class);
    }

    private static final String _sqlGetPlasmaSampleType = "SELECT sampleTypeId FROM mousemod.SampleType WHERE sampleType = ?";

    public static Integer getSampleTypeId(String name)
    {
        try
        {
            return Table.executeSingleton(schema, _sqlGetPlasmaSampleType, new Object[]{name}, Integer.class);
        }
        catch (SQLException x)
        {
            _log.error("Select sample type " + name, x);
            throw new RuntimeSQLException(x);
        }
    }

    public static int getPlasmaSampleType()
    {
        return getSampleTypeId("plasma");
    }

    public static int getUrineSampleType()
    {
        return getSampleTypeId("urine");
    }


    public static Sample getSample(String lsid)
    {
        return Table.selectObject(getTinfoSample(), new Object[]{lsid}, Sample.class);
    }

    public static Sample getSample(String sampleId, MouseModel model)
    {
        ExpSampleSet matSource = ExperimentService.get().getSampleSet(model.getMaterialSourceLSID());
        if (null == matSource)
            throw new IllegalArgumentException("No material source for model");

        String sampleLSID = matSource.getMaterialLSIDPrefix() + sampleId;
        return Table.selectObject(getTinfoSample(), new Object[]{sampleLSID}, Sample.class);        
    }

    public static Sample getSampleFromId(String sampleId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("SampleId", sampleId);
        return Table.selectObject(getTinfoSample(), filter, null, Sample.class);
    }

    public static Slide[] getSlides(Sample sample) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("sampleLSID", sample.getLSID());
        return Table.select(getTinfoSlide(), Table.ALL_COLUMNS, filter, null, Slide.class);
    }
}
