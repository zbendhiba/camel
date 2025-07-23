# LangChain4j Agent with Tools Support

This component has been enhanced to support tool calling capabilities by integrating with the `camel-langchain4j-tools` component. This allows the AI agent to discover and call Camel routes that are tagged as tools.

## Features

- **Tool Discovery**: Automatically discover Camel routes tagged with specific labels
- **Dynamic Tool Execution**: AI can decide which tools to call based on context
- **Multiple Tool Support**: Call multiple tools in sequence or parallel
- **Flexible Configuration**: Configure tools via endpoint parameters, headers, or configuration

## How It Works

1. **Define Tool Routes**: Create consumer routes using `langchain4j-tools` component with specific tags
2. **Configure Agent**: Set up the AI agent with tags to specify which tools it can access
3. **Tool Discovery**: The agent discovers available tools based on matching tags
4. **AI Decision**: The LLM decides which tools to call based on user input
5. **Tool Execution**: The agent executes the selected tools and incorporates results into the response

## Configuration Options

### Tags Configuration

Tools are configured through URI parameters:

- **URI Parameter**: `tags=users,weather` - Specify which tool tags to use

### Example Usage

#### 1. Define Tool Consumer Routes

```java
// User database tool
from("langchain4j-tools:userDb?tags=users&description=Query user database by user ID&parameter.userId=integer")
    .to("sql:SELECT name FROM users WHERE id = :#userId")
    .marshal().json();

// Weather service tool  
from("langchain4j-tools:weather?tags=weather&description=Get weather information for a city&parameter.city=string")
    .toF("http://weather-api.com/weather?city=${header.city}")
    .marshal().json();
```

#### 2. Use Agent with Tools

```java
// Agent with specific tools tags
from("direct:ask-agent")
    .to("langchain4j-agent:assistant?chatModel=#chatModel&tags=users,weather")
    .to("mock:result");

// Agent with single tool tag
from("direct:ask-user-agent")
    .to("langchain4j-agent:assistant?chatModel=#chatModel&tags=users")
    .to("mock:result");

// Agent without tools (no tags parameter)
from("direct:ask-agent-no-tools")
    .to("langchain4j-agent:assistant?chatModel=#chatModel")
    .to("mock:result");
```

#### 3. Example Interactions

```java
// Simple string usage
template.requestBody("direct:ask-agent", "What is the name of user 123?");
// AI will call the userDb tool and respond with the user's name

// Enhanced AiAgentBody with ChatMessages for complex conversations
List<ChatMessage> conversation = new ArrayList<>();
conversation.add(new SystemMessage("You are a helpful assistant with access to user database."));
conversation.add(new UserMessage("What is the name of user 123?"));
conversation.add(new AiMessage("I'll look that up for you."));
conversation.add(new UserMessage("Also, what about user 456?"));

AiAgentBody body = AiAgentBody.fromChatMessages(conversation);
template.requestBody("direct:ask-agent", body);
// AI will continue the conversation and call tools as needed

// Fluent interface (convenient for simple messages)
AiAgentBody simpleBody = new AiAgentBody()
    .withSystemMessage("You are a weather assistant.")
    .withUserMessage("What is the weather in Paris?");
template.requestBody("direct:ask-agent", simpleBody);

// Building complex conversations
AiAgentBody complexBody = new AiAgentBody()
    .addMessage(new SystemMessage("You are helpful"))
    .addMessage(new UserMessage("Hello"))
    .addMessage(new AiMessage("Hi! How can I help?"))
    .addMessage(new UserMessage("What's 2+2?"));
template.requestBody("direct:ask-agent", complexBody);
```

## Input Types Supported

The enhanced agent supports multiple input types:

1. **String**: Simple user message
2. **AiAgentBody**: Chat message container with `List<ChatMessage>` for full conversation history and tool support
   - Fluent methods: `withUserMessage()`, `withSystemMessage()`, `addMessage()`
   - Direct messages: `fromChatMessages(messages)` or constructor with messages list

## Headers

- **Output**: `LangChain4jToolsNoToolsCalled` - Set to `true` when no tools were called

## Backward Compatibility

The enhanced agent is fully backward compatible. If no tags are configured, it behaves exactly like the original agent without tool support.

## Tool Parameter Mapping

When tools are called, their parameters are mapped to exchange headers in the consumer route:

```java
// Tool definition with parameter
from("langchain4j-tools:userLookup?tags=users&description=Find user by ID&parameter.userId=integer")
    .log("Looking up user: ${header.userId}")  // Parameter is available as header
    .setBody(simple("User ${header.userId} found"));
```

## Error Handling

- If tool execution fails, the error is logged and the agent continues with available information
- If no tools match the specified tags, the agent falls back to regular chat mode
- Tool execution timeouts and errors are handled gracefully

## Performance Considerations

- Tool discovery uses an in-memory cache for fast lookup
- Multiple tool calls are executed sequentially to maintain conversation context
- Iteration limit (default: 10) prevents infinite tool calling loops

## Testing

Two types of tests are provided:

1. **Unit Tests**: Use mock ChatModel for testing tool integration logic
2. **Integration Tests**: Use real LLM (requires API key) for end-to-end testing

Run unit tests: `mvn test -Dtest=LangChain4jAgentToolsIntegrationTest`
Run integration tests: `mvn test -Dopenai.api.key=your-key -Dtest=LangChain4jAgentWithToolsTest` 