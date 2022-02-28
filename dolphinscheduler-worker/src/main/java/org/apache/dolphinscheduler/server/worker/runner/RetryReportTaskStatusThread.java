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

package org.apache.dolphinscheduler.server.worker.runner;

import org.apache.dolphinscheduler.common.thread.Stopper;
import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.server.worker.cache.TaskExecuteThreadCacheManager;
import org.apache.dolphinscheduler.server.worker.processor.TaskCallbackService;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Retry Report Task Status Thread
 */
@Component
public class RetryReportTaskStatusThread implements Runnable {

    private final Logger logger = LoggerFactory.getLogger(RetryReportTaskStatusThread.class);

    /**
     * every 5 minutes
     */
    private static final long RETRY_REPORT_TASK_STATUS_INTERVAL = 5 * 60 * 1000L;

    @Autowired
    private TaskExecuteThreadCacheManager taskExecuteThreadCacheManager;

    @Autowired
    private TaskCallbackService taskCallbackService;

    public void start() {
        Thread thread = new Thread(this, "RetryReportTaskStatusThread");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * retry task feedback
     */
    @Override
    public void run() {
        while (Stopper.isRunning()) {

            // sleep 5 minutes
            ThreadUtils.sleep(RETRY_REPORT_TASK_STATUS_INTERVAL);

            try {
                Collection<TaskExecuteThread> taskExecuteThreads = taskExecuteThreadCacheManager.getAll();
                if (CollectionUtils.isEmpty(taskExecuteThreads)) {
                    return;
                }
                for (TaskExecuteThread taskExecuteThread : taskExecuteThreads) {
                    taskCallbackService.feedback(taskExecuteThread.getTaskExecutionContext());
                }
            } catch (Exception e) {
                logger.warn("retry report task status error", e);
            }
        }
    }
}
