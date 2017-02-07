/*************************************************************************
 *                                                                       *
 *  SignServer: The OpenSource Automated Signing Server                  *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.signserver.admin.web;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import org.apache.log4j.Logger;
import org.cesecore.audit.AuditLogEntry;
import org.cesecore.audit.impl.integrityprotected.AuditRecordData;
import org.cesecore.util.query.elems.RelationalOperator;
import org.signserver.admin.web.adminws.QueryCondition;
import org.signserver.admin.web.adminws.QueryOrdering;
import org.signserver.common.SignServerException;
import org.signserver.admin.web.ejb.AdminNotAuthorizedException;
import org.signserver.admin.web.ejb.AdminWebSessionBean;

/**
 *
 * @author Markus Kilås
 * @version $Id$
 */
@ManagedBean
@ViewScoped
public class AuditLogEntryBean {

    /**
     * Logger for this class.
     */
    private static final Logger LOG = Logger.getLogger(AuditLogEntryBean.class);

    private static final List<QueryOrdering> ORDERINGS;

    @EJB
    private AdminWebSessionBean workerSessionBean;

    @ManagedProperty(value = "#{authenticationBean}")
    private AuthenticationBean authBean;

    private String sequenceNumber;
    private String node;
    private String queryError;
    private WebAuditLogEntry entry;

    static {
        QueryOrdering ordering = new QueryOrdering();
        ordering.setOrder(QueryOrdering.Order.DESC);
        ordering.setColumn(AuditRecordData.FIELD_TIMESTAMP);
        ORDERINGS = Collections.singletonList(ordering);
    }

    /**
     * Creates a new instance of AuditLogEntryBean.
     */
    public AuditLogEntryBean() {
    }

    public AuthenticationBean getAuthBean() {
        return authBean;
    }

    public void setAuthBean(AuthenticationBean authBean) {
        this.authBean = authBean;
    }

    public String getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(String sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public String getQueryError() throws AdminNotAuthorizedException {
        if (entry == null) {
            getEntry();
        }
        return queryError;
    }

    public WebAuditLogEntry getEntry() throws AdminNotAuthorizedException {
        if (entry == null && queryError == null) {
            final List<QueryCondition> conditions = Arrays.asList(
                    new QueryCondition(AuditRecordData.FIELD_SEQUENCENUMBER, RelationalOperator.EQ, sequenceNumber),
                    new QueryCondition(AuditRecordData.FIELD_NODEID, RelationalOperator.EQ, node));
            try {

                List<? extends AuditLogEntry> results = workerSessionBean.queryAuditLog(authBean.getAdminCertificate(),
                        0, 1,
                        conditions,
                        ORDERINGS);
                if (results == null || results.isEmpty()) {
                    queryError = "No results";
                } else {
                    entry = WebAuditLogEntry.fromAuditLogEntry(results.get(0));
                }

            } catch (SignServerException | EJBException ex) {
                queryError = ex.getMessage();
                LOG.error("Reload failed within the selected interval: " + ex.getMessage());
            }
        }
        return entry;
    }

}