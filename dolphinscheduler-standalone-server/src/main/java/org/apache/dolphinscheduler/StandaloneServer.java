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

package org.apache.dolphinscheduler;

import org.apache.curator.test.TestingServer;

import java.io.IOException;
import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@SpringBootApplication
public class StandaloneServer {
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        final TestingServer server = new TestingServer(true);
        System.setProperty("registry.zookeeper.connect-string", server.getConnectString());
        SpringApplication.run(StandaloneServer.class, args);
    }

//    @Bean
//    public Jackson2ObjectMapperBuilderCustomizer customJackson() {
//
//        SimpleModule module = new SimpleModule();
//        // 添加一个自定义Deserializer
//        module.addDeserializer(String.class, new StdScalarDeserializer<String>(String.class) {
//            @Override
//            public String deserialize(JsonParser p, DeserializationContext ctxt)
//                    throws IOException, JsonProcessingException {
//                return p.getValueAsString() == null ? null : p.getValueAsString().trim(); // 去掉头尾空格
//            }
//        });
//
//        return builder -> builder
//                // 在序列化时将日期转化为时间戳
////                .featuresToEnable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
////                .featuresToEnable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
//                // 在序列化枚举对象时使用toString方法
////                .featuresToEnable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
//                // 在反序列化枚举对象时使用toString方法
////                .featuresToEnable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
////                .featuresToEnable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
//                // 日期和时间格式："yyyy-MM-dd HH:mm:ss"
//                .simpleDateFormat("yyyy-MM-dd HH:mm:ss")
//                .timeZone("GMT+8:00")
//                ;
//    }
}
