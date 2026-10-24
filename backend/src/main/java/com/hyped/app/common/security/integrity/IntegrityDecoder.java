package com.hyped.app.common.security.integrity;

import com.fasterxml.jackson.databind.JsonNode;

@FunctionalInterface
public interface IntegrityDecoder {
    JsonNode decode(String evidence) throws Exception;
}
