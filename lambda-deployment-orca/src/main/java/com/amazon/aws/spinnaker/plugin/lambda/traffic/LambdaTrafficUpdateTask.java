/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.amazon.aws.spinnaker.plugin.lambda.traffic;


import com.amazon.aws.spinnaker.plugin.lambda.*;
import com.amazon.aws.spinnaker.plugin.lambda.traffic.model.LambdaBaseStrategyInput;
import com.amazon.aws.spinnaker.plugin.lambda.utils.LambdaCloudDriverUtils;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import org.apache.commons.lang3.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LambdaTrafficUpdateTask implements LambdaStageBaseTask {
    private static Logger logger = LoggerFactory.getLogger(LambdaTrafficUpdateTask.class);

    private String cloudDriverUrl;

    @Autowired
    TrafficUpdateStrategyInjector injector;

    @Autowired
    CloudDriverConfigurationProperties props;

    @Autowired
    private LambdaCloudDriverUtils utils;

    @NotNull
    @Override
    public TaskResult execute(@NotNull StageExecution stage) {
        logger.debug("Executing LambdaTrafficUpdateTask...");
        cloudDriverUrl = props.getCloudDriverBaseUrl();
        prepareTask(stage);
        LambdaCloudOperationOutput result = null;
        BaseDeploymentStrategy strat = getDeploymentStrategy(stage);
        if (shouldUpdateAlias(stage)) {
            LambdaBaseStrategyInput input = strat.setupInput(stage);
            result = strat.deploy(input);
            if (result == null) {
                return formErrorTaskResult(stage, "Deployment failed");
            }
            final StageExecution tmpStage = stage;
            result.getOutputMap().forEach((x,y) -> {
                addToTaskContext(tmpStage, (String)x, y);
            });
        }
        else {
            result = LambdaCloudOperationOutput.builder().build();
        }
        addCloudOperationToContext(stage, result, "url");
        return taskComplete(stage);
    }

    private BaseDeploymentStrategy getDeploymentStrategy(StageExecution stage) {
        return injector.getStrategy(DeploymentStrategyEnum.valueOf((String)stage.getContext().get("deploymentStrategy")));
    }
    private boolean shouldUpdateAlias(StageExecution stage) {
        return stage.getContext().containsKey("aliasName");
    }

    @Nullable
    @Override
    public TaskResult onTimeout(@NotNull StageExecution stage) {
        return null;
    }

    @Override
    public void onCancel(@NotNull StageExecution stage) {
    }
}
