package org.labkey.study.importer;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.util.DOMUtil;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.study.controllers.CohortController;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.controllers.samples.SpringSpecimenController;
import org.labkey.study.model.SecurityType;
import org.labkey.study.model.Study;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.Visit;
import org.labkey.study.visitmanager.VisitManager;
import org.labkey.study.pipeline.StudyPipeline;
import org.labkey.study.pipeline.DatasetBatch;
import org.springframework.validation.BindException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * User: adam
 * Date: Apr 11, 2009
 * Time: 2:48:31 PM
 */
public class StudyImporter
{
    private static final Logger _log = Logger.getLogger(StudyImporter.class);

    private Container _c;
    private User _user;
    private ActionURL _url;
    private File _root;
    private BindException _errors;

    public StudyImporter(Container c, User user, ActionURL url, File root, BindException errors)
    {
        _c = c;
        _user = user;
        _url = url;
        _root = root;
        _errors = errors;
    }

    private Study getStudy()
    {
        return getStudy(false);
    }

    private Study getStudy(boolean allowNullStudy)
    {
        Study study = StudyManager.getInstance().getStudy(_c);
        if (!allowNullStudy && study == null)
        {
            throw new IllegalStateException("Study does not exist.");
        }
        return study;
    }

    public boolean process() throws SQLException, ServletException, IOException, SAXException, ParserConfigurationException
    {
        File file = new File(_root, "study.xml");

        _log.info("Loading study: " + file.getAbsolutePath());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        DocumentBuilder db = dbf.newDocumentBuilder();

        Document doc = db.parse(file);

        Node rootNode = doc.getDocumentElement();

        if (!rootNode.getNodeName().equalsIgnoreCase("study"))
            throw new ServletException("Invalid study.xml");

        {
            StudyController.StudyPropertiesForm form = new StudyController.StudyPropertiesForm();
            form.setLabel(DOMUtil.getAttributeValue(rootNode, "label"));
            form.setDateBased(Boolean.parseBoolean(DOMUtil.getAttributeValue(rootNode, "dateBased")));
            form.setStartDate(new Date(DateUtil.parseDateTime(DOMUtil.getAttributeValue(rootNode, "startDate"))));
            form.setSecurityType(SecurityType.valueOf(DOMUtil.getAttributeValue(rootNode, "securityType")));

            StudyController.createStudy(getStudy(true), _c, _user, form);
        }

        for (Node child : DOMUtil.getChildNodes(rootNode, Node.ELEMENT_NODE))
        {
            String name = child.getNodeName();

            if ("visits".equals(name))
            {
                String source = DOMUtil.getAttributeValue(child, "source");
                File visitMap = new File(_root, source);

                if (visitMap.exists())
                {
                    String content = PageFlowUtil.getFileContentsAsString(visitMap);

                    VisitMapImporter importer = new VisitMapImporter();
                    List<String> errorMsg = new LinkedList<String>();

                    if (!importer.process(_user, getStudy(), content, VisitMapImporter.Format.DataFax, errorMsg))
                    {
                        for (String error : errorMsg)
                            _errors.reject("uploadVisitMap", error);

                        return false;
                    }
                }

                VisitManager visitManager = StudyManager.getInstance().getVisitManager(getStudy());

                for (Node visitNode : DOMUtil.getChildNodesWithName(child, "visit"))
                {
                    // Just a proof of concept -- only works for "show by default".  TODO: Move to visit map or move entire visit map into xml?
                    double sequenceNum = Double.parseDouble(DOMUtil.getAttributeValue(visitNode, "sequenceNum"));
                    Visit visit = visitManager.findVisitBySequence(sequenceNum);
                    Visit mutable = visit.createMutable();
                    mutable.setShowByDefault(Boolean.parseBoolean(DOMUtil.getAttributeValue(visitNode, "showByDefault")));
                    StudyManager.getInstance().updateVisit(_user, mutable);
                }
            }
            else if ("cohorts".equals(name))
            {
                String cohortType = DOMUtil.getAttributeValue(child, "type");
                assert cohortType.equals("automatic");
                Integer dataSetId = Integer.parseInt(DOMUtil.getAttributeValue(child, "dataSetId"));
                String dataSetProperty = DOMUtil.getAttributeValue(child, "dataSetProperty");

                CohortController.updateAutomaticCohort(getStudy(), _user, dataSetId, dataSetProperty);
            }
            else if ("qcStates".equals(name))
            {
                // TODO: Generalize to all qc state properties
                StudyController.ManageQCStatesForm form = new StudyController.ManageQCStatesForm();
                form.setShowPrivateDataByDefault(Boolean.parseBoolean(DOMUtil.getAttributeValue(child, "showPrivateDataByDefault")));
                StudyController.updateQcState(getStudy(), _user, form);
            }
            else if ("datasets".equals(name))
            {
                String schemaSource = null;
                String labelColumn = null;
                String typeNameColumn = null;
                String typeIdColumn = null;
                String datasetSource = null;

                for (Node subElement : DOMUtil.getChildNodes(child, Node.ELEMENT_NODE))
                {
                    String subName = subElement.getNodeName();

                    if ("schemas".equals(subName))
                    {
                        schemaSource = DOMUtil.getAttributeValue(subElement, "source");
                        labelColumn = DOMUtil.getAttributeValue(subElement, "labelColumn");
                        typeNameColumn = DOMUtil.getAttributeValue(subElement, "typeNameColumn");
                        typeIdColumn = DOMUtil.getAttributeValue(subElement, "typeIdColumn");
                    }
                    else if ("definition".equals(subName))
                    {
                        datasetSource = DOMUtil.getAttributeValue(subElement, "source");
                    }
                }

                File schemas = new File(_root, schemaSource);

                if (schemas.exists())
                {
                    if (!StudyManager.getInstance().bulkImportTypes(getStudy(), schemas, _user, labelColumn, typeNameColumn, typeIdColumn, _errors))
                        return false;
                }

                File datasetFile = new File(_root, datasetSource);

                if (datasetFile.exists())
                {
                    submitStudyBatch(getStudy(), datasetFile, _c, _user, _url);
                }
            }
            else if ("specimens".equals(name))
            {
                boolean simple = Boolean.parseBoolean(DOMUtil.getAttributeValue(child, "simpleRepository", "true"));
                StudyController.updateRepositorySettings(_c, simple);

                String source = DOMUtil.getAttributeValue(child, "source");
                File specimenFile = new File(_root, source);

                SpringSpecimenController.submitSpecimenBatch(_c, _user, _url, specimenFile);
            }
            else
            {
                throw new RuntimeException("study.xml format problem: unknown node '" + name + "'");
            }
        }

        _log.info("Finished loading study: " + file.getAbsolutePath());

        return true;
    }

    public static void submitStudyBatch(Study study, File datasetFile, Container c, User user, ActionURL url) throws IOException, DatasetLockExistsException, SQLException
    {
        if (null == datasetFile || !datasetFile.exists() || !datasetFile.isFile())
        {
            HttpView.throwNotFound();
            return;
        }

        File lockFile = StudyPipeline.lockForDataset(study, datasetFile);
        if (!datasetFile.canRead() || lockFile.exists())
        {
            throw new DatasetLockExistsException();
        }

        DatasetBatch batch = new DatasetBatch(new ViewBackgroundInfo(c, user, url), datasetFile);
        batch.submit();
    }

    public static class DatasetLockExistsException extends ServletException {}
}
