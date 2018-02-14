# Cactus

[![Build Status](https://travis-ci.org/avast/cactus.svg?branch=master)](https://travis-ci.org/avast/cactus)
[ ![Download](https://api.bintray.com/packages/avast/maven/cactus/images/download.svg) ](https://bintray.com/avast/maven/cactus/_latestVersion)

Library for mapping between [GPB](https://developers.google.com/protocol-buffers/) messages and Scala's case classes. It has support for
both GPB v2 and [GPB v3](gpbv3/README.md) (including [gRPC](grpc-common/README.md)).

The library automatically converts common data types (`String`, primitive types, collections) and can map optional fields 
into `Option`. This process is extensible via `Converter`.

The library uses `org.scalactic.Or` as a return type. The reason is it has great ability to collect all errors raised during the processing
(read more on [Scalatic website](http://www.scalactic.org/user_guide/OrAndEvery)). It results into all failures raised during the conversion
being returned as `Bad[Every[CactusFailure]]`.

The conversion never throws an exception. If there is an exception caught during the conversion, it's transformed into `UnknownFailure`.

In case of failure (e.g. `MissingRequiredField`), a whole variable path (e.g. _gpb.fieldGpb2RepeatedRecurse.fieldGpb.fieldBlob_) is reported
inside the failure.

## GPB to case class

We often need to map GPB message to business object (which is usually a case class) when GPB is used for communication 
over the network. It is a good practice to follow Google's recommendation to have all fields `optional`. However that
is not very handy during mapping because validation is necessary.

_Since v3.0, optional keyword is removed. See [GPB v3 official docs](https://developers.google.com/protocol-buffers/docs/proto3)._

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
See [GPBv3 section](gpbv3/README.md#map) for native maps mapping.

Mapping of complex messages (message contains another message which contains another message) is supported.
However **recursive** mapping (field with `Data` type in `Data` class) is **NOT supported**. 

When using your own converters, please note it's necessary to use exactly the same type as present in the GPB instance/case class.
For example see `JavaIntegerListStringConverter` in [test](gpbv2/src/test/scala/com/avast/cactus/v2/test/CactusMacrosTestV2.scala). 

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

See [unit tests](gpbv2/src/test/scala/com/avast/cactus/v2/test/CactusMacrosTestV2.scala) for more examples.

Collections (`java.util.List<T>`) are converted to `scala.collection.immutable.Seq[T]` - concretely to `scala.collection.immutable.Vector[T]` by default.
Another option is to use `scala.collection.immutable.List[T]` - you have to use the `List` type explicitly.
By specifying `scala.collection.Seq[T]`, which can contain also mutable collections, you get the `Vector[T]`.

## Case class to GPB

When using your own converters, please note it's necessary to use exactly the same type as present in the GPB builder/case class.
For example see `StringJavaIntegerListConverter` in [test](gpbv2/src/test/scala/com/avast/cactus/v2/test/CactusMacrosTestV2.scala).

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
      Good(parts.map(_.toInt).map(int2Integer).toSeq.asJava)
    } else {
      Bad(One(CustomFailure(fieldPath, s"Wrong format of '$fieldPath' field")))
    }
}
```
This is to be used when the conversion can go wrong and it's better to cover possible failures right in the converter.  
**Please note**, that even if the `StringJavaIntegerListConverter` fails, the exception _will *not*_ be thrown outside. `Bad(One(UnknownFailure(..., ...)))`
will be returned instead.
* Just implement `new Converter`
```scala
implicit val StringJavaIntegerListConverter2: Converter[String, java.lang.Iterable[_ <: Integer]] = new Converter[String, java.lang.Iterable[_ <: Integer]] {
  override def apply(fieldPath: String)(str: String): ResultOrErrors[java.lang.Iterable[_ <: Integer]] = {
      val parts = str.split(", ")

      if (parts.nonEmpty && parts.forall(_.matches("\\d+"))) {
        Good(parts.map(_.toInt).map(int2Integer).toSeq.asJava)
      } else {
        Bad(One(CustomFailure(fieldPath, s"Wrong format of '$fieldPath' field")))
      }
  }
}
```

Basic examples of custom converters may have been seen in examples above or in [unit tests](gpbv3/src/test/scala/com/avast/cactus/v3/test/CactusMacrosTestV3.scala).

## Appendix

The library is compiled with optional dependency on GPB 3.3.0. It should be safe to use the library with versions `2.6.x`, `2.5.x` or 
`2.4.x` since it uses the `MessageLite` interface only as a _"marker trait"_.
