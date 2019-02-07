# Cactus GPB v3 support

Cactus currently supports v3.3. See [official docs](https://developers.google.com/protocol-buffers/docs/proto3) for more information about v3 features.

See [unit tests](src/test/scala/com/avast/cactus/v3/test/CactusMacrosTestV3.scala) for more examples.

## Any

The `Any` is by design very benevolent from a types POV. The Cactus provides better API for parsing messages hidden in `Any` field:

```scala
import com.avast.cactus.ResultOrErrors
import com.avast.cactus.v3.TestMessageV3.{ExtensionsMessage, MessageInsideAnyField}
import com.avast.cactus.v3.{AnyValue, _}
import com.google.protobuf.Any

case class ExtClass(any: AnyValue)

case class InnerClass(fieldInt: Int, fieldString: String)

val innerMessage = MessageInsideAnyField.newBuilder().setFieldInt(42).setFieldString("ahoj").build()

// to case class:

val msg = ExtensionsMessage.newBuilder().setAny(Any.pack(innerMessage)).build()

val extClass: ResultOrErrors[ExtClass] = msg.asCaseClass[ExtClass]

val innerClass: ResultOrErrors[InnerClass] = extClass
    .flatMap(_.any.asGpb[MessageInsideAnyField]) // Any -> GPB
    .flatMap(_.asCaseClass[InnerClass]) // GPB -> case class

// to GPB:

val extensionMessage: ResultOrErrors[ExtensionsMessage] = InnerClass(42, "ahoj")
    .asGpb[MessageInsideAnyField]
    .map(miaf => ExtClass(AnyValue.of(miaf)))
    .flatMap(_.asGpb[ExtensionsMessage])
```

There is the `WrongAnyTypeFailure` error defined for a case when the programmer tries to parse the `Any`/`AnyValue` as different type than
it's serialized inside.

## OneOf

OneOf functionality is supported with mapping to a user-defined `sealed trait` and its implementations. The name of the OneOf in the GPB 
definition has to be reflected either by name of the field or by `GpbName` annotation.

```scala
import com.avast.cactus.v3._
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
  case Right(CaseClassA(_, OneOfNamed.FooInt(v))) => v
}  

// to gpb:

CaseClassA(Seq.empty, OneOfNamed.FooInt(42)).asGpb[Data]

```

There is the `OneOfValueNotSetFailure` error defined for a case when OneOf doesn't have value set even though it's required to. There is an
option to hide this state by wrapping the OneOf field in case class into the `Option[T]`:

```scala
case class CaseClassC(fieldGpb: Seq[CaseClassB], @GpbOneOf namedOneOf: Option[OneOfNamed])

```

The format of sealed trait impls is limited. It has to be either `case class` with exactly one field (no matter what it's name is but the
type obviously has to match the one in GPB) OR `case object` (which has `google.protobuf.Empty` as it's counterpart in the GPB):

```scala
import com.avast.cactus.v3._
import com.avast.cactus.v3.ValueOneOf._

/*

import "google/protobuf/empty.proto";

message Data {
    repeated Data2      field_gpb = 1;

    oneof named_one_of {
             int32                  foo_int = 2;
             string                foo_string = 3;
             google.protobuf.Empty foo_empty = 4;
    }
}
*/

sealed trait OneOfNamed

object OneOfNamed {

  case class FooInt(whatever: Int) extends OneOfNamed

  case class FooString(value: String) extends OneOfNamed
  
  case object FooEmpty extends OneOfNamed

}

case class CaseClassA(fieldGpb: Seq[CaseClassB], @GpbOneOf oneOfNamed: OneOfNamed)
```

## Map
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

val Right(converted) = original.asGpb[Data]

assertResult(Right(original))(converted.asCaseClass[CaseClass])

```

## Wrappers

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

import com.avast.cactus.v3._
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

val Right(converted) = gpb.asCaseClass[CaseClassExtensions]

assertResult(expected)(converted)

assertResult(Right(gpb))(converted.asGpb[ExtensionsMessage])

```

Conversion to basic Scala types is supported too:

```scala
import com.avast.cactus.v3_
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