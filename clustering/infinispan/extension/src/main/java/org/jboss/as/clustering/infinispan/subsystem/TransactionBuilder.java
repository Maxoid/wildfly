/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.clustering.infinispan.subsystem;

import static org.jboss.as.clustering.infinispan.subsystem.TransactionResourceDefinition.Attribute.LOCKING;
import static org.jboss.as.clustering.infinispan.subsystem.TransactionResourceDefinition.Attribute.MODE;
import static org.jboss.as.clustering.infinispan.subsystem.TransactionResourceDefinition.Attribute.STOP_TIMEOUT;

import javax.transaction.TransactionManager;
import javax.transaction.TransactionSynchronizationRegistry;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.TransactionConfiguration;
import org.infinispan.transaction.LockingMode;
import org.infinispan.transaction.tm.EmbeddedTransactionManager;
import org.jboss.as.clustering.dmr.ModelNodes;
import org.jboss.as.clustering.infinispan.TransactionManagerProvider;
import org.jboss.as.clustering.infinispan.TransactionSynchronizationRegistryProvider;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.txn.service.TxnServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.clustering.service.Builder;
import org.wildfly.clustering.service.OptionalInjectedValue;

/**
 * @author Paul Ferraro
 */
public class TransactionBuilder extends ComponentBuilder<TransactionConfiguration> {

    private final OptionalInjectedValue<TransactionManager> tm = new OptionalInjectedValue<>();
    private final OptionalInjectedValue<TransactionSynchronizationRegistry> tsr = new OptionalInjectedValue<>();

    private volatile LockingMode locking;
    private volatile long timeout;
    private volatile TransactionMode mode;

    public TransactionBuilder(PathAddress address) {
        super(CacheComponent.TRANSACTION, address);
    }

    @Override
    public ServiceBuilder<TransactionConfiguration> build(ServiceTarget target) {
        ServiceBuilder<TransactionConfiguration> builder = super.build(target);
        switch (this.mode) {
            case NONE: {
                break;
            }
            case BATCH: {
                this.tm.inject(EmbeddedTransactionManager.getInstance());
                break;
            }
            case NON_XA: {
                builder.addDependency(TxnServices.JBOSS_TXN_SYNCHRONIZATION_REGISTRY, TransactionSynchronizationRegistry.class, this.tsr);
            }
            default: {
                builder.addDependency(TxnServices.JBOSS_TXN_TRANSACTION_MANAGER, TransactionManager.class, this.tm);
            }
        }
        return builder;
    }

    @Override
    public Builder<TransactionConfiguration> configure(OperationContext context, ModelNode model) throws OperationFailedException {
        this.mode = ModelNodes.asEnum(MODE.resolveModelAttribute(context, model), TransactionMode.class);
        this.locking = ModelNodes.asEnum(LOCKING.resolveModelAttribute(context, model), LockingMode.class);
        this.timeout = STOP_TIMEOUT.resolveModelAttribute(context, model).asLong();
        return this;
    }

    @Override
    public TransactionConfiguration getValue() {
        return new ConfigurationBuilder().transaction()
                .lockingMode(this.locking)
                .cacheStopTimeout(this.timeout)
                .transactionManagerLookup(this.tm.getOptionalValue().map(TransactionManagerProvider::new).orElse(null))
                .transactionSynchronizationRegistryLookup(this.tsr.getOptionalValue().map(TransactionSynchronizationRegistryProvider::new).orElse(null))
                .transactionMode((this.mode == TransactionMode.NONE) ? org.infinispan.transaction.TransactionMode.NON_TRANSACTIONAL : org.infinispan.transaction.TransactionMode.TRANSACTIONAL)
                .useSynchronization(this.mode == TransactionMode.NON_XA)
                .recovery().enabled(this.mode == TransactionMode.FULL_XA).transaction()
                .create();
    }
}
