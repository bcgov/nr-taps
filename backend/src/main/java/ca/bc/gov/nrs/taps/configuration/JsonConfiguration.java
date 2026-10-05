package ca.bc.gov.nrs.taps.configuration;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {
  @Bean
  Jackson2ObjectMapperBuilderCustomizer boundedJsonRequests() {
    return builder -> builder.postConfigurer(mapper -> mapper.getFactory().setStreamReadConstraints(
        StreamReadConstraints.builder()
            .maxNestingDepth(20)
            .maxStringLength(1000)
            .maxNumberLength(100)
            .maxDocumentLength(65536)
            .maxTokenCount(10000)
            .build()));
  }
}
