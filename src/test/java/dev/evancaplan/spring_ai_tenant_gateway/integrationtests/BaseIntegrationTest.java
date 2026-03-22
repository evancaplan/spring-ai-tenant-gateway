package dev.evancaplan.spring_ai_tenant_gateway.integrationtests;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

public class BaseIntegrationTest {

    @MockitoBean
    public ChatModel chatModel;

    public final ObjectMapper objectMapper = new ObjectMapper();
}
