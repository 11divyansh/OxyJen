# Introduction - What is This Project?

## Goal
This library helps build JSON Schemas in Java using a fluent Builder API. It will be really useful for building custom input/output schemas for `TooNode`, `SchemaNode` etc.

Instead of writing raw JSON like:

```json
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string"
    }
  }
}
```

You can write:

```java
JSONSchema schema = JSONSchema.object()
    .property("name",
        PropertySchema.string("User name"))
    .required("name")
    .build();
```

# Understanding JSON Schema Basics

Primitives:

JSON Schema & their Java Equivalent:

- string -> String 
- number -> int/double/long  
- boolean -> boolean 
- object -> class/map 
- array -> List 

### Example JSON

```json
{
  "name": "John",
  "age": 25
}
```
Schema:

```json
{
  "type": "object",
  "properties": {
    "name": {"type":"string"},
    "age": {"type":"number"}
  }
}
```

### JSONSchema Class

Represents a complete JSON object schema

Fields:

```java
private final SchemaType type;
private final Map<String, PropertySchema> properties;
private final Set<String> required;
private final String description;
```

Field & their purpose:
- type -> object/array/string etc
- properties -> object fields
- required -> mandatory fields
- description -> schema documentation

### Builder Pattern

`JSONSchema.object()` returns `new Builder(SchemaType.OBJECT)`

Then chaining:
.property(...)
.required(...)
.build()

Without builder new JSONSchema(type, props, req, desc); would be messy and unreadable.

### First Tutorial - Simple User Schema
```java
JSONSchema userSchema = JSONSchema.object()
    .description("User object")
    .property("name",
        PropertySchema.string("User name")
            .minLength(3)
            .maxLength(20))
    .property("age",
        PropertySchema.number("User age")
            .minimum(18)
            .maximum(100))
    .required("name")
    .build();
```

Output:

```json
{
  "type":"object",
  "description":"User object",
  "properties":{
    "name":{
      "type":"string",
      "description":"User name",
      "minLength":3,
      "maxLength":20
    },
    "age":{
      "type":"number",
      "description":"User age",
      "minimum":18,
      "maximum":100
    }
  },
  "required":["name"]
}
```

### Understanding PropertySchema

Primitive String Schema, `PropertySchema.string("Username")` internally does `new Builder().type(SchemaType.STRING)`

String Validation:

Regex:

```java
.pattern("^[a-zA-Z]+$")
```

Length:

```java
.minLength(3)
.maxLength(20)
```

### Arrays Tutorial
Array of Strings:

```java
PropertySchema.array(
    PropertySchema.string("Tag").build()
)
```

```json
{
  "type":"array",
  "items":{
    "type":"string"
  }
}
```

Full Example:

```java
JSONSchema blogSchema = JSONSchema.object()
    .property("tags",
        PropertySchema.array(
            PropertySchema.string("Tag").build()
        )).build();
```

### Nested Objects Tutorial

Address Object:

```java
JSONSchema addressSchema = JSONSchema.object()
    .property("city",
        PropertySchema.string("City"))
    .property("zip",
        PropertySchema.string("ZIP"))
    .required("city")
    .build();
```

Use inside another schema:

```java
JSONSchema userSchema = JSONSchema.object()
    .property("address",
        PropertySchema.object(
            "User address",
            addressSchema
        ))
    .build();
```

### Maps / additionalProperties
This is advanced JSON Schema.

Example:

```java
PropertySchema.map(
    "Dynamic metadata",
    PropertySchema.string("Value")
)
```

Generated JSON will be:

```json
{
  "type":"object",
  "additionalProperties":{
    "type":"string"
  }
}
```

Which means:

```json
{
  "key1": "value1",
  "key2": "value2"
}
```

Dynamic keys allowed.

### Enums Tutorial

Example:

```java
PropertySchema.enumOf(
    "Role",
    "ADMIN",
    "USER",
    "MODERATOR"
)
```

Output:

```json
{
  "type":"string",
  "enum":["ADMIN","USER","MODERATOR"]
}
```