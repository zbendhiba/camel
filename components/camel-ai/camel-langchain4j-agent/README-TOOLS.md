# LangChain4j Agent Component - Tools Integration

This document explains how the `camel-langchain4j-agent` component integrates with tools from the `camel-langchain4j-tools` component to enable AI agents to call Camel routes as tools.

## Overview

The LangChain4j Agent component provides a **unified chat interface** that seamlessly integrates with Camel routes as AI tools. There is **no difference between simple chat and tool-enabled chat** - the component automatically detects available tools based on tags and enables tool functionality when appropriate.

## Key Features

- **Unified Interface**: Single chat method handles both simple chat and tool-enabled scenarios
- **Automatic Tool Detection**: Tools are automatically discovered based on tags configuration
- **Seamless Integration**: Uses existing `camel-langchain4j-tools` infrastructure
- **LangChain4j AI Services**: Leverages LangChain4j's AI Services for clean interface design

## Architecture

### Unified Processing Flow

1. **Input Processing**: Convert various input types to `List<ChatMessage>`
2. **Tool Detection**: Check if tags are configured to determine tool availability
3. **Unified Chat**: Use either:
   - **Tool-enabled chat**: When tags are present and tools are available
   - **Simple chat**: When no tags are configured or no tools match
4. **Response Handling**: Return AI response after potential tool execution

### Tool Integration

The component reuses the existing tool infrastructure from `camel-langchain4j-tools`:

- **Tool Discovery**: Uses `CamelToolExecutorCache` to find routes by tags
- **Tool Execution**: Invokes Camel routes and processes results
- **Tool Specifications**: Leverages existing `ToolSpecification` and `CamelToolSpecification`

## Configuration

### Basic Configuration

Configure the agent endpoint with tags to enable tool functionality:

```java
// Tool-enabled agent (when tools with matching tags exist)
from("direct:chat")
    .to("langchain4j-agent:my-agent?chatModel=#chatModel&tags=users,weather");

// Simple chat agent (no tools)
from("direct:simple-chat")
    .to("langchain4j-agent:my-agent?chatModel=#chatModel");
```

### Tool Route Configuration

Define tool routes using the `langchain4j-tools` component:

```java
// User management tool
from("langchain4j-tools:userInfo?tags=users&description=Query user database by ID&parameter.userId=integer")
    .to("sql:SELECT name FROM users WHERE id = :#userId")
    .setBody(simple("User found: ${body}"));

// Weather tool  
from("langchain4j-tools:weather?tags=weather&description=Get weather information&parameter.location=string")
    .to("http://weather-api.com/weather?location=${header.location}")
    .setBody(simple("Weather in ${header.location}: ${body}"));
```

### Component Configuration

```java
@Component
public class LangChain4jAgentConfiguration {
    
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(GPT_4_O_MINI)
            .build();
    }
}
```

## Usage Examples

### Input Types

The component accepts multiple input types:

#### 1. Simple String Input

```java
// String input - treated as user message
String response = template.requestBody(
    "direct:agent", 
    "What is the name of user 123?", 
    String.class
);
```

#### 2. AiAgentBody with Message List

```java
// AiAgentBody with full conversation
AiAgentBody body = new AiAgentBody()
    .addSystemMessage("You are a helpful assistant.")
    .addUserMessage("What is the name of user 123?");

String response = template.requestBody("direct:agent", body, String.class);
```

#### 3. System Message via Header

```java
// String input with system message header
template.requestBodyAndHeader(
    "direct:agent",
    "What is the name of user 123?",
    LangChain4jAgent.Headers.SYSTEM_MESSAGE,
    "You are a helpful database assistant."
);
```

### Complete Example

```java
@Component
public class ChatAgentRoutes extends RouteBuilder {
    
    @Override
    public void configure() {
        // Unified agent endpoint - handles both simple chat and tools
        from("direct:chat")
            .to("langchain4j-agent:assistant?chatModel=#chatModel&tags=users,weather")
            .log("AI Response: ${body}");

        // Tool routes
        from("langchain4j-tools:userLookup?tags=users&description=Look up user by ID&parameter.userId=integer")
            .to("sql:SELECT name, email FROM users WHERE id = :#userId")
            .choice()
                .when(body().isNotNull())
                    .setBody(simple("User found: ${body}"))
                .otherwise()
                    .setBody(constant("User not found"));

        from("langchain4j-tools:weatherInfo?tags=weather&description=Get weather for location&parameter.location=string")
            .to("http://api.weather.com/current?q=${header.location}")
            .setBody(simple("Weather in ${header.location}: ${body}"));
    }
}
```

### Test Example

```java
@Test
public void testUnifiedChatWithTools() {
    // This will automatically use tools if available
    String response = template.requestBody(
        "direct:chat",
        "What is the name of user 123 and what's the weather in Paris?",
        String.class
    );
    
    // AI will automatically call both user lookup and weather tools
    assertThat(response).contains("User found").contains("Weather in Paris");
}

@Test
public void testSimpleChatWithoutTools() {
    // Same endpoint, but without tags - no tools available
    String response = template.requestBody(
        "direct:simple-chat",
        "Hello, how are you?",
        String.class
    );
    
    // Simple chat response without tool calls
    assertThat(response).isNotEmpty();
}
```

## Tool Execution Flow

1. **Message Processing**: Input converted to `List<ChatMessage>`
2. **Tag Resolution**: Extract tags from endpoint configuration
3. **Tool Discovery**: Find matching tools using `CamelToolExecutorCache`
4. **AI Processing**: Send messages and available tools to LLM
5. **Tool Execution**: If LLM requests tools:
   - Parse tool arguments and set as exchange headers
   - Execute corresponding Camel route
   - Add tool results to conversation
   - Continue until LLM provides final response
6. **Response**: Return final AI response

## Error Handling

- **Tool Not Found**: Gracefully handled with error message in tool result
- **Tool Execution Error**: Exception captured and returned as tool result
- **No Tools Available**: Falls back to simple chat automatically
- **Iteration Limit**: Prevents infinite tool calling loops (max 10 iterations)

## Best Practices

1. **Descriptive Tool Routes**: Use clear descriptions and parameter types
2. **Appropriate Tags**: Group related tools with logical tag names
3. **Error Handling**: Implement proper error responses in tool routes
4. **Performance**: Consider tool execution costs and response times
5. **Security**: Validate tool parameters and implement access controls

## Integration with Existing Components

This implementation seamlessly integrates with:

- **camel-langchain4j-tools**: Reuses existing tool infrastructure
- **camel-langchain4j-chat**: Compatible input/output patterns
- **Standard Camel Components**: Works with any Camel route as a tool
- **LangChain4j AI Services**: Uses modern AI Services interface

## Troubleshooting

### Common Issues

1. **Tools Not Called**: 
   - Check tag matching between agent and tool routes
   - Verify tool routes are started and registered
   - Enable debug logging to see tool discovery

2. **Tool Execution Errors**:
   - Check tool route error handling
   - Verify parameter types and names
   - Review exchange headers and body content

3. **Performance Issues**:
   - Monitor tool execution times
   - Consider async processing for long-running tools
   - Implement caching where appropriate

### Debug Logging

Enable debug logging to troubleshoot tool integration:

```properties
logging.level.org.apache.camel.component.langchain4j.agent=DEBUG
logging.level.org.apache.camel.component.langchain4j.tools=DEBUG
``` 