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

import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.ExpSampleSet;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.List;
import java.util.ArrayList;

/**
 * User: migra
 * Date: Aug 4, 2005
 * Time: 9:20:00 PM
 */
public class MouseModelManager
{
    private static Logger _log = Logger.getLogger(MouseModelManager.class);

    public static Mouse insertMouse(User user, Mouse mouse) throws SQLException
    {
        return Table.insert(user, MouseSchema.getMouse(), mouse);
    }

    public static void beginTransaction() throws SQLException
    {
        MouseSchema.getMouse().getSchema().getScope().beginTransaction();
    }

    public static void commitTransaction() throws SQLException
    {
        MouseSchema.getMouse().getSchema().getScope().commitTransaction();
    }

    public static void rollbackTransaction()
    {
        MouseSchema.getMouse().getSchema().getScope().rollbackTransaction();
    }

    public static BreedingPair getBreedingPair(int breedingPairId)
    {
        SimpleFilter filter = new SimpleFilter("breedingPairId", breedingPairId);
        BreedingPair[] pairs = null;
        try
        {
            pairs = Table.select(MouseSchema.getBreedingPair(), Table.ALL_COLUMNS, filter, null, BreedingPair.class);
        }
        catch (SQLException x)
        {
            _log.error("Loading breeding pair: " + breedingPairId, x);
        }

        if (null == pairs || pairs.length != 1)
            throw new IllegalArgumentException("Can't find breeding pair");
        return pairs[0];
    }

    /**
     * Cage numbers have a weird numbering system. A, B .. Z, AA, AB, AC.
     * Note that A represents 0 UNLESS it is the high-order char of a multi-char
     * string.
     */
    public static String[] getNextCageNames(int modelId, int nCages) throws SQLException
    {
        SqlDialect dialect = MouseSchema.getSchema().getSqlDialect();
        String sql = "SELECT Max(" + "" +
                "CASE WHEN " + dialect.getVarcharLengthFunction() + "(cageName) = 1 THEN " +
                "ascii(substring(cageName, 1, 1)) - ascii('A')" +
                "ELSE " +
                "(ascii(substring(cageName, 1, 1)) - ascii('A') + 1) * 26 + ascii(substring(cageName, 2, 1)) - ascii('A')" +
                "END ) FROM mousemod.Cage WHERE ModelId = ?";

        Integer lastCageNum = Table.executeSingleton(MouseSchema.getSchema(), sql, new Object[]{modelId}, Integer.class);
        int cageNum = 0;
        if (null != lastCageNum)
            cageNum = lastCageNum + 1;

        String cageNames[] = new String[nCages];
        for (int i = 0; i < nCages; i++)
        {
            int val = cageNum++;
            String str = "";
            if (val < 26)
                str = new StringBuffer(1).append((char) (val + 'A')).toString();
            else if (val < 26 * 26)
            {
                char first = (char) ('A' + val / 26 - 1);
                char second = (char) ('A' + val % 26);
                str = new StringBuffer(2).append(first).append(second).toString();
            }

            cageNames[i] = str;
        }

        return cageNames;
    }

    public static MouseModel getModel(int modelId)
    {
        return Table.selectObject(MouseSchema.getMouseModel(), modelId, MouseModel.class);
    }

    public static Litter getLitter(int breedingPairId, String litterName)
    {
        SimpleFilter filter = new SimpleFilter("breedingPairId", breedingPairId);
        filter.addCondition("name", litterName);
        Litter[] litters;
        try
        {
            litters = Table.select(MouseSchema.getLitter(), Table.ALL_COLUMNS, filter, null, Litter.class);
        }
        catch (SQLException x)
        {
            _log.error("Exception finding litter: " + litterName, x);
            return null;
        }

        if (null != litters && litters.length == 1)
            return litters[0];

        return null;
    }

    public static MouseModel insertModel(User user, MouseModel model) throws SQLException
    {
        Container c = ContainerManager.getForId(model.getContainer());
        ExpSampleSet matSource = ExperimentService.get().createSampleSet();
        matSource.setLSID(new Lsid("MaterialSource", "Folder-" + c.getRowId(), PageFlowUtil.filter(model.getName())).toString());
        matSource.setName("Mouse Model: " + model.getName());
        matSource.setContainer(c);
        matSource.setDescription("Material Source created automatically for mouse model " + model.getName() + " in folder " + c.getPath());
        matSource.setMaterialLSIDPrefix(new Lsid("MouseSample", "Folder-" + c.getRowId() + "." + PageFlowUtil.filter(model.getName()), "").toString());
        matSource.insert(user);

        model.setMaterialSourceLSID(matSource.getLSID());
        return Table.insert(user, MouseSchema.getMouseModel(), model);
    }

    //Should cache...
    public static MouseModel getMouseModel(int modelId)
    {
        return Table.selectObject(MouseSchema.getMouseModel(), modelId, MouseModel.class);
    }


    public static Mouse getMouse(String mouseEntityId)
    {
        return Table.selectObject(MouseSchema.getMouse(), mouseEntityId, Mouse.class);
    }

    public static Mouse getMouse(int mouseId)
    {
        SimpleFilter filter = new SimpleFilter("mouseId", mouseId);

        Mouse[] mice = null;
        try
        {
            mice = Table.select(MouseSchema.getMouse(), Table.ALL_COLUMNS, filter, null, Mouse.class);
        }
        catch (SQLException x)
        {
            _log.error("Error selecting mouse " + mouseId, x);
        }

        if (null == mice || mice.length == 0)
            return null;
        else
            return mice[0];
    }

