# Cactus

[![Build Status](https://travis-ci.org/avast/cactus.svg?branch=master)](https://travis-ci.org/avast/cactus)
[ ![Download](https://api.bintray.com/packages/avast/maven/cactus/images/download.svg) ](https://bintray.com/avast/maven/cactus/_latestVersion)

Library for mapping between [GPB](https://developers.google.com/protocol-buffers/) messages and Scala's case classes.

The library automatically converts common data types (`String`, primitive types, collections) and can map optional fields 
into `Option`. This process is extensible via `Converter`.

## GPB to case class

We often need to map GPB message to business object (which is usually a case class) when GPB is used for communication 
over the network. It is a good practice to follow Google's recommendation to have all fields `optional`. However that
is not very handy during mapping because validation is necessary.

This library solves both problems - it generates mapping (using macros) between GPB message and Scala case class
based on the fields of the case class and validates that all the required fields (not wrapped in `Option`) are present.
It also automatically converts between common data types.

There are several types of failure which can happen. The most common one is `MissingFieldFailure` which contains the name of the missing field.

It is common that the field has different name in the GPB and in the case class. You can use `GpbName` annotation 
to override the expected name - see the example.

Sometimes the case class has more fields than the GPB and you just want to ignore them during conversion. You can use `GpbIgnored` 
annotation to achieve this. Please note that the ignored field in the case class **must** have default value!

GPBs 2.x do not have map type and it's usually solved by using repeated field with message, which contains `key` and `value` fields. Automatic conversion
to (and from) Scala `Map` is supported via `GpbMap` annotation - see the `CaseClassA` in example.
See [GPBv3 section](#map) for native maps mapping.

Mapping of complex messages (message contains another message which contains another message) is supported.
However **recursive** mapping (field with `Data` type in `Data` class) is **NOT supported**. 

When using your own converters, please note it's necessary to use exactly the same type as present in the GPB instance/case class.
For example see `JavaIntegerListStringConverter` in [test](macros/src/test/scala/com/avast/cactus/v2/CactusMacrosTestV2.scala). 

### Example

GPB:

```
message Data {
    optional string     field = 1;                          // REQUIRED
    optional int32      field_int_name = 2;                 // REQUIRED
    optional int32      field_option = 3;                   // OPTIONAL
    repeated string     field_strings = 4;                  
    repeated string     field_strings_name = 5;             
    repeated int32      field_option_integers = 6;          
    repeated int32      field_option_integers_empty = 7;    
    optional Data2      field_gpb_option = 8; 	            // OPTIONAL
    optional Data2      field_gpb_option_empty = 9;         // OPTIONAL
    optional Data2      field_gpb = 10;   		            // REQUIRED
    optional bytes      field_blob = 11;                    // REQUIRED
    repeated MapMessage field_map = 12;                     // OPTIONAL
    repeated Data2      field_gpb_repeated = 13;            // OPTIONAL
}

message Data2 {
    optional double     field_double = 1;	  	            // REQUIRED
    optional bytes      field_blob = 2;	                    // REQUIRED
}

message MapMessage {
    optional string key = 1;                                // REQUIRED
    optional string value = 2;                              // REQUIRED
    optional string other = 3;                              // OPTIONAL
}
```

Scala:
```scala
// your own converters:
implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))
implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter((b: ByteString) => b.toStringUtf8)

case class CaseClassA(
  field: String,
  @GpbName("fieldIntName") // different name in GPB than in case class 
  fieldInt: Int,
  fieldOption: Option[Int],
  fieldBlob: ByteString,
  @GpbName("fieldStringsName")
  fieldStrings2: List[String],
  fieldGpb: CaseClassB,
  fieldGpbOption: Option[CaseClassB],
  fieldGpbOptionEmpty: Option[CaseClassB],
  fieldGpbRepeated: Seq[CaseClassB],
  fieldStrings: immutable.Seq[String],
  fieldOptionIntegers: Seq[Int],
  fieldOptionIntegersEmpty: List[Int],
  @GpbMap(key = "key", value = "value")
  fieldMap: Map[String, String]
)

case class CaseClassB(
  fieldDouble: Double, 
  @GpbName("fieldBlob")
  fieldString: String // this is possible thanks to the user-specified converter, the field type is `ByteString` in the GPB
)

object Test extends App {
  import com.avast.cactus._
  
  val gpbInternal = Data2.newBuilder()
   .setFieldDouble(0.9)
   .setFieldBlob(ByteString.copyFromUtf8("text"))
   .build()
  
  val dataRepeated = Seq(gpbInternal, gpbInternal, gpbInternal)
  
  val gpb = TestMessage.Data.newBuilder()
   .setField("ahoj")
   .setFieldIntName(9)
   .setFieldOption(13)
   .setFieldBlob(ByteString.EMPTY)
   .setFieldGpb(gpbInternal)
   .setFieldGpbOption(gpbInternal)
   .addAllFieldStrings(Seq("a", "b").asJava)
   .addAllFieldStringsName(Seq("a").asJava)
   .addAllFieldOptionIntegers(Seq(3, 6).map(int2Integer).asJava)
   .addAllFieldMap(map.map { case (key, value) => TestMessage.MapMessage.newBuilder().setKey(key).setValue(value).build() }.asJava)
   .addAllFieldGpbRepeated(dataRepeated.asJava)
   .build()

  gpb.asCaseClass[CaseClassA] match {
    case Good(inst) =>
      println(inst)

    case Bad(e) =>
      println(s"Missing required fields: '${e.mkString(", ")}'")
  }
}
```

See [unit tests](macros/src/test/scala/com/avast/cactus/v2/CactusMacrosTestV2.scala) for more examples.

Collections (`java.util.List<T>`) are converted to `scala.collection.immutable.Seq[T]` - concretely to `scala.collection.immutable.Vector[T]` by default.
Another option is to use `scala.collection.immutable.List[T]` - you have to use the `List` type explicitly.
By specifying `scala.collection.Seq[T]`, which can contain also mutable collections, you get the `Vector[T]`.

## Case class to GPB

When using your own converters, please note it's necessary to use exactly the same type as present in the GPB builder/case class.
For example see `StringJavaIntegerListConverter` in [test](macros/src/test/scala/com/avast/cactus/v2/CactusMacrosTestV2.scala).

### Example

GPB:

```
message Data {
    optional string     field = 1;                          // REQUIRED
    optional int32      field_int_name = 2;                 // REQUIRED
    optional int32      field_option = 3;                   // OPTIONAL
    repeated string     field_strings = 4;                  
    repeated string     field_strings_name = 5;             
    repeated int32      field_option_integers = 6;          
    repeated int32      field_option_integers_empty = 7;    
    optional Data2      field_gpb_option = 8; 	            // OPTIONAL
    optional Data2      field_gpb_option_empty = 9;         // OPTIONAL
    optional Data2      field_gpb = 10;   		            // REQUIRED
    optional bytes      field_blob = 11;                    // REQUIRED
    repeated Data2      field_gpb_repeated = 12;            // OPTIONAL
}

message Data2 {
    optional double     field_double = 1;	  	            // REQUIRED
    optional bytes      field_blob = 2;	                    // REQUIRED
}

```

Scala:
```scala
case class CaseClassA(field: String,
  @GpbName("fieldIntName")
  fieldInt: Int,
  fieldOption: Option[Int],
  fieldBlob: ByteString,
  @GpbName("fieldStringsName")
  fieldStrings2: Vector[String],
  fieldGpb: CaseClassB,
  fieldGpbOption: Option[CaseClassB],
  fieldGpbOptionEmpty: Option[CaseClassB],
  fieldGpbRepeated: Seq[CaseClassB],
  fieldStringsList: Seq[String],
  fieldOptionIntegersList: Option[Seq[Int]],
  fieldOptionIntegersEmptyList: Option[List[Int]])

case class CaseClassB(fieldDouble: Double, @GpbName("fieldBlob") fieldString: String)


object Test extends App {
  import com.avast.cactus._
  
  val caseClassB = CaseClassB(0.9, "text")
  
  val caseClassBRepeated = Seq(caseClassB, caseClassB, caseClassB)
  
  val caseClass = CaseClassA(
    field = "ahoj",
    fieldInt = 9,
    fieldOption = Some(13),
    fieldBlob = ByteString.EMPTY,
    fieldStrings2 = Vector("a"),
    fieldGpb = CaseClassB(0.9, "text"),
    fieldGpbOption = Some(caseClassB),
    fieldGpbOptionEmpty = None,
    fieldGpbRepeated = caseClassBRepeated,
    fieldStringsList = Seq("a", "b"),
    fieldOptionIntegersList = Some(Seq(3, 6)),
    fieldOptionIntegersEmptyList = None
  )

  caseClass.asGpb[Data] match {
    case Good(inst) =>
      println(inst)

    case Bad(e) =>
      println("Errors: " + e)
  }
}
```

See [unit tests](macros/src/test/scala/com/avast/cactus/v2/CactusMacrosTestV2.scala) for more examples.

## GPB v3 support

Cactus currently supports v3.3. See [official docs](https://developers.google.com/protocol-buffers/docs/proto3) for more information about v3 features.

See [unit tests](macros/src/test/scala/com/avast/cactus/v3/CactusMacrosTestV3.scala) for 


### Any

The `Any` is by design very benevolent from a types POV. The Cactus provides better API for parsing messages hidden in `Any` field:

```scala
import com.avast.cactus.v3.AnyValue // mind the import!
import com.google.protobuf.Any

case class ExtClass(any: AnyValue)

val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

// to case class:

val msg = ExtensionsMessage.newBuilder().setAny(Any.pack(innerMessage)).build()


val c1 = msg.asCaseClass[ExtClass]
val c2 = c1.flatMap(_.any.asGpb[MessageInsideAnyField])

// to GPB:

val c21 = ExtClass(AnyValue.of(innerMessage))
val c22 = c21.asGpb[ExtensionsMessage]
```

There is the `WrongAnyTypeFailure` error defined for a case when the programmer tries to parse the `Any` as different type than it's serialized inside.

### OneOf

OneOf functionality is supported with mapping to a user-defined `sealed trait` and its implementations. The name of the OneOf in the GPB 
definition has to be reflected either by name of the field or by `GpbName` annotation.

```scala
import com.avast.cactus._
import com.avast.cactus.v3.ValueOneOf._

/*
message Data {
    repeated Data2      field_gpb = 1;

    oneof named_one_of {
             int32      foo_int = 2;
             string     foo_string = 3;
    }
}
*/

sealed trait OneOfNamed

object OneOfNamed {

  case class FooInt(value: Int) extends OneOfNamed

  case class FooString(value: String) extends OneOfNamed

}

case class CaseClassA(fieldGpb: Seq[CaseClassB], @GpbOneOf @GpbName("NamedOneOf") oneOfNamed: OneOfNamed)

// to case class:

val gpb = Data.newBuilder()
  .setFieldGpb(gpb2)
  .setFooInt(42)
  .build()
  
gpb.asCaseClass[CaseClassA] match {
  case Good(CaseClassA(_, OneOfNamed.FooInt(v))) => v
}  

// to gpb:

CaseClassA(Seq.empty, OneOfNamed.FooInt(42)).asGpb[Data]

```

There is the `OneOfValueNotSetFailure` error defined for a case when OneOf doesn't have value set even though it's required to. There is an
option to hide this state by wrapping the OneOf field in case class into the `Option[T]`:

```scala
case class CaseClassC(fieldGpb: Seq[CaseClassB], @GpbOneOf namedOneOf: Option[OneOfNamed])

```

### Map
GPB v3 has native support for maps - the map is then directly in the GPB as the `java.util.Map` class. Cactus supports this the same as custom
maps implementations by repeated field (GPBv2 way).

```scala

/*
message Data {
             string     field_string = 1;                   // REQUIRED
             string     field_option = 2;                   // OPTIONAL
             map<string, int32> field_map = 3;              // REQUIRED
             map<string, MapInnerMessage> field_map2 = 4;   // REQUIRED

             message MapInnerMessage {
                string     field_string = 1;
                string     field_int = 2;
             }
}
*/

case class CaseClassMapInnerMessage(fieldString: String, fieldInt: Int)

case class CaseClass(fieldString: String,
                     fieldOption: Option[String],
                     fieldMap: Map[String, Int],
                     fieldMap2: Map[String, CaseClassMapInnerMessage])

val original = CaseClass(
    fieldString = "ahoj",
    fieldOption = Some("ahoj2"),
    fieldMap = Map("one" -> 1, "two" -> 2),
    fieldMap2 = Map("one" -> CaseClassMapInnerMessage("str", 42))
)

val Good(converted) = original.asGpb[Data]

assertResult(Good(original))(converted.asCaseClass[CaseClass])

```

### Wrappers

There as a possibility to use so-called wrappers provided by Google as an extension. This is fully supported by Cactus by mapping to prepared
case classes - `trait ValueOneOf` and its implementations. 

The same as normal fields, these can be wrapped in the `Option[T]` which turns them into optional fields.


```scala

/*
message ExtensionsMessage {
             google.protobuf.BoolValue bool_value = 1;
             google.protobuf.Int32Value int32_value = 2;
             google.protobuf.Int64Value long_value = 3;

             google.protobuf.Duration duration = 4;
             google.protobuf.Timestamp timestamp = 5;
             google.protobuf.ListValue list_value = 6;

             google.protobuf.Struct struct = 7;
}
*/

import com.avast.cactus._
import com.avast.cactus.v3.ValueOneOf._
import com.google.protobuf.{Any, BoolValue, ByteString, BytesValue, DoubleValue, FloatValue, Int32Value, Int64Value, ListValue, StringValue, Struct, Value, Duration => GpbDuration, Timestamp => GpbTimestamp}

case class CaseClassExtensions(boolValue: BoolValue,
                               int32Value: Int32Value,
                               longValue: Int64Value,
                               listValue: ListValue,
                               duration: GpbDuration,
                               timestamp: GpbTimestamp,
                               struct: Map[String, ValueOneOf])

val gpb = ExtensionsMessage.newBuilder()
  .setBoolValue(BoolValue.newBuilder().setValue(true))
  .setInt32Value(Int32Value.newBuilder().setValue(123))
  .setLongValue(Int64Value.newBuilder().setValue(456))
  .setDuration(GpbDuration.newBuilder().setSeconds(123).setNanos(456))
  .setTimestamp(GpbTimestamp.newBuilder().setSeconds(123).setNanos(456))
  .setListValue(ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)))
  .setStruct(Struct.newBuilder().putFields("mapKey", Value.newBuilder().setNumberValue(42).build()))
  .build()

val expected = CaseClassExtensions(
  boolValue = BoolValue.newBuilder().setValue(true).build(),
  int32Value = Int32Value.newBuilder().setValue(123).build(),
  longValue = Int64Value.newBuilder().setValue(456).build(),
  duration = GpbDuration.newBuilder().setSeconds(123).setNanos(456).build(),
  timestamp = GpbTimestamp.newBuilder().setSeconds(123).setNanos(456).build(),
  listValue = ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)).build(),
  struct = Map("mapKey" -> NumberValue(42))
)

val Good(converted) = gpb.asCaseClass[CaseClassExtensions]

assertResult(expected)(converted)

assertResult(Good(gpb))(converted.asGpb[ExtensionsMessage])

```

Conversion to basic Scala types is supported too:

```scala
import com.avast.cactus._
import com.avast.cactus.v3.ValueOneOf._
import com.google.protobuf.{BoolValue, BytesValue, DoubleValue, FloatValue, Int32Value, Int64Value, ListValue, StringValue, Struct, Value, Duration => GpbDuration, Timestamp => GpbTimestamp}

val gpb = ExtensionsMessage.newBuilder()
  .setBoolValue(BoolValue.newBuilder().setValue(true))
  .setInt32Value(Int32Value.newBuilder().setValue(123))
  .setLongValue(Int64Value.newBuilder().setValue(456))
  .setDuration(GpbDuration.newBuilder().setSeconds(123).setNanos(456))
  .setTimestamp(GpbTimestamp.newBuilder().setSeconds(123).setNanos(456))
  .setListValue(ListValue.newBuilder().addValues(Value.newBuilder().setNumberValue(456.789)))
  .setStruct(Struct.newBuilder().putFields("mapKey", Value.newBuilder().setNumberValue(42).build()))
  .build()

case class CaseClassExtensionsScala(boolValue: Boolean,
                                    int32Value: Int,
                                    longValue: Option[Long], // Option is supported
                                    listValue: Seq[ValueOneOf],
                                    duration: Duration,
                                    timestamp: Instant,
                                    struct: Map[String, ValueOneOf]
                                    )
                                    
gpb.asCaseClass[CaseClassExtensionsScala]
```

## Appendix

The library is compiled with optional dependency on GPB 3.3.0. It should be safe to use the library with versions `2.6.x`, `2.5.x` or 
`2.4.x` since it uses the `MessageLite` interface only as a _"marker trait"_.
