package com.platform.web.support.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Shared JSON serialization and deserialization rules. */
@Configuration
public class CommonJacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeWithOffsetSerializer());
        javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeWithOffsetDeserializer());
        mapper.registerModule(javaTimeModule);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private static final class LocalDateTimeWithOffsetDeserializer extends JsonDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String value=parser.getValueAsString();
            if(value==null) return (LocalDateTime)context.handleUnexpectedToken(LocalDateTime.class,parser);
            try {
                return OffsetDateTime.parse(value,DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            } catch(java.time.format.DateTimeParseException offsetFailure) {
                try { return LocalDateTime.parse(value,DateTimeFormatter.ISO_LOCAL_DATE_TIME); }
                catch(java.time.format.DateTimeParseException invalid) {
                    return (LocalDateTime)context.handleWeirdStringValue(LocalDateTime.class,value,"Invalid ISO date-time");
                }
            }
        }
    }

    private static final class LocalDateTimeWithOffsetSerializer extends JsonSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value,
                              JsonGenerator generator,
                              SerializerProvider serializers) throws IOException {
            String timestamp = value.atZone(ZoneId.systemDefault())
                    .toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            generator.writeString(timestamp);
        }
    }
}
