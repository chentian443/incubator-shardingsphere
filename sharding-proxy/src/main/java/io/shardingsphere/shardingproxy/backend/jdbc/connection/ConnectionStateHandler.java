/*
 * Copyright 2016-2018 shardingsphere.io.
 * <p>
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
 * </p>
 */

package io.shardingsphere.shardingproxy.backend.jdbc.connection;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Connection state handler.
 *
 * @author zhaojun
 */
@RequiredArgsConstructor
public class ConnectionStateHandler {
    
    private volatile AtomicReference<ConnectionStatus> status = new AtomicReference<>(ConnectionStatus.INIT);
    
    private final ResourceSynchronizer resourceSynchronizer;
    
    /**
     * Change connection status using compare and set.
     *
     * @param expect expect status
     * @param update new update status
     * @return boolean set succeed or failed
     */
    public boolean compareAndSetStatus(final ConnectionStatus expect, final ConnectionStatus update) {
        return status.compareAndSet(expect, update);
    }
    
    /**
     * Change connection status using get and set.
     *
     * @param update new update status
     */
    public void getAndSetStatus(final ConnectionStatus update) {
        status.getAndSet(update);
        if (ConnectionStatus.TERMINATED == status.get()) {
            resourceSynchronizer.doNotify();
        }
    }
    
    /**
     * Get current connection status.
     *
     * @return connection status
     */
    public ConnectionStatus getStatus() {
        return status.get();
    }
    
    /**
     * Change connection status to running if necessary.
     */
    public void changeRunningStatusIfNecessary() {
        if (ConnectionStatus.INIT == status.get() || ConnectionStatus.TERMINATED == status.get()) {
            status.getAndSet(ConnectionStatus.RUNNING);
        }
    }
    
    /**
     * Judge whether connection is in transaction or not.
     *
     * @return true or false
     */
    public boolean isInTransaction() {
        return ConnectionStatus.TRANSACTION == status.get();
    }
    
    /**
     * Notify connection to finish wait if necessary.
     */
    public void doNotifyIfNecessary() {
        if (status.compareAndSet(ConnectionStatus.RUNNING, ConnectionStatus.RELEASE)) {
            resourceSynchronizer.doNotify();
        }
    }
    
    /**
     * Wait until connection is released if necessary.
     *
     * @throws InterruptedException interrupted exception
     */
    public void waitUntilConnectionReleasedIfNecessary() throws InterruptedException {
        if (ConnectionStatus.TRANSACTION != status.get() && ConnectionStatus.INIT != status.get() && ConnectionStatus.TERMINATED != status.get()) {
            while (!compareAndSetStatus(ConnectionStatus.RELEASE, ConnectionStatus.RUNNING)) {
                resourceSynchronizer.doAwait();
            }
        }
    }
}
