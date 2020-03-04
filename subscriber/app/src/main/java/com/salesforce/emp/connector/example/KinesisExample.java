/*
 * Copyright (c) 2016, salesforce.com, inc.
 * All rights reserved.
 * Licensed under the BSD 3-Clause license.
 * For full license text, see LICENSE.TXT file in the repo root  or https://opensource.org/licenses/BSD-3-Clause
 */
package com.salesforce.emp.connector.example;

import static com.salesforce.emp.connector.LoginHelper.login;

import java.net.URL;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.eclipse.jetty.util.ajax.JSON;

import org.apache.commons.lang3.ObjectUtils;

import com.salesforce.emp.connector.BayeuxParameters;
import com.salesforce.emp.connector.EmpConnector;
import com.salesforce.emp.connector.LoginHelper;
import com.salesforce.emp.connector.TopicSubscription;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.kinesis.KinesisClientBuilder;
import software.amazon.kinesis.common.KinesisClientUtil;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.KinesisAsyncClient;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

/**
 * An example of using the EMP connector using login credentials
 *
 * @author hal.hildebrand
 * @since API v37.0
 */
public class KinesisExample {

    public static void main(String[] argv) throws Exception {
        long replayFrom = EmpConnector.REPLAY_FROM_EARLIEST;

        if (argv.length == 2) {
            replayFrom = Long.parseLong(argv[1]);
        }

        String topic = argv[0];
        
        SsmClient ssmClient = SsmClient.create();
        
        final String sandboxActive = KinesisExample.getParameter(ssmClient, "/sfdc/sandbox/active", false);

        final Boolean sandbox;
        if (sandboxActive.equals("0")) {
            sandbox = false;
        } else {
            sandbox = true;        
        }
        
        final String domain;
        final String username;
        final String password;
        final String security_token;

        if (sandbox) {
            System.out.println("Sandbox");
            domain = KinesisExample.getParameter(ssmClient, "/sfdc/sandbox/domain", false);
            username = KinesisExample.getParameter(ssmClient, "/sfdc/sandbox/username", true);
            password = KinesisExample.getParameter(ssmClient, "/sfdc/sandbox/password", true);
            security_token = KinesisExample.getParameter(ssmClient, "/sfdc/sandbox/security_token", true);
        } else {
            System.out.println("NOT Sandbox");
            domain = "login";
            username = KinesisExample.getParameter(ssmClient, "/sfdc/username", true);
            password = KinesisExample.getParameter(ssmClient, "/sfdc/password", true);
            security_token = KinesisExample.getParameter(ssmClient, "/sfdc/security_token", true);
        }
        
        

        
        BearerTokenProvider tokenProvider = new BearerTokenProvider(() -> {
            try {                     
                return login(new URL("https://"+domain+".salesforce.com"), username, password+security_token);   
            } catch (Exception e) {
                e.printStackTrace(System.err);
                System.exit(1);
                throw new RuntimeException(e);
            }
        });        

        BayeuxParameters params = tokenProvider.login();

        Consumer<Map<String, Object>> consumer = event -> {
            System.out.println(String.format("Received event : \n%s", JSON.toString(event)));

            HashMap<String, HashMap> payload = (HashMap<String, HashMap>)event.get("payload");
            HashMap<String, String> changeEventHeader = (HashMap<String, String>)payload.get("ChangeEventHeader");
            
            String entityName = changeEventHeader.get("entityName");
            String kinesisStreamName = System.getenv("KINESIS_STREAM") != "" ? System.getenv("KINESIS_STREAM") : "sfdc-ChangeEvents";
            System.out.println("Kinesis stream: " + kinesisStreamName);
            new KinesisExample(kinesisStreamName).publishRecord(entityName, JSON.toString(event));   
        };

        EmpConnector connector = new EmpConnector(params);

        connector.setBearerTokenProvider(tokenProvider);

        connector.start().get(5, TimeUnit.SECONDS);

        TopicSubscription subscription = connector.subscribe(topic, replayFrom, consumer).get(5, TimeUnit.SECONDS);

        System.out.println(String.format("Subscribed: %s", subscription));
        
    }

    public static String getParameter(SsmClient ssmClient, String key, boolean withDecryption) {
        final GetParameterResponse result = ssmClient
                .getParameter(request -> request.name(key).withDecryption(withDecryption));
        return result.parameter().value();
    }

    private final String streamName;    
    private final KinesisAsyncClient kinesisClient;

    private KinesisExample(String streamName) {
        this.streamName = streamName;
        this.kinesisClient = KinesisClientUtil.createKinesisAsyncClient(KinesisAsyncClient.builder());        
    }

    private void publishRecord(String partitionKey, String payload) {        
        PutRecordRequest request = PutRecordRequest.builder()
                .partitionKey(partitionKey)
                .streamName(streamName)
                .data(SdkBytes.fromUtf8String(payload))
                .build();
        try {
            kinesisClient.putRecord(request).get();            
        } catch (Exception e) {           
            System.out.println("error");
        }
    }
}
