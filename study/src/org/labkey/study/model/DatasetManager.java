package org.labkey.study.model;

import org.labkey.api.study.DataSet;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * User: adam
 * Date: 2/1/13
 * Time: 10:51 PM
 */
public class DatasetManager
{
    // Thread-safe list implementation that allows iteration and modifications without external synchronization
    public static final List<DataSetListener> _listeners = new CopyOnWriteArrayList<DataSetListener>();

    public static void addDataSetListener(DataSetListener listener)
    {
        _listeners.add(listener);
    }

    public interface DataSetListener
    {
        void dataSetChanged(DataSet def);
    }
}
