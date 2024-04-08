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
package org.signserver.server.signers;

import java.util.List;
import org.apache.log4j.Logger;
import org.signserver.common.WorkerStatusInfo;
import org.signserver.server.IServices;
import org.signserver.server.cryptotokens.ICryptoTokenV4;

/**
 * Worker not performing any operations on its own.
 * Meant as a placeholder for a crypto token to be referenced from an other
 * worker.
 * @author Markus Kilås
 * @version $Id$
 */
public class CryptoWorker extends NullSigner {

    private static final Logger LOG = Logger.getLogger(CryptoWorker.class);
    private static final String WORKER_TYPE = "CryptoWorker";

    @Override
    protected boolean isNoCertificates() {
        return true;
    }

    @Override
    public WorkerStatusInfo getStatus(List<String> additionalFatalErrors, final IServices services) {
        WorkerStatusInfo status = super.getStatus(additionalFatalErrors, services);

        status.setWorkerType(WORKER_TYPE);
        return status;
    }

    public boolean requiresTransaction(final IServices services) {
        try {
            ICryptoTokenV4 cryptoToken = super.getCryptoToken(services);
            if (cryptoToken == null) {
                return false;
            }
            return cryptoToken.requiresTransactionForSigning();
        } catch (Exception e) {
            LOG.warn("Unable to determine whether a worker requires a transaction. Defaulting to False.", e);
            return false;
        }
    }

}
