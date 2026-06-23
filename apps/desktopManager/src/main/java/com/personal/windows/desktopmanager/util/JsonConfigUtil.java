package com.personal.windows.desktopmanager.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public final class JsonConfigUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private JsonConfigUtil() {
    }

    public static <T> T readValue(Path filePath, TypeReference<T> typeRef) {
        if (!Files.exists(filePath)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(filePath.toFile(), typeRef);
        } catch (IOException e) {
            throw new com.personal.windows.desktopmanager.exception.ConfigException("读取配置文件失败: " + filePath, e);
        }
    }

    public static void writeValue(Path filePath, Object value) {
        try {
            Path tempFile = filePath.resolveSibling(filePath.getFileName() + ".tmp");
            OBJECT_MAPPER.writeValue(tempFile.toFile(), value);
            Files.move(tempFile, filePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new com.personal.windows.desktopmanager.exception.ConfigException("写入配置文件失败: " + filePath, e);
        }
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
