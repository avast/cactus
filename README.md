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
It also automatically converts between common data types. Note: `Option[List[A]]` never contains an empty collection; that is turned to `None`.

Currently there is only one possible failure (`MissingFieldFailure`) which contains the name of the missing field.

It is common that the field has different name in the GPB and in the case class. You can use `GpbName` annotation 
to override the expected name - see the example.

You have the option to either add or not suffix `List` to the name of the case class field when mapping a collection - 
see `fieldString2` vs. `fieldStringsList` in the example.

Mapping of complex messages (message contains another message which contains another message) is supported.
However **recursive** mapping (field with `Data` type in `Data` class) is **NOT supported**. 

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
// your own converters:
implicit val StringToByteStringConver: Convert[String, ByteString] = Convert((b: String) => ByteString.copyFromUtf8(b))
implicit val ByteStringToStringConver: Convert[ByteString, String] = Convert((b: ByteString) => b.toStringUtf8)

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
  fieldStringsList: immutable.Seq[String],
  fieldOptionIntegersList: Option[List[Int]],
  fieldOptionIntegersEmptyList: Option[List[Int]]
)

case class CaseClassB(
  fieldDouble: Double, 
  @GpbName("fieldBlob")
  fieldString: String // this is possible thanks to the user-specified converter, it's `ByteString` in the GPB
)

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

Collections (`java.util.List<T>`) are converted to `scala.collection.immutable.Seq[T]` -
concretely to `scala.collection.immutable.List[T]`. Default Scala `Seq[T]` is mutable and cannot be used in the target 
case class. This is intentional design of the converter since both GPB and case classes are meant to be immutable. 

## Case class to GPB

Case class can be mapped back to GPB as easily as in the other direction. The source case class can contain an arbitrary 
scala collection (even mutable).

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

The library is compiled with dependency on GPB 2.6.1. It should be safe to exclude the dependency and use version
`2.5.x` or `2.4.x` instead since it uses the `MessageLite` interface only as a "marker trait".
