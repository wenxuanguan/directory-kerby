/**
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.kerby.kerberos.kerb.gss.impl;


import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sun.security.jgss.GSSCaller;

import javax.security.auth.kerberos.KerberosKey;
import javax.security.auth.kerberos.KerberosPrincipal;
import javax.security.auth.kerberos.KeyTab;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

public final class GssAcceptCred extends GssCredElement {

    private static final Logger LOG = LoggerFactory.getLogger(GssAcceptCred.class);

    private final KeyTab keyTab;
    private final Set<KerberosKey> kerberosKeySet;

    public static GssAcceptCred getInstance(final GSSCaller caller,
                                            GssNameElement name, int lifeTime) throws GSSException {

        // Try to get a keytab first
        KeyTab keyTab = getKeyTab(name);
        Set<KerberosKey> kerberosKeySet = null;
        if (keyTab == null) {
            // Otherwise try to get a kerberos key
            if (name == null) {
                kerberosKeySet = CredUtils.getKerberosKeysFromContext(caller, null, null);
            } else {
                kerberosKeySet = CredUtils.getKerberosKeysFromContext(caller, name.getPrincipalName().getName(), null);
            }
        }

        if (keyTab == null && kerberosKeySet == null) {
            String error = "Failed to find any Kerberos credential";
            if (name != null) {
                error +=  " for " + name.getPrincipalName().getName();
            }
            throw new GSSException(GSSException.NO_CRED, -1, error);
        }

        if (name == null) {
            if (keyTab != null) {
                try {
                    Method m = keyTab.getClass().getDeclaredMethod("getPrincipal");
                    KerberosPrincipal princ = (KerberosPrincipal) m.invoke(keyTab);
                    name = GssNameElement.getInstance(princ.getName(),
                                                      GSSName.NT_HOSTBASED_SERVICE);
                } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                    | IllegalArgumentException | InvocationTargetException e) {
                    String error = "Can't get a principal from the keytab";
                    LOG.info(error, e);
                    throw new GSSException(GSSException.NO_CRED, -1, error);
                }
            } else {
                name = GssNameElement.getInstance(
                    kerberosKeySet.iterator().next().getPrincipal().getName(),
                    GSSName.NT_HOSTBASED_SERVICE);
            }
        }

        return new GssAcceptCred(caller, name, keyTab, lifeTime, kerberosKeySet);
    }

    private static KeyTab getKeyTab(GssNameElement name) throws GSSException {
        if (name == null) {
            return CredUtils.getKeyTabFromContext(null);
        } else {
            KerberosPrincipal princ = new KerberosPrincipal(name.getPrincipalName().getName(),
                                                            name.getPrincipalName().getNameType().getValue());
            return CredUtils.getKeyTabFromContext(princ);
        }
    }

    private GssAcceptCred(GSSCaller caller, GssNameElement name, KeyTab keyTab,
                          int lifeTime, Set<KerberosKey> kerberosKeySet) {
        super(caller, name);
        this.keyTab = keyTab;
        this.accLifeTime = lifeTime;
        this.kerberosKeySet = kerberosKeySet;
    }

    public boolean isInitiatorCredential() throws GSSException {
        return false;
    }

    public boolean isAcceptorCredential() throws GSSException {
        return true;
    }

    public KeyTab getKeyTab() {
        return this.keyTab;
    }

    public EncryptionKey getEncryptionKey(int encryptType, int kvno) {

        if (kerberosKeySet != null) {
            KerberosKey[] keys = kerberosKeySet.toArray(new KerberosKey[kerberosKeySet.size()]);
            // We don't check the kvno here - see DIRKRB-638
            return GssUtil.getEncryptionKey(keys, encryptType);
        }

        // Otherwise get it from the keytab
        KerberosPrincipal princ = new KerberosPrincipal(name.getPrincipalName().getName(),
                                                        name.getPrincipalName().getNameType().getValue());
        KerberosKey[] keys = keyTab.getKeys(princ);
        return GssUtil.getEncryptionKey(keys, encryptType, kvno);
    }
}
