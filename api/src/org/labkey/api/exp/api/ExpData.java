package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.ExperimentDataHandler;
import org.apache.activemq.broker.region.cursors.PendingMessageCursor;

import java.net.URI;
import java.util.Date;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.io.File;

public interface ExpData extends ExpObject
{
    ExpProtocolApplication getSourceApplication();
    ExpProtocolApplication[] getTargetApplications();
    ExpRun[] getTargetRuns();
    ExpRun getRun();
    DataType getDataType();
    URI getDataFileURI();
    File getDataFile();
    void delete(User user) throws Exception;

    void setDataFileURI(URI uri);
    void setSourceApplication(ExpProtocolApplication app);
    void save(User user);
    Date getCreated();

    ExperimentDataHandler findDataHandler();

    String getDataFileUrl();

    File getFile();

    ExpProtocolApplication retrieveSourceApp();

    List<ExpProtocolApplication> retrieveSuccessorAppList();

    void storeSourceApp(ExpProtocolApplication expProtocolApplication);

    void storeSuccessorAppList(ArrayList<ExpProtocolApplication> expProtocolApplications);

    void storeSuccessorRunIdList(ArrayList<Integer> integers);

    List<Integer> retrieveSuccessorRunIdList();

    boolean isInlineImage();

    boolean isFileOnDisk();

    void setDataFileUrl(String s);

    void insert(User user);

    ExpProtocol getSourceProtocol();

    void setSourceProtocol(ExpProtocol expProtocol);

    void setRun(ExpRun expRun);
}
