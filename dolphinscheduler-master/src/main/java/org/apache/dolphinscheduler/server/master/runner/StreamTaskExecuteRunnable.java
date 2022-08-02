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

package org.apache.dolphinscheduler.server.master.runner;

import static org.apache.dolphinscheduler.common.Constants.DEFAULT_WORKER_GROUP;

import org.apache.dolphinscheduler.common.Constants;
import org.apache.dolphinscheduler.common.enums.Flag;
import org.apache.dolphinscheduler.common.enums.Priority;
import org.apache.dolphinscheduler.common.thread.ThreadUtils;
import org.apache.dolphinscheduler.common.utils.LoggerUtils;
import org.apache.dolphinscheduler.dao.entity.Environment;
import org.apache.dolphinscheduler.dao.entity.TaskDefinition;
import org.apache.dolphinscheduler.dao.entity.TaskInstance;
import org.apache.dolphinscheduler.dao.entity.Tenant;
import org.apache.dolphinscheduler.plugin.task.api.TaskChannel;
import org.apache.dolphinscheduler.plugin.task.api.TaskExecutionContext;
import org.apache.dolphinscheduler.plugin.task.api.enums.ExecutionStatus;
import org.apache.dolphinscheduler.plugin.task.api.model.Property;
import org.apache.dolphinscheduler.plugin.task.api.parameters.AbstractParameters;
import org.apache.dolphinscheduler.plugin.task.api.parameters.ParametersNode;
import org.apache.dolphinscheduler.plugin.task.api.parameters.resource.ResourceParametersHelper;
import org.apache.dolphinscheduler.plugin.task.api.parser.ParamUtils;
import org.apache.dolphinscheduler.remote.command.TaskDispatchCommand;
import org.apache.dolphinscheduler.remote.command.TaskExecuteRunningAckMessage;
import org.apache.dolphinscheduler.remote.command.TaskExecuteStartCommand;
import org.apache.dolphinscheduler.server.master.builder.TaskExecutionContextBuilder;
import org.apache.dolphinscheduler.server.master.config.MasterConfig;
import org.apache.dolphinscheduler.server.master.dispatch.ExecutorDispatcher;
import org.apache.dolphinscheduler.server.master.dispatch.context.ExecutionContext;
import org.apache.dolphinscheduler.server.master.dispatch.enums.ExecutorType;
import org.apache.dolphinscheduler.server.master.dispatch.exceptions.ExecuteException;
import org.apache.dolphinscheduler.server.master.event.StateEvent;
import org.apache.dolphinscheduler.server.master.event.StateEventHandleError;
import org.apache.dolphinscheduler.server.master.event.StateEventHandleException;
import org.apache.dolphinscheduler.server.master.metrics.TaskMetrics;
import org.apache.dolphinscheduler.server.master.processor.queue.TaskEvent;
import org.apache.dolphinscheduler.server.master.runner.task.StreamTaskProcessor;
import org.apache.dolphinscheduler.service.bean.SpringApplicationContext;
import org.apache.dolphinscheduler.service.process.ProcessService;
import org.apache.dolphinscheduler.service.task.TaskPluginManager;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.NonNull;

/**
 * stream task execute
 */
