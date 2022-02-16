package org.apache.dolphinscheduler.service;

import org.apache.dolphinscheduler.common.utils.DateUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Date;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@JsonComponent
public class DateJsonSeserializer extends JsonSerializer<Date> {
    private final static Logger logger = LoggerFactory.getLogger(DateJsonSeserializer.class);

    @Override
    public void serialize(Date value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        logger.info("serialize...");
        gen.writeString(DateUtils.dateToString(value));
    }

//    @Override
//    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
//        System.out.println("createContextual");
//        if (property != null) {
//            System.out.println("createContextual: " + property.getMember());
//            JsonFormat.Value format = ctxt.getAnnotationIntrospector().findFormat(property.getMember());
//            if (format != null) {
//                TimeZone tz = format.getTimeZone();
//                // First: fully custom pattern?
//                if (format.hasPattern()) {
//                    final String pattern = format.getPattern();
//                    if (!FORMATS.contains(pattern)) {
//                        FORMATS.add(pattern);
//                    }
//                    final Locale loc = format.hasLocale() ? format.getLocale() : ctxt.getLocale();
//                    SimpleDateFormat df = new SimpleDateFormat(pattern, loc);
//                    if (tz == null) {
//                        tz = ctxt.getTimeZone();
//                    }
//                    df.setTimeZone(tz);
//                    return new DateJsonDeserializer(df, pattern);
//                }
//                // But if not, can still override timezone
//                if (tz != null) {
//                    DateFormat df = ctxt.getConfig().getDateFormat();
//                    // one shortcut: with our custom format, can simplify handling a bit
//                    if (df.getClass() == StdDateFormat.class) {
//                        final Locale loc = format.hasLocale() ? format.getLocale() : ctxt.getLocale();
//                        StdDateFormat std = (StdDateFormat) df;
//                        std = std.withTimeZone(tz);
//                        std = std.withLocale(loc);
//                        df = std;
//                    } else {
//                        // otherwise need to clone, re-set timezone:
//                        df = (DateFormat) df.clone();
//                        df.setTimeZone(tz);
//                    }
//                    return new DateJsonDeserializer(df);
//                }
//            }
//        }
//        return this;
//    }
}
