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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.ViewScoped;
import org.signserver.common.CryptoTokenAuthenticationFailureException;
import org.signserver.common.CryptoTokenOfflineException;
import org.signserver.common.InvalidWorkerIdException;
import org.signserver.common.WorkerIdentifier;
import org.signserver.admin.web.ejb.AdminNotAuthorizedException;
import org.signserver.admin.web.ejb.AdminWebSessionBean;

/**
 *
 * @author Markus Kilås
 * @version $Id$
 */
@ManagedBean
@ViewScoped
public class WorkersBean {

    @EJB
    private AdminWebSessionBean workerSessionBean;

    @ManagedProperty(value = "#{authenticationBean}")
    private AuthenticationBean authBean;

    private String workersRequestedSelected;
    private Map<Integer, Boolean> selectedIds;

    private List<Worker> workers;

    private String activatePassword;

    /**
     * Creates a new instance of WorkersManagedBean
     */
    public WorkersBean() {

    }

    public AuthenticationBean getAuthBean() {
        return authBean;
    }

    public void setAuthBean(AuthenticationBean authBean) {
        this.authBean = authBean;
    }

    public String getWorkersRequestedSelected() {
        return workersRequestedSelected;
    }

    public void setWorkersRequestedSelected(String workersRequestedSelected) {
        this.workersRequestedSelected = workersRequestedSelected;
    }

    @SuppressWarnings("UseSpecificCatch")
    public List<Worker> getWorkers() throws AdminNotAuthorizedException {
        if (workers == null) {
            workers = new ArrayList<>();
            for (int id : workerSessionBean.getAllWorkers(authBean.getAdminCertificate())) {
                Properties config = workerSessionBean.getCurrentWorkerConfig(authBean.getAdminCertificate(), id).getProperties();
                final String name = config.getProperty("NAME", String.valueOf(id));
                Worker w = new Worker(id, true, name, config);
                try {
                    w.setStatus(workerSessionBean.getStatus(authBean.getAdminCertificate(), new WorkerIdentifier(id)).getFatalErrors().isEmpty() ? "ACTIVE" : "OFFLINE");
                } catch (Throwable ignored) { // NOPMD: We safe-guard for bugs in worker implementations and don't want the GUI to fail for those.
                    w.setStatus("OFFLINE");
                }

                workers.add(w);
            }
        }
        return workers;
    }

    public String getActivatePassword() {
        return activatePassword;
    }

    public void setActivatePassword(String activatePassword) {
        this.activatePassword = activatePassword;
    }

    public Map<Integer, Boolean> getSelectedIds() {
        if (selectedIds == null) {
            selectedIds = new HashMap<>();
            if (workersRequestedSelected != null) {
                String[] split = workersRequestedSelected.split(",");
                for (String s : split) {
                    s = s.trim();
                    if (!s.isEmpty()) {
                        selectedIds.put(Integer.valueOf(s.trim()), Boolean.TRUE);
                    }
                }
            }
        }
        return selectedIds;
    }

    public List<Worker> getSelectedWorkers() throws AdminNotAuthorizedException {
        final ArrayList<Worker> results = new ArrayList<>(selectedIds.size());
        for (Worker worker : getWorkers()) {
            if (Boolean.TRUE.equals(selectedIds.get(worker.getId()))) {
                results.add(worker);
            }
        }
        return results;
    }

    public String bulkAction(String page) {
        StringBuilder sb = new StringBuilder();
        sb.append(page);
        sb.append("?faces-redirect=true&amp;includeViewParams=true&amp;workers=");
        for (Map.Entry<Integer, Boolean> entry : getSelectedIds().entrySet()) {
            if (entry.getValue()) {
                sb.append(entry.getKey()).append(",");
            }
        }
        return sb.toString();
    }

    public String activateStep2Action() throws AdminNotAuthorizedException {
        System.out.println("step2");
        System.out.println("selected workers: " + getSelectedIds());
        for (Worker worker : getSelectedWorkers()) {
            try {
                workerSessionBean.activateSigner(authBean.getAdminCertificate(), new WorkerIdentifier(worker.getId()), activatePassword);
                selectedIds.remove(worker.getId());
            } catch (CryptoTokenAuthenticationFailureException | CryptoTokenOfflineException | InvalidWorkerIdException ex) {
                worker.setError("Failed: " + ex.getMessage());
            }
        }

        if (selectedIds.isEmpty()) {
            return "workers";
        } else {
            return "";
        }
    }

}