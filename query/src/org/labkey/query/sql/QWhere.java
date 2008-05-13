/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

package org.labkey.query.sql;

import org.apache.commons.lang.ObjectUtils;
import org.apache.xmlbeans.QNameSet;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.labkey.api.data.CompareType;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryParseException;
import org.labkey.query.design.DgCompare;
import org.labkey.query.design.DgQuery;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.StringTokenizer;

public class QWhere extends QNode<QExpr>
{
    public QWhere()
    {
    }

    public void appendSource(SourceBuilder builder)
    {
        builder.pushPrefix("\nWHERE ");
        for (QExpr child : children())
        {
            boolean fParen = Operator.and.needsParentheses(child, child == getFirstChild());
            if (fParen)
            {
                builder.pushPrefix("(");
            }
            child.appendSource(builder);
            if (fParen)
            {
                builder.popPrefix(")");
            }
            builder.nextPrefix(" AND\n");
        }
        builder.popPrefix();
    }

    protected boolean addCompare(DgQuery.Where where, QExpr expr)
    {
        if (!(expr instanceof QOperator))
        {
            return false;
        }
        QOperator qop = (QOperator) expr;
        List<QExpr> children = qop.childList();
        if (children.size() < 1 || children.size() > 2)
        {
            return false;
        }
        Operator op = qop.getOperator();
        FieldKey field = children.get(0).getFieldKey();
        if (field == null)
            return false;
        String value = "";
        if (op == Operator.is_not || op == Operator.is)
        {
            if (children.size() != 2)
                return false;
            if (!(children.get(1) instanceof QNull))
                return false;
            DgCompare comp = where.addNewCompare();
            comp.setField(field.toString());
            comp.setOp(op == Operator.is ? CompareType.ISBLANK.getUrlKey() : CompareType.NONBLANK.getUrlKey());
            return true;
        }


        if (Operator.in == op)
        {
            if (children.size() != 2 && !(children.get(1) instanceof QExprList))
            {
                return false;
            }

            QExprList valueList = (QExprList)children.get(1);
            DgCompare comp = where.addNewCompare();
            comp.setField(field.toString());
            comp.setOp(CompareType.IN.getUrlKey());
            StringBuilder sb = new StringBuilder();
            String separator = "";
            for (QExpr child : valueList.children())
            {
                sb.append(separator);
                separator = ", ";
                if (!(child instanceof IConstant))
                {
                    return false;
                }
                sb.append(ObjectUtils.toString(((IConstant) child).getValue()));
            }
            comp.setLiteral(sb.toString());
            return true;
        }

        if (children.size() == 2)
        {
            QExpr operand = children.get(1);
            if (!(operand instanceof IConstant))
            {
                return false;
            }
            value = ObjectUtils.toString(((IConstant) operand).getValue());
        }

        CompareType ctOp;
        if (Operator.eq == op)
        {
            ctOp = CompareType.EQUAL;
        }
        else if (Operator.ne == op)
        {
            ctOp = CompareType.NEQ;
        }
        else if (Operator.gt == op)
        {
            ctOp = CompareType.GT;
        }
        else if (Operator.lt == op)
        {
            ctOp = CompareType.LT;
        }
        else if (Operator.ge == op)
        {
            ctOp = CompareType.GTE;
        }
        else if (Operator.le == op)
        {
            ctOp = CompareType.LTE;
        }
        else if (Operator.like == op)
        {
            if (value.length() < 2)
                return false;
            if (!value.endsWith("%"))
                return false;
            value = value.substring(0, value.length() - 1);
            if (value.startsWith("%"))
            {
                ctOp = CompareType.CONTAINS;
                value = value.substring(1);
            }
            else
            {
                ctOp = CompareType.STARTS_WITH;
            }
        }
        else
        {
            return false;
        }
        DgCompare comp = where.addNewCompare();
        comp.setField(field.toString());
        comp.setOp(ctOp.getUrlKey());
        comp.setLiteral(value);
        return true;
    }

    protected void fillWhere(DgQuery.Where where, QNode<QExpr> parent)
    {
        for (QExpr child : parent.children())
        {
            if (Operator.and.is(child))
            {
                fillWhere(where, child);
            }
            else if (!addCompare(where, child))
            {
                where.addNewSql().setStringValue(child.getSourceText());
            }
        }
    }

    public void fillWhere(DgQuery.Where where)
    {
        fillWhere(where, this);
    }

    protected QExpr getExpr(DgCompare compare)
    {
        CompareType op = CompareType.getByURLKey(compare.getOp());
        if (op == null)
        {
            return null;
        }
        QExpr field = QFieldKey.of(FieldKey.fromString(compare.getField()));

        if (op == CompareType.IN)
        {
            StringTokenizer st = new StringTokenizer(compare.getLiteral(), ";", false);
            QExprList exprList = new QExprList();
            while (st.hasMoreTokens())
            {
                String token = st.nextToken().trim();
                if (token.length() > 0)
                {
                    try
                    {
                        exprList.appendChild(new QNumber(Double.valueOf(token)));
                    }
                    catch (IllegalArgumentException iae)
                    {
                        exprList.appendChild(new QString(token));
                    }
                }
            }
            return Operator.in.expr(field, exprList);
        }

        QExpr value = null;
        if (compare.getLiteral() != null)
        {
            try
            {
                value = new QNumber(Double.valueOf(compare.getLiteral()));
            }
            catch (IllegalArgumentException iae)
            {
                value = new QString(compare.getLiteral());
            }
        }

        switch (op)
        {
            case EQUAL:
            case DATE_EQUAL:
                return Operator.eq.expr(field, value);
            case NEQ:
            case NEQ_OR_NULL:
            case DATE_NOT_EQUAL:
                return Operator.ne.expr(field, value);
            case GT:
                return Operator.gt.expr(field, value);
            case LT:
                return Operator.lt.expr(field, value);
            case GTE:
                return Operator.ge.expr(field, value);
            case LTE:
                return Operator.le.expr(field, value);
            case ISBLANK:
                return Operator.is.expr(field, new QNull());
            case NONBLANK:
                return Operator.is_not.expr(field, new QNull());
            case STARTS_WITH:
                return Operator.like.expr(field, new QString(compare.getLiteral() + "%"));
            case CONTAINS:
                return Operator.like.expr(field, new QString("%" + compare.getLiteral() + "%"));
        }
        return null;

    }

    public void updateWhere(DgQuery.Where where, List<? super QueryParseException> errors)
    {
        setFirstChild(null);

        QNameSet qnames = QNameSet.forArray(new QName[] {
                new QName("http://query.labkey.org/design", "compare"),
                new QName("http://query.labkey.org/design", "sql")
        });
        for (XmlObject child : where.selectChildren(qnames))
        {
            if (child instanceof DgCompare)
            {
                QExpr expr = getExpr((DgCompare)child);
                if (expr != null)
                    appendChild(expr);
            }
            else if (child instanceof XmlString)
            {
                XmlString sql = (XmlString)child;
                QExpr expr = QParser.parseExpr(sql.getStringValue(), errors); 
                appendChild(expr);
            }
        }
    }
}