public class StreamTaskExecuteRunnable implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(StreamTaskExecuteRunnable.class);

    protected MasterConfig masterConfig;

    protected ProcessService processService;

    protected ExecutorDispatcher dispatcher;

    protected TaskDefinition taskDefinition;

    protected TaskPluginManager taskPluginManager;

    // todo remove task processor
    protected StreamTaskProcessor streamTaskProcessor;

    protected TaskExecuteStartCommand taskExecuteStartCommand;

    protected TaskInstance taskInstance;

    /**
     * task event queue
     */
    private final ConcurrentLinkedQueue<TaskEvent> taskEvents = new ConcurrentLinkedQueue<>();

    private TaskRunnableStatus taskRunnableStatus = TaskRunnableStatus.CREATED;

    public StreamTaskExecuteRunnable(TaskDefinition taskDefinition, TaskExecuteStartCommand taskExecuteStartCommand) {
        this.processService = SpringApplicationContext.getBean(ProcessService.class);
        this.masterConfig = SpringApplicationContext.getBean(MasterConfig.class);
        this.dispatcher = SpringApplicationContext.getBean(ExecutorDispatcher.class);
        this.taskPluginManager = SpringApplicationContext.getBean(TaskPluginManager.class);
        this.streamTaskProcessor = new StreamTaskProcessor();
        this.taskDefinition = taskDefinition;
        this.taskExecuteStartCommand = taskExecuteStartCommand;
    }

    public TaskInstance getTaskInstance() {
        return taskInstance;
    }

    @Override
    public void run() {
        // submit task
        taskInstance = newTaskInstance(taskDefinition);
        processService.saveTaskInstance(taskInstance);

        // dispatch task
        TaskExecutionContext taskExecutionContext = getTaskExecutionContext(taskInstance);
        if (taskExecutionContext == null) {
            taskInstance.setState(ExecutionStatus.FAILURE);
            processService.saveTaskInstance(taskInstance);
            return;
        }

        TaskDispatchCommand dispatchCommand = new TaskDispatchCommand(taskExecutionContext,
            masterConfig.getMasterAddress(),
            taskExecutionContext.getHost(),
            System.currentTimeMillis());

        ExecutionContext executionContext = new ExecutionContext(dispatchCommand.convert2Command(), ExecutorType.WORKER, taskExecutionContext.getWorkerGroup(), taskInstance);
        Boolean dispatchSuccess = false;
        try {
            dispatchSuccess = dispatcher.dispatch(executionContext);
        } catch (ExecuteException e) {
            logger.error("Master dispatch task to worker error, taskInstanceId: {}, worker: {}",
                taskInstance.getId(),
                executionContext.getHost(),
                e);
        }
        if (!dispatchSuccess) {
            logger.info("Master failed to dispatch task to worker, taskInstanceId: {}, worker: {}",
                taskInstance.getId(),
                executionContext.getHost());

            // set task instance fail
            taskInstance.setState(ExecutionStatus.FAILURE);
            processService.saveTaskInstance(taskInstance);
            return;
        }

        logger.info("Master success dispatch task to worker, taskInstanceId: {}, worker: {}",
            taskInstance.getId(),
            executionContext.getHost());
    }

    public boolean isStart() {
        return TaskRunnableStatus.STARTED == taskRunnableStatus;
    }

    public boolean addTaskEvent(TaskEvent taskEvent) {
        if (taskInstance.getId() != taskEvent.getTaskInstanceId()) {
            logger.info("state event would be abounded, taskInstanceId:{}, eventType:{}, state:{}", taskEvent.getTaskInstanceId(), taskEvent.getEvent(), taskEvent.getState());
            return false;
        }
        taskEvents.add(taskEvent);
        return true;
    }

    public int eventSize() {
        return this.taskEvents.size();
    }

    /**
     * handle event
     */
    public void handleEvents() {
        if (!isStart()) {
            logger.info(
                "The stream task instance is not started, will not handle its state event, current state event size: {}",
                taskEvents.size());
            return;
        }
        TaskEvent taskEvent = null;
        while (!this.taskEvents.isEmpty()) {
            try {
                taskEvent = this.taskEvents.peek();
                LoggerUtils.setTaskInstanceIdMDC(taskEvent.getTaskInstanceId());

                logger.info("Begin to handle state event, {}", taskEvent);
                if (this.handleTaskEvent(taskEvent)) {
                    this.taskEvents.remove(taskEvent);
                }
            } catch (StateEventHandleError stateEventHandleError) {
                logger.error("State event handle error, will remove this event: {}", taskEvent, stateEventHandleError);
                this.taskEvents.remove(taskEvent);
                ThreadUtils.sleep(Constants.SLEEP_TIME_MILLIS);
            } catch (StateEventHandleException stateEventHandleException) {
                logger.error("State event handle error, will retry this event: {}",
                    taskEvent,
                    stateEventHandleException);
                ThreadUtils.sleep(Constants.SLEEP_TIME_MILLIS);
            } catch (Exception e) {
                // we catch the exception here, since if the state event handle failed, the state event will still keep in the stateEvents queue.
                logger.error("State event handle error, get a unknown exception, will retry this event: {}",
                    taskEvent,
                    e);
                ThreadUtils.sleep(Constants.SLEEP_TIME_MILLIS);
            } finally {
                LoggerUtils.removeWorkflowAndTaskInstanceIdMDC();
            }
        }
    }

    public TaskInstance newTaskInstance(TaskDefinition taskDefinition) {
        TaskInstance taskInstance = new TaskInstance();
        taskInstance.setTaskCode(taskDefinition.getCode());
        taskInstance.setTaskDefinitionVersion(taskDefinition.getVersion());
        taskInstance.setName(taskDefinition.getName());
        // task instance state
        taskInstance.setState(ExecutionStatus.SUBMITTED_SUCCESS);
        // set process instance id to 0
        taskInstance.setProcessInstanceId(0);
        // task instance type
        taskInstance.setTaskType(taskDefinition.getTaskType().toUpperCase());
        // task instance whether alert
        taskInstance.setAlertFlag(Flag.NO);

        // task instance start time
        taskInstance.setStartTime(null);

        // task instance flag
        taskInstance.setFlag(Flag.YES);

        // task instance current retry times
        taskInstance.setRetryTimes(0);
        taskInstance.setMaxRetryTimes(taskDefinition.getFailRetryTimes());
        taskInstance.setRetryInterval(taskDefinition.getFailRetryInterval());

        //set task param
        taskInstance.setTaskParams(taskDefinition.getTaskParams());

        //set task group and priority
        taskInstance.setTaskGroupId(taskDefinition.getTaskGroupId());
        taskInstance.setTaskGroupPriority(taskDefinition.getTaskGroupPriority());

        //set task cpu quota and max memory
        taskInstance.setCpuQuota(taskDefinition.getCpuQuota());
        taskInstance.setMemoryMax(taskDefinition.getMemoryMax());

        // task instance priority
        taskInstance.setTaskInstancePriority(Priority.MEDIUM);
        if (taskDefinition.getTaskPriority() != null) {
            taskInstance.setTaskInstancePriority(taskDefinition.getTaskPriority());
        }

        // delay execution time
        taskInstance.setDelayTime(taskDefinition.getDelayTime());

        // task dry run flag
        taskInstance.setDryRun(taskExecuteStartCommand.getDryRun());

        taskInstance.setWorkerGroup(StringUtils.isBlank(taskDefinition.getWorkerGroup()) ? DEFAULT_WORKER_GROUP : taskDefinition.getWorkerGroup());
        taskInstance.setEnvironmentCode(taskDefinition.getEnvironmentCode() == 0 ? -1 : taskDefinition.getEnvironmentCode());

        if (!taskInstance.getEnvironmentCode().equals(-1L)) {
            Environment environment = processService.findEnvironmentByCode(taskInstance.getEnvironmentCode());
            if (Objects.nonNull(environment) && StringUtils.isNotEmpty(environment.getConfig())) {
                taskInstance.setEnvironmentConfig(environment.getConfig());
            }
        }
        return taskInstance;
    }

    /**
     * get TaskExecutionContext
     *
     * @param taskInstance taskInstance
     * @return TaskExecutionContext
     */
    protected TaskExecutionContext getTaskExecutionContext(TaskInstance taskInstance) {
        int userId = taskInstance.getProcessDefine() == null ? 0 : taskInstance.getProcessDefine().getUserId();
        Tenant tenant = processService.getTenantForProcess(taskInstance.getProcessInstance().getTenantId(), userId);

        // verify tenant is null
        if (tenant == null) {
            logger.error("tenant not exists,task instance id : {}",
                taskInstance.getId());
            return null;
        }

        // set queue for process instance, user-specified queue takes precedence over tenant queue
        String userQueue = processService.queryUserQueueByProcessInstance(taskInstance.getProcessInstance());
        taskInstance.getProcessInstance().setQueue(org.apache.dolphinscheduler.spi.utils.StringUtils.isEmpty(userQueue) ? tenant.getQueue() : userQueue);
        taskInstance.getProcessInstance().setTenantCode(tenant.getTenantCode());
        taskInstance.setResources(streamTaskProcessor.getResourceFullNames(taskInstance));

        TaskChannel taskChannel = taskPluginManager.getTaskChannel(taskInstance.getTaskType());
        ResourceParametersHelper resources = taskChannel.getResources(taskInstance.getTaskParams());
        streamTaskProcessor.setTaskResourceInfo(resources);

        AbstractParameters baseParam = taskPluginManager.getParameters(ParametersNode.builder().taskType(taskInstance.getTaskType()).taskParams(taskInstance.getTaskParams()).build());
        Map<String, Property> propertyMap = paramParsingPreparation(taskInstance, baseParam);
        return TaskExecutionContextBuilder.get()
            .buildTaskInstanceRelatedInfo(taskInstance)
            .buildTaskDefinitionRelatedInfo(taskInstance.getTaskDefine())
            .buildProcessInstanceRelatedInfo(taskInstance.getProcessInstance())
            .buildProcessDefinitionRelatedInfo(taskInstance.getProcessDefine())
            .buildResourceParametersInfo(resources)
            .buildBusinessParamsMap(new HashMap<>())
            .buildParamInfo(propertyMap)
            .create();
    }

    protected boolean handleTaskEvent(TaskEvent taskEvent)  throws StateEventHandleException, StateEventHandleError {
        measureTaskState(taskEvent);

        if (taskInstance.getState() == null) {
            throw new StateEventHandleError("Task state event handle error due to task state is null");
        }

        taskInstance.setStartTime(taskEvent.getStartTime());
        taskInstance.setHost(taskEvent.getWorkerAddress());
        taskInstance.setLogPath(taskEvent.getLogPath());
        taskInstance.setExecutePath(taskEvent.getExecutePath());
        taskInstance.setPid(taskEvent.getProcessId());
        taskInstance.setAppLink(taskEvent.getAppIds());
        taskInstance.setState(taskEvent.getState());
        taskInstance.setEndTime(taskEvent.getEndTime());
        taskInstance.setVarPool(taskEvent.getVarPool());
        processService.changeOutParam(taskInstance);
        processService.updateTaskInstance(taskInstance);

        // send ack
        sendAckToWorker(taskEvent);

        if (taskInstance.getState().typeIsFinished()) {
            logger.info("The stream task instance is finish, taskInstanceId:{}, state:{}", taskInstance.getId(), taskEvent.getState());
        }

        return true;
    }

    private void measureTaskState(TaskEvent taskEvent) {
        if (taskEvent == null || taskEvent.getState() == null) {
            // the event is broken
            logger.warn("The task event is broken..., taskEvent: {}", taskEvent);
            return;
        }
        if (taskEvent.getState().typeIsFinished()) {
            TaskMetrics.incTaskInstanceByState("finish");
        }
        switch (taskEvent.getState()) {
            case STOP:
                TaskMetrics.incTaskInstanceByState("stop");
                break;
            case SUCCESS:
                TaskMetrics.incTaskInstanceByState("success");
                break;
            case FAILURE:
                TaskMetrics.incTaskInstanceByState("fail");
                break;
            default:
                break;
        }
    }

    public Map<String, Property> paramParsingPreparation(@NonNull TaskInstance taskInstance, @NonNull AbstractParameters parameters) {
        // assign value to definedParams here
        Map<String,String> globalParamsMap = taskExecuteStartCommand.getStartParams();
        Map<String, Property> globalParams = ParamUtils.getUserDefParamsMap(globalParamsMap);

        // combining local and global parameters
        Map<String, Property> localParams = parameters.getInputLocalParametersMap();

        //stream pass params
        parameters.setVarPool(taskInstance.getVarPool());
        Map<String, Property> varParams = parameters.getVarPoolMap();

        if (globalParams.isEmpty() && localParams.isEmpty() && varParams.isEmpty()) {
            return null;
        }

        if (varParams.size() != 0) {
            globalParams.putAll(varParams);
        }
        if (localParams.size() != 0) {
            globalParams.putAll(localParams);
        }

        return globalParams;
    }

    private void sendAckToWorker(TaskEvent taskEvent) {
        // If event handle success, send ack to worker to otherwise the worker will retry this event
        TaskExecuteRunningAckMessage taskExecuteRunningAckMessage =
            new TaskExecuteRunningAckMessage(ExecutionStatus.SUCCESS, taskEvent.getTaskInstanceId());
        taskEvent.getChannel().writeAndFlush(taskExecuteRunningAckMessage.convert2Command());
    }

    private enum TaskRunnableStatus {
        CREATED, STARTED,
        ;
    }
}
