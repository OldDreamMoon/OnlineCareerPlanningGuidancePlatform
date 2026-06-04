package com.bishe.server.ai.gateway;

import com.bishe.server.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * provider client 注册表。
 */
@Component
public class AiProviderRegistry {

    private final Map<AiProviderType, AiProviderClient> clients = new EnumMap<>(AiProviderType.class);

    public AiProviderRegistry(List<AiProviderClient> clientList) {
        for (AiProviderClient client : clientList) {
            clients.put(client.providerType(), client);
        }
    }

    public AiProviderClient get(AiProviderType providerType) {
        AiProviderClient client = clients.get(providerType);
        if (client == null) {
            throw new ApiException("AI-2001", "provider client unsupported", HttpStatus.BAD_GATEWAY);
        }
        return client;
    }
}
