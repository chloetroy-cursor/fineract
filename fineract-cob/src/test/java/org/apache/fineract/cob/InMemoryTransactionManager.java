/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.fineract.cob;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SmartTransactionObject;

/**
 * A transaction manager without a database, for the one thread a test runs on. It can suspend a transaction for an
 * inner one, mark a joined transaction rollback-only and fail a commit, which is everything a transaction
 * synchronization can see of a real one.
 */
final class InMemoryTransactionManager extends AbstractPlatformTransactionManager {

    private static final long serialVersionUID = 1L;

    private transient InMemoryTransaction current;
    private boolean commitFails;

    void failCommits() {
        commitFails = true;
    }

    @Override
    protected Object doGetTransaction() {
        return new TransactionObject(current);
    }

    @Override
    protected boolean isExistingTransaction(Object transaction) {
        return ((TransactionObject) transaction).transaction != null;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        current = new InMemoryTransaction();
        ((TransactionObject) transaction).transaction = current;
    }

    @Override
    protected Object doSuspend(Object transaction) {
        InMemoryTransaction suspended = current;
        ((TransactionObject) transaction).transaction = null;
        current = null;
        return suspended;
    }

    @Override
    protected void doResume(Object transaction, Object suspendedResources) {
        current = (InMemoryTransaction) suspendedResources;
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
        if (commitFails) {
            throw new TransactionSystemException("the commit failed");
        }
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
        // nothing to undo
    }

    @Override
    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        ((TransactionObject) status.getTransaction()).transaction.rollbackOnly = true;
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        current = null;
    }

    private static final class InMemoryTransaction {

        private boolean rollbackOnly;
    }

    private static final class TransactionObject implements SmartTransactionObject {

        private InMemoryTransaction transaction;

        private TransactionObject(InMemoryTransaction transaction) {
            this.transaction = transaction;
        }

        @Override
        public boolean isRollbackOnly() {
            return transaction != null && transaction.rollbackOnly;
        }
    }
}
