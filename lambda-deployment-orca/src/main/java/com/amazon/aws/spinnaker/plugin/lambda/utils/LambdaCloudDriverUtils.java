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

package com.amazon.aws.spinnaker.plugin.lambda.utils;

import com.amazon.aws.spinnaker.plugin.lambda.verify.model.LambdaCloudDriverErrorObject;
import com.amazon.aws.spinnaker.plugin.lambda.verify.model.LambdaCloudDriverTaskResults;
import com.amazon.aws.spinnaker.plugin.lambda.verify.model.LambdaVerificationStatusOutput;
import com.amazon.aws.spinnaker.plugin.lambda.verify.model.LambdaCloudDriverResultObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class LambdaCloudDriverUtils {
    private static final Logger logger = LoggerFactory.getLogger(LambdaCloudDriverUtils.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CLOUDDRIVER_GET_PATH = "/functions";

    @Autowired
    CloudDriverConfigurationProperties props;

    public LambdaCloudDriverResponse postToCloudDriver(String endPointUrl, String jsonString) {
        RequestBody body = RequestBody.create(MediaType.parse("application/json"), jsonString);
        Request request = new Request.Builder()
                .url(endPointUrl)
                .post(body)
                .build();
        OkHttpClient client = new OkHttpClient();
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            String respString = response.body().string();
            if (200 != response.code()) {
                logger.error("Error calling cloud driver");
                logger.error(respString);
                throw new RuntimeException("Error calling cloud driver: " + respString);
            }
            logger.debug(respString);
            LambdaCloudDriverResponse respObj = objectMapper.readValue(respString, LambdaCloudDriverResponse.class);
            return respObj;
        } catch (Exception e) {
            logger.error("Error calling clouddriver.", e);
            throw new RuntimeException(e);
        }
    }


    public LambdaCloudDriverTaskResults verifyStatus(String endPoint) {
        String respString = getFromCloudDriver(endPoint);
        try {

            JsonNode jsonResults = objectMapper.readTree(respString);
            JsonNode statusNode = jsonResults.get("status");
            ArrayNode resultsNode = (ArrayNode)jsonResults.get("resultObjects");
            LambdaCloudDriverResultObject ro = null;
            LambdaCloudDriverErrorObject err = null;
            if ((resultsNode != null) && resultsNode.isArray()) {
                ro = objectMapper.convertValue(resultsNode.get(0), LambdaCloudDriverResultObject.class);
                err = objectMapper.convertValue(resultsNode.get(0), LambdaCloudDriverErrorObject.class);
            }
            LambdaVerificationStatusOutput st = objectMapper.convertValue(statusNode, LambdaVerificationStatusOutput.class);

            return LambdaCloudDriverTaskResults.builder().results(ro).status(st).errors(err).build();
        }
        catch (Exception e) {
            logger.error(String.format("Failed verifying task at {}", endPoint), e);
            throw new RuntimeException(e);
        }
    }


    public String getFromCloudDriver(String endPoint) {
        Request request = new Request.Builder()
                .url(endPoint)
                .get()
                .build();
        OkHttpClient client = new OkHttpClient();
        Call call = client.newCall(request);
        Map xx = new HashMap<String, String>();
        try {
            Response response = call.execute();
            String respString = response.body().string();
            return respString;
        }
        catch (Exception e) {
            logger.error("Exception verifying task", e);
            throw new RuntimeException(e);
        }
    }

    public boolean lambdaExists(LambdaGetInput inp) {
        LambdaGetOutput thisLambda = retrieveLambda(inp);
        return thisLambda != null;
    }

    public LambdaGetOutput retrieveLambda(LambdaGetInput inp) {
        //{{clouddriver_url}}/functions?functionName=a1-json_simple_lambda_222&region=us-west-2&account=aws-managed-1
        logger.debug("Retrieve Lambda");
        String cloudDriverUrl =  props.getCloudDriverBaseUrl();
        String region = inp.getRegion();
        String acc = inp.getAccount();
        String fName = inp.getFunctionName();
        String appPrefix = String.format("%s-", inp.getAppName());
        if (!fName.startsWith(appPrefix)) {
            fName = String.format("%s-%s", inp.getAppName(),inp.getFunctionName());
        }
        String url = cloudDriverUrl + CLOUDDRIVER_GET_PATH ;
        HttpUrl.Builder httpBuilder = HttpUrl.parse(url).newBuilder();
        httpBuilder.addQueryParameter("region", region);
        httpBuilder.addQueryParameter("account", acc);
        httpBuilder.addQueryParameter("functionName", fName);
        Request request = new Request.Builder().url(httpBuilder.build()).build();
        OkHttpClient client = new OkHttpClient();
        Call call = client.newCall(request);
        try {
            Response response = call.execute();
            if (200 != response.code()) {
                logger.error("Could not retrieve lambda");
                return null;
            }
            logger.debug("Found a function");
            String respString = response.body().string();
            LambdaGetOutput allItem = this.asObjectFromList(respString, LambdaGetOutput.class);
            return allItem;
        }
        catch (Exception e) {
            logger.error("Error calling clouddriver to find lambda.", e);
            throw new RuntimeException(e);
        }
    }

    public <T> T getInput(StageExecution stage, Class<T> type) {
        try {
            T ldi = objectMapper.convertValue(stage.getContext(), type);
            return ldi;
        }
        catch (Throwable e) {
            e.printStackTrace();
            logger.error("Could not convert value");
        }
        return null;
    }

    public <T> T asObjectFromList(String inpString, Class<T> type) {
        try {
            objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            List<T> someClassList = objectMapper.readValue(inpString, typeFactory.constructCollectionType(List.class, type));
            return someClassList.get(0);
        }
        catch (Throwable e) {
            e.printStackTrace();
            logger.error("Could not convert value");
        }
        return null;
    }

    public <T> T asObject(String inpString, Class<T> type) {
        try {
            objectMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            TypeFactory typeFactory = objectMapper.getTypeFactory();
            List<T> someClassList = objectMapper.readValue(inpString, typeFactory.constructCollectionType(List.class, type));
            T ldi = objectMapper.convertValue(inpString, type);
            return ldi;
        }
        catch (Throwable e) {
            e.printStackTrace();
            logger.error("Could not convert value");
        }
        return null;
    }

    public String asString(Object inp) {
        try {
            return objectMapper.writeValueAsString(inp);
        } catch (JsonProcessingException e) {
            logger.error("Could not jsonify", e);
            throw new RuntimeException(e);
        }
    }

    public String getCanonicalVersion(LambdaGetOutput lf, String canonicalVersion, int retentionNumber) {
        List<String> revisions = getSortedRevisions(lf);

        if (!canonicalVersion.startsWith("$")) {  // actual version
            return canonicalVersion;
        }

        if (canonicalVersion.startsWith("$LATEST")) { // latest version number
            return revisions.get(0);
        }

        if (canonicalVersion.startsWith("$OLDEST")) { // oldest version number
            return revisions.get(revisions.size() - 1);
        }

        if (canonicalVersion.startsWith("$PREVIOUS")) { // latest - 1 version number
            if (revisions.size() >= 2)
                return revisions.get(1);
            else
                return null;
        }

        if (canonicalVersion.startsWith("$MOVING")) { // list of versions
            if (revisions.size() > retentionNumber) {
                List<String> toRemoveList = revisions.subList(retentionNumber, revisions.size());
                return String.join(",", toRemoveList);
            }
        }
        // Couldnt find it.
        logger.error(String.format("Found invalid version string %s", canonicalVersion));
        return null;
    }

    public List<String> getSortedRevisions(LambdaGetOutput lf) {
        List<String> revisions =  lf.getRevisions().values().stream().collect(Collectors.toList());
        revisions = revisions.stream().filter(x -> {
            return !x.equals("$LATEST");
        }).collect(Collectors.toList());
        revisions = revisions.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        return revisions;
    }
    public LambdaGetOutput findLambda(StageExecution stage) {
        return findLambda(stage, false);
    }

    public LambdaGetOutput findLambda(StageExecution stage, boolean shouldRetry) {
        LambdaGetInput lgi = this.getInput(stage, LambdaGetInput.class);
        lgi.setAppName(stage.getExecution().getApplication());
        //LambdaGetOutput lf = (LambdaGetOutput)stage.getContext().get(LambdaStageConstants.lambdaObjectKey);
        LambdaGetOutput lf = this.retrieveLambda(lgi);
        int count = 0;
        while (lf == null && count < 5 && shouldRetry == true) {
            count++;
            lf = this.retrieveLambda((lgi));
            this.await(stage);
        }
        return lf;
    }

    public void await(StageExecution stage) {
        try {
            Thread.sleep(25000);
        }
        catch (Throwable e) {
            logger.error("Error awaiting lambda creation", e);
        }
    }
}
