/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.dolphinscheduler.server.worker.cache.impl;

import org.apache.dolphinscheduler.server.worker.cache.TaskExecuteThreadCacheManager;
import org.apache.dolphinscheduler.server.worker.runner.TaskExecuteThread;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

/**
 * task execution context cache
 */
@Component
public class TaskExecuteThreadCacheManagerImpl implements TaskExecuteThreadCacheManager {

    private Map<Integer, TaskExecuteThread> taskExecuteThreadMap = new ConcurrentHashMap<>();

    /**
     * cache
     *
     * @param taskExecuteThread taskExecuteThread
     */
    public void cache(TaskExecuteThread taskExecuteThread) {
        if (taskExecuteThread == null
                || taskExecuteThread.getTaskExecutionContext() == null
                || taskExecuteThread.getTaskExecutionContext().getTaskInstanceId() == 0) {
            return;
        }
        taskExecuteThreadMap.put(taskExecuteThread.getTaskExecutionContext().getTaskInstanceId(), taskExecuteThread);
    }

    /**
     * remove task execution context
     *
     * @param taskInstanceId taskInstanceId
     */
    public void remove(Integer taskInstanceId) {
        taskExecuteThreadMap.remove(taskInstanceId);
    }

    /**
     * get task execution context
     */
    public TaskExecuteThread get(Integer taskInstanceId) {
        return taskExecuteThreadMap.get(taskInstanceId);
    }

    /**
     * contains
     */
    public boolean contains(Integer taskInstanceId) {
        return taskExecuteThreadMap.containsKey(taskInstanceId);
    }

    /**
     * get all
     */
    public Collection<TaskExecuteThread> getAll() {
        return ImmutableList.copyOf(taskExecuteThreadMap.values());
    }
}