    /**
     * Given a Mouse object, inspect it for possible primary or alternate keys and
     * return an up-to-date copy of the mouse object.
     */
    public static Mouse getMouse(Mouse mouse)
    {
        if (!PageFlowUtil.empty(mouse.getEntityId()))
            return getMouse(mouse.getEntityId());
        else if (mouse.getMouseId() != 0)
            return getMouse(mouse.getMouseId());
        else if (mouse.getModelId() != 0 && mouse.getContainer() != null)
            return getMouse(ContainerManager.getForId(mouse.getContainer()), mouse.getMouseNo());
        else
            return null;
    }

    public static Mouse getMouse(Container container, String mouseNo)
    {
        SimpleFilter filter = new SimpleFilter("Container", container.getId());
        filter.addCondition("MouseNo", mouseNo);

        Mouse[] mice = null;
        try
        {
            mice = Table.select(MouseSchema.getMouse(), Table.ALL_COLUMNS, filter, null, Mouse.class);
        }
        catch (SQLException x)
        {
            _log.error("Error selecting mouse " + mouseNo + " in " + container != null ? container.getName() : " null container ", x);
        }

        if (null == mice || mice.length == 0)
            return null;
        else
            return mice[0];
    }

    public static Mouse updateMouse(User user, Mouse mouse) throws SQLException
    {
        return Table.update(user, MouseSchema.getMouse(), mouse, mouse.getEntityId(), null);
    }

    public static Cage getCage(int modelId, String cageName)
    {
        SimpleFilter filter = new SimpleFilter("ModelId", modelId);
        filter.addCondition("CageName", cageName);
        Cage[] cages = null;

        try
        {
            cages = Table.select(MouseSchema.getCage(), Table.ALL_COLUMNS, filter, null, Cage.class);
        }
        catch (SQLException x)
        {
            _log.error("Error finding cage. ModelId=" + modelId + " CageName=" + cageName);
        }

        if (null == cages || cages.length == 0)
            return null;

        return cages[0];
    }

    public static Cage insertCage(User user, Cage cage) throws SQLException
    {
        return Table.insert(user, MouseSchema.getCage(), cage);
    }

    public static Litter insertLitter(User user, Litter litter) throws Exception
    {
        return Table.insert(user, MouseSchema.getLitter(), litter);
    }


    public static final String LOOKUP_CONTAINER_NAME = "MouseLookups";

    public static Container getLookupContainer()
    {
        Container shared = ContainerManager.getSharedContainer();
        return ContainerManager.getForPath(shared.getPath() + "/" + LOOKUP_CONTAINER_NAME);
    }

    public static Container ensureLookupContainer() throws SQLException
    {
        Container shared = ContainerManager.getSharedContainer();
        return ContainerManager.ensureContainer(shared.getPath() + "/" + LOOKUP_CONTAINER_NAME);
    }

    public static MouseModel[] getModels(Container c) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("Container", c.getId());
        MouseModel[] models = Table.select(MouseSchema.getMouseModel(), Table.ALL_COLUMNS, filter, null, MouseModel.class);
        return models;
    }

    public static List<String> getSummary(Container c) throws SQLException
    {
        MouseModel[] models = getModels(c);
        if (null == models || models.length == 0)
            return null;

        Integer mouseCount = getMouseCount(c);
        Integer sampleCount = getSampleCount(c);

        List<String> summary = new ArrayList<String>();
        summary.add(models.length + " Mouse Model" + (models.length > 1 ? "s" : "") + " containing " +
                mouseCount + (mouseCount == 1 ? " mouse" : " mice") + " and " +
                sampleCount +  (sampleCount == 1 ? " sample." : " samples."));

        return summary;
    }

    private static Integer getMouseCount(Container c) throws SQLException
    {
        String countMiceSql = "SELECT Count(*) FROM " + MouseSchema.getMouse().getFromSQL().getSQL() +
                " WHERE Container = ?";
        return Table.executeSingleton(MouseSchema.getSchema(), countMiceSql, new Object[] {c.getId()}, Integer.class);
    }

    private static Integer getSampleCount(Container c) throws SQLException
    {
        String countSampleSql = "SELECT Count(*) FROM " + MouseSchema.getMouseSample().getFromSQL().getSQL() +
                " WHERE Container = ?";
        return Table.executeSingleton(MouseSchema.getSchema(), countSampleSql, new Object[] {c.getId()}, Integer.class);
    }

    private static final String DELETE_LOCATION  = "DELETE FROM " + MouseSchema.getLocation().getFromSQL().getSQL() + " WHERE SampleLSID IN (" +
            "SELECT LSID FROM " + MouseSchema.getSample().getFromSQL().getSQL() + " WHERE Container = ?)";

    public static void deleteAll(Container c) throws SQLException
    {
        Filter filter = new SimpleFilter("Container", c.getId());
        Integer sampleCount = getSampleCount(c);
        if (sampleCount > 0)
        {
            Table.delete(MouseSchema.getSlide(), filter);
            //TODO: Wiki Controller deletes attachments. This seems like a bad thing to rely on!
            Table.execute(MouseSchema.getSchema(), DELETE_LOCATION, new Object[]{c.getId()});
            Table.delete(MouseSchema.getSample(), filter);
            //Experiment manager will delete materials, material source
        }
        Table.delete(MouseSchema.getMouseTask(), filter);
        Table.delete(MouseSchema.getMouse(), filter);
        Table.delete(MouseSchema.getLitter(), filter);
        Table.delete(MouseSchema.getCage(), filter);
        Table.delete(MouseSchema.getBreedingPair(), filter);
        Table.delete(MouseSchema.getMouseModel(), filter);
    }
}
