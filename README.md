# Cactus

[![Build Status](https://travis-ci.org/avast/cactus.svg?branch=master)](https://travis-ci.org/avast/cactus)
[ ![Download](https://api.bintray.com/packages/avast/maven/cactus/images/download.svg) ](https://bintray.com/avast/maven/cactus/_latestVersion)

Library for mapping between [GPB](https://developers.google.com/protocol-buffers/) generated Java classes and Scala's case classes.
It has support for both GPB v2 and [GPB v3](gpbv3/README.md) (including [gRPC](grpc-common/README.md)).

The library automatically converts common data types (`String`, primitive types, collections) and can map optional fields 
into `Option`. This process is extensible via `Converter`.

The library uses `Either[NonEmptyList[CactusFailure], A]` as a return type. There is collection of errors used which means not only the first
but all found errors are returned in the `cats.data.NonEmptyList`.

The conversion never throws an exception. If there is an exception caught during the conversion, it's transformed into `UnknownFailure`.

In case of failure (e.g. `MissingRequiredField`), a whole variable path (e.g. _gpb.fieldGpb2RepeatedRecurse.fieldGpb.fieldBlob_) is reported
inside the failure.

## Dependency
Gradle:
```groovy
compile "com.avast.cactus:ARTIFACT_2.12:VERSION"
```
SBT:
```scala
"com.avast.cactus" %% "ARTIFACT" % "VERSION"
```
where current version is [available here](https://bintray.com/avast/maven/cactus/_latestVersion) and _ARTIFACT_ is one of:
1. cactus-gpbv2 (conversion of GPB v2.x.x)
1. cactus-gpbv3 (conversion of GPB v3.x.x)
1. bytes-gpbv2 (additional converters for support [Avast Bytes](https://github.com/avast/bytes))
1. bytes-gpbv3 (same as `bytes-gpbv2` but supports some GPBv3 types too)
1. grpc-client (support for mapping of gRPC client, see [docs](grpc-common/README.md))
1. grpc-server (support for mapping of gRPC server, see [docs](grpc-common/README.md))


## GPB to case class

We often need to map GPB message to business object (which is usually a case class) when GPB is used for communication 
over the network. It is a good practice to follow Google's recommendation to have all fields `optional`. However that
is not very handy during mapping because some validation is necessary.

_Since v3.0, optional keyword is removed. See [GPB v3 official docs](https://developers.google.com/protocol-buffers/docs/proto3)._

This library solves both problems - it generates mapping (using macros) between Java GPB class and Scala case class
based on the fields of the case class and validates that all the required fields (not wrapped in `Option`) are present.
It also automatically converts between common data types.

There are several types of failure which can happen. The most common one is `MissingFieldFailure` which contains a name of the missing field.

It is common that the field has different name in the GPB and in the case class. You can use the `GpbName` annotation 
to override the expected name - see the example below.

Sometimes the case class has more fields than the GPB and you just want to ignore them during the conversion. You can use the `GpbIgnored` 
annotation to achieve this. Please note that the ignored field in the case class **must** have default value!

GPBs 2.x do not have map type and it's usually solved by using repeated field with message, which contains `key` and `value` fields.
There is an automatic conversion to (and from) Scala `Map` supported via the `GpbMap` annotation - see the `CaseClassA` in example.
See [GPBv3 section](gpbv3/README.md#map) for native maps mapping.

Mapping of complex messages (message contains another message which contains another message) is supported.
However **recursive** mapping (field with `Data` type in `Data` class) is **NOT supported**. 

When using your own converters, please note it's necessary to use exactly the same type as present in the GPB instance/case class.
For example see `JavaIntegerListStringConverter` in [test](gpbv2/src/test/scala/com/avast/cactus/v2/test/CactusMacrosTestV2.scala). 

### Example

GPB:

```proto
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
implicit val ByteStringToStringConverter: Converter[ByteString, String] = Converter(_.toStringUtf8)

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
  // Warning: You may be missing some version-specific support without this import, e.g. GPBv3 value wrappers.
  import com.avast.cactus.v2._ // or v3
  
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
    case Right(inst) =>
      println(inst)

    case Left(e) =>
      println(s"Missing required fields: '${e.toList.mkString(", ")}'")
  }
}
```

See [unit tests](gpbv2/src/test/scala/com/avast/cactus/v2/test/CactusMacrosTestV2.scala) for more examples.

Collections (`java.util.List<T>`) are converted to `scala.collection.immutable.Seq[T]` - concretely to `scala.collection.immutable.Vector[T]` by default.
Another option is to use `scala.collection.immutable.List[T]` - you have to use the `List` type explicitly.
By specifying `scala.collection.Seq[T]`, which can contain also mutable collections, you get the `Vector[T]`.

## Case class to GPB

When using your own converters, please note it's necessary to use exactly the same type as present in the GPB builder/case class.
For example see `StringJavaIntegerListConverter` in [test](gpbv2/src/test/scala/com/avast/cactus/v2/test/CactusMacrosTestV2.scala).

### Example

GPB:

```proto
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
  // Warning: You may be missing some version-specific support without this import, e.g. GPBv3 value wrappers.
  import com.avast.cactus.v2._ // or v3
  
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
    case Right(inst) =>
      println(inst)

    case Left(e) =>
      println("Errors: " + e)
  }
}
```

See [unit tests](gpbv2/src/test/scala/com/avast/cactus/v2/test/CactusMacrosTestV2.scala) for more examples.

## GPB v3 support

See more in [gpbv3 module](gpbv3/README.md).

## Defining own converters

There are following ways how to implement own `Converter`:
* Plain conversion  
    `implicit val StringToByteStringConverter: Converter[String, ByteString] = Converter((b: String) => ByteString.copyFromUtf8(b))`  
    This is to be used when just simple A -> B conversion is needed.
* Checked conversion
    ```scala
    // unsafe way:
    implicit val StringJavaIntegerListConverter: Converter[String, java.lang.Iterable[_ <: Integer]] = Converter(_.split(", ").map(_.toInt).map(int2Integer).toSeq.asJava)
    
    // safe (checked) way:
    implicit val StringJavaIntegerListConverter2: Converter[String, java.lang.Iterable[_ <: Integer]] = Converter.checked { (fieldPath, str) =>
        val parts = str.split(", ")
        
        if (parts.nonEmpty && parts.forall(_.matches("\\d+"))) {
          Right(parts.map(_.toInt).map(int2Integer).toSeq.asJava)
        } else {
          Left(NonEmptyList.one(CustomFailure(fieldPath, s"Wrong format of '$fieldPath' field")))
        }
    }
    ```
    This is to be used when the conversion can go wrong and it's better to cover possible failures right in the converter.  
    **Please note**, that even if the `StringJavaIntegerListConverter` fails, the exception _will *not*_ be thrown outside.
    The `UnknownFailure(..., ...)` error will be returned instead.
    
* Just implement `new Converter`
    ```scala
    implicit val StringJavaIntegerListConverter2: Converter[String, java.lang.Iterable[_ <: Integer]] = new Converter[String, java.lang.Iterable[_ <: Integer]] {
      override def apply(fieldPath: String)(str: String): ResultOrErrors[java.lang.Iterable[_ <: Integer]] = {
          val parts = str.split(", ")
    
          if (parts.nonEmpty && parts.forall(_.matches("\\d+"))) {
            Right(parts.map(_.toInt).map(int2Integer).toSeq.asJava)
          } else {
            Left(NonEmptyList.one(CustomFailure(fieldPath, s"Wrong format of '$fieldPath' field")))
          }
      }
    }
    ```
    
* Derive converter from existing one
    
    There are multiple methods which allow you to manually derive and combine converters on the `Converter` trait
    (see [ConverterMethods](common/src/main/scala/com/avast/cactus/ConverterMethods.scala)).      
    For example:
    * You have `Converter[A, B]` - you can derive `Converter[A, C]` by using `converterAB.map((b: B) => ???:C)`.
    * You have `Converter[A, B]` and `Converter[B, C]` - you can derive `Converter[A, C]` by using `converterAB.andThen(converterBC)`
    
    See [unit tests](common/src/test/scala/com/avast/cactus/ConverterMethodsTest.scala) for more examples.
    
    Note: You can let Cactus derive `Converter[A, B]` automatically and use it then, e.g.
    ```scala
      val convAtoB = implicitly[Converter[A, B]]
      val convAtoC: Converter[A, C] = convAtoB.map((b: B) => C(b))
    ```

Examples of custom converters may have been seen in examples above or in [unit tests](gpbv3/src/test/scala/com/avast/cactus/v3/test/CactusMacrosTestV3.scala).

### Advanced example 1

Scenario:

Having proto messages

```proto
message Event {
    optional int32 number = 1;
}

message EventsResponse {
    repeated Event events = 1;
}
```

you want to have a following code in Scala:

```scala
import com.avast.cactus.ResultOrErrors
import com.avast.cactus.v3._

import my.gpb.{Event => GpbEvent, EventsResponse => GpbEventsResopnse}

case class Event(number: Int)

val response: Seq[Event] = myMethodCall()

val gpbResponse: ResultOrErrors[GpbEventsResponse] = response.asGpb[GpbEventsResponse]

```

Unfortunately this code won't compile because Cactus is unable to generate `Converter[Seq[Event], GpbEventsResponse]` automatically.

There are three possible solutions:
1. Implement the converter manually - but that is what Cactus is trying to help you from ;-)
1. Wrap `Seq[Event]` into some case class (e.g. `case class EventsResponse(events: Seq[Event])`) which would add basically useless code into
the application
1. Implement the converter but let Cactus do the dirty part
    1. Cactus is able to generate `Converter[Event, GpbEvent]` and derive `Converter[Seq[Event], Seq[GpbEvent]]` from it
    1. You have to implement just `Seq[GpbEvent] => GpbEventsResponse` which is an easy task:
        ```scala
           implicit val theConverter: Converter[Seq[Event], GpbEventsResponse] = {
             import scala.collection.JavaConverters._
      
             implicitly[Converter[Seq[Event], Seq[GpbEvent]]]
                 .map(events => GpbEventsResponse.newBuilder().addAllEvents(events.asJava).build())
           }
        ```
        
### Advanced example 2

Scenario:

You have `findUser` endpoint in your service. As it's usual for all find* methods, it's valid case that the user is not found. You want to
express this as `Option[User]` in Scala API and now you need to model GPB messages and related converter.  

```proto
message OptionalUserResponseMessage {
    UserMessage user = 1;
}

message UserMessage {
    string name = 1;                                        // REQUIRED
    int32 age = 2;                                          // REQUIRED
}
```

```scala
case class OptionalUserResponse(user: Option[User])

case class User(name: String, age: Int)
```

Now, the Cactus is able to convert `OptionalUserResponse` to `OptionalUserResponseMessage` (and back), but you want to have just `Option[User]`
in your API, not the whole `OptionalUserResponse`.

Creating converter for a client side is very easy:

```scala
implicit val convGpbToOptUser: Converter[OptionalUserResponseMessage, Option[User]] = {
  implicitly[Converter[OptionalUserResponseMessage, OptionalUserResponse]]
    .map(_.user) // append `OptionalUserResponse => Option[User]` to the converter
}
```

But on a server side, you call DAO which gets you class `DbUser`:

```scala
// def findUser: Option[DbUser] = ???

case class DbUser(firstName: String, surName: String, dateOfBirth: LocalDate)
``` 

You decide to return `Option[DbUser]` from your handler and thus to convert `DbUser` directly to GPB. That is obviously something Cactus
cannot do by its own. However, you can derive your custom converter if you provide a way how to convert `DbUser` to `User`:

```scala
def toAge(d: Duration): Int = ???

implicit val dbUserToUser: Converter[DbUser, User] = Converter{ dbUser =>
  User(s"${dbUser.firstName} ${dbUser.surName}", toAge(Duration.between(dbUser.dateOfBirth, Instant.now())))
}

implicit val convToGpb: Converter[Option[DbUser], OptionalUserResponseMessage] = {
  implicitly[Converter[OptionalUserResponse, OptionalUserResponseMessage]] // 1 -> Converter[OptionalUserResponse, OptionalUserResponseMessage]
    .contraMap(OptionalUserResponse.apply) // 2 -> Converter[Option[User], OptionalUserResponseMessage]
    .compose[Option[DbUser]] // 3 -> Converter[Option[DbUser], OptionalUserResponseMessage]
}
```

This deserves more detailed explanation:

1. Cactus derives `Converter[OptionalUserResponse, OptionalUserResponseMessage]` for you.
1. You _prepend_ conversion function `Option[User] => OptionalUserResponse` to the converter from previous step.
1. You _prepend_ your custom `Converter[DbUser, User]` to the converter from previous step.  
    (In fact, the `Converter[Option[DbUser], Option[User]]` is needed here, but Cactus will _lift_ your custom converter automatically to fit.)

## Optional modules

### Bytes

There are modules `cactus-bytes-gpbv2` and `cactus-bytes-gpbv3` containing support for conversion between GPB types and 
[Avast Bytes](https://github.com/avast/bytes).  
You have to use this import to make mentioned converters available:
```scala
import com.avast.cactus.bytes._
```
Then the proto
```proto
message Message {
    optional double     number = 1;	  	            // REQUIRED
    optional bytes      blob = 2;	                // REQUIRED
}
```
becomes convertible to
```scala
case class TheCaseClass(number: Double, blob: com.avast.bytes.Bytes)
```

When using `cactus-bytes-gpbv3` module, you can use `google.protobuf.BytesValue` in the GPB message too:

```proto
message Message {
    optional double                       number = 1;	            // REQUIRED
    optional google.protobuf.BytesValue   blob = 2;	                // REQUIRED
}
```

## Using Cactus in your own library

Sometimes you need to wrap the Cactus parsing under the hood of your own library and be GPB-version-agnostic at the same time.  
For example you could have method for parsing some event into a case class but the event is encoded in the GPB like this:
```scala
import cats.syntax.either._
import com.google.protobuf.{MessageLite, Parser}
import com.avast.cactus.{CactusParser, Converter}

import CactusParser._ // enables the asCaseClass and asGpb methods!

def parse[GpbMessage <: MessageLite: Parser: ClassTag, CaseClass: Converter[GpbMessage, ?]]: Either[Exception, CaseClass] = {
  Try(implicitly[Parser[GpbMessage]].parseFrom(eventBody.newInputStream())) match {
    case Success(gpb) => gpb.asCaseClass[CaseClass].leftMap(fs => new RuntimeException("Errors: " + fs.toList.mkString("[", ", ", "]")))
    case Failure(NonFatal(e)) => Left(new RuntimeException("Could not parse GPB message", e))
  }
}
```
This is where `com.avast.cactus.CactusParser` from _cactus-common_ module should be used - depending on this module does not add dependency on neither the GPBv2 nor GPBv3.

## Notes
1. There is [Kind Projector](https://github.com/non/kind-projector) library used in some examples above.
1. In case of any problem (or you just want to see the generated code), you can turn on debug of cactus by passing `cactus.debug=true` system
property to the compiler. E.g. in IDEA go to `File | Settings | Build, Execution, Deployment | Compiler | Scala Compiler | Scala Compile Server`
and add ` -Dcactus.debug=true` to JVM options.
1. Name you GPB fields with a common sense. Some combinations will cause clashes: e.g. `Enum enum` and `map<string, Enum> enumMap` will cause
the mapping/compilation to fail with a nonsense message.
