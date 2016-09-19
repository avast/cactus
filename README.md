# Cactus

[![Build Status](https://travis-ci.org/avast/cactus.svg?branch=master)](https://travis-ci.org/avast/cactus)
Library for conversion between [GPB](https://developers.google.com/protocol-buffers/) instance and Scala case class.

## GPB to case class

As we use [GPB](https://developers.google.com/protocol-buffers/)s for communication over the network, we quite often need to map the GPB to our business
object, which is usually a case class. We follow Google recommendations and have all fields `optional`, which is quite not handy when doing the business 
object mapping because some validation is necessary.  

This library solves both problems together - it generates conversion code (using macros) which is reading data from the GPB instance and putting them into the
case class and
depending on case class fields types it does automatic conversion (primitive types, collections) and also the validation - fields not wrapped in `Option` are
considered to be required and their absence causes failure of the conversion.

Currently there is only one possible conversion failure - `MissingFieldFailure`, which contains name of missing field.

It's common that the field has different name in the GPB than we want to have in the case class. This is solved with `GpbName` annotation - see
the example.

When mapping the list, you have option to either add or not add `List` suffix to name of the case class field - see `fieldString2` vs. `fieldStringsList`
in the example.

The GPB message can contain another GPB message inside, as in the example below. Recursive instances (field with `Data` type in `Data` class) are NOT supported.

### Example

GPB:

```
message Data {
    optional string     field = 1;                          // REQUIRED
    optional int32      field_int_name = 2;                 // REQUIRED
    optional int32      field_option = 3;                   // OPTIONAL
    repeated string     field_strings = 4;                  // REQUIRED
    repeated string     field_strings_name = 5;             // REQUIRED
    repeated int32      field_option_integers = 6;          // OPTIONAL
    repeated int32      field_option_integers_empty = 7;    // OPTIONAL
    optional Data2      field_gpb_option = 8; 	            // OPTIONAL
    optional Data2      field_gpb_option_empty = 9;         // OPTIONAL
    optional Data2      field_gpb = 10;   		            // REQUIRED
    optional bytes      field_blob = 11;                    // REQUIRED
}

message Data2 {
    optional double     field_double = 1;	  	            // REQUIRED
    optional bytes      field_blob = 2;	                    // REQUIRED
}

```

Scala:
```scala

case class CaseClassA(field: String,
  @GpbName("fieldIntName") // different name in GPB than in case class 
  fieldInt: Int,
  fieldOption: Option[Int],
  fieldBlob: ByteString,
  @GpbName("fieldStringsName")
  fieldStrings2: List[String],
  fieldGpb: CaseClassB,
  fieldGpbOption: Option[CaseClassB],
  fieldGpbOptionEmpty: Option[CaseClassB],
  fieldStringsList: immutable.Seq[String],
  fieldOptionIntegersList: Option[List[Int]],
  fieldOptionIntegersEmptyList: Option[List[Int]])

case class CaseClassB(fieldDouble: Double, @GpbName("fieldBlob") fieldString: String)


object Test extends App {
  import com.avast.cactus._
  
  val gpbInternal = Data2.newBuilder()
   .setFieldDouble(0.9)
   .setFieldBlob(ByteString.copyFromUtf8("text"))
   .build()
  
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
   .build()

  gpb.asCaseClass[CaseClassA] match {
    case Right(inst) =>
      println(inst)

    case Left(MissingFieldFailure(fieldName)) =>
      println(s"Missing required field: '$fieldName'")
  }
}

```
See [unit tests](macros/src/test/scala/com/avast/cactus/CactusMacrosTest.scala) for more examples.

Collections (`java.util.List<T>`) are converted to `scala.collection.immutable.Seq[T]`, concretely to `scala.collection.immutable.List[T]`. Default Scala
`Seq[T]` is mutable and cannot be used in the target case class. Since both the GPB and case classes are meant to be immutable, this is intentional design of the converter. 

## Case class to GPB

Case class can be converted to GPB the same easy way as in the other direction.  

The source case class can contain an arbitrary scala collection (even mutable).

When mapping the collection, you have option to either add or not add `List` suffix to name of the case class field - see `fieldString2` vs. `fieldStringsList`
in the example.

### Example

GPB:

```
message Data {
    optional string     field = 1;                          // REQUIRED
    optional int32      field_int_name = 2;                 // REQUIRED
    optional int32      field_option = 3;                   // OPTIONAL
    repeated string     field_strings = 4;                  // REQUIRED
    repeated string     field_strings_name = 5;             // REQUIRED
    repeated int32      field_option_integers = 6;          // OPTIONAL
    repeated int32      field_option_integers_empty = 7;    // OPTIONAL
    optional Data2      field_gpb_option = 8; 	            // OPTIONAL
    optional Data2      field_gpb_option_empty = 9;         // OPTIONAL
    optional Data2      field_gpb = 10;   		            // REQUIRED
    optional bytes      field_blob = 11;                    // REQUIRED
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
  fieldStringsList: Seq[String],
  fieldOptionIntegersList: Option[Seq[Int]],
  fieldOptionIntegersEmptyList: Option[List[Int]])

case class CaseClassB(fieldDouble: Double, @GpbName("fieldBlob") fieldString: String)


object Test extends App {
  import com.avast.cactus._
  
  val caseClass = CaseClassA(
    field = "ahoj",
    fieldInt = 9,
    fieldOption = Some(13),
    fieldBlob = ByteString.EMPTY,
    fieldStrings2 = Vector("a"),
    fieldGpb = CaseClassB(0.9, "text"),
    fieldGpbOption = Some(CaseClassB(0.9, "text")),
    fieldGpbOptionEmpty = None,
    fieldStringsList = Seq("a", "b"),
    fieldOptionIntegersList = Some(Seq(3, 6)),
    fieldOptionIntegersEmptyList = None
  )

  caseClass.asGpb[Data] match {
    case Right(inst) =>
      println(inst)

    case Left(f) =>
      println(f)
  }
}

```
See [unit tests](macros/src/test/scala/com/avast/cactus/CactusMacrosTest.scala) for more examples.

## Appendix

The library is compiled with dependency on GPB 2.6.1. Since it use the `MessageLite` interface only as a "marker trait", it should be safe to exclude the GPB
dependency and use v 2.5.0 or 2.4.1 instead.


Author: Jan Kolena (kolena[at]avast.com), [Avast Software s.r.o.](https://www.avast.com)
